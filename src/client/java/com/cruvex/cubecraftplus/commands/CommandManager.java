package com.cruvex.cubecraftplus.commands;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public class CommandManager {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess) -> {
            LeaderboardCommand.register(dispatcher);
        }));
    }
}
