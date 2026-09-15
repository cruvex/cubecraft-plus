package com.cruvex.cubecraftplus.managers;

import com.cruvex.cubecraftplus.util.Debug;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/** Runs server commands one at a time and captures their chat replies. Client thread only. */
public class ChatQueryManager {

    private static final long TIMEOUT_MS = 5000;
    /** Silence that ends a reply, since CubeCraft sends no footer. */
    private static final long QUIET_MS = 250;
    /** CubeCraft rejects a command within ~1s of the last accepted one on its cooldown. */
    private static final long COMMAND_INTERVAL_MS = 1100;
    private static final int MAX_ATTEMPTS = 3;
    private static final Pattern TOO_FAST = Pattern.compile("^You are executing this command too fast!");

    private static ChatQueryManager instance;

    /** {@code millis} is the time from sending the command to its header arriving. */
    public record Reply(Component header, List<Component> lines, long millis) {
    }

    private record Query(String command, Pattern header, Pattern line, boolean hide,
                         CompletableFuture<Reply> future) {
    }

    private final Deque<Query> queue = new ArrayDeque<>();
    /** First words of player commands on the queries' cooldown, see shareCooldown. */
    private final Set<String> cooldownCommands = new HashSet<>();
    /** Player commands held back while queries run. */
    private final Deque<String> held = new ArrayDeque<>();
    private long nextSendAt;
    /** Whether the running cooldown was started by a query rather than the player. */
    private boolean queryCooldown;
    /** Keeps our own sends from being held. */
    private boolean sending;

    // The head's reply so far; sentAt is 0 while unsent
    private long sentAt;
    private int attempts;
    private @Nullable Component header;
    private long headerAt;
    private final List<Component> lines = new ArrayList<>();
    private long lastMessageAt;

    public static ChatQueryManager getInstance() {
        if (instance == null) {
            instance = new ChatQueryManager();
        }
        return instance;
    }

    public void init() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        // Clicked commands skip this event; ClientPacketListenerMixin holds those
        ClientSendMessageEvents.ALLOW_COMMAND.register(command -> !hold(command));
    }

    /** Queues a command without its slash; the reply is its header plus matching lines until quiet. */
    public CompletableFuture<Reply> send(String command, Pattern header, Pattern line, boolean hide) {
        Query query = new Query(command, header, line, hide, new CompletableFuture<>());
        queue.add(query);
        return query.future();
    }

    /** Registers commands, by first word, whose cooldown the player shares with queries. */
    public void shareCooldown(Collection<String> commands) {
        commands.forEach(command -> cooldownCommands.add(command.toLowerCase(Locale.ROOT)));
    }

    /** Holds a player command on the shared cooldown while queries are in the way; true means don't send it. */
    public boolean hold(String command) {
        if (sending) return false;

        String trimmed = command.startsWith("/") ? command.substring(1) : command;
        if (!isCooldownCommand(trimmed)) return false;

        long now = System.currentTimeMillis();
        boolean queriesInTheWay = !queue.isEmpty() || !held.isEmpty() || (queryCooldown && now < nextSendAt);
        if (!queriesInTheWay) {
            nextSendAt = now + COMMAND_INTERVAL_MS;
            queryCooldown = false;
            return false;
        }

        Debug.log("ChatQuery: holding /{} until the query in flight is done", trimmed);
        held.add(trimmed);
        return true;
    }

    private void onEndTick(Minecraft client) {
        if (queue.isEmpty() && held.isEmpty()) return;

        ClientPacketListener connection = client.getConnection();
        if (connection == null) {
            Debug.log("ChatQuery: not connected, dropping {} queries and {} held commands",
                    queue.size(), held.size());
            held.clear();
            // Copied first so a callback queueing a retry can't disturb the loop
            List<Query> dropped = List.copyOf(queue);
            queue.clear();
            clearReply();
            dropped.forEach(queued -> queued.future().cancel(false));
            return;
        }

        long now = System.currentTimeMillis();

        // Held commands go first, but not while a query awaits a reply theirs could be mistaken for
        if (!held.isEmpty() && sentAt == 0) {
            if (now < nextSendAt) return;

            String command = held.poll();
            Debug.log("ChatQuery: sending held /{}", command);
            dispatch(connection, command, now, false);
            return;
        }

        Query query = queue.peek();
        if (query == null) return;

        if (sentAt == 0) {
            if (now < nextSendAt) return;

            attempts++;
            Debug.log("ChatQuery: sending /{} (attempt {})", query.command(), attempts);
            dispatch(connection, query.command(), now, true);
            sentAt = now;
        } else if (header != null) {
            if (now - lastMessageAt <= QUIET_MS) return;

            Reply reply = new Reply(header, List.copyOf(lines), headerAt - sentAt);
            Debug.log("ChatQuery: /{} replied with {} lines in {}ms",
                    query.command(), reply.lines().size(), lastMessageAt - sentAt);
            finish();
            query.future().complete(reply);
        } else if (now - sentAt > TIMEOUT_MS) {
            Debug.log("ChatQuery: no reply to /{}", query.command());
            finish();
            query.future().completeExceptionally(new TimeoutException("No reply to /" + query.command()));
        }
    }

    /** Offers a server chat message to the query in flight; returns whether to hide it. */
    public boolean onServerMessage(Component message) {
        Query query = queue.peek();
        if (query == null || sentAt == 0) return false;

        String text = message.getString();
        // Rejected before replying: resend once the cooldown allows
        if (header == null && attempts < MAX_ATTEMPTS && TOO_FAST.matcher(text).find()) {
            Debug.log("ChatQuery: /{} turned away as too fast", query.command());
            sentAt = 0;
            return query.hide();
        }

        Pattern pattern = header == null ? query.header() : query.line();
        if (!pattern.matcher(text).find()) return false;

        long now = System.currentTimeMillis();
        if (header == null) {
            header = message;
            headerAt = now;
        } else {
            lines.add(message);
        }
        lastMessageAt = now;
        return query.hide();
    }

    private void dispatch(ClientPacketListener connection, String command, long now, boolean fromQuery) {
        sending = true;
        try {
            connection.sendCommand(command);
        } finally {
            sending = false;
        }
        nextSendAt = now + COMMAND_INTERVAL_MS;
        queryCooldown = fromQuery;
    }

    private boolean isCooldownCommand(String command) {
        int space = command.indexOf(' ');
        String name = space < 0 ? command : command.substring(0, space);
        return cooldownCommands.contains(name.toLowerCase(Locale.ROOT));
    }

    // Dequeue before completing, so a callback can queue the next query
    private void finish() {
        queue.poll();
        clearReply();
    }

    private void clearReply() {
        sentAt = 0;
        attempts = 0;
        header = null;
        lines.clear();
    }
}
