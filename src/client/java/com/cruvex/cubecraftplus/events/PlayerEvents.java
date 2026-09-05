package com.cruvex.cubecraftplus.events;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.level.GameType;

public final class PlayerEvents {

    /**
     * The server respawned the client.
     *
     * @see net.minecraft.network.protocol.game.ClientboundRespawnPacket#dataToKeep()
     */
    public static final Event<Respawn> RESPAWN = EventFactory.createArrayBacked(
            Respawn.class,
            callbacks -> dataToKeep -> {
                for (Respawn callback : callbacks) {
                    callback.onRespawn(dataToKeep);
                }
            });

    /** The server changed the client's game mode. */
    public static final Event<GameModeChange> GAME_MODE_CHANGE = EventFactory.createArrayBacked(
            GameModeChange.class,
            callbacks -> mode -> {
                for (GameModeChange callback : callbacks) {
                    callback.onGameModeChange(mode);
                }
            });

    @FunctionalInterface
    public interface Respawn {
        void onRespawn(byte dataToKeep);
    }

    @FunctionalInterface
    public interface GameModeChange {
        void onGameModeChange(GameType mode);
    }

    private PlayerEvents() {
    }
}
