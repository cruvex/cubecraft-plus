package com.cruvex.cubecraftplus.events;

import com.cruvex.cubecraftplus.cubesocket.CubeSocketState;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

public class CubeSocketEvents {

    public static final Event<Connected> SOCKET_CONNECT =
            EventFactory.createArrayBacked(Connected.class,
                    callbacks -> () -> {
                        for (Connected cb : callbacks) cb.onConnected();
                    });

    public static final Event<Disconnected> SOCKET_DISCONNECT =
            EventFactory.createArrayBacked(Disconnected.class,
                    callbacks -> reason -> {
                        for (Disconnected cb : callbacks) cb.onDisconnected(reason);
                    });

    public static final Event<ReloadRequested> SOCKET_RELOAD_REQUEST =
            EventFactory.createArrayBacked(ReloadRequested.class,
                    callbacks -> () -> {
                        for (ReloadRequested cb : callbacks) cb.onReloadRequested();
                    });

    public static final Event<StateUpdated> SOCKET_STATE_UPDATE =
            EventFactory.createArrayBacked(StateUpdated.class,
                    callbacks -> state -> {
                        for (StateUpdated cb : callbacks) cb.onStateUpdated(state);
                    });

    @FunctionalInterface
    public interface Connected {
        void onConnected();
    }

    @FunctionalInterface
    public interface Disconnected {
        void onDisconnected(String reason);
    }

    @FunctionalInterface
    public interface ReloadRequested {
        void onReloadRequested();
    }

    @FunctionalInterface
    public interface StateUpdated {
        void onStateUpdated(CubeSocketState state);
    }
}
