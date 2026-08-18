package com.cruvex.cubecraftplus.cubesocket.protocol;

public abstract class Packet {

    public abstract void read(PacketBuffer buf);

    public abstract void write(PacketBuffer buf);

    /** Outbound-only packets never arrive, so they leave this alone. */
    public void handle(PacketHandler packetHandler) {
    }
}
