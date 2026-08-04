package com.cruvex.cubecraftplus.cubesocket.pipeline;

import com.cruvex.cubecraftplus.cubesocket.protocol.PacketBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class PacketPrepender extends MessageToByteEncoder<ByteBuf> {

  @Override
  protected void encode(ChannelHandlerContext ctx, ByteBuf buffer, ByteBuf out) {
    int length = buffer.readableBytes();
    int varInt = PacketBuffer.getVarIntSize(length);
    if (varInt > 3) {
      throw new IllegalArgumentException("unable to fit " + length + " into 3");
    }

    out.ensureWritable(varInt + length);
    PacketBuffer.writeVarIntToBuffer(out, length);
    out.writeBytes(buffer, buffer.readerIndex(), length);
  }
}
