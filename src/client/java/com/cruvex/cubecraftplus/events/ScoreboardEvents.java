package com.cruvex.cubecraftplus.events;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.Nullable;

public interface ScoreboardEvents {
    Event<AddObjective> ADD_OBJECTIVE = EventFactory.createArrayBacked(
            AddObjective.class,
            callbacks -> objective -> {
                for (AddObjective callback : callbacks) {
                    callback.onAddObjective(objective);
                }
            });

    Event<TeamChange> TEAM_CHANGE = EventFactory.createArrayBacked(
            TeamChange.class,
            callbacks -> team -> {
                for (TeamChange callback : callbacks) {
                    callback.onTeamChange(team);
                }
            });

    @FunctionalInterface
    interface AddObjective {
        void onAddObjective(@Nullable Objective objective);
    }

    @FunctionalInterface
    interface TeamChange {
        void onTeamChange(PlayerTeam team);
    }
}
