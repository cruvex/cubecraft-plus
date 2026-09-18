package com.cruvex.cubecraftplus.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class Chat {

    public static void send(Component message) {
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendSystemMessage(message);
            }
        });
    }
}
