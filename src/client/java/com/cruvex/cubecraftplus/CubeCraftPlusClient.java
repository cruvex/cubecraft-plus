package com.cruvex.cubecraftplus;

import com.cruvex.cubecraftplus.commands.CommandManager;
import com.cruvex.cubecraftplus.external.CubepanionAPI;
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

		CubepanionAPI.I().loadInitialData();

		CommandManager.register();
	}
}