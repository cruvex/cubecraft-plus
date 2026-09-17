package com.cruvex.cubecraftplus.managers;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.events.CubeEvents;
import com.cruvex.cubecraftplus.util.ModPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.cruvex.cubecraftplus.model.Friend;
import com.cruvex.cubecraftplus.util.Debug;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** Reads CubeCraft's paginated friends list from chat, see docs/chat-command-capture.md. */
public class FriendsManager {

    private static final Pattern HEADER = Pattern.compile("^-+ Friends ");
    /** Absent when the list fits on one page. */
    private static final Pattern PAGE = Pattern.compile("(\\d+)/(\\d+)");
    /** A name with any rank symbols around it, but no comma: that separates names sharing a line. */
    private static final String LISTED_NAME = "[^a-zA-Z0-9_,]*[a-zA-Z0-9_]{2,16}[^a-zA-Z0-9_,]*";
    /** Friends with the same status share a line: {@code RodzZ__, TakeMyGear - Playing Team EggWars ...}. */
    private static final Pattern LINE = Pattern.compile("^(" + LISTED_NAME + "(?:," + LISTED_NAME + ")*) - (.+)$");
    private static final Pattern NAME_IN_LINE = Pattern.compile("[a-zA-Z0-9_]{2,16}");
    /** Lines, not friends: a shared line counts once. */
    private static final int LINES_PER_PAGE = 10;

    // What CubeCraft says when the list changes, see docs/friends-tracking.md
    private static final String NAME = "\\W*([a-zA-Z0-9_]{2,16})\\W*";
    private static final Pattern JOINED = Pattern.compile("^\\[Friend] \\[\\+] " + NAME + " joined the network\\.$");
    private static final Pattern LEFT = Pattern.compile("^\\[Friend] \\[-] " + NAME + " left the network\\.$");
    private static final Pattern THEY_ACCEPTED = Pattern.compile("^" + NAME + " has accepted your friend request\\.$");
    private static final Pattern YOU_ACCEPTED = Pattern.compile("^You are now friends with " + NAME + "\\.$");
    private static final Pattern REMOVED_YOU = Pattern.compile("^" + NAME + " has removed you from their friends list!$");
    private static final Pattern YOU_REMOVED = Pattern.compile("^You are no longer friends with " + NAME + "\\.$");

    /** CubeCraft's own words; an online friend's status says where they are instead. */
    private static final String ONLINE = "Online";
    private static final String OFFLINE = "Offline";

    /** Share a cooldown with the page queries; /fmsg does not. */
    private static final Set<String> COMMANDS = Set.of("f", "fl", "friend", "friends");
    /** Gives the proxy time to settle before the first commands after joining. */
    private static final long JOIN_DELAY_MS = 5000;
    /** How stale online friends' statuses may get while something is showing them. */
    private static final long ONLINE_CHECK_MS = 10_000;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static FriendsManager instance;

    private List<Friend> friends = List.of();
    private @Nullable CompletableFuture<List<Friend>> refreshing;
    private @Nullable CompletableFuture<Void> checkingOnline;
    /** Counts friend messages, so a load can tell whether the list moved under its pages. */
    private int friendMessages;
    /** When online friends' statuses were last read, by a full load or an online check. */
    private long onlineCheckedAt;
    private long joinedAt;

    /** The description is debug output, with {@code {}} for the name. */
    private record Event(Pattern pattern, String describes, Consumer<String> apply) {
    }

    private final List<Event> events = List.of(
            new Event(JOINED, "{} came online", name -> setOnline(name, true)),
            new Event(LEFT, "{} went offline", name -> setOnline(name, false)),
            // Accepting is something they just did, so they are online; the player accepting says nothing
            new Event(THEY_ACCEPTED, "{} accepted your request", name -> add(new Friend(name, true, ONLINE))),
            new Event(YOU_ACCEPTED, "you accepted {}'s request", name -> add(new Friend(name, false, ""))),
            new Event(REMOVED_YOU, "{} removed you", this::remove),
            new Event(YOU_REMOVED, "you removed {}", this::remove));

