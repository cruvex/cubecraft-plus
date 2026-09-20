package com.cruvex.cubecraftplus.commands;

import com.cruvex.cubecraftplus.game.CubeCraftManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public class CommandManager {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess) -> {
            if (!CubeCraftManager.getInstance().isOnCubeCraft()) return;

            LeaderboardCommand.register(dispatcher);
            CcpCommand.register(dispatcher);
        }));
    }
}
