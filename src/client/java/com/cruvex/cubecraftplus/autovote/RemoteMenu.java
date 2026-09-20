package com.cruvex.cubecraftplus.autovote;

import com.cruvex.cubecraftplus.debug.Debug;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** A chest menu the server has open for us, tracked from packets instead of from a screen: nothing is drawn. */
public final class RemoteMenu implements VoteMenu {

    private static RemoteMenu instance;

    private static final int CLOSED = -1;

    private int containerId = CLOSED;
    private String title = "";
    private int stateId;
    private List<ItemStack> items = List.of();
    // Never reset, so a redraw still counts when the server moves us to another menu
    private int redraws;

    public static RemoteMenu getInstance() {
        if (instance == null) {
            instance = new RemoteMenu();
        }
        return instance;
    }

    /** A menu was opened for us. Slots stay empty until the contents packet arrives. */
    public void opened(int containerId, String title) {
        this.containerId = containerId;
        this.title = title;
        this.stateId = 0;
        this.items = List.of();
    }

    public void setContents(int containerId, int stateId, List<ItemStack> items) {
        if (containerId != this.containerId) return;

        this.stateId = stateId;
        this.items = items;
        this.redraws++;
    }

    public void setSlot(int containerId, int stateId, int slot, ItemStack item) {
        if (containerId != this.containerId || slot < 0 || slot >= items.size()) return;

        this.stateId = stateId;
        // items is the packet's own list, so copy before writing into it
        this.items = new ArrayList<>(items);
        this.items.set(slot, item);
    }

    /** Returns whether the menu the server closed was ours. */
    public boolean closedByServer(int containerId) {
        if (containerId != this.containerId) return false;

        forget();
        return true;
    }

    public boolean isOpen() {
        return containerId != CLOSED;
    }

    @Override
    public int containerId() {
        return containerId;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public int slotCount() {
        return items.size();
    }

    /** Full contents packets seen, which is how the server acknowledges a click. */
    public int redraws() {
        return redraws;
    }

    @Override
    public boolean hasItem(int slot) {
        return slot >= 0 && slot < items.size() && !items.get(slot).isEmpty();
    }

    /** Sends the pickup a vanilla client would, but without predicting it: the server cancels the move and resends. */
    @Override
    public void click(int slot) {
        ClientPacketListener connection = connection();
        if (connection == null || !isOpen() || slot < 0 || slot >= items.size()) return;

        Int2ObjectMap<HashedStack> changedSlots = new Int2ObjectOpenHashMap<>();
        changedSlots.put(slot, HashedStack.EMPTY);
        HashedStack carried =
                HashedStack.create(items.get(slot), connection.decoratedHashOpsGenenerator());

        Debug.log("AutoVote: click menu={} slot={} stateId={} item='{}'",
                containerId, slot, stateId, items.get(slot).getHoverName().getString());
        connection.send(new ServerboundContainerClickPacket(
                containerId, stateId, (short) slot, (byte) 0,
                ClickType.PICKUP, changedSlots, carried));
    }

    @Override
    public void close() {
        ClientPacketListener connection = connection();
        if (isOpen() && connection != null) {
            Debug.log("AutoVote: closing menu {}", containerId);
            connection.send(new ServerboundContainerClosePacket(containerId));
        }
        forget();
    }

    /** Drops the menu without telling the server. */
    public void forget() {
        containerId = CLOSED;
        title = "";
        stateId = 0;
        items = List.of();
    }

    private ClientPacketListener connection() {
        return Minecraft.getInstance().getConnection();
    }
}
