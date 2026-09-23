package com.cruvex.cubecraftplus.gui.tab;

import com.cruvex.cubecraftplus.config.ModConfig;
import com.cruvex.cubecraftplus.gui.screen.ConfigScreen;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.Component;

/** What the mod submits to Cubepanion. */
public class CubepanionTab extends ConfigTab {

    private static final String LANG_PREFIX = "cubecraftplus.cubepanion.";

    public CubepanionTab(ConfigScreen screen, ModConfig config) {
        super(screen);

        ModConfig.LeaderboardSubmitConfig leaderboard = config.leaderboardSubmit;
        CycleButton<Boolean> leaderboardToggle = toggle(LANG_PREFIX + "leaderboard", LANG_PREFIX + "leaderboard.tooltip",
                leaderboard.enabled, value -> leaderboard.enabled = value);

        ModConfig.GameStatsConfig gameStats = config.gameStats;
        CycleButton<Boolean> gameStatsToggle = toggle(LANG_PREFIX + "gamestats", LANG_PREFIX + "gamestats.tooltip",
                gameStats.enabled, value -> gameStats.enabled = value);

        ModConfig.CubeSocketConfig cubeSocket = config.cubeSocket;
        leaderboardToggle.active = cubeSocket.enabled;
        gameStatsToggle.active = cubeSocket.enabled;

        list.addHeader(Component.translatable(LANG_PREFIX + "cubesocket.title"));
        list.addBig(toggle(LANG_PREFIX + "cubesocket", LANG_PREFIX + "cubesocket.tooltip",
                cubeSocket.enabled, value -> {
                    cubeSocket.enabled = value;
                    leaderboardToggle.active = value;
                    gameStatsToggle.active = value;
                }));

        list.addHeader(Component.translatable(LANG_PREFIX + "leaderboard.title"));
        list.addBig(leaderboardToggle);

        list.addHeader(Component.translatable(LANG_PREFIX + "gamestats.title"));
        list.addBig(gameStatsToggle);
    }

    @Override
    public Component getTabTitle() {
        return Component.translatable(LANG_PREFIX + "title");
    }
}
