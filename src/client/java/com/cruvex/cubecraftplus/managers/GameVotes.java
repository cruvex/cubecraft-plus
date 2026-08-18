package com.cruvex.cubecraftplus.managers;

import com.cruvex.cubecraftplus.config.ModConfig;
import com.cruvex.cubecraftplus.external.CubepanionAPI;
import com.cruvex.cubecraftplus.model.AutoVoteCategory;
import com.cruvex.cubecraftplus.model.AutoVoteConfiguration;
import com.cruvex.cubecraftplus.model.CubeGame;
import com.cruvex.cubecraftplus.model.Game;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Turns the autovote config into the clicks AutoVoteManager performs. */
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

    /** Runs every tick while idle, so it stays a lookup and builds no vote list. */
    public static int hotbarSlotFor(CubeGame game) {
        AutoVoteConfiguration configuration = configurationFor(game);
        return configuration == null ? -1 : configuration.hotbarSlot();
    }

    public static List<VotePair> forGame(CubeGame game, ModConfig.AutoVoteConfig config) {
        AutoVoteConfiguration configuration = configurationFor(game);
        if (configuration == null) {
            return List.of();
        }

        List<VotePair> votes = new ArrayList<>();
        for (AutoVoteCategory category : configuration.categories()) {
            int slot = slotFor(config, category);
            if (slot < 0) {
                continue; // "don't vote", or nothing published to fall back to
            }
            votes.add(new VotePair(category.choiceIndex(), slot, category.menuTitle()));
        }
        return List.copyOf(votes);
    }

    /** The slot the player picked for a category, or its published default while they have not. */
    public static int slotFor(ModConfig.AutoVoteConfig config, AutoVoteCategory category) {
        Integer saved = config.slots.get(category.id());
        return saved != null ? saved : category.defaultSlot();
    }

    @Nullable
    public static AutoVoteConfiguration configurationFor(CubeGame game) {
        if (game == null || game == CubeGame.NONE) {
            return null;
        }

        CubepanionAPI api = CubepanionAPI.getInstance();
        List<AutoVoteConfiguration> configurations = api.getAutoVoteConfigurations();

        Game apiGame = api.tryGame(game.getString());
        if (apiGame != null) {
            for (AutoVoteConfiguration configuration : configurations) {
                if (configuration.gameId() == apiGame.id()) {
                    return configuration;
                }
            }
            return null;
        }

        // Games have not loaded yet: fall back to the published name, which matches getString
        for (AutoVoteConfiguration configuration : configurations) {
            if (CubeGame.stringToGame(configuration.gameName()) == game) {
                return configuration;
            }
        }
        return null;
    }

    /** The config's name wins: solo Lucky Islands has displayName "Lucky Islands". */
    public static CubeGame cubeGameFor(AutoVoteConfiguration configuration) {
        CubeGame game = CubeGame.stringToGame(configuration.gameName());
        if (game != CubeGame.NONE) {
            return game;
        }

        Game apiGame = CubepanionAPI.getInstance().getGameById(configuration.gameId());
        return apiGame != null && apiGame.displayName() != null
                ? CubeGame.stringToGame(apiGame.displayName())
                : CubeGame.NONE;
    }

    private GameVotes() {}
}
