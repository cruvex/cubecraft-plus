package com.cruvex.cubecraftplus.model;

import java.util.List;

/** A voting category. choiceIndex -1 means the voting item opens the vote menu directly. */
public record AutoVoteCategory(String id, String name, String itemId, int choiceIndex,
                               String menuTitle, List<AutoVoteCategoryOption> options) {

    public AutoVoteCategory {
        options = options == null ? List.of() : List.copyOf(options);
        menuTitle = menuTitle == null ? "" : menuTitle;
    }

    public int defaultSlot() {
        for (AutoVoteCategoryOption option : options) {
            if (option.defaultSelected()) {
                return option.slot();
            }
        }
        return -1;
    }
}
