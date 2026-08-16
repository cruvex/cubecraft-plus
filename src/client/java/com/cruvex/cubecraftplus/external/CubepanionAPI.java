package com.cruvex.cubecraftplus.external;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.managers.CacheManager;
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
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class CubepanionAPI {
    private static final Logger LOGGER = CubeCraftPlusClient.LOGGER;

    private final TypeToken<List<Game>> gamesToken = new TypeToken<>() {};
    private final TypeToken<List<LeaderboardRow>> lbRowsToken = new TypeToken<>() {};
    private final TypeToken<List<AutoVoteConfiguration>> autoVoteConfigToken = new TypeToken<>() {};
//  private final TypeToken<List<ChestLocation>> chestLocationToken = new TypeToken<>() {};
//  private final TypeToken<List<GameMap>> gameMapsToken = new TypeToken<>() {};

    private final String baseUrl = System.getenv("DEV") == null ?
      "https://cubepanion.ameliah.art/api" : "http://192.168.0.193:5050/api";
    private final String baseUrlv2 = System.getenv("DEV") == null ?
      "https://cubepanion.ameliah.art/api/v2" : "http://192.168.0.193:5050/api/v2";

    // Served from the repo rather than the API, so adding a game only takes a commit there
    private static final String AUTO_VOTE_CONFIG_URL =
            "https://raw.githubusercontent.com/Fesaa/Cubepanion/refs/heads/main/config/auto_vote.json";
    private static final String BUNDLED_AUTO_VOTE_CONFIG = "assets/cubecraft-plus/auto_vote.json";

    // Swapped rather than mutated on reload: the client thread reads these while an HTTP thread writes
    private volatile Map<String, Game> games = Map.of();
    private volatile Map<Integer, Game> gameById = Map.of();
    private volatile List<AutoVoteConfiguration> autoVoteConfigurations = List.of();
    private volatile LeaderboardConfiguration leaderboardConfiguration = LeaderboardConfiguration.DISABLED;
//  private final List<ChestLocation> chestLocations = new ArrayList<>();
//  private final HashMap<Integer, HashMap<String, AbstractGameMap>> convertedGameMaps = new HashMap<>();

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    private static CubepanionAPI instance = null;

    public CubepanionAPI() {
        instance = this;
    }

    public static CubepanionAPI getInstance() {
        if (instance == null) instance = new CubepanionAPI();
        return instance;
    }

  /**
   * Loads AutoVoteConfigurations first from cache, then from bundled copy.
   */
  public void seedFromCache() {
    CacheManager cache = CacheManager.getInstance();

    List<Game> cachedGames = cache.getGames();
    if (!cachedGames.isEmpty()) {
      indexGames(cachedGames);
      LOGGER.info("Seeded {} games from cache", cachedGames.size());
    }

    List<AutoVoteConfiguration> cached = sanitize(cache.getAutoVoteConfigurations());
    if (!cached.isEmpty()) {
      this.autoVoteConfigurations = cached;
      LOGGER.info("Seeded {} autovote configurations from cache", cached.size());
      return;
    }

    List<AutoVoteConfiguration> bundled = sanitize(readBundledAutoVoteConfig());
    this.autoVoteConfigurations = bundled;
    LOGGER.info("Seeded {} autovote configurations from the bundled copy", bundled.size());
  }

  /** Run on every cube join, not once at startup: this data changes server-side. */
  public void loadInitialData() {
    LOGGER.info("Loading initial data from {} & {}", this.baseUrl, this.baseUrlv2);

    this.loadLeaderboardConfiguration();
    this.loadGames();
    this.loadAutoVoteConfig();
  }

  private void loadGames() {
    this.getGames()
        .exceptionallyAsync(ex -> {
            LOGGER.error("Failed to load games, some features may not work correctly {}", ex);
          return null;
        })
        .thenAcceptAsync(games -> {
          if (games == null) {
            return;
          }

          indexGames(games);
          CacheManager.getInstance().putGames(games);

            LOGGER.info("Loaded {} games", games.size());
        })
        .exceptionallyAsync(ex -> {
            LOGGER.error("Failed to load games, some features may not work correctly {}", ex);
          return null;
        });
  }

  private void indexGames(List<Game> games) {
    Map<String, Game> byName = new HashMap<>();
    Map<Integer, Game> byId = new HashMap<>();
    for (var game : games) {
      byId.put(game.id(), game);

      byName.put(game.name(), game);
      byName.put(game.displayName(), game);
      game.aliases().forEach(a -> byName.put(a, game));
    }
    this.games = byName;
    this.gameById = byId;
  }

  /** A failed or empty fetch keeps whatever was seeded, rather than clearing it. */
  public CompletableFuture<Void> loadAutoVoteConfig() {
    return this.get(AUTO_VOTE_CONFIG_URL, autoVoteConfigToken)
        .thenAccept(configurations -> {
          List<AutoVoteConfiguration> sanitized = sanitize(configurations);
          if (sanitized.isEmpty()) {
            LOGGER.warn("Autovote config came back empty, keeping the {} configurations already loaded",
                this.autoVoteConfigurations.size());
            return;
          }

          this.autoVoteConfigurations = sanitized;
          CacheManager.getInstance().putAutoVoteConfigurations(sanitized);
          LOGGER.info("Loaded {} autovote configurations", sanitized.size());
        })
        .exceptionally(ex -> {
          LOGGER.error("Failed to load the autovote config, keeping the {} configurations already loaded",
              this.autoVoteConfigurations.size(), ex);
          return null;
        });
  }

  public List<AutoVoteConfiguration> getAutoVoteConfigurations() {
    return this.autoVoteConfigurations;
  }

  private List<AutoVoteConfiguration> readBundledAutoVoteConfig() {
    Optional<Path> path = FabricLoader.getInstance()
        .getModContainer(CubeCraftPlusClient.MOD_ID)
        .flatMap(container -> container.findPath(BUNDLED_AUTO_VOTE_CONFIG));
    if (path.isEmpty()) {
      LOGGER.warn("Bundled autovote config {} is missing from the mod jar", BUNDLED_AUTO_VOTE_CONFIG);
      return List.of();
    }

    try (Reader reader = Files.newBufferedReader(path.get())) {
      return gson.fromJson(reader, autoVoteConfigToken);
    } catch (IOException | JsonParseException e) {
      LOGGER.warn("Failed to read the bundled autovote config", e);
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

//    this.loadChestLocations()
//        .exceptionallyAsync(ex -> {
//            LOGGER.error("Failed to load chest locations, some features may not work correctly {}", ex);
//          return null;
//        })
//        .thenAcceptAsync(chestLocations -> {
//          if (chestLocations == null) {
//            return;
//          }
//
//          this.chestLocations.clear();
//          this.chestLocations.addAll(chestLocations);
//          log.info("Loaded {} chest locations", this.chestLocations.size());
//        })
//        .exceptionallyAsync(ex -> {
//          log.error("Failed to load chest locations, some features may not work correctly {}", ex);
//          return null;
//        });

//    this.loadGameMaps()
//        .exceptionallyAsync(ex -> {
//            LOGGER.error("Failed to load game maps, some features may not work correctly {}", ex);
//          return null;
//        })
//        .thenAcceptAsync(gameMaps -> {
//          if (gameMaps == null) {
//              LOGGER.warn("Failed to load game maps, some features may not work correctly");
//            return;
//          }
//
//            LOGGER.info("Loaded {} game-maps", gameMaps.size());
//
//          this.convertedGameMaps.clear();
//          for (var gameMap : gameMaps) {
//            var convertedMap = AbstractGameMap.constructFromAPI(gameMap);
//
//            var map = this.convertedGameMaps.putIfAbsent(gameMap.gameId(), new HashMap<>());
//            if (map == null) {
//              continue;
//            }
//            map.put(gameMap.mapName().toLowerCase(), convertedMap);
//          }
//
//            LOGGER.info("Loaded {} game-maps", this.convertedGameMaps.size());
//        })
//        .exceptionallyAsync(ex -> {
//            LOGGER.error("Failed to load game maps, some features may not work correctly {}", ex);
//          return null;
//        });

//  public boolean hasMaps(CubeGame cubeGame) {
//    var game = this.tryGame(cubeGame.getString());
//    if (game == null) {
//      return false;
//    }
//
//    return this.convertedGameMaps.containsKey(game.id());
//  }

//  @Nullable
//  public AbstractGameMap currentMap() {
//    var m = Cubepanion.get().getManager();
//    return getGameMap(m.getDivision(), m.getMapName());
//  }

//  @Nullable
//  public AbstractGameMap getGameMap(CubeGame cubeGame, String mapName) {
//    var game = this.tryGame(cubeGame.getString());
//    if (game == null) {
//      return null;
//    }
//
//    var maps = this.convertedGameMaps.get(game.id());
//    if (maps == null) {
//      return null;
//    }
//
//    return maps.get(mapName.toLowerCase());
//  }

//  public List<ChestLocation> getChestLocations() {
//    return this.chestLocations;
//  }

  public CompletableFuture<Void> loadLeaderboardConfiguration() {
    return this.get(this.baseUrlv2 + "/Leaderboard/config", LeaderboardConfiguration.class)
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

  public LeaderboardConfiguration getLeaderboardConfiguration() {
    return this.leaderboardConfiguration;
  }

  @Nullable
  public Game getGameById(int id) {
    return this.gameById.get(id);
  }

  @Nullable
  private Game getGame(String game) {
    return this.games.get(game);
  }

//  @Nullable
//  public Game getGame(CubeGame cubeGame) {
//    return this.tryGame(cubeGame.getString());
//  }
//
//  @Nullable
//  public Game getNotNullGame(String game) {
//    return this.games.getOrDefault(game, Game.UNKNOWN);
//  }

  @Nullable
  public Game tryGame(String game) {
    return this.getGame(game.replace(" ", "_").toLowerCase().trim());
  }

  public Collection<Game> getAllGames() {
    return this.gameById.values();
  }

//  public int totalGames() {
//    return new HashSet<>(this.games.values()).size();
//  }
//
  public CompletableFuture<Leaderboard> getLeaderboard(Game game, int lower, int upper) {
    return this.get(String.format("%s/Leaderboard/game/%s?lower=%s&upper=%s",
        this.baseUrlv2, game.name(), lower, upper), Leaderboard.class);
  }

  public CompletableFuture<PlayerLeaderboard> getPlayerLeaderboard(String name) {
    return this.get(this.baseUrlv2+"/Leaderboard/player/"+name, PlayerLeaderboard.class);
  }

//  public CompletableFuture<List<ChestLocation>> loadChestLocations() {
//    return this.get(this.baseUrl+"/Chests", chestLocationToken);
//  }

//  public CompletableFuture<List<GameMap>> loadGameMaps() {
//    return this.get(this.baseUrlv2+"/Maps", gameMapsToken);
//  }

  public CompletableFuture<List<Game>> getGames() {
    return this.get(this.baseUrlv2+"/Games", gamesToken);
  }

  public CompletableFuture<Void> submit(Game game, List<LeaderboardRow> entries, String playerUuid) {
      var submission = new Submission(playerUuid, game.id(), entries);
      String json = gson.toJson(submission);

      HttpRequest request = HttpRequest.newBuilder()
              .uri(URI.create(baseUrlv2 + "/Leaderboard"))
              .header("Content-Type", "application/json")
              .header("User-Agent", "CubeCraftPlus")
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .build();

      Debug.log("Leaderboard submit to {}: {} ({}), {} places as {}",
              request.uri(), game.name(), game.id(), entries.size(), playerUuid);
      LOGGER.debug("Leaderboard submit body: {}", json);

      return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
              .thenCompose(response -> {
                  // Submissions are queued, so success is 202 rather than 200
                  if (response.statusCode() != 202) {
                      String body = response.body() == null ? "" : response.body();
                      Debug.log("Failed leaderboard submit to {}, {}: {}", request.uri(), response.statusCode(), body);
                      return CompletableFuture.<Void>failedFuture(
                              new IllegalArgumentException("CubepanionAPI submit returned non-202: " + response.statusCode() + ", body: " + body)
                      );
                  }

                  return CompletableFuture.<Void>completedFuture(null);
              });
  }

  public CompletableFuture<List<LeaderboardRow
          >> batch(Game game, List<String> players) {
      var batchRequest = new BatchRequest(game.name(), players);
      String json = gson.toJson(batchRequest);

      HttpRequest request = HttpRequest.newBuilder()
              .uri(URI.create(baseUrl + "/Leaderboard/batch"))
              .header("Content-Type", "application/json")
              .header("User-Agent", "CubeCraftPlus")
              .POST(HttpRequest.BodyPublishers.ofString(json))
              .build();

      return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
              .thenCompose(response -> {
                  if (response.statusCode() != 200) {
                      Debug.log("Failed batch request to {}, {}", request.uri(), response.statusCode());
                      return CompletableFuture.failedFuture(
                              new IllegalArgumentException("CubepanionAPI batch returned non-200: " + response.statusCode())
                      );
                  }

                  if (response.body() == null || response.body().isEmpty()) {
                      return CompletableFuture.completedFuture(List.of());
                  }

                  try {
                      List<LeaderboardRow> rows = gson.fromJson(response.body(), lbRowsToken);
                      return CompletableFuture.completedFuture(rows == null ? List.of() : rows);
                  } catch (JsonSyntaxException e) {
                      return CompletableFuture.failedFuture(e);
                  }
              });
  }

    private <T> CompletableFuture<T> get(String url, Class<T> clazz) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("User-Agent", "CubeCraftPlus")
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (response.statusCode() != 200) {
                        Debug.log("Failed to make get request to {}, {}", url, response.statusCode());
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("CubepanionAPI returned a non 200 status code: " + response.statusCode())
                        );
                    }

                    if (response.body() == null || response.body().isEmpty()) {
                        Debug.log("CubepanionAPI returned an empty response");
                        return CompletableFuture.completedFuture(null);
                    }

                    try {
                        return CompletableFuture.completedFuture(
                                gson.fromJson(response.body(), clazz)
                        );
                    } catch (JsonSyntaxException e) {
                        return CompletableFuture.failedFuture(e);
                    }
                });
    }

    private <T> CompletableFuture<T> get(String url, TypeToken<T> token) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("User-Agent", "CubeCraftPlus")
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (response.statusCode() != 200) {
                        Debug.log("Failed to make get request to {}, {}", url, response.statusCode());
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("CubepanionAPI returned a non 200 status code: " + response.statusCode())
                        );
                    }

                    if (response.body() == null || response.body().isEmpty()) {
                        Debug.log("CubepanionAPI returned an empty response");
                        return CompletableFuture.completedFuture(null);
                    }

                    try {
                        return CompletableFuture.completedFuture(
                                gson.fromJson(response.body(), token)
                        );
                    } catch (JsonSyntaxException e) {
                        return CompletableFuture.failedFuture(e);
                    }
                });
    }
//
//  private <T> CompletableFuture<T> get(String url, TypeToken<T> token) {
//    CompletableFuture<T> future = new CompletableFuture<>();
//
//    Request.ofString()
//        .url(url)
//        .async()
//        .execute(c -> {
//          if (c.hasException()) {
//            log.debug("Failed to make get request to {}, {}", url, c.exception());
//            future.completeExceptionally(c.exception());
//            return;
//          }
//
//          if (c.getStatusCode() != 200) {
//            log.debug("Failed to make get request to {}, {}", url, c.getStatusCode());
//            future.completeExceptionally(new IllegalArgumentException("CubepanionAPI returned a non 200 status code: " + c.getStatusCode()));
//            return;
//          }
//
//          if (c.isEmpty()) {
//            log.debug("CubepanionAPI returned an empty response");
//            future.complete(null);
//            return;
//          }
//
//          try {
//            future.complete(GsonUtil.DEFAULT_GSON.fromJson(c.get(), token));
//          } catch (JsonSyntaxException exp) {
//            future.completeExceptionally(exp);
//          }
//        });
//
//    return future;
//  }
}