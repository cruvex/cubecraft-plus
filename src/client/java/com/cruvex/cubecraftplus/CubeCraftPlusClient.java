package com.cruvex.cubecraftplus;

import com.cruvex.cubecraftplus.commands.CommandManager;
import com.cruvex.cubecraftplus.external.CubepanionAPI;
import com.cruvex.cubecraftplus.managers.AutoVoteManager;
import com.cruvex.cubecraftplus.managers.ConfigManager;
import com.cruvex.cubecraftplus.managers.CubeCraftManager;
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
		// TODO: only load on CubeCraft join
		CubepanionAPI.getInstance().loadInitialData();

		// TODO: only load on CubeCraft join ?
		CommandManager.register();

		// TODO: only load on CubeCraft join
		CubeCraftManager.getInstance().init();

		// TODO: only load on CubeCraft join
		AutoVoteManager.getInstance().init();
	}
}