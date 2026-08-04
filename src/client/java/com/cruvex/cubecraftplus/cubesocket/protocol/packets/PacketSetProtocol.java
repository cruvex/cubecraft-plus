package com.cruvex.cubecraftplus.cubesocket.protocol.packets;

import com.cruvex.cubecraftplus.cubesocket.protocol.Packet;
import com.cruvex.cubecraftplus.cubesocket.protocol.PacketBuffer;
import com.cruvex.cubecraftplus.cubesocket.protocol.PacketHandler;

public class PacketSetProtocol extends Packet {

  private int protocolVersion;

  public PacketSetProtocol() {
  }

  public PacketSetProtocol(int protocolVersion) {
    this.protocolVersion = protocolVersion;
  }

  @Override
  public void read(PacketBuffer buf) {
    this.protocolVersion = buf.readInt();
  }

  @Override
  public void write(PacketBuffer buf) {
    buf.writeInt(this.protocolVersion);
  }

  @Override
  public void handle(PacketHandler packetHandler) {

  }

  public int getProtocolVersion() {
    return this.protocolVersion;
  }
}
