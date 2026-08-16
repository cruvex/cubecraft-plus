package com.cruvex.cubecraftplus.model;

/** One option in a voting category. Slot -1 is "don't vote" and skips the category. */
public record AutoVoteCategoryOption(int slot, String name, boolean defaultSelected) {
}
