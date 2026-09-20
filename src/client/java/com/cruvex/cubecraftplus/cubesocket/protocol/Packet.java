package com.cruvex.cubecraftplus.cubesocket.protocol;

public abstract class Packet {

    public abstract void read(PacketBuffer buf);

    public abstract void write(PacketBuffer buf);

    /** Handles an inbound packet; outbound-only packets never arrive and leave this alone. */
    public void handle(PacketHandler packetHandler) {
    }
}
