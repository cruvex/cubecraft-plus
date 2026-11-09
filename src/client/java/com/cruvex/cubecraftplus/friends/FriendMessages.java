package com.cruvex.cubecraftplus.friends;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.debug.Debug;
import com.cruvex.cubecraftplus.game.CubeCraftManager;
import com.cruvex.cubecraftplus.util.ModPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Saves messages sent to and from friends, one file per friend. */
public class FriendMessages {

    private static final String NAME = "\\W*([a-zA-Z0-9_]{2,16})\\W*";
    private static final Pattern SENT = Pattern.compile("^\\[Friend] Me -> " + NAME + " : (.*)$");
    private static final Pattern RECEIVED = Pattern.compile("^\\[Friend] " + NAME + " -> Me : (.*)$");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private static FriendMessages instance;

    /** One thread, so lines are written in the order the messages arrived. */
    private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "CubeCraftPlus Messages");
        thread.setDaemon(true);
        return thread;
    });

    /** Each save waits for the one before it, so a slow uuid lookup can't reorder messages. */
    private CompletableFuture<Void> lastSave = CompletableFuture.completedFuture(null);
    private final Map<UUID, Integer> saves = new ConcurrentHashMap<>();

    /** One line of a history file; {@code name} is the friend's name at the time. */
    public record Message(long time, boolean out, String name, String text) {
    }

    public static FriendMessages getInstance() {
        if (instance == null) {
            instance = new FriendMessages();
        }
        return instance;
    }

    public void init() {
        ClientReceiveMessageEvents.GAME.register(this::onGameMessage);
    }

    private void onGameMessage(Component component, boolean overlay) {
        if (overlay || !CubeCraftManager.getInstance().isOnCubeCraft()) return;

        Message message = parse(component.getString());
        UUID account = FriendsManager.account();
        if (message == null || account == null) return;

        CompletableFuture<UUID> lookup = FriendsManager.getInstance().confirmOnline(message.name()).exceptionally(error -> null);
        lastSave = lastSave.exceptionally(error -> null).thenCombineAsync(lookup, (previous, id) -> {
            save(account, id, message);
            if (id != null) {
                saves.merge(id, 1, Integer::sum);
            }
            return null;
        }, writer);
    }

    /** Changes whenever a message with this friend is saved, so an open history knows to reload. */
    public int saveCount(UUID friend) {
        return saves.getOrDefault(friend, 0);
    }

    public boolean hasHistory(UUID friend) {
        UUID account = FriendsManager.account();
        return account != null && Files.exists(historyFile(account, friend));
    }

    /** Oldest first; on the writer thread, so it includes everything already queued to be saved. */
    public CompletableFuture<List<Message>> load(UUID friend) {
        UUID account = FriendsManager.account();
        if (account == null) return CompletableFuture.completedFuture(List.of());

        return CompletableFuture.supplyAsync(() -> read(historyFile(account, friend)), writer);
    }

    private static Path historyFile(UUID account, UUID friend) {
        return ModPaths.messages(account).resolve(friend + ".jsonl");
    }

    private static List<Message> read(Path file) {
        List<Message> messages = new ArrayList<>();
        if (!Files.exists(file)) return messages;

        try {
            for (String line : Files.readAllLines(file)) {
                Message message = parseLine(line);
                if (message != null) {
                    messages.add(message);
                }
            }
        } catch (IOException e) {
            CubeCraftPlusClient.LOGGER.warn("Failed to read messages from {}", file, e);
        }
        return messages;
    }

    /** Null for a line a crash cut short, so one bad line doesn't lose the rest. */
    private static @Nullable Message parseLine(String line) {
        try {
            return GSON.fromJson(line, Message.class);
        } catch (JsonParseException e) {
            return null;
        }
    }

    private static @Nullable Message parse(String text) {
        Matcher sent = SENT.matcher(text);
        if (sent.matches()) return new Message(System.currentTimeMillis(), true, sent.group(1), sent.group(2));

        Matcher received = RECEIVED.matcher(text);
        if (received.matches()) return new Message(System.currentTimeMillis(), false, received.group(1), received.group(2));
        return null;
    }

    /** Saves under the friend's uuid, or under their name in unresolved/ while it is not known. */
    private static void save(UUID account, @Nullable UUID id, Message message) {
        Path folder = ModPaths.messages(account);
        Path unresolved = folder.resolve("unresolved").resolve(message.name().toLowerCase(Locale.ROOT) + ".jsonl");
        try {
            if (id == null) {
                append(unresolved, GSON.toJson(message) + "\n");
                Debug.log("Messages: no uuid for {} yet, saved in unresolved", message.name());
                return;
            }

            Path friend = historyFile(account, id);
            // Older lines, saved while the uuid was unknown
            if (Files.exists(unresolved)) {
                append(friend, Files.readString(unresolved));
                Files.delete(unresolved);
            }
            append(friend, GSON.toJson(message) + "\n");
            Debug.log("Messages: saved message {} {}", message.out() ? "to" : "from", message.name());
        } catch (IOException e) {
            CubeCraftPlusClient.LOGGER.warn("Failed to save a message with {}", message.name(), e);
        }
    }

    private static void append(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
