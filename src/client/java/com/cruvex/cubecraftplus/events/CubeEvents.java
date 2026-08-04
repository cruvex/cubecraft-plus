package com.cruvex.cubecraftplus.events;

import com.cruvex.cubecraftplus.model.CubeGame;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public class CubeEvents {
    public static final Event<CubeJoin> CUBE_JOIN =
            EventFactory.createArrayBacked(CubeJoin.class,
                    callbacks -> () -> {
                        for (CubeJoin cb : callbacks) cb.onCubeJoin();
                    });

    public static final Event<CubeGameJoin> GAME_JOIN =
            EventFactory.createArrayBacked(CubeGameJoin.class,
                    callbacks -> (game) -> {
                        for (CubeGameJoin cb : callbacks) cb.onGameJoin(game);
                    });

    @FunctionalInterface
    public interface CubeJoin {
        void onCubeJoin();
    }

    @FunctionalInterface
    public interface CubeGameJoin {
        void onGameJoin(CubeGame game);
    }
}
