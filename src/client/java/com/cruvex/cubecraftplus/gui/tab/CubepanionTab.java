package com.cruvex.cubecraftplus.gui.tab;

import com.cruvex.cubecraftplus.config.ModConfig;
import com.cruvex.cubecraftplus.gui.screen.ConfigScreen;
import net.minecraft.network.chat.Component;

/** What the mod submits to Cubepanion. */
public class CubepanionTab extends ConfigTab {

    private static final String LANG_PREFIX = "cubecraftplus.cubepanion.";

    public CubepanionTab(ConfigScreen screen, ModConfig config) {
        super(screen);

        ModConfig.LeaderboardSubmitConfig leaderboard = config.leaderboardSubmit;
        list.addHeader(Component.translatable(LANG_PREFIX + "leaderboard.title"));
        list.addBig(toggle(LANG_PREFIX + "leaderboard", LANG_PREFIX + "leaderboard.tooltip",
                leaderboard.enabled, value -> leaderboard.enabled = value));

        ModConfig.GameStatsConfig gameStats = config.gameStats;
        list.addHeader(Component.translatable(LANG_PREFIX + "gamestats.title"));
        list.addBig(toggle(LANG_PREFIX + "gamestats", LANG_PREFIX + "gamestats.tooltip",
                gameStats.enabled, value -> gameStats.enabled = value));
    }

    @Override
    public Component getTabTitle() {
        return Component.translatable(LANG_PREFIX + "title");
    }
}
