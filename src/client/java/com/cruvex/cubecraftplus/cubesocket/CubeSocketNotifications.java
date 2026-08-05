package com.cruvex.cubecraftplus.cubesocket;

import com.cruvex.cubecraftplus.events.CubeSocketEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

public class CubeSocketNotifications {

  public CubeSocketNotifications() {
    CubeSocketEvents.SOCKET_CONNECT.register(() -> toast("Successfully connected"));
    CubeSocketEvents.SOCKET_DISCONNECT.register(this::toast);
    CubeSocketEvents.SOCKET_RELOAD_REQUEST.register(() -> toast("Successfully reloaded"));
  }

  private void toast(String message) {
    Minecraft client = Minecraft.getInstance();
    client.execute(() -> SystemToast.add(
        client.gui.toastManager(),
        SystemToast.SystemToastId.NARRATOR_TOGGLE,
        Component.literal("CubeSocket"),
        Component.literal(message)
    ));
  }
}
