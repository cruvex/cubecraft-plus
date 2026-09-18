package com.cruvex.cubecraftplus.leaderboard;

import java.util.Date;
import java.util.List;

public record Leaderboard(int gameId, Date lastUpdated, List<LeaderboardRow> rows) {

}