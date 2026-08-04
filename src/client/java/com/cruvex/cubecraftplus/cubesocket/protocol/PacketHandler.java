package com.cruvex.cubecraftplus.cubesocket.protocol;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketDisconnect;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketHelloPong;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketLoginComplete;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketPong;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketReload;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;

public abstract class PacketHandler extends SimpleChannelInboundHandler<Object> {

  private static final Logger LOGGER = CubeCraftPlusClient.LOGGER;

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Object packet) {
    this.handlePacket((Packet) packet);
  }

  protected void handlePacket(Packet packet) {
    packet.handle(this);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    super.exceptionCaught(ctx, cause);
    LOGGER.error("An exception occurred while handling a CubeSocket packet", cause);
  }

  public abstract void handle(PacketPong packet);

  public abstract void handle(PacketHelloPong packet);

  public abstract void handle(PacketLoginComplete packet);

  public abstract void handle(PacketDisconnect packet);

  public abstract void handle(PacketReload packet);

}
