package com.cruvex.cubecraftplus.autovote;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.HashedStack;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.world.inventory.ContainerInput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * The click packet silent voting sends instead of going through a screen. For now each visible click also builds
 * one and compares it with what vanilla sent, so the silent version is proven before it is used.
 */
public final class SilentClick {

    // The rehearsal waiting for vanilla's own packet; clicks go out one at a time, so one is enough
    private static @Nullable ServerboundContainerClickPacket expected;

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

    /** Remembers the packet silent voting would send, to check against the one vanilla sends next. */
    static void rehearse(ServerboundContainerClickPacket packet) {
        expected = packet;
    }

    /** Called for every outbound packet; compares the first click after a rehearsal. */
    public static void onSent(Packet<?> packet) {
        if (expected == null || !(packet instanceof ServerboundContainerClickPacket actual)) return;

        List<String> differences = differences(expected, actual);
        if (differences.isEmpty()) {
            AutoVoteManager.log("rehearsal: silent click on slot {} matches vanilla's", actual.slotNum());
        } else {
            AutoVoteManager.log("rehearsal: silent click on slot {} differs from vanilla's: {}",
                    actual.slotNum(), String.join("; ", differences));
        }
        expected = null;
    }

    private static List<String> differences(ServerboundContainerClickPacket ours, ServerboundContainerClickPacket vanilla) {
        List<String> differences = new ArrayList<>();
        compare(differences, "containerId", ours.containerId(), vanilla.containerId());
        compare(differences, "stateId", ours.stateId(), vanilla.stateId());
        compare(differences, "slot", ours.slotNum(), vanilla.slotNum());
        compare(differences, "button", ours.buttonNum(), vanilla.buttonNum());
        compare(differences, "input", ours.containerInput(), vanilla.containerInput());
        if (!sameSlots(ours.changedSlots(), vanilla.changedSlots())) {
            differences.add("changed ours=" + ours.changedSlots() + " vanilla=" + vanilla.changedSlots());
        }
        if (!sameStack(ours.carriedItem(), vanilla.carriedItem())) {
            differences.add("carried ours=" + ours.carriedItem() + " vanilla=" + vanilla.carriedItem());
        }
        return differences;
    }

    private static void compare(List<String> differences, String field, Object ours, Object vanilla) {
        if (!ours.equals(vanilla)) {
            differences.add(field + " ours=" + ours + " vanilla=" + vanilla);
        }
    }

    private static boolean sameSlots(Int2ObjectMap<HashedStack> ours, Int2ObjectMap<HashedStack> vanilla) {
        if (!ours.keySet().equals(vanilla.keySet())) return false;

        for (int slot : ours.keySet()) {
            if (!sameStack(ours.get(slot), vanilla.get(slot))) return false;
        }
        return true;
    }

    /**
     * By value, the way the server checks it. HashedPatchMap keeps its hashes in an IdentityHashMap, whose equals
     * compares the boxed Integers by reference, so two maps with the same numbers usually count as different.
     */
    private static boolean sameStack(HashedStack ours, HashedStack vanilla) {
        if (!(ours instanceof HashedStack.ActualItem a) || !(vanilla instanceof HashedStack.ActualItem b)) {
            return ours.equals(vanilla);
        }
        return a.item().equals(b.item())
                && a.count() == b.count()
                && new HashMap<>(a.components().addedComponents()).equals(new HashMap<>(b.components().addedComponents()))
                && new HashSet<>(a.components().removedComponents()).equals(new HashSet<>(b.components().removedComponents()));
    }

    private SilentClick() {
    }
}
