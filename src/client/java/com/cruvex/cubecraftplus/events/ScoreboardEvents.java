package com.cruvex.cubecraftplus.events;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import org.jetbrains.annotations.Nullable;

public interface ScoreboardEvents {
    Event<AddObjective> ADD_OBJECTIVE = EventFactory.createArrayBacked(
            AddObjective.class,
            callbacks -> objective -> {
                for (AddObjective callback : callbacks) {
                    callback.onAddObjective(objective);
                }
            });

    @FunctionalInterface
    interface AddObjective {
        void onAddObjective(@Nullable Objective objective);
    }
}
