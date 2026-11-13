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
        List<PlayerObservation> observations = parse(message).stream()
                .map(PlayerProfiles.getInstance()::withKnownLegacy)
                .toList();
        if (observations.isEmpty()) return;

        List<String> lines = new ArrayList<>();
        int news = 0;
        for (PlayerObservation observation : observations) {
            boolean isNews = PlayerProfiles.getInstance().record(observation);
            if (isNews) {
                ObservationSender.getInstance().add(observation);
                news++;
            }
            lines.add(describe(observation, isNews));
        }

        PlayerObservation first = observations.getFirst();
        String summary = observations.size() == 1
                ? "Observed " + first.name() + note(first, news == 1)
                : "Observed " + observations.size() + " players (" + news + " new)";
        Component hover = Component.literal(String.join("\n", lines));
        Debug.log(Component.literal(summary).withStyle(style -> style.withHoverEvent(new HoverEvent.ShowText(hover))));
    }

    private static String describe(PlayerObservation observation, boolean news) {
        UUID uuid = observation.uuid();
        return observation.name() + " " + observation.ranks() + (Boolean.TRUE.equals(observation.legacy()) ? " legacy" : "")
                + ", level " + observation.level() + note(observation, news)
                + "\n  " + (uuid == null ? "not in the tab list" : uuid);
    }

    private static String note(PlayerObservation observation, boolean news) {
        if (observation.uuid() == null) return " (no uuid, not stored)";
        if (observation.legacy() == null) return " (legacy unknown, not stored)";
        return news ? "" : " (unchanged)";
    }

    public static List<PlayerObservation> parse(Component message) {
        Instant now = Instant.now();
        // Bold means a subscription rank anywhere, but only /online bolds reliably; kill messages never do
        boolean online = ONLINE_HEADER.matcher(message.getString()).find();

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
            List<Rank> ranks = ranksLine.find() ? Rank.parse(ranksLine.group(1), name.group(1)) : List.of();
            Boolean legacy = isBold || ranks.stream().noneMatch(Rank::hasLegacy) ? Boolean.FALSE
                    : online ? Boolean.TRUE : null;
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
