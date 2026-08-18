package com.cruvex.cubecraftplus.model;

import java.util.List;

/**
 * A game as the API defines it.
 *
 * @param aliases other ways of writing the game, e.g. tew, li
 * @param scoreType what the leaderboard counts: wins, medals, kills
 * @param shouldTrack whether the game is eligible for tracking by the stats tracker
 * @param hasPreLobby a real pre-lobby, as opposed to the cages that also count in manager
 */
public record Game(int id, String name, String displayName, List<String> aliases, boolean active,
                   String scoreType, boolean shouldTrack, boolean hasPreLobby) {
}
