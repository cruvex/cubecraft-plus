package com.cruvex.cubecraftplus.cubesocket.protocol.packets;

import com.cruvex.cubecraftplus.cubesocket.protocol.Packet;
import com.cruvex.cubecraftplus.cubesocket.protocol.PacketBuffer;
import com.cruvex.cubecraftplus.game.Game;

import java.time.Instant;

/** A game's current player count, keyed by the game's display name. */
public class PacketGameStatUpdate extends Packet {

    private String game;
    private int playerCount;
    private long timestamp;

    public PacketGameStatUpdate() {
    }

    public PacketGameStatUpdate(Game game, int playerCount) {
        this.game = game.displayName();
        this.playerCount = playerCount;
        this.timestamp = Instant.now().toEpochMilli();
    }

    @Override
    public void read(PacketBuffer buf) {
        this.game = buf.readString();
        this.playerCount = buf.readInt();
        this.timestamp = buf.readLong();
    }

    @Override
    public void write(PacketBuffer buf) {
        buf.writeString(this.game);
        buf.writeInt(this.playerCount);
        buf.writeLong(this.timestamp);
    }
}
