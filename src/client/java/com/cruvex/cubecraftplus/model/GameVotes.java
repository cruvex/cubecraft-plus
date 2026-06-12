package com.cruvex.cubecraftplus.model;

import com.cruvex.cubecraftplus.config.ModConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-game vote definitions for CubeCraft pre-game voting (category slots and submenu
 * titles taken from the Cubepanion addon). Which option gets clicked comes from the
 * config; categories set to {@code NONE} are skipped.
 */
public final class GameVotes {

    /**
     * One vote: which category item to click in the main voting menu, which option to
     * click in the vote menu, and the vote menu's title. Games without a category menu
     * (BedWars) use categorySlot -1: the menu opened by the voting item is the vote
     * menu itself and has no return button.
     */
    public record VotePair(int categorySlot, int voteSlot, String submenuTitle) {
        public boolean hasSubmenu() {
            return categorySlot != -1;
        }
    }

    public static final int RETURN_SLOT = 31;

    public static List<VotePair> forGame(CubeGame game, ModConfig.AutoVoteConfig config) {
        if (game == null) return List.of();
        return switch (game) {
            case TEAM_EGGWARS -> votes(
                    pair(11, config.eggWars.perk.slot, "Perk Voting"),
                    pair(13, config.eggWars.items.slot, "Items Voting"),
                    pair(15, config.eggWars.health.slot, "Health Voting"));
            case SOLO_SKYWARS -> votes(
                    pair(11, config.skyWars.chests.slot, "Chest Items"),
                    pair(13, config.skyWars.projectiles.slot, "Projectile Options"),
                    pair(15, config.skyWars.time.slot, "Time"));
            case SOLO_LUCKYISLANDS, TEAM_LUCKY_ISLANDS -> votes(
                    pair(12, config.luckyIslands.blocks.slot, "Game Option Voting"),
                    pair(14, config.luckyIslands.time.slot, "Time Voting"));
            case PILLARS_OF_FORTUNE -> votes(
                    pair(12, config.pillarsOfFortune.gameMode.slot, "Game Mode"),
                    pair(14, config.pillarsOfFortune.mapMode.slot, "Map Modifier"));
            case BEDWARS -> votes(
                    pair(-1, config.bedWars.modifier.slot, "Modifiers"));
            default -> List.of();
        };
    }

    private static VotePair pair(int categorySlot, int voteSlot, String submenuTitle) {
        return voteSlot < 0 ? null : new VotePair(categorySlot, voteSlot, submenuTitle);
    }

    private static List<VotePair> votes(VotePair... pairs) {
        List<VotePair> result = new ArrayList<>();
        for (VotePair pair : pairs) {
            if (pair != null) result.add(pair);
        }
        return List.copyOf(result);
    }

    private GameVotes() {}
}
