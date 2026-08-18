package com.cruvex.cubecraftplus.events;

import com.cruvex.cubecraftplus.model.Game;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public final class CubeEvents {

    public static final Event<CubeJoin> CUBE_JOIN =
            EventFactory.createArrayBacked(CubeJoin.class,
                    callbacks -> () -> {
                        for (CubeJoin cb : callbacks) cb.onCubeJoin();
                    });

    public static final Event<GameJoin> GAME_JOIN =
            EventFactory.createArrayBacked(GameJoin.class,
                    callbacks -> (game) -> {
                        for (GameJoin cb : callbacks) cb.onGameJoin(game);
                    });

    @FunctionalInterface
    public interface CubeJoin {
        void onCubeJoin();
    }

    @FunctionalInterface
    public interface GameJoin {
        void onGameJoin(Game game);
    }

    private CubeEvents() {
    }
}
