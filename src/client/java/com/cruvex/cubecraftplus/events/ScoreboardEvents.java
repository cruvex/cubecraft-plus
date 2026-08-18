package com.cruvex.cubecraftplus.events;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;

public final class ScoreboardEvents {

    public static final Event<AddObjective> ADD_OBJECTIVE = EventFactory.createArrayBacked(
            AddObjective.class,
            callbacks -> objective -> {
                for (AddObjective callback : callbacks) {
                    callback.onAddObjective(objective);
                }
            });

    public static final Event<TeamChange> TEAM_CHANGE = EventFactory.createArrayBacked(
            TeamChange.class,
            callbacks -> team -> {
                for (TeamChange callback : callbacks) {
                    callback.onTeamChange(team);
                }
            });

    @FunctionalInterface
    public interface AddObjective {
        void onAddObjective(Objective objective);
    }

    @FunctionalInterface
    public interface TeamChange {
        void onTeamChange(PlayerTeam team);
    }

    private ScoreboardEvents() {
    }
}
