package com.cruvex.cubecraftplus.autovote;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ContainerInput;

/** The click silent voting sends; it matched vanilla's own clicks field for field, hashes included. */
final class SilentClick {

    /** The pickup vanilla sends; the server cancels it and resends the slot, so nothing is applied locally. */
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
