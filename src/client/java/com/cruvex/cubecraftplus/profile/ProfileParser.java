package com.cruvex.cubecraftplus.profile;

import com.cruvex.cubecraftplus.chat.ChatQueryManager;
import com.cruvex.cubecraftplus.debug.Debug;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the profile hovers CubeCraft puts on player names in {@code /online}, joins and chat. */
public final class ProfileParser {

    private static final Pattern ONLINE_HEADER = Pattern.compile("^There (?:are|is) \\d+ players? online");
    private static final Pattern NO_LINES = Pattern.compile("(?!)");
    private static final Pattern NAME = Pattern.compile("^Name: (\\S+)", Pattern.MULTILINE);
    private static final Pattern RANKS = Pattern.compile("^Ranks: (.*)$", Pattern.MULTILINE);
    private static final Pattern LEVEL = Pattern.compile("^Level: (\\d+)", Pattern.MULTILINE);

    /** Runs {@code /online}, hiding its reply. */
    public static CompletableFuture<List<PlayerObservation>> fetchOnline() {
        return ChatQueryManager.getInstance().send("online", ONLINE_HEADER, NO_LINES, true)
                .thenApply(reply -> parse(reply.header()));
    }

    public static void onServerMessage(Component message) {
        for (PlayerObservation observation : parse(message)) {
            if (PlayerProfiles.getInstance().record(observation)) {
                Debug.log("Observed {}", observation);
            }
        }
    }

    public static List<PlayerObservation> parse(Component message) {
        Instant now = Instant.now();

        // Subscription ranks show the name in bold, legacy ones don't
        Map<Component, Boolean> bold = new LinkedHashMap<>();
        message.visit((style, text) -> {
            if (style.getHoverEvent() instanceof HoverEvent.ShowText(Component profile) && !text.isBlank()) {
                bold.merge(profile, style.isBold(), Boolean::logicalOr);
            }
            return Optional.empty();
        }, Style.EMPTY);

        List<PlayerObservation> observations = new ArrayList<>();
        bold.forEach((profile, isBold) -> {
            String text = profile.getString();
            Matcher name = NAME.matcher(text);
            Matcher level = LEVEL.matcher(text);
            if (!name.find() || !level.find()) return;

            Matcher ranksLine = RANKS.matcher(text);
            List<Rank> ranks = ranksLine.find() ? Rank.parse(ranksLine.group(1)) : List.of();
            boolean legacy = !isBold && ranks.stream().anyMatch(Rank::hasLegacy);
            observations.add(new PlayerObservation(name.group(1), uuid(name.group(1)), ranks, legacy,
                    Integer.parseInt(level.group(1)), now));
        });
        return observations;
    }

    private static @Nullable UUID uuid(String name) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(name);
        return info == null ? null : info.getProfile().id();
    }

    private ProfileParser() {
    }
}
