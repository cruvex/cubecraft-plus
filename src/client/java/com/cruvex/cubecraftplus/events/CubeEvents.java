package com.cruvex.cubecraftplus.events;

import com.cruvex.cubecraftplus.model.Game;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import org.jetbrains.annotations.Nullable;

public final class CubeEvents {

    /** Joined CubeCraft itself. Fires once per connection, not per server switch. */
    public static final Event<CubeJoin> CUBE_JOIN =
            EventFactory.createArrayBacked(CubeJoin.class,
                    callbacks -> () -> {
                        for (CubeJoin cb : callbacks) cb.onCubeJoin();
                    });

    /** A new game instance: the lobby, a pre-game lobby, or the next round of the same game. */
    public static final Event<GameJoin> GAME_JOIN =
            EventFactory.createArrayBacked(GameJoin.class,
                    callbacks -> (game) -> {
                        for (GameJoin cb : callbacks) cb.onGameJoin(game);
                    });

    /** The game started. Never fires for the lobby. */
    public static final Event<GameStart> GAME_START =
            EventFactory.createArrayBacked(GameStart.class,
                    callbacks -> (game) -> {
                        for (GameStart cb : callbacks) cb.onGameStart(game);
                    });

    /** The client died and will respawn. */
    public static final Event<ClientDeath> CLIENT_DEATH =
            EventFactory.createArrayBacked(ClientDeath.class,
                    callbacks -> (game) -> {
                        for (ClientDeath cb : callbacks) cb.onClientDeath(game);
                    });

    /** The client is out of the game for good. The game may still be running. */
    public static final Event<ClientEliminated> CLIENT_ELIMINATED =
            EventFactory.createArrayBacked(ClientEliminated.class,
                    callbacks -> (game) -> {
                        for (ClientEliminated cb : callbacks) cb.onClientEliminated(game);
                    });

    /**
     * The game finished. The winner is a player name, a team colour, or null when unread.
     * Only fires while the client is still on the game's server.
     */
    public static final Event<GameEnd> GAME_END =
            EventFactory.createArrayBacked(GameEnd.class,
                    callbacks -> (game, winner) -> {
                        for (GameEnd cb : callbacks) cb.onGameEnd(game, winner);
                    });

    @FunctionalInterface
    public interface CubeJoin {
        void onCubeJoin();
    }

    @FunctionalInterface
    public interface GameJoin {
        void onGameJoin(Game game);
    }

    @FunctionalInterface
    public interface GameStart {
        void onGameStart(Game game);
    }

    @FunctionalInterface
    public interface ClientDeath {
        void onClientDeath(Game game);
    }

    @FunctionalInterface
    public interface ClientEliminated {
        void onClientEliminated(Game game);
    }

    @FunctionalInterface
    public interface GameEnd {
        void onGameEnd(Game game, @Nullable String winner);
    }

    private CubeEvents() {
    }
}
