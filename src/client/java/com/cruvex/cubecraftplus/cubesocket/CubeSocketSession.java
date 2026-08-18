package com.cruvex.cubecraftplus.cubesocket;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.cubesocket.protocol.PacketHandler;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketDisconnect;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketHelloPong;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketLocationUpdate;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketLogin;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketLoginComplete;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketPing;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketPong;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketReload;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketSetProtocol;
import com.cruvex.cubecraftplus.events.CubeSocketEvents;
import com.cruvex.cubecraftplus.external.CubepanionAPI;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class CubeSocketSession extends PacketHandler {

    private static final Logger LOGGER = CubeCraftPlusClient.LOGGER;

    private final CubeSocket socket;

    private long lastReload = -1;

    public CubeSocketSession(CubeSocket socket) {
        this.socket = socket;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (this.socket.getState() != CubeSocketState.OFFLINE) {
            this.socket.updateState(CubeSocketState.OFFLINE);
            CubeSocketEvents.SOCKET_DISCONNECT.invoker().onDisconnected("Server forced a disconnect");
        }
    }

    @Override
    public void handle(PacketHelloPong packet) {
        this.socket.updateState(CubeSocketState.LOGIN);

        Minecraft client = Minecraft.getInstance();
        UUID uuid = client.player != null ? client.player.getUUID() : UUID.randomUUID();

        this.socket.sendPacket(new PacketLogin(uuid));
    }

    @Override
    public void handle(PacketPong packet) {
        this.socket.keepAlive();
        this.socket.schedule(() -> this.socket.sendPacket(new PacketPing()), 5L, TimeUnit.SECONDS);
    }

    @Override
    public void handle(PacketLoginComplete packet) {
        this.socket.updateState(CubeSocketState.CONNECTED);

        CubeSocketEvents.SOCKET_CONNECT.invoker().onConnected();
        this.socket.sendPacket(new PacketPing());

        this.socket.schedule(
                () -> this.socket.sendPacket(new PacketSetProtocol(SharedConstants.getProtocolVersion())),
                1L, TimeUnit.SECONDS);

        this.socket.schedule(
                () -> this.socket.sendPacket(PacketLocationUpdate.lobbyMove()),
                2L, TimeUnit.SECONDS);
    }

    @Override
    public void handle(PacketDisconnect packet) {
        this.socket.updateState(CubeSocketState.OFFLINE);
        CubeSocketEvents.SOCKET_DISCONNECT.invoker().onDisconnected(packet.getReason());
    }

    @Override
    public void handle(PacketReload packet) {
        long now = System.currentTimeMillis();
        if (now - this.lastReload < 5000L) {
            this.lastReload = now;
            LOGGER.warn("CubeSocket tried reloading data less than 5s apart, ignoring");
            return;
        }

        CubeSocketEvents.SOCKET_RELOAD_REQUEST.invoker().onReloadRequested();
        CubepanionAPI.getInstance().loadInitialData();
        this.lastReload = now;
    }
}
