package com.cruvex.cubecraftplus;

import com.cruvex.cubecraftplus.commands.CommandManager;
import com.cruvex.cubecraftplus.cubesocket.CubeSocket;
import com.cruvex.cubecraftplus.events.CubeEvents;
import com.cruvex.cubecraftplus.events.ServerEventHandler;
import com.cruvex.cubecraftplus.external.CubepanionAPI;
import com.cruvex.cubecraftplus.managers.AutoVoteManager;
import com.cruvex.cubecraftplus.managers.ConfigManager;
import com.cruvex.cubecraftplus.managers.CubeCraftManager;
import com.cruvex.cubecraftplus.managers.LeaderboardSubmitManager;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CubeCraftPlusClient implements ClientModInitializer {

	public static final String MOD_ID = "cubecraft-plus";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("{} client init", MOD_ID);
		LOGGER.debug("{} DEBUG", MOD_ID);

		ConfigManager.getInstance().init();

		ServerEventHandler.register();
		CubeEvents.CUBE_JOIN.register(() -> CubepanionAPI.getInstance().loadInitialData());

		CommandManager.register();

		CubeCraftManager.getInstance().init();
		AutoVoteManager.getInstance().init();
		LeaderboardSubmitManager.getInstance().init();
		CubeSocket.getInstance().init();
	}
}