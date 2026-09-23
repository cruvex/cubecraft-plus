package com.cruvex.cubecraftplus.cubesocket;

import com.cruvex.cubecraftplus.gui.toast.ModToast;
import net.minecraft.network.chat.Component;

public class CubeSocketNotifications {

    private static final Component TITLE = Component.literal("CubeSocket");

    public CubeSocketNotifications() {
        CubeSocketEvents.SOCKET_CONNECT.register(() -> ModToast.show(ModToast.Icon.CUBEPANION_HAPPY, TITLE,
                Component.translatable("cubecraftplus.cubesocket.connected")));
        CubeSocketEvents.SOCKET_DISCONNECT.register(reason -> ModToast.show(ModToast.Icon.CUBEPANION_SAD, TITLE,
                Component.translatable("cubecraftplus.cubesocket.disconnected", reason)));
        CubeSocketEvents.SOCKET_RELOAD_REQUEST.register(() -> ModToast.show(ModToast.Icon.CUBEPANION_HAPPY, TITLE,
                Component.translatable("cubecraftplus.cubesocket.reloaded")));
    }
}
