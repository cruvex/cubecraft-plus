package com.cruvex.cubecraftplus.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.jetbrains.annotations.Nullable;

public class Util {

    /** Address of the server the client is connected to, or null in singleplayer. */
    public static @Nullable String getServerIp(Minecraft client) {
        ServerData server = client.getCurrentServer();
        return server == null ? null : server.ip;
    }

    public static String capitalize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }
}
