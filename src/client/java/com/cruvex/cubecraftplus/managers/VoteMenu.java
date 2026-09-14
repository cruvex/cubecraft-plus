package com.cruvex.cubecraftplus.managers;

/** A voting menu AutoVote is driving, read either from packets or from the open screen. */
public interface VoteMenu {

    int containerId();

    String title();

    /** Chest slots plus the player inventory, or 0 before the contents are known. */
    int slotCount();

    boolean hasItem(int slot);

    void click(int slot);

    void close();
}
