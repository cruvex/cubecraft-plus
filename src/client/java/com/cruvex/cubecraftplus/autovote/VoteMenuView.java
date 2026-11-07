package com.cruvex.cubecraftplus.autovote;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * A snapshot of one menu, with the markers CubeCraft's voting menus carry. See
 * docs/autovote-server-behaviour.md for where each one comes from.
 */
public record VoteMenuView(int containerId, int openCount, int stateId, String title, List<ItemStack> items) {

    /** Close on a top-level menu, Back on a submenu. */
    public static final int NAV_SLOT = 31;

    private static final String VARIANT_KEY = "cubetap:item_variant";
    private static final String MAIN_MENU_TITLE = "voting";
    private static final Pattern VOTE_COUNT = Pattern.compile("\\d+ votes?");

    /** The list of categories, which the voting item opens in games that have more than one. */
    public boolean isCategoryList() {
        return isTopLevel() && titleContains(MAIN_MENU_TITLE);
    }

    /** The menu holding one category's options: a submenu, or the top level in games without categories. */
    public boolean isVoteMenu(String menuTitle, boolean hasCategoryList) {
        return (hasCategoryList ? isSubmenu() : isTopLevel()) && titleContains(menuTitle);
    }

    /** Also false for the Loading placeholder, which has no slot 31 at all. */
    public boolean isTopLevel() {
        return "close".equals(variant(NAV_SLOT));
    }

    public boolean isSubmenu() {
        return "back".equals(variant(NAV_SLOT));
    }

    public boolean hasItem(int slot) {
        return !item(slot).isEmpty();
    }

    /** Options end their lore with the running total, e.g. "3 votes"; fillers and buttons do not. */
    public boolean isVoteOption(int slot) {
        ItemLore lore = item(slot).get(DataComponents.LORE);
        if (lore == null || lore.lines().isEmpty()) return false;

        List<Component> lines = lore.lines();
        return VOTE_COUNT.matcher(lines.get(lines.size() - 1).getString().trim()).matches();
    }

    /** Foil marks the option we voted for, and only that one. */
    public boolean hasFoil(int slot) {
        return item(slot).hasFoil();
    }

    public String name(int slot) {
        return item(slot).getHoverName().getString();
    }

    private boolean titleContains(String text) {
        return title.toLowerCase(Locale.ROOT).contains(text.toLowerCase(Locale.ROOT));
    }

    private String variant(int slot) {
        CustomData data = item(slot).get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getStringOr(VARIANT_KEY, "");
    }

    private ItemStack item(int slot) {
        return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
    }
}
