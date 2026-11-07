package com.cruvex.cubecraftplus.debug;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Logs menu traffic both ways as {@link SignalProbe} signals, so a vote can be read back click by click. */
public class MenuProbe {

    private static final int CLOSED = -1;
    private static final int INVENTORY_MENU = 0;
    private static final int HOTBAR_SIZE = 9;
    private static final String EMPTY = "empty";

    private static MenuProbe instance;

    private int menuId = CLOSED;
    // What the open menu last held, so a resend logs only what it changed
    private List<String> slots = new ArrayList<>();
    private String screen = "";
    private String hotbar = "";

    public static MenuProbe getInstance() {
        if (instance == null) {
            instance = new MenuProbe();
        }
        return instance;
    }

    public void init() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(this::reset));
    }

    // Inbound

    public void onOpenScreen(ClientboundOpenScreenPacket packet) {
        if (!active()) return;

        menuId = packet.getContainerId();
        slots = new ArrayList<>();
        signal("MENU_OPEN", "id={} type={} title='{}'", menuId,
                BuiltInRegistries.MENU.getKey(packet.getType()), packet.getTitle().getString());
    }

    public void onContent(ClientboundContainerSetContentPacket packet) {
        if (!active()) return;

        int id = packet.containerId();
        if (id == INVENTORY_MENU) {
            signal("MENU_CONTENT", "id={} stateId={} slots={} (inventory)",
                    id, packet.stateId(), packet.items().size());
            return;
        }

        List<String> items = packet.items().stream().map(MenuProbe::describe).collect(Collectors.toList());
        boolean resend = id == menuId && slots.size() == items.size();
        signal("MENU_CONTENT", "id={} stateId={} slots={} resend={} carried={} items=[{}]",
                id, packet.stateId(), items.size(), resend, describe(packet.carriedItem()),
                changes(resend ? slots : List.of(), items));

        if (id == menuId) {
            slots = items;
        }
    }

    public void onSetSlot(ClientboundContainerSetSlotPacket packet) {
        if (!active()) return;

        int id = packet.getContainerId();
        if (id == INVENTORY_MENU && menuId == CLOSED) return; // every pickup in a game

        String item = describe(packet.getItem());
        if (id == menuId && packet.getSlot() >= 0 && packet.getSlot() < slots.size()) {
            slots.set(packet.getSlot(), item);
        }
        signal("MENU_SLOT", "id={} stateId={} slot={} item={}",
                id, packet.getStateId(), packet.getSlot(), item);
    }

    public void onCursor(ClientboundSetCursorItemPacket packet) {
        signal("MENU_CURSOR", "item={}", describe(packet.contents()));
    }

    public void onClose(ClientboundContainerClosePacket packet) {
        signal("MENU_CLOSE", "id={}", packet.getContainerId());
        closed(packet.getContainerId());
    }

    // Outbound

    public void onSend(Packet<?> packet) {
        switch (packet) {
            case ServerboundContainerClickPacket click -> signal("OUT_CLICK",
                    "id={} stateId={} slot={} button={} input={} changed={} carried={}",
                    click.containerId(), click.stateId(), click.slotNum(), click.buttonNum(),
                    click.containerInput(), click.changedSlots(), click.carriedItem());
            case ServerboundContainerClosePacket close -> {
                signal("OUT_CLOSE", "id={}", close.getContainerId());
                closed(close.getContainerId());
            }
            case ServerboundUseItemPacket use ->
                    signal("OUT_USE", "hand={} sequence={}", use.hand(), use.sequence());
            case ServerboundUseItemOnPacket use -> signal("OUT_USE_ON", "hand={} block={} face={} sequence={}",
                    use.hand(), use.hitResult().getBlockPos(), use.hitResult().getDirection(), use.sequence());
            case ServerboundSetCarriedItemPacket carried -> signal("OUT_CARRIED", "slot={}", carried.getSlot());
            default -> {
            }
        }
    }

    // Client state, read each tick

    private void onEndTick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || !active()) return;

        trackScreen(client.gui.screen());
        trackHotbar(player);
    }

    private void trackScreen(Screen current) {
        String described = current == null ? "none"
                : current.getClass().getSimpleName() + " '" + current.getTitle().getString() + "'";
        if (described.equals(screen)) return;

        screen = described;
        signal("SCREEN", "{}", described);
    }

    /** Names rather than full stacks: this is for spotting the voting item, not for every count change. */
    private void trackHotbar(LocalPlayer player) {
        List<String> names = new ArrayList<>();
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) {
                names.add(slot + ":" + describe(stack.copyWithCount(1)));
            }
        }

        String described = "selected=" + player.getInventory().getSelectedSlot() + " " + names;
        if (described.equals(hotbar)) return;

        hotbar = described;
        signal("HOTBAR", "{}", described);
    }

    // Plumbing

    private void closed(int containerId) {
        if (containerId == menuId) {
            menuId = CLOSED;
            slots = new ArrayList<>();
        }
    }

    private void reset() {
        menuId = CLOSED;
        slots = new ArrayList<>();
        screen = "";
        hotbar = "";
    }

    private static boolean active() {
        return SignalProbe.getInstance().active();
    }

    private static void signal(String type, String message, Object... args) {
        if (active()) {
            SignalProbe.getInstance().signal(type, message, args);
        }
    }

    /** Every slot that differs from before, so an empty before lists the whole menu. */
    private static String changes(List<String> before, List<String> after) {
        List<String> changed = new ArrayList<>();
        for (int slot = 0; slot < after.size(); slot++) {
            String was = slot < before.size() ? before.get(slot) : EMPTY;
            if (!after.get(slot).equals(was)) {
                changed.add(slot + ":" + after.get(slot));
            }
        }
        return String.join("; ", changed);
    }

    /** Everything that could identify an item, since that is what the rebuild has to pick between. */
    private static String describe(ItemStack stack) {
        if (stack.isEmpty()) return EMPTY;

        StringBuilder described = new StringBuilder()
                .append('\'').append(stack.getHoverName().getString()).append("' ")
                .append(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath())
                .append(" x").append(stack.getCount());
        if (stack.hasFoil()) {
            described.append(" foil");
        }

        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore != null && !lore.lines().isEmpty()) {
            described.append(" lore=[")
                    .append(lore.lines().stream().map(Component::getString).collect(Collectors.joining(" / ")))
                    .append(']');
        }

        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data != null && !data.isEmpty()) {
            described.append(" data=").append(data);
        }
        return described.toString();
    }
}