    private record Page(int number, int total, List<Friend> friends) {
    }

    public static FriendsManager getInstance() {
        if (instance == null) {
            instance = new FriendsManager();
        }
        return instance;
    }

    public void init() {
        ChatQueryManager.getInstance().shareCooldown(COMMANDS);
        CubeEvents.CUBE_JOIN.register(this::onCubeJoin);
        ClientReceiveMessageEvents.GAME.register(this::onGameMessage);
        // Can fire off the client thread
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> friends = List.of()));
    }

    /** Replaced rather than modified, so a changed reference means a changed list. */
    public List<Friend> getFriends() {
        return friends;
    }

    public boolean isRefreshing() {
        return refreshing != null && !refreshing.isDone();
    }

    /** Why a refresh failed, for the player: unwraps the CompletionException a failed page arrives in. */
    public static String failureReason(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return cause instanceof CancellationException ? "disconnected" : cause.getMessage();
    }

    /** Fetches and stores the whole list, sharing a refresh that is already running. */
    public CompletableFuture<List<Friend>> refresh() {
        if (isRefreshing()) return refreshing;

        refreshing = walk(false);
        return refreshing;
    }

    /**
     * A load the list changed under runs once more: shifting pages list one friend twice and can skip
     * another, and a skipped friend would later look like they had removed the player.
     */
    private CompletableFuture<List<Friend>> walk(boolean retried) {
        int messagesBefore = friendMessages;
        return fetchPages().thenCompose(pages -> {
            List<Friend> loaded = merge(pages);
            friends = loaded;
            onlineCheckedAt = System.currentTimeMillis();

            int listed = pages.stream().mapToInt(page -> page.friends().size()).sum();
            if (friendMessages == messagesBefore && listed == loaded.size()) {
                persist();
                return CompletableFuture.completedFuture(loaded);
            }
            if (retried) {
                Debug.log("Friends: list changed under both loads, not saving it");
                return CompletableFuture.completedFuture(loaded);
            }

            Debug.log("Friends: list changed while loading, loading again");
            return walk(true);
        });
    }

    /** {@link #checkOnlineNow}, at most once per {@link #ONLINE_CHECK_MS}. */
    public void checkOnline() {
        if (System.currentTimeMillis() - onlineCheckedAt >= ONLINE_CHECK_MS) {
            checkOnlineNow();
        }
    }

    /**
     * Re-reads where online friends are, since a join or leave message does not say. Online friends are
     * listed first, so this is usually a single page. Skipped while a load, which reads them anyway, or
     * another check is running, and while the proxy settles after joining.
     */
    public void checkOnlineNow() {
        long now = System.currentTimeMillis();
        if (isRefreshing() || isCheckingOnline() || now - joinedAt < JOIN_DELAY_MS) return;
        if (!CubeCraftManager.getInstance().isOnCubeCraft()) return;

        onlineCheckedAt = now;
        int messagesBefore = friendMessages;
        checkingOnline = fetchOnline(1, new ArrayList<>())
                .thenAccept(online -> {
                    if (friendMessages != messagesBefore) {
                        Debug.log("Friends: list changed during the online check, ignoring it");
                        return;
                    }
                    applyOnline(online);
                })
                .whenComplete((done, error) -> {
                    if (error != null) {
                        Debug.log("Friends: online check failed: {}", failureReason(error));
                    }
                });
    }

    private boolean isCheckingOnline() {
        return checkingOnline != null && !checkingOnline.isDone();
    }

    /** Fetches pages until one lists an offline friend, which ends the online part of the list. */
    private static CompletableFuture<List<Friend>> fetchOnline(int number, List<Friend> online) {
        return fetchPage(number).thenCompose(page -> {
            page.friends().stream().filter(Friend::online).forEach(online::add);

            boolean allOnline = page.friends().stream().allMatch(Friend::online);
            return allOnline && page.number() < page.total()
                    ? fetchOnline(number + 1, online)
                    : CompletableFuture.completedFuture(online);
        });
    }

    private void applyOnline(List<Friend> online) {
        Map<String, Friend> byName = new HashMap<>();
        for (Friend friend : online) {
            byName.put(friend.name().toLowerCase(Locale.ROOT), friend);
        }

        List<Friend> updated = friends.stream()
                .map(friend -> {
                    Friend listed = byName.get(friend.name().toLowerCase(Locale.ROOT));
                    if (listed != null) return new Friend(friend.name(), true, listed.status());
                    // Online here but not on the online pages, so a leave message was missed
                    return friend.online() ? new Friend(friend.name(), false, OFFLINE) : friend;
                })
                .toList();
        Debug.log("Friends: checked {} online friends", online.size());

        // A new list rebuilds an open screen's rows, so only replace it when something changed
        if (!updated.equals(friends)) {
            friends = updated;
        }
    }

    private void onGameMessage(Component message, boolean overlay) {
        if (overlay) return;

        String text = message.getString();
        for (Event event : events) {
            Matcher matcher = event.pattern().matcher(text);
            if (!matcher.matches()) continue;

            String name = matcher.group(1);
            Debug.log("Friends: " + event.describes(), name);
            event.apply().accept(name);

            friendMessages++;
            if (isRefreshing() || isCheckingOnline()) {
                Debug.log("Friends: that arrived during a load");
            }
            return;
        }
    }

    /** Only names are saved, so a status change needs no write. */
    private void setOnline(String name, boolean online) {
        if (!known(name)) {
            Debug.log("Friends: {} is not in the list", name);
            return;
        }

        // A notification does not say where they went, so this is all the status there is until a load
        String status = online ? ONLINE : OFFLINE;
        friends = friends.stream()
                .map(friend -> friend.name().equalsIgnoreCase(name) ? new Friend(friend.name(), online, status) : friend)
                .toList();
    }

    private void add(Friend added) {
        if (known(added.name())) {
            Debug.log("Friends: {} was already in the list", added.name());
            return;
        }

        friends = Stream.concat(friends.stream(), Stream.of(added)).toList();
        persist();
    }

    private void remove(String name) {
        if (!known(name)) {
            Debug.log("Friends: {} is not in the list", name);
            return;
        }

        friends = friends.stream().filter(friend -> !friend.name().equalsIgnoreCase(name)).toList();
        persist();
    }

    private boolean known(String name) {
        return friends.stream().anyMatch(friend -> friend.name().equalsIgnoreCase(name));
    }

    private void persist() {
        UUID account = account();
        if (account != null) {
            save(account, friends);
        }
    }

    private record Saved(long savedAt, List<String> names) {
    }

    private static List<String> loadSaved(UUID account) {
        Path path = ModPaths.friends(account);
        if (!Files.exists(path)) return List.of();

        try (Reader reader = Files.newBufferedReader(path)) {
            Saved saved = GSON.fromJson(reader, Saved.class);
            return saved == null || saved.names() == null ? List.of() : saved.names();
        } catch (IOException | JsonParseException e) {
            CubeCraftPlusClient.LOGGER.warn("Failed to read {}, ignoring it", ModPaths.display(path), e);
            return List.of();
        }
    }

    private static void save(UUID account, List<Friend> friends) {
        Path path = ModPaths.friends(account);
        Saved saved = new Saved(System.currentTimeMillis(), friends.stream().map(Friend::name).toList());
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(saved, writer);
            }
        } catch (IOException e) {
            CubeCraftPlusClient.LOGGER.warn("Failed to save {}", ModPaths.display(path), e);
        }
    }

    private static @Nullable UUID account() {
        Minecraft client = Minecraft.getInstance();
        return client.player == null ? null : client.player.getUUID();
    }

    private void onCubeJoin() {
        // The join load below reads the statuses, so an open screen need not check before it starts
        joinedAt = System.currentTimeMillis();
        onlineCheckedAt = joinedAt;

        UUID account = account();
        if (account != null) {
            // Known names until the load below replaces them; last session's status would be stale
            friends = loadSaved(account).stream().map(name -> new Friend(name, false, "")).toList();
        }

        Minecraft client = Minecraft.getInstance();
        CompletableFuture.delayedExecutor(JOIN_DELAY_MS, TimeUnit.MILLISECONDS, client::execute)
                .execute(() -> refresh().whenComplete((loaded, error) -> {
                    if (error != null) {
                        Debug.log("Friends: loading on join failed: {}", error.getMessage());
                    } else {
                        Debug.log("Friends: loaded {} friends on join", loaded.size());
                    }
                }));
    }

    /** Fetches every page, hidden from chat. */
    private CompletableFuture<List<Page>> fetchPages() {
        if (!CubeCraftManager.getInstance().isOnCubeCraft()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Not on CubeCraft"));
        }

        return fetchPage(1).thenCompose(first -> {
            List<CompletableFuture<ChatQueryManager.Reply>> replies = IntStream.rangeClosed(2, first.total())
                    .mapToObj(FriendsManager::query)
                    .toList();
            List<CompletableFuture<Page>> pages = IntStream.range(0, replies.size())
                    .mapToObj(index -> replies.get(index).thenApply(reply -> parsePage(reply, index + 2)))
                    .toList();

            // One page failing dooms the load, so stop sending the rest rather than run them for nothing
            CompletableFuture<List<Page>> result = new CompletableFuture<>();
            pages.forEach(page -> page.whenComplete((value, error) -> {
                if (error == null) return;

                result.completeExceptionally(error);
                replies.forEach(reply -> reply.cancel(false));
            }));

            CompletableFuture.allOf(pages.toArray(CompletableFuture[]::new))
                    .thenApply(done -> Stream.concat(Stream.of(first), pages.stream().map(CompletableFuture::join)).toList())
                    .thenAccept(result::complete);
            return result;
        });
    }

    /** Merges pages by name, since a friend coming online mid-walk can land on two pages. */
    private static List<Friend> merge(List<Page> pages) {
        Map<String, Friend> byName = new LinkedHashMap<>();
        for (Page page : pages) {
            for (Friend friend : page.friends()) {
                byName.putIfAbsent(friend.name().toLowerCase(Locale.ROOT), friend);
            }
        }
        return List.copyOf(byName.values());
    }

    private static CompletableFuture<Page> fetchPage(int number) {
        return query(number).thenApply(reply -> parsePage(reply, number));
    }

    private static CompletableFuture<ChatQueryManager.Reply> query(int number) {
        String command = number == 1 ? "friend list" : "friend list " + number;
        return ChatQueryManager.getInstance().send(command, HEADER, LINE, true);
    }

    /** A reply the query manager gave up on can be read as the next page's, so the header has to agree. */
    private static Page parsePage(ChatQueryManager.Reply reply, int expected) {
        Matcher counter = PAGE.matcher(reply.header().getString());
        boolean paged = counter.find();
        int number = paged ? Integer.parseInt(counter.group(1)) : 1;
        int total = paged ? Integer.parseInt(counter.group(2)) : 1;

        if (number != expected) {
            throw new IllegalStateException("Asked for friends page " + expected + ", got " + number);
        }
        // Only the last page can be short, so a short earlier page means a line did not match and would be lost
        if (number < total && reply.lines().size() != LINES_PER_PAGE) {
            throw new IllegalStateException("Friends page " + number + " had " + reply.lines().size()
                    + " lines, expected " + LINES_PER_PAGE);
        }

        List<Friend> friends = reply.lines().stream().flatMap(line -> parseLine(line).stream()).toList();
        return new Page(number, total, friends);
    }

    private static List<Friend> parseLine(Component line) {
        Matcher matcher = LINE.matcher(line.getString());
        if (!matcher.find()) {
            throw new IllegalStateException("Not a friend line: " + line.getString());
        }

        String status = matcher.group(2);
        boolean online = !status.equals(OFFLINE);
        return NAME_IN_LINE.matcher(matcher.group(1)).results()
                .map(name -> new Friend(name.group(), online, status))
                .toList();
    }
}
