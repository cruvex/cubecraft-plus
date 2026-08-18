package com.cruvex.cubecraftplus.cubesocket.protocol.packets;

import com.cruvex.cubecraftplus.cubesocket.protocol.Packet;
import com.cruvex.cubecraftplus.cubesocket.protocol.PacketBuffer;
import com.cruvex.cubecraftplus.cubesocket.protocol.PacketHandler;

public class PacketDisconnect extends Packet {

    private String reason;

    public PacketDisconnect() {
        this.reason = "Unknown";
    }

    public PacketDisconnect(String reason) {
        this.reason = reason;
    }

    @Override
    public void read(PacketBuffer buf) {
        this.reason = buf.readString();
    }

    @Override
    public void write(PacketBuffer buf) {
        buf.writeString(this.reason);
    }

    @Override
    public void handle(PacketHandler packetHandler) {
        packetHandler.handle(this);
    }

    public String getReason() {
        return this.reason;
    }
}
