package com.cruvex.cubecraftplus.model;

/** Shape of CubeCraft's leaderboards, served by the API so it can change without a mod update. */
public record LeaderboardConfiguration(boolean enabled, int playerCount, int pageCount) {

  /** Used until the API answers, so nothing is submitted against a guessed layout. */
  public static final LeaderboardConfiguration DISABLED = new LeaderboardConfiguration(false, 200, 100);

  public boolean canSubmit() {
    return enabled && pageCount > 0 && playerCount > 0;
  }
}
