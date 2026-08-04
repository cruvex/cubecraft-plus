package com.cruvex.cubecraftplus.cubesocket.protocol.packets;

import com.cruvex.cubecraftplus.cubesocket.protocol.Packet;
import com.cruvex.cubecraftplus.cubesocket.protocol.PacketBuffer;
import com.cruvex.cubecraftplus.cubesocket.protocol.PacketHandler;

public class PacketLoginComplete extends Packet {

  @Override
  public void read(PacketBuffer buf) {

  }

  @Override
  public void write(PacketBuffer buf) {

  }

  @Override
  public void handle(PacketHandler packetHandler) {
    packetHandler.handle(this);
  }
}
