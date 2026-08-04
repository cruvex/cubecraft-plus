package com.cruvex.cubecraftplus.cubesocket.pipeline;

import com.cruvex.cubecraftplus.cubesocket.CubeSocket;
import com.cruvex.cubecraftplus.cubesocket.protocol.Packet;
import com.cruvex.cubecraftplus.cubesocket.protocol.PacketBuffer;
import com.cruvex.cubecraftplus.util.Debug;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class PacketEncoder extends MessageToByteEncoder<Packet> {

  private final CubeSocket cubeSocket;

  public PacketEncoder(CubeSocket cubeSocket) {
    this.cubeSocket = cubeSocket;
  }

  @Override
  protected void encode(ChannelHandlerContext ctx, Packet packet, ByteBuf byteBuf) {
    int id = this.cubeSocket.getProtocol().getPacketId(packet);
    if (id != 0 && id != 1) {
      Debug.log("CubeSocket out: {} {}", id, packet.getClass().getSimpleName());
    }

    PacketBuffer buffer = new PacketBuffer(byteBuf);
    buffer.writeVarIntToBuffer(id);
    packet.write(buffer);
  }
}
