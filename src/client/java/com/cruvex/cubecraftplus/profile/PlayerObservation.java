package com.cruvex.cubecraftplus.profile;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A player's profile as one chat message showed it; {@code legacy} marks a permanent rather than subscription rank. */
public record PlayerObservation(String name, @Nullable UUID uuid, List<Rank> ranks, boolean legacy, int level,
                                Instant observedAt) {

    public boolean sameProfile(PlayerObservation other) {
        return name.equals(other.name) && ranks.equals(other.ranks) && legacy == other.legacy && level == other.level;
    }
}
