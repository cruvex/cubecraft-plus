package com.cruvex.cubecraftplus.cubesocket.protocol.packets;

import com.cruvex.cubecraftplus.cubesocket.protocol.Packet;
import com.cruvex.cubecraftplus.cubesocket.protocol.PacketBuffer;
import com.cruvex.cubecraftplus.cubesocket.protocol.PacketHandler;

import java.util.UUID;

public class PacketLogin extends Packet {

  private UUID uuid;

  public PacketLogin() {
  }

  public PacketLogin(UUID uuid) {
    this.uuid = uuid;
  }

  @Override
  public void read(PacketBuffer buf) {

  }

  @Override
  public void write(PacketBuffer buf) {
    buf.writeUUID(this.uuid);
  }

  @Override
  public void handle(PacketHandler packetHandler) {

  }
}
