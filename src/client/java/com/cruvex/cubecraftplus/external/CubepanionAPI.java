package com.cruvex.cubecraftplus.external;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.model.AutoVoteCategory;
import com.cruvex.cubecraftplus.model.AutoVoteCategoryOption;
import com.cruvex.cubecraftplus.model.AutoVoteConfiguration;
import com.cruvex.cubecraftplus.model.BatchRequest;
import com.cruvex.cubecraftplus.model.Game;
import com.cruvex.cubecraftplus.model.Leaderboard;
import com.cruvex.cubecraftplus.model.LeaderboardConfiguration;
import com.cruvex.cubecraftplus.model.LeaderboardRow;
import com.cruvex.cubecraftplus.model.PlayerLeaderboard;
import com.cruvex.cubecraftplus.model.Submission;
import com.cruvex.cubecraftplus.util.Debug;
import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** Client for the Cubepanion HTTP API, plus the autovote config it serves from GitHub. */
public class CubepanionAPI {

    private static final Logger LOGGER = CubeCraftPlusClient.LOGGER;

    private static final boolean DEV = System.getenv("DEV") != null;
    private static final String BASE_URL = DEV ? "http://192.168.0.193:5050/api" : "https://cubepanion.ameliah.art/api";
    private static final String BASE_URL_V2 = BASE_URL + "/v2";

    // Served from the repo rather than the API, so adding a game only takes a commit there
    private static final String AUTO_VOTE_CONFIG_URL =
            "https://raw.githubusercontent.com/Fesaa/Cubepanion/refs/heads/main/config/auto_vote.json";
    private static final String BUNDLED_AUTO_VOTE_CONFIG = "assets/cubecraft-plus/auto_vote.json";
    private static final String BUNDLED_GAMES = "assets/cubecraft-plus/games.json";

    private static final TypeToken<List<Game>> GAMES = new TypeToken<>() {};
    private static final TypeToken<List<LeaderboardRow>> LEADERBOARD_ROWS = new TypeToken<>() {};
    private static final TypeToken<List<AutoVoteConfiguration>> AUTO_VOTE_CONFIGS = new TypeToken<>() {};
    private static final TypeToken<LeaderboardConfiguration> LEADERBOARD_CONFIG = TypeToken.get(LeaderboardConfiguration.class);
    private static final TypeToken<Leaderboard> LEADERBOARD = TypeToken.get(Leaderboard.class);
    private static final TypeToken<PlayerLeaderboard> PLAYER_LEADERBOARD = TypeToken.get(PlayerLeaderboard.class);

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    private static CubepanionAPI instance;

    // Swapped rather than mutated on reload: the client thread reads these while an HTTP thread writes
    private volatile Map<String, Game> games = Map.of();
    private volatile Map<Integer, Game> gameById = Map.of();
    private volatile List<AutoVoteConfiguration> autoVoteConfigurations = List.of();
    private volatile LeaderboardConfiguration leaderboardConfiguration = LeaderboardConfiguration.DISABLED;

    private CubepanionAPI() {
    }

    public static CubepanionAPI getInstance() {
        if (instance == null) {
            instance = new CubepanionAPI();
        }
        return instance;
    }

    /**
     * Last known good data, so game detection and autovote work at tick 0 with GitHub or the
     * API unreachable. Cache first, then the copy bundled in the jar.
     */
    public void seedOfflineData() {
        List<Game> games = ApiCache.getInstance().getGames();
        String gamesFrom = "cache";
        if (games.isEmpty()) {
            games = readBundled(BUNDLED_GAMES, GAMES);
            gamesFrom = "the bundled copy";
        }
        if (!games.isEmpty()) {
            indexGames(games);
            LOGGER.info("Seeded {} games from {}", games.size(), gamesFrom);
        }

        List<AutoVoteConfiguration> autoVote = sanitize(ApiCache.getInstance().getAutoVoteConfigurations());
        String autoVoteFrom = "cache";
        if (autoVote.isEmpty()) {
            autoVote = sanitize(readBundled(BUNDLED_AUTO_VOTE_CONFIG, AUTO_VOTE_CONFIGS));
            autoVoteFrom = "the bundled copy";
        }
        this.autoVoteConfigurations = autoVote;
        LOGGER.info("Seeded {} autovote configurations from {}", autoVote.size(), autoVoteFrom);
    }

    /** Run on every cube join, not once at startup: this data changes server-side. */
    public void loadInitialData() {
        LOGGER.info("Loading initial data from {}", BASE_URL_V2);

        loadLeaderboardConfiguration();
        loadGames();
        loadAutoVoteConfig();
    }

    private void loadGames() {
        get(BASE_URL_V2 + "/Games", GAMES)
                .thenAcceptAsync(games -> {
                    if (games == null) {
                        return;
                    }

                    indexGames(games);
                    ApiCache.getInstance().putGames(games);
                    LOGGER.info("Loaded {} games", games.size());
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to load games, some features may not work correctly", ex);
                    return null;
                });
    }

    private void indexGames(List<Game> games) {
        Map<String, Game> byName = new HashMap<>();
        Map<Integer, Game> byId = new HashMap<>();
        for (Game game : games) {
            byId.put(game.id(), game);
            index(byName, game.name(), game);
            index(byName, game.displayName(), game);
            game.aliases().forEach(alias -> index(byName, alias, game));
        }
        this.games = byName;
        this.gameById = byId;
    }

