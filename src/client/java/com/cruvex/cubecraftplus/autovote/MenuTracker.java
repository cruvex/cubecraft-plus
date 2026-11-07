package com.cruvex.cubecraftplus.autovote;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The menu the server has open for us, read from the container packets whether or not a screen shows it,
 * so visible and silent voting see the same thing. Fed on the client thread by MenuTrackerMixin.
 */
public final class MenuTracker {

    private static final int CLOSED = -1;
    /** The player's own inventory: a close for it is how the server closes everything. */
    private static final int INVENTORY_MENU = 0;

    private static MenuTracker instance;

    private int containerId = CLOSED;
    private String title = "";
    private int stateId;
    private List<ItemStack> items = List.of();
    // Every open counts, so a caller can tell the menu it acted on from the one that answered
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

    public void opened(int containerId, String title) {
        this.containerId = containerId;
        this.title = title;
        this.stateId = 0;
        this.items = List.of();
        this.opens++;
    }

    public void setContents(int containerId, int stateId, List<ItemStack> items) {
        if (containerId != this.containerId) return;

        this.stateId = stateId;
        this.items = items;
    }

    public void setSlot(int containerId, int stateId, int slot, ItemStack item) {
        if (containerId != this.containerId || slot < 0 || slot >= items.size()) return;

        this.stateId = stateId;
        // items can be the packet's own list, so copy before writing into it
        List<ItemStack> copy = new ArrayList<>(items);
        copy.set(slot, item);
        this.items = copy;
    }

    public void closedByServer(int containerId) {
        if (containerId == INVENTORY_MENU || containerId == this.containerId) {
            forget();
        }
    }

    /** We closed it ourselves, through Esc or a close packet of our own. */
    public void closedByClient(int containerId) {
        clientCloses++;
        if (containerId == this.containerId) {
            forget();
        }
    }

    public int opens() {
        return opens;
    }

    /** Closes sent from this client, for any menu: how a run notices the player pressing Esc. */
    public int clientCloses() {
        return clientCloses;
    }

    /** The open menu as it stands now, or null when there is none. */
    public @Nullable VoteMenuView view() {
        return containerId == CLOSED ? null : new VoteMenuView(containerId, opens, stateId, title, items);
    }

    private void forget() {
        containerId = CLOSED;
        title = "";
        stateId = 0;
        items = List.of();
    }
}
