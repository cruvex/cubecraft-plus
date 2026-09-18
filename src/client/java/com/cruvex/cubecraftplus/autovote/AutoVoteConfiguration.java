package com.cruvex.cubecraftplus.autovote;

import java.util.List;

/** One game's voting definition, matched to a {@link com.cruvex.cubecraftplus.game.Game} by gameId. */
public record AutoVoteConfiguration(int gameId, String gameName, int hotbarSlot,
                                    List<AutoVoteCategory> categories) {

    public AutoVoteConfiguration {
        categories = categories == null ? List.of() : List.copyOf(categories);
    }
}
