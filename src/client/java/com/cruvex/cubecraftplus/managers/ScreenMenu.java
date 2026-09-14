package com.cruvex.cubecraftplus.managers;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;

/** The voting menu as the player sees it, used when silent voting is off. */
record ScreenMenu(ContainerScreen screen) implements VoteMenu {

    @Override
    public int containerId() {
        return screen.getMenu().containerId;
    }

    @Override
    public String title() {
        return screen.getTitle().getString();
    }

    @Override
    public int slotCount() {
        return screen.getMenu().slots.size();
    }

    @Override
    public boolean hasItem(int slot) {
        ChestMenu menu = screen.getMenu();
        return slot >= 0 && slot < menu.slots.size() && !menu.slots.get(slot).getItem().isEmpty();
    }

    @Override
    public void click(int slot) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null) return;

        client.gameMode.handleInventoryMouseClick(containerId(), slot, 0, ClickType.PICKUP, player);
    }

    @Override
    public void close() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.closeContainer();
        }
    }
}
