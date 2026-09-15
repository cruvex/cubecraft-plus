package com.cruvex.cubecraftplus.managers;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.events.CubeEvents;
import com.cruvex.cubecraftplus.util.ModPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.cruvex.cubecraftplus.model.Friend;
import com.cruvex.cubecraftplus.util.Debug;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/** Reads CubeCraft's paginated friends list from chat, see docs/chat-command-capture.md. */
public class FriendsManager {

    private static final Pattern HEADER = Pattern.compile("^-+ Friends ");
    /** Absent when the list fits on one page. */
    private static final Pattern PAGE = Pattern.compile("(\\d+)/(\\d+)");
    /** Allows rank symbols around the name. */
    private static final Pattern LINE = Pattern.compile("^\\W*([a-zA-Z0-9_]{2,16})\\W* - (.+)$");

    /** Share a cooldown with the page queries; /fmsg does not. */
    private static final Set<String> COMMANDS = Set.of("f", "fl", "friend", "friends");
    /** Gives the proxy time to settle before the first commands after joining. */
    private static final long JOIN_DELAY_MS = 5000;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static FriendsManager instance;

    private List<Friend> friends = List.of();
    private @Nullable CompletableFuture<List<Friend>> refreshing;

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
        // Can fire off the client thread
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> friends = List.of()));
    }

    public List<Friend> getFriends() {
        return friends;
    }

    /** Fetches and stores the whole list, sharing a refresh that is already running. */
    public CompletableFuture<List<Friend>> refresh() {
        if (refreshing != null && !refreshing.isDone()) return refreshing;

        refreshing = fetchPages().thenApply(pages -> {
            List<Friend> loaded = merge(pages);
            friends = loaded;

            // Pages shifting mid-walk list one friend twice and can skip another, so don't persist that
            int listed = pages.stream().mapToInt(page -> page.friends().size()).sum();
            UUID account = account();
            if (listed != loaded.size()) {
                Debug.log("Friends: list shifted while loading, not saving it");
            } else if (account != null) {
                save(account, loaded);
            }
            return loaded;
        });
        return refreshing;
    }

    private record Saved(long savedAt, List<String> names) {
    }

    private static List<String> load(UUID account) {
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
        UUID account = account();
        if (account != null) {
            // Known names until the load below replaces them; last session's status would be stale
            friends = load(account).stream().map(name -> new Friend(name, false)).toList();
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

        return fetchPage("friend list").thenCompose(first -> {
            List<CompletableFuture<Page>> pages = IntStream.rangeClosed(1, first.total())
                    .mapToObj(number -> number == 1
                            ? CompletableFuture.completedFuture(first)
                            : fetchPage("friend list " + number))
                    .toList();

            return CompletableFuture.allOf(pages.toArray(CompletableFuture[]::new))
                    .thenApply(done -> pages.stream().map(CompletableFuture::join).toList());
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

    private CompletableFuture<Page> fetchPage(String command) {
        return ChatQueryManager.getInstance()
                .send(command, HEADER, LINE, true)
                .thenApply(FriendsManager::parsePage);
    }

    private static Page parsePage(ChatQueryManager.Reply reply) {
        Matcher counter = PAGE.matcher(reply.header().getString());
        boolean paged = counter.find();
        int number = paged ? Integer.parseInt(counter.group(1)) : 1;
        int total = paged ? Integer.parseInt(counter.group(2)) : 1;

        List<Friend> friends = reply.lines().stream().map(FriendsManager::parseFriend).toList();
        return new Page(number, total, friends);
    }

    private static Friend parseFriend(Component line) {
        Matcher matcher = LINE.matcher(line.getString());
        if (!matcher.find()) {
            throw new IllegalStateException("Not a friend line: " + line.getString());
        }
        // Online friends show where they are ("Playing SkyWars..."), not "Online"
        return new Friend(matcher.group(1), !matcher.group(2).equals("Offline"));
    }
}
