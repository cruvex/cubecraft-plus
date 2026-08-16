package com.cruvex.cubecraftplus.config;

import java.util.HashMap;
import java.util.Map;

public class ModConfig {

    public AutoVoteConfig autoVote = new AutoVoteConfig();

    public LeaderboardSubmitConfig leaderboardSubmit = new LeaderboardSubmitConfig();

    public static class LeaderboardSubmitConfig {
        public boolean enabled = true;
    }

    public static class AutoVoteConfig {
        public boolean enabled = true;

        public Map<String, Integer> slots = new HashMap<>();
    }

    /** Repairs fields Gson left null, so nothing downstream has to null-check. */
    public void validate() {
        if (leaderboardSubmit == null) leaderboardSubmit = new LeaderboardSubmitConfig();

        if (autoVote == null) autoVote = new AutoVoteConfig();
        if (autoVote.slots == null) autoVote.slots = new HashMap<>();
    }
}
