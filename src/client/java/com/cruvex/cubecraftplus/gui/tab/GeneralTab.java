package com.cruvex.cubecraftplus.gui.tab;

import com.cruvex.cubecraftplus.config.ModConfig;
import com.cruvex.cubecraftplus.gui.screen.ConfigScreen;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.Component;

/** Mod-wide settings, and small features that don't warrant a tab of their own. */
public class GeneralTab extends ConfigTab {

    private static final String LANG_PREFIX = "cubecraftplus.general.";

    public GeneralTab(ConfigScreen screen, ModConfig config) {
        super(screen);

        ModConfig.ChestFinderConfig chestFinder = config.chestFinder;
        list.addHeader(Component.translatable(LANG_PREFIX + "chestfinder.title"));

        CycleButton<Boolean> highlight = toggle(LANG_PREFIX + "chestfinder.highlight",
                LANG_PREFIX + "chestfinder.highlight.tooltip",
                chestFinder.highlight, value -> chestFinder.highlight = value);
        // Greyed out while the finder itself is off, the highlight only drawing with it
        highlight.active = chestFinder.enabled;

        list.addBig(toggle(LANG_PREFIX + "chestfinder", LANG_PREFIX + "chestfinder.tooltip",
                chestFinder.enabled, value -> {
                    chestFinder.enabled = value;
                    highlight.active = value;
                }));
        list.addBig(highlight);

        ModConfig.UpdateCheckConfig updateCheck = config.updateCheck;
        list.addHeader(Component.translatable(LANG_PREFIX + "update.title"));
        list.addBig(toggle(LANG_PREFIX + "update", LANG_PREFIX + "update.tooltip",
                updateCheck.enabled, value -> updateCheck.enabled = value));
    }

    @Override
    public Component getTabTitle() {
        return Component.translatable(LANG_PREFIX + "title");
    }
}
