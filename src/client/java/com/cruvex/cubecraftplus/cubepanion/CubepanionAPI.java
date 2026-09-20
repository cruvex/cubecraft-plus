package com.cruvex.cubecraftplus.cubepanion;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.autovote.AutoVoteConfiguration;
import com.cruvex.cubecraftplus.chestfinder.ChestLocation;
import com.cruvex.cubecraftplus.debug.Debug;
import com.cruvex.cubecraftplus.game.Game;
import com.cruvex.cubecraftplus.leaderboard.Leaderboard;
import com.cruvex.cubecraftplus.leaderboard.LeaderboardConfiguration;
import com.cruvex.cubecraftplus.leaderboard.LeaderboardRow;
import com.cruvex.cubecraftplus.leaderboard.PlayerLeaderboard;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Client for the Cubepanion API and the autovote config on its GitHub; holds no data itself. */
public class CubepanionAPI {

    private static final Logger LOGGER = CubeCraftPlusClient.LOGGER;

    private static final String BASE_URL = "https://cubepanion.ameliah.art/api";
    private static final String BASE_URL_V2 = BASE_URL + "/v2";

    // Served from Cubepanion's repo rather than its API
    private static final String AUTO_VOTE_CONFIG_URL =
            "https://raw.githubusercontent.com/Fesaa/Cubepanion/refs/heads/main/config/auto_vote.json";

    private static final TypeToken<List<Game>> GAMES = new TypeToken<>() {};
    private static final TypeToken<List<LeaderboardRow>> LEADERBOARD_ROWS = new TypeToken<>() {};
    private static final TypeToken<List<AutoVoteConfiguration>> AUTO_VOTE_CONFIGS = new TypeToken<>() {};
    private static final TypeToken<List<ChestLocation>> CHEST_LOCATIONS = new TypeToken<>() {};
    private static final TypeToken<LeaderboardConfiguration> LEADERBOARD_CONFIG = TypeToken.get(LeaderboardConfiguration.class);
    private static final TypeToken<Leaderboard> LEADERBOARD = TypeToken.get(Leaderboard.class);
    private static final TypeToken<PlayerLeaderboard> PLAYER_LEADERBOARD = TypeToken.get(PlayerLeaderboard.class);

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    private static CubepanionAPI instance;

    private CubepanionAPI() {
    }

    public static CubepanionAPI getInstance() {
        if (instance == null) {
            instance = new CubepanionAPI();
        }
        return instance;
    }

    public CompletableFuture<List<Game>> fetchGames() {
        return get(BASE_URL_V2 + "/Games", GAMES);
    }

    public CompletableFuture<List<AutoVoteConfiguration>> fetchAutoVoteConfigs() {
        return get(AUTO_VOTE_CONFIG_URL, AUTO_VOTE_CONFIGS);
    }

    public CompletableFuture<LeaderboardConfiguration> fetchLeaderboardConfiguration() {
        return get(BASE_URL_V2 + "/Leaderboard/config", LEADERBOARD_CONFIG);
    }

    public CompletableFuture<List<ChestLocation>> fetchChestLocations() {
        return get(BASE_URL + "/Chests", CHEST_LOCATIONS);
    }

    public CompletableFuture<Leaderboard> getLeaderboard(Game game, int lower, int upper) {
        return get(String.format("%s/Leaderboard/game/%s?lower=%s&upper=%s",
                BASE_URL_V2, game.name(), lower, upper), LEADERBOARD);
    }

    public CompletableFuture<PlayerLeaderboard> getPlayerLeaderboard(String name) {
        return get(BASE_URL_V2 + "/Leaderboard/player/" + name, PLAYER_LEADERBOARD);
    }

    public CompletableFuture<Void> submit(Game game, List<LeaderboardRow> entries, String playerUuid) {
        String json = gson.toJson(new Submission(playerUuid, game.id(), entries));
        HttpRequest request = request(BASE_URL_V2 + "/Leaderboard")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        Debug.log("Leaderboard submit to {}: {} ({}), {} places as {}",
                request.uri(), game.name(), game.id(), entries.size(), playerUuid);
        LOGGER.debug("Leaderboard submit body: {}", json);

        // 202, not 200: submissions are queued
        return send(request, 202).thenAccept(body -> {});
    }

    public CompletableFuture<List<LeaderboardRow>> batch(Game game, List<String> players) {
        String json = gson.toJson(new BatchRequest(game.name(), players));
        HttpRequest request = request(BASE_URL + "/Leaderboard/batch")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        return send(request, 200).thenApply(body -> {
            List<LeaderboardRow> rows = parse(body, LEADERBOARD_ROWS);
            return rows == null ? List.<LeaderboardRow>of() : rows;
        });
    }

    private <T> CompletableFuture<T> get(String url, TypeToken<T> type) {
        return send(request(url).GET().build(), 200).thenApply(body -> parse(body, type));
    }

    private static HttpRequest.Builder request(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("User-Agent", "CubeCraftPlus-fabric-mod");
    }

    private static CompletableFuture<String> send(HttpRequest request, int expected) {
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (response.statusCode() == expected) {
                        return CompletableFuture.completedFuture(response.body());
                    }

                    String body = response.body() == null ? "" : response.body();
                    Debug.log("CubepanionAPI {} returned {}: {}", request.uri(), response.statusCode(), body);
                    return CompletableFuture.failedFuture(new IOException("CubepanionAPI " + request.uri()
                            + " returned " + response.statusCode() + ", body: " + body));
                });
    }

    private static <T> @Nullable T parse(@Nullable String body, TypeToken<T> type) {
        if (body == null || body.isEmpty()) {
            Debug.log("CubepanionAPI returned an empty response");
            return null;
        }
        return gson.fromJson(body, type);
    }

    private record Submission(String uuid, int gameId, List<LeaderboardRow> entries) {}

    private record BatchRequest(String game, List<String> players) {}
}
