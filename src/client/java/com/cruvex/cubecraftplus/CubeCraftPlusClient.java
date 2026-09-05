package com.cruvex.cubecraftplus;

import com.cruvex.cubecraftplus.commands.CommandManager;
import com.cruvex.cubecraftplus.cubesocket.CubeSocket;
import com.cruvex.cubecraftplus.events.CubeEvents;
import com.cruvex.cubecraftplus.external.CubepanionAPI;
import com.cruvex.cubecraftplus.managers.AutoVoteManager;
import com.cruvex.cubecraftplus.managers.ConfigManager;
import com.cruvex.cubecraftplus.managers.CubeCraftManager;
import com.cruvex.cubecraftplus.managers.GameManager;
import com.cruvex.cubecraftplus.managers.LeaderboardSubmitManager;
import com.cruvex.cubecraftplus.util.SignalProbe;
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
        // Seed games and autovote from cache, then the bundled copy, so both keep
        // working when the API is down
        CubepanionAPI.getInstance().seedOfflineData();

        CubeEvents.CUBE_JOIN.register(() -> CubepanionAPI.getInstance().loadInitialData());

        CommandManager.register();

        CubeCraftManager.getInstance().init();
        GameManager.getInstance().init();
        // After the managers, so a signal is logged with the game they just concluded
        SignalProbe.getInstance().init();
        AutoVoteManager.getInstance().init();
        LeaderboardSubmitManager.getInstance().init();
        CubeSocket.getInstance().init();
    }
}
