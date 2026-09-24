package com.cruvex.cubecraftplus.friends;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.chat.ChatQueryManager;
import com.cruvex.cubecraftplus.debug.Debug;
import com.cruvex.cubecraftplus.game.CubeCraftManager;
import com.cruvex.cubecraftplus.game.CubeEvents;
import com.cruvex.cubecraftplus.util.ModPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
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
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Holds the friends list: read from chat on join and refresh, kept current from friend messages. */
public class FriendsManager {

    /** Or, with no friends, the whole reply: one line in place of the header, read as an empty page. */
    private static final Pattern HEADER = Pattern.compile("^-+ Friends |^You do not have any friends!$");
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

    /** Commands that share a cooldown with the page queries; /fmsg does not. */
    private static final Set<String> COMMANDS = Set.of("f", "fl", "friend", "friends");
    /** How long after joining to wait before the first commands, for the proxy to settle. */
    private static final long JOIN_DELAY_MS = 5000;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static FriendsManager instance;

    private List<Friend> friends = List.of();
    /** Uuids by lowercase listed name, from lookups while the friend was online or carried over a rename. */
    private final Map<String, UUID> ids = new HashMap<>();
    /** Whether the list came from the server this session, rather than being last session's names or nothing. */
    private boolean listLoaded;
    private @Nullable CompletableFuture<List<Friend>> refreshing;
    private @Nullable CompletableFuture<Void> checkingOnline;
    /** Friend messages seen, which a load compares before and after to spot a list that moved. */
    private int friendMessages;
    private long joinedAt;

    /** The description is debug output, with {@code {}} for the name. */
    private record Event(Pattern pattern, String describes, Consumer<String> apply) {
    }

    private final List<Event> events = List.of(
            new Event(JOINED, "{} came online", name -> setOnline(name, true)),
            new Event(LEFT, "{} went offline", name -> setOnline(name, false)),
            // They just accepted, so they are online; the player accepting says nothing about them
            new Event(THEY_ACCEPTED, "{} accepted your request", name -> add(new Friend(name, true, ONLINE))),
            new Event(YOU_ACCEPTED, "you accepted {}'s request", name -> add(new Friend(name, false, ""))),
            new Event(REMOVED_YOU, "{} removed you", this::remove),
            new Event(YOU_REMOVED, "you removed {}", this::remove));

    private record Page(int number, int total, List<Friend> friends) {
    }

    /** {@code names} is the format from before uuids were saved. */
    private record Saved(long savedAt, @Nullable List<SavedFriend> friends, @Nullable List<String> names) {
    }

