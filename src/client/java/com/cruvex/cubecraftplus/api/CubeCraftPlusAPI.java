package com.cruvex.cubecraftplus.api;

import com.cruvex.cubecraftplus.debug.Debug;
import com.cruvex.cubecraftplus.update.UpdateChecker;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Client for our own backend (cubecraft-tracker). Only ever talks to that service: the bearer token
 * it carries must never reach Cubepanion, which keeps its own client in {@code cubepanion/}.
 */
public final class CubeCraftPlusAPI {

    private static final String PRODUCTION_URL = "https://cubecraftplus.net";
    private static final String DEVELOPMENT_URL = "http://localhost:8080";

    /**
     * The {@code cubecraftplus.api.url} system property overrides the default, e.g.
     * {@code JAVA_TOOL_OPTIONS=-Dcubecraftplus.api.url=http://localhost:8080}.
     */
    private static final String BASE_URL = baseUrl();

    private static final String USER_AGENT = "CubeCraftPlus/" + UpdateChecker.installedVersion()
            + " (MC " + UpdateChecker.minecraftVersion() + ")";

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Gson gson = new Gson();

    private static CubeCraftPlusAPI instance;

    private CubeCraftPlusAPI() {
    }

    public static CubeCraftPlusAPI getInstance() {
        if (instance == null) {
            instance = new CubeCraftPlusAPI();
        }
        return instance;
    }

    /** Step 1 of the handshake: a server id to join with, and the secret to verify with. */
    CompletableFuture<Challenge> challenge(String username) {
        return post("/api/auth/challenge", new ChallengeRequest(username), Challenge.class);
    }

    /** Steps 3 and 4: the API asks Mojang who joined with {@code serverId} and mints them a token. */
    CompletableFuture<MintedToken> verify(Challenge challenge) {
        return post("/api/auth/verify", new VerifyRequest(challenge.serverId(), challenge.verifier()), MintedToken.class);
    }

    /** Who the API thinks {@code token} belongs to. */
    CompletableFuture<Me> me(Token token) {
        HttpRequest request = request("/api/auth/me", token).GET().build();
        return send(request).thenApply(body -> parse(body, Me.class));
    }

    private <T> CompletableFuture<T> post(String path, Object body, Class<T> type) {
        HttpRequest request = request(path, null)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                .build();
        return send(request).thenApply(response -> parse(response, type));
    }

    private static HttpRequest.Builder request(String path, Token token) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT);
        if (token != null) {
            builder.header("Authorization", "Bearer " + token.value());
        }
        return builder;
    }

    // Bodies are never logged: requests carry the verifier, responses the token
    private static CompletableFuture<String> send(HttpRequest request) {
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (response.statusCode() == 200) {
                        return CompletableFuture.completedFuture(response.body());
                    }
                    Debug.log("CubeCraftPlusAPI {} returned {}", request.uri().getPath(), response.statusCode());
                    return CompletableFuture.failedFuture(new ApiException(request.uri().getPath(), response.statusCode()));
                });
    }

    private static <T> T parse(String body, Class<T> type) {
        T value = body == null || body.isEmpty() ? null : gson.fromJson(body, type);
        if (value == null) {
            throw new JsonParseException("Empty response from CubeCraftPlusAPI");
        }
        return value;
    }

    private static String baseUrl() {
        String override = System.getProperty("cubecraftplus.api.url");
        if (override != null && !override.isBlank()) {
            return override.endsWith("/") ? override.substring(0, override.length() - 1) : override;
        }
        return FabricLoader.getInstance().isDevelopmentEnvironment() ? DEVELOPMENT_URL : PRODUCTION_URL;
    }

    /** A response other than 200; {@link #isUnauthorized()} means the credential was refused. */
    static final class ApiException extends IOException {

        private final int status;

        ApiException(String path, int status) {
            super("CubeCraftPlusAPI " + path + " returned " + status);
            this.status = status;
        }

        boolean isUnauthorized() {
            return status == 401;
        }
    }

    private record ChallengeRequest(String username) {}

    private record VerifyRequest(String serverId, String verifier) {}

    record Challenge(String serverId, String verifier) {}

    record MintedToken(String token, UUID uuid, String expiresAt) {}

    public record Me(UUID uuid, String expiresAt) {}
}
