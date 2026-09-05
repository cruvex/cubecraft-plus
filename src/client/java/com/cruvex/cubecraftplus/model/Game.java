package com.cruvex.cubecraftplus.model;

import java.util.List;

/**
 * A game as the API defines it.
 *
 * @param aliases other ways of writing the game, e.g. tew, li
 * @param scoreType what the leaderboard counts: wins, medals, kills
 * @param shouldTrack whether the game is eligible for tracking by the stats tracker
 * @param hasPreLobby a real pre-lobby, as opposed to cages
 * @param enabledFlags {@link GameFlag} bitfield, read through {@link #hasFlag}
 */
public record Game(int id, String name, String displayName, List<String> aliases, boolean active,
                   String scoreType, boolean shouldTrack, boolean hasPreLobby, int enabledFlags) {

    public boolean hasFlag(GameFlag flag) {
        return flag.isSetIn(enabledFlags);
    }

    /** The lobby is a game like any other to the API, but has no leaderboard to search. */
    public boolean isLobby() {
        return "main_lobby".equals(name);
    }
}
