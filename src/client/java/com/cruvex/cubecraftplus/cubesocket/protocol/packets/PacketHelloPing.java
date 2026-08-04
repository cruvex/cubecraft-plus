package com.cruvex.cubecraftplus.cubesocket.protocol.packets;

import com.cruvex.cubecraftplus.cubesocket.protocol.Packet;
import com.cruvex.cubecraftplus.cubesocket.protocol.PacketBuffer;
import com.cruvex.cubecraftplus.cubesocket.protocol.PacketHandler;

import java.time.Instant;

public class PacketHelloPing extends Packet {

  private long timestamp;

  public PacketHelloPing() {
    this.timestamp = Instant.now().toEpochMilli();
  }

  public PacketHelloPing(long timestamp) {
    this.timestamp = timestamp;
  }

  @Override
  public void read(PacketBuffer buf) {
    this.timestamp = buf.readLong();
  }

  @Override
  public void write(PacketBuffer buf) {
    buf.writeLong(this.timestamp);
  }

  @Override
  public void handle(PacketHandler packetHandler) {

  }

  public long getTimestamp() {
    return this.timestamp;
  }
}
