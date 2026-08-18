package com.cruvex.cubecraftplus.cubesocket.pipeline;

import com.cruvex.cubecraftplus.cubesocket.protocol.PacketBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class PacketSplitter extends ByteToMessageDecoder {

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) {
        buffer.markReaderIndex();
        if (!buffer.isReadable()) {
            buffer.resetReaderIndex();
            return;
        }

        int packetLength = PacketBuffer.readVarIntFromBuffer(buffer);
        if (buffer.readableBytes() < packetLength) {
            buffer.resetReaderIndex();
            return;
        }

        out.add(buffer.readBytes(packetLength));
    }
}
