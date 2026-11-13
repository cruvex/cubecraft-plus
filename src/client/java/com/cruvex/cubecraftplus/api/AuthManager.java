package com.cruvex.cubecraftplus.api;

import com.cruvex.cubecraftplus.debug.Debug;
import com.cruvex.cubecraftplus.game.CubeCraftManager;
import com.cruvex.cubecraftplus.game.CubeEvents;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.ForcedUsernameChangeException;
import com.mojang.authlib.exceptions.InsufficientPrivilegesException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import com.mojang.authlib.exceptions.UserBannedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Proves to {@link CubeCraftPlusAPI} which account the player is on, through Mojang's session server
 * (player-verification.md §3), and holds the resulting token per account.
 *
 * <p>Tokens are keyed by profile uuid and looked up from {@link Minecraft#getUser()} at the moment of use,
 * so an account switcher mid-launch gets its own token instead of borrowing the last one.
 */
public final class AuthManager {

    private static final Duration JOIN_DELAY = Duration.ofSeconds(5);

    private static AuthManager instance;

    private final CubeCraftPlusAPI api = CubeCraftPlusAPI.getInstance();

    private final Map<UUID, Token> tokens = new ConcurrentHashMap<>();
    /** A second join by the same account replaces the first at Mojang, so never run two at once. */
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    /** Accounts Mojang refused outright; not retried until the next launch. */
    private final Set<UUID> refused = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> lastFailure = new ConcurrentHashMap<>();

    /** joinServer blocks on HTTP, so it gets its own thread rather than the client's or the common pool. */
    private final ExecutorService joinExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "CubeCraftPlus-Auth");
        thread.setDaemon(true);
        return thread;
    });

    private AuthManager() {
    }

    public static AuthManager getInstance() {
        if (instance == null) {
            instance = new AuthManager();
        }
        return instance;
    }

    public void init() {
        // Fires once the connection is in the play phase, so CubeCraft's own join has already gone
        // through, and only once per CubeCraft session, not on every server switch inside it
        CubeEvents.CUBE_JOIN.register(this::scheduleAuthentication);
    }

    /**
     * Runs {@link #authenticate()} a few seconds after joining. Mojang has answered 429 to our join when it
     * came a second or two after CubeCraft's own, so ours is kept out of that window.
     */
    private void scheduleAuthentication() {
        UUID uuid = currentUuid();
        CompletableFuture.delayedExecutor(JOIN_DELAY.toMillis(), TimeUnit.MILLISECONDS).execute(() ->
                Minecraft.getInstance().execute(() -> {
                    if (!CubeCraftManager.getInstance().isOnCubeCraft()) {
                        Debug.log("Skipping CubeCraft+ authentication: left CubeCraft");
                    } else if (!uuid.equals(currentUuid())) {
                        Debug.log("Skipping CubeCraft+ authentication: account switched");
                    } else {
                        authenticate();
                    }
                }));
    }

    /** Runs the handshake for the current account unless it has a token, one is running, or Mojang refused it. */
    public void authenticate() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isDemo() || !mc.allowsMultiplayer() || mc.multiplayerBan() != null) {
            Debug.log("Skipping CubeCraft+ authentication: demo, multiplayer blocked or banned");
            return;
        }

        User user = mc.getUser();
        UUID uuid = user.getProfileId();
        if (token(uuid) != null) {
            Debug.log("Skipping CubeCraft+ authentication for {}: already has a token", uuid);
            return;
        }
        if (refused.contains(uuid)) {
            Debug.log("Skipping CubeCraft+ authentication for {}: Mojang refused it this launch", uuid);
            return;
        }
        if (!inFlight.add(uuid)) {
            Debug.log("Skipping CubeCraft+ authentication for {}: already in progress", uuid);
            return;
        }

        // Read now, on the client thread, so a switch mid-handshake cannot mix two accounts
        String username = user.getName();
        String accessToken = user.getAccessToken();
        Debug.log("Authenticating {} ({}) with CubeCraft+", username, uuid);

        api.challenge(username)
                .thenApplyAsync(challenge -> {
                    joinServer(mc, uuid, accessToken, challenge.serverId());
                    return challenge;
                }, joinExecutor)
                .thenCompose(api::verify)
                .whenComplete((minted, error) -> {
                    inFlight.remove(uuid);
                    if (error != null) {
                        onFailure(uuid, unwrap(error));
                    } else if (!uuid.equals(minted.uuid())) {
                        // Mojang vouched for someone else; filing it under either account would misattribute
                        onFailure(uuid, "Verified as " + minted.uuid() + " instead");
                    } else {
                        tokens.put(uuid, new Token(minted.token(), uuid, Instant.parse(minted.expiresAt())));
                        lastFailure.remove(uuid);
                        Debug.log("Authenticated {} with CubeCraft+", uuid);
                    }
                });
    }

    /** Whether the current account holds a token the API has not yet refused. */
    public boolean isAuthenticated() {
        return current() != null;
    }

    public boolean isAuthenticating() {
        return inFlight.contains(currentUuid());
    }

    /** Why the current account's last handshake failed, if it did. */
    public Optional<String> lastFailure() {
        return Optional.ofNullable(lastFailure.get(currentUuid()));
    }

    /** Asks the API who the current token belongs to; fails when there is none or it was refused. */
    public CompletableFuture<CubeCraftPlusAPI.Me> whoAmI() {
        Token token = current();
        if (token == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Not authenticated"));
        }
        return api.me(token).whenComplete((me, error) -> {
            if (error != null) {
                retireIfRefused(token, error);
            }
        });
    }

    /** Drops {@code token} if {@code error} is the API refusing it, so the next join runs the handshake again. */
    void retireIfRefused(Token token, Throwable error) {
        if (unwrap(error) instanceof CubeCraftPlusAPI.ApiException refusal && refusal.isUnauthorized()) {
            tokens.remove(token.uuid(), token);
            Debug.log("CubeCraft+ token for {} was refused; re-authenticating on next join", token.uuid());
        }
    }

    @Nullable Token current() {
        return token(currentUuid());
    }

    private @Nullable Token token(UUID uuid) {
        Token token = tokens.get(uuid);
        if (token != null && token.isExpiring()) {
            tokens.remove(uuid, token);
            return null;
        }
        return token;
    }

    private static UUID currentUuid() {
        return Minecraft.getInstance().getUser().getProfileId();
    }

    private static void joinServer(Minecraft mc, UUID uuid, String accessToken, String serverId) {
        try {
            mc.services().sessionService().joinServer(uuid, accessToken, serverId);
        } catch (AuthenticationException e) {
            throw new CompletionException(e);
        }
    }

    private void onFailure(UUID uuid, Throwable error) {
        // Bad credentials (a dev launch), blocked multiplayer, bans and forced renames will not change this
        // launch. Anything else, including a plain AuthenticationException such as a 429, gets the next join
        if (error instanceof InvalidCredentialsException
                || error instanceof InsufficientPrivilegesException
                || error instanceof UserBannedException
                || error instanceof ForcedUsernameChangeException) {
            refused.add(uuid);
        }
        onFailure(uuid, error.getClass().getSimpleName()
                + (error.getMessage() == null ? "" : ": " + error.getMessage()));
    }

    private void onFailure(UUID uuid, String reason) {
        lastFailure.put(uuid, reason);
        // Never the access token, verifier or minted token: the reason comes from exception text only
        Debug.log("CubeCraft+ authentication failed for {}: {}", uuid, reason);
    }

    private static Throwable unwrap(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null) {
            error = error.getCause();
        }
        return error;
    }
}