    private static void index(Map<String, Game> byName, @Nullable String key, Game game) {
        if (key != null && !key.isBlank()) {
            byName.put(normalize(key), game);
        }
    }

    /** Both sides of the lookup go through this, so aliases with spaces or capitals resolve. */
    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    /** A failed or empty fetch keeps whatever was seeded, rather than clearing it. */
    public CompletableFuture<Void> loadAutoVoteConfig() {
        return get(AUTO_VOTE_CONFIG_URL, AUTO_VOTE_CONFIGS)
                .thenAccept(configurations -> {
                    List<AutoVoteConfiguration> sanitized = sanitize(configurations);
                    if (sanitized.isEmpty()) {
                        LOGGER.warn("Autovote config came back empty, keeping the {} configurations already loaded",
                                this.autoVoteConfigurations.size());
                        return;
                    }

                    this.autoVoteConfigurations = sanitized;
                    ApiCache.getInstance().putAutoVoteConfigurations(sanitized);
                    LOGGER.info("Loaded {} autovote configurations", sanitized.size());
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to load the autovote config, keeping the {} configurations already loaded",
                            this.autoVoteConfigurations.size(), ex);
                    return null;
                });
    }

    public CompletableFuture<Void> loadLeaderboardConfiguration() {
        return get(BASE_URL_V2 + "/Leaderboard/config", LEADERBOARD_CONFIG)
                .thenAccept(config -> {
                    if (config == null) {
                        LOGGER.warn("Leaderboard configuration request came back empty, leaderboard submitting stays off");
                        return;
                    }

                    this.leaderboardConfiguration = config;
                    LOGGER.info("Loaded leaderboard configuration: enabled={}, {} places over {} pages",
                            config.enabled(), config.playerCount(), config.pageCount());
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to load leaderboard configuration, leaderboard submitting stays off", ex);
                    return null;
                });
    }

    private <T> List<T> readBundled(String resource, TypeToken<List<T>> type) {
        Optional<Path> path = FabricLoader.getInstance()
                .getModContainer(CubeCraftPlusClient.MOD_ID)
                .flatMap(container -> container.findPath(resource));
        if (path.isEmpty()) {
            LOGGER.warn("Bundled {} is missing from the mod jar", resource);
            return List.of();
        }

        try (Reader reader = Files.newBufferedReader(path.get())) {
            List<T> value = gson.fromJson(reader, type);
            return value == null ? List.of() : value;
        } catch (IOException | JsonParseException e) {
            LOGGER.warn("Failed to read bundled {}", resource, e);
            return List.of();
        }
    }

    /** Drops malformed entries, so nothing downstream null-checks or bounds-checks a remote number. */
    private static List<AutoVoteConfiguration> sanitize(@Nullable List<AutoVoteConfiguration> configurations) {
        if (configurations == null) {
            return List.of();
        }

        List<AutoVoteConfiguration> result = new ArrayList<>();
        for (AutoVoteConfiguration configuration : configurations) {
            if (configuration == null || configuration.gameName() == null) {
                continue;
            }
            if (configuration.hotbarSlot() < 0 || configuration.hotbarSlot() > 8) {
                LOGGER.warn("Skipping autovote config for {}: hotbar slot {} is out of range",
                        configuration.gameName(), configuration.hotbarSlot());
                continue;
            }

            List<AutoVoteCategory> categories = configuration.categories();
            if (categories == null || categories.isEmpty() || !categoriesAreValid(configuration, categories)) {
                continue;
            }

            result.add(configuration);
        }
        return List.copyOf(result);
    }

    private static boolean categoriesAreValid(AutoVoteConfiguration configuration, List<AutoVoteCategory> categories) {
        for (AutoVoteCategory category : categories) {
            if (category == null || category.id() == null || category.name() == null) {
                return false;
            }

            List<AutoVoteCategoryOption> options = category.options();
            if (options == null || options.isEmpty() || category.choiceIndex() < -1) {
                LOGGER.warn("Skipping autovote config for {}: category {} is malformed",
                        configuration.gameName(), category.id());
                return false;
            }

            for (AutoVoteCategoryOption option : options) {
                if (option == null || option.name() == null || option.slot() < -1) {
                    LOGGER.warn("Skipping autovote config for {}: category {} has a malformed option",
                            configuration.gameName(), category.id());
                    return false;
                }
            }
        }
        return true;
    }

    public List<AutoVoteConfiguration> getAutoVoteConfigurations() {
        return this.autoVoteConfigurations;
    }

    public LeaderboardConfiguration getLeaderboardConfiguration() {
        return this.leaderboardConfiguration;
    }

    public @Nullable Game getGameById(int id) {
        return this.gameById.get(id);
    }

    public @Nullable Game tryGame(String game) {
        return this.games.get(normalize(game));
    }

    public Collection<Game> getAllGames() {
        return this.gameById.values();
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

        // Submissions are queued, so success is 202 rather than 200
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
                .header("User-Agent", "CubeCraftPlus");
    }

    /** Completes with the response body, or fails when the status is anything but {@code expected}. */
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
}
