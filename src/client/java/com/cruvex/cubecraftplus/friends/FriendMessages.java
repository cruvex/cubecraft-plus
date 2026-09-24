package com.cruvex.cubecraftplus.friends;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.debug.Debug;
import com.cruvex.cubecraftplus.game.CubeCraftManager;
import com.cruvex.cubecraftplus.util.ModPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Saves messages sent to and from friends, one file per friend; see docs/friend-messages.md. */
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

        FriendsManager.getInstance().confirmOnline(message.name())
                .whenComplete((id, error) -> writer.execute(() -> save(account, error == null ? id : null, message)));
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

            Path friend = folder.resolve(id + ".jsonl");
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
