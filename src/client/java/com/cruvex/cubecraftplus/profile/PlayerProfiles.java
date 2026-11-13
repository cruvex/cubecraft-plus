package com.cruvex.cubecraftplus.profile;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** The latest observation per player, keeping only what is new. Client thread only. */
public class PlayerProfiles {

    /** How long an unchanged profile counts as known before seeing it again is news. */
    private static final Duration REFRESH = Duration.ofMinutes(30);

    private static PlayerProfiles instance;

    private final Map<UUID, PlayerObservation> latest = new HashMap<>();

    public static PlayerProfiles getInstance() {
        if (instance == null) {
            instance = new PlayerProfiles();
        }
        return instance;
    }

    /** Fills an unknown legacy from the stored profile, as long as the ranks are the same. */
    public PlayerObservation withKnownLegacy(PlayerObservation observation) {
        if (observation.legacy() != null || observation.uuid() == null) return observation;

        PlayerObservation previous = latest.get(observation.uuid());
        return previous != null && previous.ranks().equals(observation.ranks())
                ? observation.withLegacy(previous.legacy())
                : observation;
    }

    /** Stores the observation if it changes the profile or the last one is due a refresh; returns whether it did. */
    public boolean record(PlayerObservation observation) {
        if (observation.uuid() == null || observation.legacy() == null) return false;

        PlayerObservation previous = latest.get(observation.uuid());
        if (previous != null && previous.sameProfile(observation)
                && observation.observedAt().isBefore(previous.observedAt().plus(REFRESH))) {
            return false;
        }

        latest.put(observation.uuid(), observation);
        return true;
    }

    public @Nullable PlayerObservation get(UUID uuid) {
        return latest.get(uuid);
    }
}