    private record SavedFriend(String name, @Nullable UUID id) {
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
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            friends = List.of();
            listLoaded = false;
        }));
    }

    /** Replaced on every change, never modified, so a changed reference means a changed list. */
    public List<Friend> getFriends() {
        return friends;
    }

    public boolean isRefreshing() {
        return refreshing != null && !refreshing.isDone();
    }

    public boolean isLoaded() {
        return listLoaded;
    }

    /** Why a refresh failed, in words for the player, unwrapping the CompletionException it arrives in. */
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

    /** Pages through the whole list, and runs once more when it changed underneath. */
    private CompletableFuture<List<Friend>> walk(boolean retried) {
        if (!CubeCraftManager.getInstance().isOnCubeCraft()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Not on CubeCraft"));
        }

        int messagesBefore = friendMessages;
        return fetchPages(page -> true, this::show).thenCompose(pages -> {
            List<Friend> loaded = identified(merge(pages));
            friends = loaded;
            listLoaded = true;
            lookUpUnconfirmed();

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

    /** Applies the online friends a walk has already listed, without waiting for the rest of it. */
    private void show(List<Page> walked) {
        if (allOnline(walked.getLast())) return;

        applyOnline(online(walked));
    }

    /** Re-reads which friends are online and where, which join and leave messages do not say. */
    public void checkOnline() {
        if (isRefreshing() || isCheckingOnline() || System.currentTimeMillis() - joinedAt < JOIN_DELAY_MS) return;
        if (!CubeCraftManager.getInstance().isOnCubeCraft()) return;
        if (CubeCraftManager.getInstance().isAfk()) return;

        int messagesBefore = friendMessages;
        checkingOnline = fetchPages(FriendsManager::allOnline)
                .thenAccept(pages -> {
                    if (friendMessages != messagesBefore) {
                        Debug.log("Friends: list changed during the online check, ignoring it");
                        return;
                    }
                    applyOnline(online(pages));
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

    private void applyOnline(List<Friend> online) {
        Map<String, Friend> byName = new HashMap<>();
        for (Friend friend : online) {
            byName.put(friend.name().toLowerCase(Locale.ROOT), friend);
        }

        List<Friend> updated = friends.stream()
                .map(friend -> {
                    Friend listed = byName.get(friend.name().toLowerCase(Locale.ROOT));
                    if (listed != null) return friend.withStatus(true, listed.status());
                    // Online here but not listed online, so a leave message was missed
                    return friend.online() ? friend.withStatus(false, OFFLINE) : friend;
                })
                .toList();

        // Only replaced when something changed; a new list rebuilds an open screen's rows
        if (!updated.equals(friends)) {
            friends = updated;
            Debug.log("Friends: {} friends online", online.size());
            lookUpUnconfirmed();
        }
    }

    private void onGameMessage(Component message, boolean overlay) {
        // CubeCraft only: other servers word their friend messages the same way
        if (overlay || !CubeCraftManager.getInstance().isOnCubeCraft()) return;

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

    /** Sets a friend's online state and status, without saving. */
    private void setOnline(String name, boolean online) {
        if (!known(name)) {
            Debug.log("Friends: {} is not in the list", name);
            return;
        }

        String status = online ? ONLINE : OFFLINE;
        friends = friends.stream()
                .map(friend -> friend.name().equalsIgnoreCase(name) ? friend.withStatus(online, status) : friend)
                .toList();
        if (online) {
            lookUpUnconfirmed();
        }
    }

    private void add(Friend added) {
        if (known(added.name())) {
            Debug.log("Friends: {} was already in the list", added.name());
            return;
        }

        friends = Stream.concat(friends.stream(), Stream.of(added)).toList();
        persist();
        lookUpUnconfirmed();
    }

    private void remove(String name) {
        if (!known(name)) {
            Debug.log("Friends: {} is not in the list", name);
            return;
        }

        friends = friends.stream().filter(friend -> !friend.name().equalsIgnoreCase(name)).toList();
        ids.remove(name.toLowerCase(Locale.ROOT));
        persist();
    }

    private boolean known(String name) {
        return friends.stream().anyMatch(friend -> friend.name().equalsIgnoreCase(name));
    }

    private List<Friend> identified(List<Friend> list) {
        return list.stream()
                .map(friend -> friend.withId(ids.get(friend.name().toLowerCase(Locale.ROOT))))
                .toList();
    }

    /** Looks up friends without a uuid who are online or were never looked up. */
    private void lookUpUnconfirmed() {
        HeadResolver resolver = HeadResolver.getInstance();
        List<String> names = friends.stream()
                .filter(friend -> friend.id() == null && (friend.online() || resolver.idFor(friend.name()) == null))
                .map(Friend::name)
                .toList();
        if (names.isEmpty()) return;

        resolver.lookUp(names).thenAcceptAsync(this::confirm, Minecraft.getInstance());
    }

    /** Keeps a looked-up uuid for online friends, and for a friend whose uuid was known under an old name. */
    private void confirm(Map<String, UUID> found) {
        boolean changed = false;
        for (Friend friend : friends) {
            UUID id = found.get(friend.name().toLowerCase(Locale.ROOT));
            if (friend.id() != null || id == null) continue;

            String oldName = renamedFrom(id);
            if (oldName == null && !friend.online()) continue;

            if (oldName != null) {
                Debug.log("Friends: {} renamed to {}", oldName, friend.name());
                ids.remove(oldName);
            }
            ids.put(friend.name().toLowerCase(Locale.ROOT), id);
            changed = true;
        }

        if (changed) {
            friends = identified(friends);
            persist();
        }
    }

    /** The listed name a known uuid had, if that name is no longer in the list. */
    private @Nullable String renamedFrom(UUID id) {
        return ids.entrySet().stream()
                .filter(entry -> entry.getValue().equals(id) && !known(entry.getKey()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private void onCubeJoin() {
        joinedAt = System.currentTimeMillis();

        UUID account = account();
        if (account != null) {
            // Last session's list, until the load below replaces it
            friends = loadSaved(account);
            ids.clear();
            for (Friend friend : friends) {
                if (friend.id() != null) {
                    ids.put(friend.name().toLowerCase(Locale.ROOT), friend.id());
                }
            }
        }

        Minecraft client = Minecraft.getInstance();
        CompletableFuture.delayedExecutor(JOIN_DELAY_MS, TimeUnit.MILLISECONDS, client::execute)
                .execute(() -> refresh().whenComplete((loaded, error) -> {
                    if (error != null) {
                        Debug.log("Friends: loading on join failed: {}", failureReason(error));
                    } else {
                        Debug.log("Friends: loaded {} friends on join", loaded.size());
                    }
                }));
    }

    /** The saved friends, all offline. */
    private static List<Friend> loadSaved(UUID account) {
        Path path = ModPaths.friends(account);
        if (!Files.exists(path)) return List.of();

        try (Reader reader = Files.newBufferedReader(path)) {
            Saved saved = GSON.fromJson(reader, Saved.class);
            if (saved == null) return List.of();
            if (saved.friends() != null) {
                return saved.friends().stream().map(friend -> new Friend(friend.name(), friend.id(), false, "")).toList();
            }
            if (saved.names() != null) {
                return saved.names().stream().map(name -> new Friend(name, false, "")).toList();
            }
            return List.of();
        } catch (IOException | JsonParseException e) {
            CubeCraftPlusClient.LOGGER.warn("Failed to read {}, ignoring it", ModPaths.display(path), e);
            return List.of();
        }
    }

    private void persist() {
        UUID account = account();
        if (account == null) return;

        Path path = ModPaths.friends(account);
        List<SavedFriend> saving = friends.stream().map(friend -> new SavedFriend(friend.name(), friend.id())).toList();
        Saved saved = new Saved(System.currentTimeMillis(), saving, null);
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

    private static CompletableFuture<List<Page>> fetchPages(Predicate<Page> more) {
        return fetchPages(more, pages -> {});
    }

    /** Fetches pages from 1 until the last, or one {@code more} rejects; {@code each} sees the pages so far. */
    private static CompletableFuture<List<Page>> fetchPages(Predicate<Page> more, Consumer<List<Page>> each) {
        return fetchPagesFrom(1, new ArrayList<>(), more, each);
    }

    private static CompletableFuture<List<Page>> fetchPagesFrom(int number, List<Page> pages, Predicate<Page> more,
                                                               Consumer<List<Page>> each) {
        return fetchPage(number).thenCompose(page -> {
            pages.add(page);
            each.accept(pages);
            return page.number() < page.total() && more.test(page)
                    ? fetchPagesFrom(number + 1, pages, more, each)
                    : CompletableFuture.completedFuture(pages);
        });
    }

    private static CompletableFuture<Page> fetchPage(int number) {
        String command = number == 1 ? "friend list" : "friend list " + number;
        return ChatQueryManager.getInstance()
                .send(command, HEADER, LINE, true)
                .thenApply(reply -> parsePage(reply, number));
    }

    /** Online friends are listed first, so a page with anyone offline is the last one holding any. */
    private static boolean allOnline(Page page) {
        return page.friends().stream().allMatch(Friend::online);
    }

    private static List<Friend> online(List<Page> pages) {
        return pages.stream().flatMap(page -> page.friends().stream()).filter(Friend::online).toList();
    }

    /** Merges the pages by name, a friend coming online mid-walk being able to land on two. */
    private static List<Friend> merge(List<Page> pages) {
        Map<String, Friend> byName = new LinkedHashMap<>();
        for (Page page : pages) {
            for (Friend friend : page.friends()) {
                byName.putIfAbsent(friend.name().toLowerCase(Locale.ROOT), friend);
            }
        }
        return List.copyOf(byName.values());
    }

    /** Reads one page, rejecting a reply whose header names another page or holds too few lines. */
    private static Page parsePage(ChatQueryManager.Reply reply, int expected) {
        Matcher counter = PAGE.matcher(reply.header().getString());
        boolean paged = counter.find();
        int number = paged ? Integer.parseInt(counter.group(1)) : 1;
        int total = paged ? Integer.parseInt(counter.group(2)) : 1;

        if (number != expected) {
            throw new IllegalStateException("Asked for friends page " + expected + ", got " + number);
        }
        // Only the last page may be short
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
