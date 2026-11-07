package com.cruvex.cubecraftplus.autovote;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** A snapshot of one menu and the markers CubeCraft's voting menus carry (docs/autovote-server-behaviour.md). */
public record VoteMenuView(int containerId, int openCount, int stateId, String title, List<ItemStack> items) {

    // Close on a top-level menu, Back on a submenu
    public static final int NAV_SLOT = 31;

    private static final String VARIANT_KEY = "cubetap:item_variant";
    private static final String MAIN_MENU_TITLE = "voting";
    private static final Pattern VOTE_COUNT = Pattern.compile("\\d+ votes?");

    /** The whole title, as submenus like "Perk Voting" contain the word too; either button, as a nested list has Back. */
    public boolean isCategoryList() {
        return (isTopLevel() || isSubmenu()) && plainTitle().equalsIgnoreCase(MAIN_MENU_TITLE);
    }

    /** A submenu, or the top level in games without a category list. */
    public boolean isVoteMenu(String menuTitle, boolean hasCategoryList) {
        return (hasCategoryList ? isSubmenu() : isTopLevel()) && titleContains(menuTitle);
    }

    public boolean isSubmenu() {
        return "back".equals(variant(NAV_SLOT));
    }

    public boolean hasItem(int slot) {
        return !item(slot).isEmpty();
    }

    /** Options end their lore with the running total, e.g. "3 votes". */
    public boolean isVoteOption(int slot) {
        ItemLore lore = item(slot).get(DataComponents.LORE);
        if (lore == null || lore.lines().isEmpty()) return false;

        List<Component> lines = lore.lines();
        return VOTE_COUNT.matcher(lines.get(lines.size() - 1).getString().trim()).matches();
    }

    /** Foil marks our own vote, and only that. */
    public boolean hasFoil(int slot) {
        return item(slot).hasFoil();
    }

    public String name(int slot) {
        return item(slot).getHoverName().getString();
    }

    // The Loading placeholder has no slot 31, so it is neither
    private boolean isTopLevel() {
        return "close".equals(variant(NAV_SLOT));
    }

    private boolean titleContains(String text) {
        return title.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT));
    }

    /** Without the custom-font glyphs CubeCraft draws the menu background with. */
    private String plainTitle() {
        StringBuilder plain = new StringBuilder();
        title.codePoints()
                .filter(codePoint -> Character.getType(codePoint) != Character.PRIVATE_USE)
                .forEach(plain::appendCodePoint);
        return plain.toString().trim();
    }

    private String variant(int slot) {
        CustomData data = item(slot).get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getStringOr(VARIANT_KEY, "");
    }

    ItemStack item(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }
}
