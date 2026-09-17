package com.cruvex.cubecraftplus.events;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.network.chat.Component;

public final class HudEvents {

    /** The server set the title text. A server that keeps a title up re-sends it. */
    public static final Event<Title> TITLE = EventFactory.createArrayBacked(
            Title.class,
            callbacks -> text -> {
                for (Title callback : callbacks) {
                    callback.onTitle(text);
                }
            });

    @FunctionalInterface
    public interface Title {
        void onTitle(Component text);
    }

    private HudEvents() {
    }
}
