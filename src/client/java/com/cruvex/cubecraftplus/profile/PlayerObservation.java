package com.cruvex.cubecraftplus.profile;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A player's profile as one chat message showed it; {@code legacy} marks a permanent rank, null when the message cannot tell. */
public record PlayerObservation(String name, @Nullable UUID uuid, List<Rank> ranks, @Nullable Boolean legacy, int level,
                                Instant observedAt) {

    public PlayerObservation withLegacy(@Nullable Boolean legacy) {
        return new PlayerObservation(name, uuid, ranks, legacy, level, observedAt);
    }

    public boolean sameProfile(PlayerObservation other) {
        return name.equals(other.name) && ranks.equals(other.ranks) && Objects.equals(legacy, other.legacy)
                && level == other.level;
    }
}
