package com.cruvex.cubecraftplus.autovote;

import com.cruvex.cubecraftplus.config.ModConfig;
import com.cruvex.cubecraftplus.game.Game;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Turns the autovote config into the clicks autovote performs. */
public final class GameVotes {

    /** categorySlot -1 means the game has no category menu (BedWars): the voting item opens the vote menu itself. */
    public record VotePair(int categorySlot, int voteSlot, String submenuTitle) {
        public boolean hasSubmenu() {
            return categorySlot != -1;
        }
    }

    public static List<VotePair> forGame(@Nullable Game game, ModConfig.AutoVoteConfig config) {
        AutoVoteConfiguration configuration = AutoVoteConfigs.getInstance().find(game);
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

    private GameVotes() {}
}
