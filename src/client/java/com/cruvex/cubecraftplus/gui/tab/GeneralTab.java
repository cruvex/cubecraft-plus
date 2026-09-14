package com.cruvex.cubecraftplus.gui.tab;

import com.cruvex.cubecraftplus.config.ModConfig;
import com.cruvex.cubecraftplus.gui.screen.ConfigScreen;
import net.minecraft.network.chat.Component;

/** Small standalone features that don't warrant a tab of their own. */
public class GeneralTab extends ConfigTab {

    private static final String LANG_PREFIX = "cubecraftplus.general.";

    public GeneralTab(ConfigScreen screen, ModConfig config) {
        super(screen);

        ModConfig.ChestFinderConfig chestFinder = config.chestFinder;
        list.addBig(toggle(LANG_PREFIX + "chestfinder", LANG_PREFIX + "chestfinder.tooltip",
                chestFinder.enabled, value -> chestFinder.enabled = value));
        list.addBig(toggle(LANG_PREFIX + "chestfinder.highlight", LANG_PREFIX + "chestfinder.highlight.tooltip",
                chestFinder.highlight, value -> chestFinder.highlight = value));
    }

    @Override
    public Component getTabTitle() {
        return Component.translatable(LANG_PREFIX + "title");
    }
}
