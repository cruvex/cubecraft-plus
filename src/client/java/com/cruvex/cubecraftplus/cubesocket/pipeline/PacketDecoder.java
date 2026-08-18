package com.cruvex.cubecraftplus.cubesocket.pipeline;

import com.cruvex.cubecraftplus.cubesocket.CubeSocket;
import com.cruvex.cubecraftplus.cubesocket.protocol.Packet;
import com.cruvex.cubecraftplus.cubesocket.protocol.PacketBuffer;
import com.cruvex.cubecraftplus.util.Debug;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.io.IOException;
import java.util.List;

public class PacketDecoder extends ByteToMessageDecoder {

    private final CubeSocket cubeSocket;

    public PacketDecoder(CubeSocket cubeSocket) {
        this.cubeSocket = cubeSocket;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf byteBuf, List<Object> out) throws Exception {
        if (byteBuf.readableBytes() < 1) {
            return;
        }

        PacketBuffer packetBuffer = new PacketBuffer(byteBuf);
        int id = packetBuffer.readVarIntFromBuffer();
        Packet packet = this.cubeSocket.getProtocol().getPacket(id);

        // Ping and pong flow every few seconds, they'd drown out everything else
        if (id != 0 && id != 1) {
            Debug.log("CubeSocket in: {} {}", id, packet.getClass().getSimpleName());
        }

        packet.read(packetBuffer);
        if (byteBuf.readableBytes() > 0) {
            throw new IOException("Packet " + packet.getClass().getSimpleName() + " (" + id + ") was larger than expected, "
                    + byteBuf.readableBytes() + " bytes left over");
        }

        out.add(packet);
    }
}
