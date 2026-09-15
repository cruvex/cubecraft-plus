package com.cruvex.cubecraftplus.managers;

import com.cruvex.cubecraftplus.model.Friend;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
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

    private static FriendsManager instance;

    public record Page(int number, int total, List<Friend> friends) {
    }

    public static FriendsManager getInstance() {
        if (instance == null) {
            instance = new FriendsManager();
        }
        return instance;
    }

    public void init() {
        ChatQueryManager.getInstance().shareCooldown(COMMANDS);
    }

    /** Fetches every page, hidden from chat. */
    public CompletableFuture<List<Page>> fetchPages() {
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
    public static List<Friend> merge(List<Page> pages) {
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
