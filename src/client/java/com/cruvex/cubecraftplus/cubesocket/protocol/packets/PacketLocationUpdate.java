package com.cruvex.cubecraftplus.cubesocket.protocol.packets;

import com.cruvex.cubecraftplus.cubesocket.protocol.Packet;
import com.cruvex.cubecraftplus.cubesocket.protocol.PacketBuffer;
import com.cruvex.cubecraftplus.cubesocket.protocol.PacketHandler;
import com.cruvex.cubecraftplus.managers.CubeCraftManager;

public class PacketLocationUpdate extends Packet {

  private String origin;
  private String destination;
  private boolean preLobby;

  public PacketLocationUpdate() {
  }

  public PacketLocationUpdate(String origin, String destination, boolean preLobby) {
    this.origin = origin;
    this.destination = destination;
    this.preLobby = preLobby;
  }

  /** Lobby move built from the tracked server ids, sent once the socket is logged in. */
  public static PacketLocationUpdate lobbyMove() {
    CubeCraftManager manager = CubeCraftManager.getInstance();

    return new PacketLocationUpdate(
        "main_lobby-lobby" + manager.getLastServerId(),
        "main_lobby-lobby" + manager.getServerId(),
        false
    );
  }

  @Override
  public void read(PacketBuffer buf) {
    this.origin = buf.readString();
    this.destination = buf.readString();
    this.preLobby = buf.readBoolean();
  }

  @Override
  public void write(PacketBuffer buf) {
    buf.writeString(this.origin);
    buf.writeString(this.destination);
    buf.writeBoolean(this.preLobby);
  }

  @Override
  public void handle(PacketHandler packetHandler) {

  }

  public String getOrigin() {
    return this.origin;
  }

  public String getDestination() {
    return this.destination;
  }

  public boolean isPreLobby() {
    return this.preLobby;
  }
}
