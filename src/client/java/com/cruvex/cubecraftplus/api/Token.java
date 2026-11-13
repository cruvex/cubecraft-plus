package com.cruvex.cubecraftplus.api;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A mod bearer token for {@link CubeCraftPlusAPI}. In memory only: it lives a day and re-minting it is a
 * silent call on the next join, so it never earns a place on disk.
 */
record Token(String value, UUID uuid, Instant expiresAt) {

    /** Re-minted a little early rather than sent at the edge of its life. */
    private static final Duration MARGIN = Duration.ofMinutes(5);

    /**
     * A hint only, read off the local clock: the API's 401 is what actually retires a token. This just
     * keeps a launch that outlives its token from sending one that is sure to be refused.
     */
    boolean isExpiring() {
        return Instant.now().isAfter(expiresAt.minus(MARGIN));
    }

    /** Keeps the value out of logs and crash reports. */
    @Override
    public String toString() {
        return "Token[uuid=" + uuid + ", expiresAt=" + expiresAt + "]";
    }
}
