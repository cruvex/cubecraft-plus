package com.cruvex.cubecraftplus.gui.tab;

import com.cruvex.cubecraftplus.config.ModConfig;
import com.cruvex.cubecraftplus.gui.screen.ConfigScreen;
import net.minecraft.network.chat.Component;

public class AutoVoteTab extends ConfigTab {

    private static final String LANG_PREFIX = "cubecraftplus.autovote.";

    public AutoVoteTab(ConfigScreen screen, ModConfig.AutoVoteConfig config) {
        super(screen);

        list.addBig(toggle(LANG_PREFIX + "enabled", config.enabled, value -> config.enabled = value));

        list.addHeader(Component.translatable(LANG_PREFIX + "group.eggWars"));
        list.addSmall(
                dropdown(LANG_PREFIX + "eggWarsItems", ModConfig.ThreeOptionsMode.values(), config.eggWars.items, v -> config.eggWars.items = v),
                dropdown(LANG_PREFIX + "eggWarsHealth", ModConfig.TwoOptionsMode.values(), config.eggWars.health, v -> config.eggWars.health = v));
        list.addSmall(
                dropdown(LANG_PREFIX + "eggWarsPerk", ModConfig.TwoOptionsMode.values(), config.eggWars.perk, v -> config.eggWars.perk = v), null);

        list.addHeader(Component.translatable(LANG_PREFIX + "group.skyWars"));
        list.addSmall(
                dropdown(LANG_PREFIX + "skyWarsChests", ModConfig.ThreeOptionsMode.values(), config.skyWars.chests, v -> config.skyWars.chests = v),
                dropdown(LANG_PREFIX + "skyWarsProjectiles", ModConfig.ThreeOptionsMode.values(), config.skyWars.projectiles, v -> config.skyWars.projectiles = v));
        list.addSmall(
                dropdown(LANG_PREFIX + "skyWarsTime", ModConfig.ThreeOptionsMode.values(), config.skyWars.time, v -> config.skyWars.time = v), null);

        list.addHeader(Component.translatable(LANG_PREFIX + "group.luckyIslands"));
        list.addSmall(
                dropdown(LANG_PREFIX + "luckyIslandsBlocks", ModConfig.FourOptionsMode.values(), config.luckyIslands.blocks, v -> config.luckyIslands.blocks = v),
                dropdown(LANG_PREFIX + "luckyIslandsTime", ModConfig.ThreeOptionsMode.values(), config.luckyIslands.time, v -> config.luckyIslands.time = v));

        list.addHeader(Component.translatable(LANG_PREFIX + "group.pillarsOfFortune"));
        list.addSmall(
                dropdown(LANG_PREFIX + "pofGameMode", ModConfig.FourOptionsMode.values(), config.pillarsOfFortune.gameMode, v -> config.pillarsOfFortune.gameMode = v),
                dropdown(LANG_PREFIX + "pofMapMode", ModConfig.FiveOptionsMode.values(), config.pillarsOfFortune.mapMode, v -> config.pillarsOfFortune.mapMode = v));

        list.addHeader(Component.translatable(LANG_PREFIX + "group.bedWars"));
        list.addSmall(
                dropdown(LANG_PREFIX + "bedWarsModifier", ModConfig.TwoOptionsMode.values(), config.bedWars.modifier, v -> config.bedWars.modifier = v), null);
    }

    @Override
    public Component getTabTitle() {
        return Component.translatable(LANG_PREFIX + "title");
    }
}
