package com.cruvex.cubecraftplus.events;

import com.cruvex.cubecraftplus.util.Debug;
import com.cruvex.cubecraftplus.util.Util;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

public class ServerEventHandler {

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onServerJoin(client));
    }

    private static void onServerJoin(Minecraft client) {
        String ip = Util.getServerIp(client);

        if (!Util.isKubusMaken(ip)) {
            Debug.log("Joined {}, not CubeCraft", ip == null ? "singleplayer" : ip);
            return;
        }

        Debug.log("Joined CubeCraft ({})", ip);
        CubeEvents.CUBE_JOIN.invoker().onCubeJoin();
    }
}
