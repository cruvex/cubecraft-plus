package com.cruvex.cubecraftplus.events;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.PlayerTeam;

public final class ScoreboardEvents {

    /** A display slot was pointed at an objective. */
    public static final Event<SetDisplayObjective> SET_DISPLAY_OBJECTIVE = EventFactory.createArrayBacked(
            SetDisplayObjective.class,
            callbacks -> (slot, objectiveName) -> {
                for (SetDisplayObjective callback : callbacks) {
                    callback.onSetDisplayObjective(slot, objectiveName);
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
    public interface SetDisplayObjective {
        void onSetDisplayObjective(DisplaySlot slot, String objectiveName);
    }

    @FunctionalInterface
    public interface TeamChange {
        void onTeamChange(PlayerTeam team);
    }

    private ScoreboardEvents() {
    }
}
