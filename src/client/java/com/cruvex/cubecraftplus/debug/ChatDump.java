package com.cruvex.cubecraftplus.debug;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.chat.Chat;
import com.cruvex.cubecraftplus.util.ModPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.RegistryOps;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Records server chat as raw component JSON to debug/chat-*.log, showing a reply's real structure. */
public final class ChatDump {

    private static final int TEXT_LIMIT = 60;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static @Nullable Path path;
    private static long startedAt;
    private static long endsAt;
    private static int count;

    /** Starts a recording lasting {@code windowMs}; call before sending anything. */
    public static Path start(String label, long windowMs) {
        Path file = ModPaths.debugLog("chat");
        path = file;
        startedAt = System.currentTimeMillis();
        endsAt = startedAt + windowMs;
        count = 0;
        write("# " + label);
        return file;
    }

    public static void sent(String command) {
        if (!recording()) return;
        write("\n# sent /" + command + " at +" + (System.currentTimeMillis() - startedAt) + "ms");
    }

    /** Called for every server chat message, before a query can hide it. */
    public static void onServerMessage(Component message) {
        if (!recording()) return;

        long elapsed = System.currentTimeMillis() - startedAt;
        count++;
        write("\n# message " + count + " at +" + elapsed + "ms\n" + toJson(message));

        String kinds = events(message).stream()
                .map(event -> event.split(" ", 2)[0])
                .distinct()
                .collect(Collectors.joining(", "));
        String summary = String.format(Locale.ROOT, "#%d +%dms%s",
                count, elapsed, kinds.isEmpty() ? "" : ", events: " + kinds);

        Chat.send(Component.literal("[Dump] ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(summary).withStyle(ChatFormatting.GRAY)));
    }

    /** Describes every click and hover event in a component. */
    public static Set<String> events(Component component) {
        Set<String> events = new LinkedHashSet<>();
        component.visit((style, text) -> {
            if (style.getClickEvent() != null) events.add(describe(style.getClickEvent()));
            if (style.getHoverEvent() != null) events.add(describe(style.getHoverEvent()));
            return Optional.empty();
        }, Style.EMPTY);
        return events;
    }

    private static boolean recording() {
        if (path != null && System.currentTimeMillis() > endsAt) {
            path = null;
        }
        return path != null;
    }

    private static String describe(ClickEvent click) {
        String action = click.action().getSerializedName();
        return switch (click) {
            case ClickEvent.RunCommand run -> action + " " + run.command();
            case ClickEvent.SuggestCommand suggest -> action + " " + suggest.command();
            default -> action;
        };
    }

    private static String describe(HoverEvent hover) {
        String action = hover.action().getSerializedName();
        return switch (hover) {
            case HoverEvent.ShowEntity entity -> action + " " + entity.entity().uuid;
            case HoverEvent.ShowText text -> action + " '" + abbreviate(text.value().getString()) + "'";
            default -> action;
        };
    }

    private static String abbreviate(String text) {
        String single = text.replace('\n', '/');
        return single.length() <= TEXT_LIMIT ? single : single.substring(0, TEXT_LIMIT) + "...";
    }

    private static String toJson(Component message) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) return message.getString();

        return ComponentSerialization.CODEC
                .encodeStart(RegistryOps.create(JsonOps.INSTANCE, connection.registryAccess()), message)
                .result()
                .map(GSON::toJson)
                .orElseGet(message::getString);
    }

    private static void write(String text) {
        if (path == null) return;

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, text + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            CubeCraftPlusClient.LOGGER.warn("Chat dump could not write to {}", path, e);
        }
    }

    private ChatDump() {
    }
}
