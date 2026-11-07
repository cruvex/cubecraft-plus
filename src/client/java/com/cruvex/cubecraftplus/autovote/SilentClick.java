package com.cruvex.cubecraftplus.autovote;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ContainerInput;

/**
 * The click packet silent voting sends instead of going through a screen. Rehearsed against vanilla's own clicks
 * before it was used: 50 of 50 matched, component hashes included.
 */
final class SilentClick {

    /**
     * A left click the way vanilla sends one on these menus: the clicked slot predicted empty and its stack on the
     * cursor. The server cancels the pickup and resends the slot, so nothing here is applied locally.
     */
    static ServerboundContainerClickPacket build(VoteMenuView menu, int slot, ClientPacketListener connection) {
        Int2ObjectMap<HashedStack> changedSlots = new Int2ObjectOpenHashMap<>();
        changedSlots.put(slot, HashedStack.EMPTY);
        HashedStack carried = HashedStack.create(menu.item(slot), connection.decoratedHashOpsGenenerator());
        return new ServerboundContainerClickPacket(menu.containerId(), menu.stateId(), (short) slot, (byte) 0,
                ContainerInput.PICKUP, changedSlots, carried);
    }

    private SilentClick() {
    }
}
