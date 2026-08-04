package com.cruvex.cubecraftplus.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

public class Util {

    /** Address of the server the client is connected to, or null in singleplayer. */
    public static @Nullable String getServerIp(Minecraft client) {
        ServerData server = client.getCurrentServer();
        return server == null ? null : server.ip;
    }

    public static boolean isOnCubeCraft(Minecraft client) {
        return isKubusMaken(getServerIp(client));
    }

    public static String capitalize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    /** Whether a server address is CubeCraft, using Cubepanion's rules. */
    public static boolean isKubusMaken(String address) {
        if (address == null) return false;

        // Server list entries may carry a port, the domains below don't
        String host = address.toLowerCase(Locale.ROOT).trim();
        int portSeparator = host.lastIndexOf(':');
        if (portSeparator > -1) {
            host = host.substring(0, portSeparator);
        }

        if (host.endsWith("cubecraft.net")) return true;
        if (host.endsWith("cubecraftgames.net")) return true;
        if (host.endsWith("ccgn.co") && !host.contains("maps")) return true;

        // Dev and test servers
        return host.contains("-dev-cc") || host.endsWith("test.ziax.com");
    }
}
