package com.cruvex.cubecraftplus.config;

import java.util.HashMap;
import java.util.Map;

public class ModConfig {

    public AutoVoteConfig autoVote = new AutoVoteConfig();

    public LeaderboardSubmitConfig leaderboardSubmit = new LeaderboardSubmitConfig();

    public ChestFinderConfig chestFinder = new ChestFinderConfig();

    public UpdateCheckConfig updateCheck = new UpdateCheckConfig();

    public GameStatsConfig gameStats = new GameStatsConfig();

    public CubeSocketConfig cubeSocket = new CubeSocketConfig();

    public static class UpdateCheckConfig {
        public boolean enabled = true;
    }

    public static class GameStatsConfig {
        public boolean enabled = true;
    }

    public static class CubeSocketConfig {
        public boolean enabled = true;
    }

    public static class LeaderboardSubmitConfig {
        public boolean enabled = true;
    }

    public static class ChestFinderConfig {
        public boolean enabled = true;

        /** Draws a box and beam on the found chest. */
        public boolean highlight = true;
    }

    public static class AutoVoteConfig {
        public boolean enabled = true;

        /** Votes through the container packets, so the menus never open on screen. */
        public boolean silent = true;

        public Map<String, Integer> slots = new HashMap<>();
    }

    /** Fills in any field Gson left null, so nothing downstream has to null-check. */
    public void validate() {
        if (leaderboardSubmit == null) leaderboardSubmit = new LeaderboardSubmitConfig();
        if (chestFinder == null) chestFinder = new ChestFinderConfig();
        if (updateCheck == null) updateCheck = new UpdateCheckConfig();
        if (gameStats == null) gameStats = new GameStatsConfig();
        if (cubeSocket == null) cubeSocket = new CubeSocketConfig();

        if (autoVote == null) autoVote = new AutoVoteConfig();
        if (autoVote.slots == null) autoVote.slots = new HashMap<>();
    }
}
