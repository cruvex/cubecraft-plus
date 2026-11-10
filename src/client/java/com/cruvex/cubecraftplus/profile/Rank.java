package com.cruvex.cubecraftplus.profile;

import com.cruvex.cubecraftplus.debug.Debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** CubeCraft ranks by the resource pack glyph that draws their badge. */
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
    LAPIS('');

    private final char glyph;

    Rank(char glyph) {
        this.glyph = glyph;
    }

    /** Ranks sold both as a subscription and, before that, permanently. */
    public boolean hasLegacy() {
        return this == IRON || this == DIAMOND || this == OBSIDIAN;
    }

    public static List<Rank> parse(String glyphs) {
        List<Rank> ranks = new ArrayList<>();
        for (char c : glyphs.toCharArray()) {
            if (Character.isWhitespace(c)) continue;

            Rank rank = byGlyph(c);
            if (rank != null) {
                ranks.add(rank);
            } else {
                Debug.log("Unknown rank glyph U+{}", String.format(Locale.ROOT, "%04X", (int) c));
            }
        }
        return ranks;
    }

    private static Rank byGlyph(char glyph) {
        for (Rank rank : values()) {
            if (rank.glyph == glyph) return rank;
        }
        return null;
    }
}
