package com.cruvex.cubecraftplus.autovote;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** The menu the server has open for us, read from the packets whether or not a screen shows it. */
public final class MenuTracker {

    private static final int CLOSED = -1;
    // A close for the player's inventory is how the server closes everything
    private static final int INVENTORY_MENU = 0;

    private static MenuTracker instance;

    private int containerId = CLOSED;
    private String title = "";
    private int stateId;
    private List<ItemStack> items = List.of();
    private boolean hidden;
    private int opens;
    private int clientCloses;

    public static MenuTracker getInstance() {
        if (instance == null) {
            instance = new MenuTracker();
        }
        return instance;
    }

    public void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> forget());
    }

    public void opened(int containerId, String title, boolean hidden) {
        this.containerId = containerId;
        this.title = title;
        this.stateId = 0;
        this.items = List.of();
        this.hidden = hidden;
        this.opens++;
    }

    public boolean isHidden(int containerId) {
        return hidden && containerId == this.containerId;
    }

    public void setContents(int containerId, int stateId, List<ItemStack> items) {
        if (containerId != this.containerId) return;

        this.stateId = stateId;
        this.items = items;
    }

    public void setSlot(int containerId, int stateId, int slot, ItemStack item) {
        if (containerId != this.containerId || slot < 0 || slot >= items.size()) return;

        this.stateId = stateId;
        // items can be the packet's own list
        List<ItemStack> copy = new ArrayList<>(items);
        copy.set(slot, item);
        this.items = copy;
    }

    public void closedByServer(int containerId) {
        if (containerId == INVENTORY_MENU || containerId == this.containerId) {
            forget();
        }
    }

    /** Only closes of this menu count: closing the player's inventory sends one for menu 0. */
    public void closedByClient(int containerId) {
        if (containerId == this.containerId) {
            clientCloses++;
            forget();
        }
    }

    public int opens() {
        return opens;
    }

    public int clientCloses() {
        return clientCloses;
    }

    public @Nullable VoteMenuView view() {
        return containerId == CLOSED ? null : new VoteMenuView(containerId, opens, stateId, title, items);
    }

    private void forget() {
        containerId = CLOSED;
        title = "";
        stateId = 0;
        items = List.of();
        hidden = false;
    }
}
