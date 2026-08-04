package com.cruvex.cubecraftplus.gui.tab;

import com.cruvex.cubecraftplus.config.ModConfig;
import com.cruvex.cubecraftplus.gui.screen.ConfigScreen;
import net.minecraft.network.chat.Component;

public class LeaderboardTab extends ConfigTab {

    private static final String LANG_PREFIX = "cubecraftplus.leaderboard.";

    public LeaderboardTab(ConfigScreen screen, ModConfig.LeaderboardSubmitConfig config) {
        super(screen);

        list.addBig(toggle(LANG_PREFIX + "submit", LANG_PREFIX + "submit.tooltip",
                config.enabled, value -> config.enabled = value));
    }

    @Override
    public Component getTabTitle() {
        return Component.translatable(LANG_PREFIX + "title");
    }
}
