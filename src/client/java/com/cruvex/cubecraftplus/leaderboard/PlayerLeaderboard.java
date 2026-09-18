package com.cruvex.cubecraftplus.leaderboard;

import java.util.List;

public record PlayerLeaderboard(int totalScore, int totalRank, List<LeaderboardRow> leaderboards) {

}