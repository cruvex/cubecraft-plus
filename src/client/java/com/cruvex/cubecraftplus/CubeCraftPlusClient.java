package com.cruvex.cubecraftplus;

import com.cruvex.cubecraftplus.autovote.AutoVoteConfigs;
import com.cruvex.cubecraftplus.autovote.AutoVoteManager;
import com.cruvex.cubecraftplus.chat.ChatQueryManager;
import com.cruvex.cubecraftplus.chestfinder.ChestFinderManager;
import com.cruvex.cubecraftplus.commands.CommandManager;
import com.cruvex.cubecraftplus.config.ConfigManager;
import com.cruvex.cubecraftplus.cubesocket.CubeSocket;
import com.cruvex.cubecraftplus.cubesocket.CubeSocketEvents;
import com.cruvex.cubecraftplus.debug.SignalProbe;
import com.cruvex.cubecraftplus.friends.FriendsManager;
import com.cruvex.cubecraftplus.game.CubeCraftManager;
import com.cruvex.cubecraftplus.game.CubeEvents;
import com.cruvex.cubecraftplus.game.GameManager;
import com.cruvex.cubecraftplus.game.GameRegistry;
import com.cruvex.cubecraftplus.leaderboard.LeaderboardSubmitManager;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CubeCraftPlusClient implements ClientModInitializer {

    public static final String MOD_ID = "cubecraft-plus";

    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("{} client init", MOD_ID);

        ConfigManager.getInstance().init();
        GameRegistry.getInstance().seed();
        AutoVoteConfigs.getInstance().seed();

        CubeEvents.CUBE_JOIN.register(CubeCraftPlusClient::loadRemoteData);
        CubeSocketEvents.SOCKET_RELOAD_REQUEST.register(CubeCraftPlusClient::loadRemoteData);

        CommandManager.register();

        ChatQueryManager.getInstance().init();
        FriendsManager.getInstance().init();
        ChestFinderManager.getInstance().init();
        CubeCraftManager.getInstance().init();
        GameManager.getInstance().init();
        // After the managers, so a signal is logged with the game they just concluded
        SignalProbe.getInstance().init();
        AutoVoteManager.getInstance().init();
        LeaderboardSubmitManager.getInstance().init();
        CubeSocket.getInstance().init();
    }

    /** Every join rather than once at startup, since this data changes server-side. */
    private static void loadRemoteData() {
        LOGGER.info("Loading data from Cubepanion");
        GameRegistry.getInstance().load();
        AutoVoteConfigs.getInstance().load();
        LeaderboardSubmitManager.getInstance().loadConfiguration();
        ChestFinderManager.getInstance().loadLocations();
    }
}
