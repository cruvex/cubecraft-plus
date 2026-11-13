package com.cruvex.cubecraftplus.profile;

import com.cruvex.cubecraftplus.debug.Debug;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** CubeCraft ranks by the resource pack glyph that draws their badge, or by name for the ones written as text. */
public enum Rank {
    ADMIN(''),
    MOD(''),
    SR_MOD(''),
    HELPER(''),
    DEVELOPER(''),
    DESIGNER(''),
    TWITCH(''),
    PARTNER(''),
    TIKTOK(''),
    YOUTUBER(''),
    QA(''),
    STONE(''),
    IRON(''),
    DIAMOND(''),
    OBSIDIAN(''),
    EMERALD(''),
    GOLD(''),
    LAPIS(''),
    /** A retired rank some players still have, shown as text. */
    PLUS('\0');

    private final char glyph;

    Rank(char glyph) {
        this.glyph = glyph;
    }

    /** Ranks sold both as a subscription and, before that, permanently. */
    public boolean hasLegacy() {
        return this == IRON || this == DIAMOND || this == OBSIDIAN;
    }

    /** Reads a profile's "Ranks:" line: glyphs and words separated by spaces or commas. */
    public static List<Rank> parse(String line, String player) {
        List<Rank> ranks = new ArrayList<>();
        for (String part : line.split("[\\s,]+")) {
            if (part.isEmpty()) continue;

            Rank rank = find(part);
            if (rank != null) ranks.add(rank);
            else Debug.log("Unknown rank '{}' for {}", part, player);
        }
        return ranks;
    }

    private static @Nullable Rank find(String part) {
        for (Rank rank : values()) {
            if (part.length() == 1 ? rank.glyph == part.charAt(0) : rank.name().equalsIgnoreCase(part)) return rank;
        }
        return null;
    }
}
