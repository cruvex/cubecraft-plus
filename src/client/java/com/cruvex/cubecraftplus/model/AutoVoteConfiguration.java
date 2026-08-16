package com.cruvex.cubecraftplus.model;

import java.util.List;

/** One game's voting definition. gameName matches {@link CubeGame#getString()}. */
public record AutoVoteConfiguration(int gameId, String gameName, int hotbarSlot, String icon,
                                    List<AutoVoteCategory> categories) {

    public AutoVoteConfiguration {
        categories = categories == null ? List.of() : List.copyOf(categories);
    }
}
