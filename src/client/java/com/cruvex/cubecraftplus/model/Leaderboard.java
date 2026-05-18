package com.cruvex.cubecraftplus.model;

import java.util.Date;
import java.util.List;

public record Leaderboard(int gameId, Date lastUpdated, List<LeaderboardRow> rows) {

}