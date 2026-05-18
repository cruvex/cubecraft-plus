package com.cruvex.cubecraftplus.model;

import java.util.List;

public record PlayerLeaderboard(int totalScore, int totalRank, List<LeaderboardRow> leaderboards) {

}