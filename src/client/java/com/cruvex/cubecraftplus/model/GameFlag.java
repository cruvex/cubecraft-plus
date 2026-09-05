package com.cruvex.cubecraftplus.model;

/**
 * Per-game behaviour flags, served by the API as the {@code enabledFlags} bitfield. The bit
 * positions are the API's, not ours to renumber.
 */
public enum GameFlag {
    RESPAWN_TAGS(1),
    /** The game drops the client straight in, with no cages or pre-game lobby. */
    NO_PRE_GAME_STATE(1 << 1),
    LOBBY(1 << 2),
    DISCORD_RPC_PLAYER_TRACKING(1 << 3),
    DONT_DROP_TOOLS(1 << 4),
    /** The sidebar changes during play, so its title is not a reliable game name. */
    IGNORE_SCOREBOARD_UPDATES(1 << 5),
    ALTERNATE_MAP_TRACKER(1 << 6),
    HAS_RESPAWNS(1 << 7),
    IN_GAME_AFTER_ELIMINATION(1 << 8),
    COOLDOWNS(1 << 9),
    MINI_GAME(1 << 10);

    private final int bit;

    GameFlag(int bit) {
        this.bit = bit;
    }

    public int bit() {
        return bit;
    }

    public boolean isSetIn(int flags) {
        return (flags & bit) != 0;
    }
}
