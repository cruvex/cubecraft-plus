package com.cruvex.cubecraftplus.external;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.model.BatchRequest;
import com.cruvex.cubecraftplus.model.Game;
import com.cruvex.cubecraftplus.model.Leaderboard;
import com.cruvex.cubecraftplus.model.LeaderboardRow;
import com.cruvex.cubecraftplus.model.PlayerLeaderboard;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class CubepanionAPI {
    private static final Logger LOGGER = CubeCraftPlusClient.LOGGER;

    private final TypeToken<List<Game>> gamesToken = new TypeToken<>() {};
    private final TypeToken<List<LeaderboardRow>> lbRowsToken = new TypeToken<>() {};
//  private final TypeToken<List<ChestLocation>> chestLocationToken = new TypeToken<>() {};
//  private final TypeToken<List<GameMap>> gameMapsToken = new TypeToken<>() {};

    private final String baseUrl = System.getenv("DEV") == null ?
      "https://cubepanion.ameliah.art/api" : "http://192.168.0.193:5050/api";
    private final String baseUrlv2 = System.getenv("DEV") == null ?
      "https://cubepanion.ameliah.art/api/v2" : "http://192.168.0.193:5050/api/v2";

    private final Map<String, Game> games = new HashMap<>();
    private final Map<Integer, Game> gameById = new HashMap<>();
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

  public void loadInitialData() {
    LOGGER.info("Loading initial data from {} & {}", this.baseUrl, this.baseUrlv2);
    this.getGames()
        .exceptionallyAsync(ex -> {
            LOGGER.error("Failed to load games, some features may not work correctly {}", ex);
          return null;
        })
        .thenAcceptAsync(games -> {
          if (games == null) {
            return;
          }

          for (var game : games) {
            this.gameById.put(game.id(), game);

            this.games.put(game.name(), game);
            this.games.put(game.displayName(), game);
            game.aliases().forEach(a -> this.games.put(a, game));
          }

            LOGGER.info("Loaded {} games", games.size());
        })
        .exceptionallyAsync(ex -> {
            LOGGER.error("Failed to load games, some features may not work correctly {}", ex);
          return null;
        });

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
  }

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

//  public CompletableFuture<Void> submit(Game game, List<LeaderboardRow> entries) {
//    var submission = new Submission(SessionTracker.get().uuid().toString(), game.id(), entries);
//
//    var player = Laby.labyAPI().minecraft().getClientPlayer();
//    if (player == null) {
//      return CompletableFuture.completedFuture(null);
//    }
//
//    CompletableFuture<Void> future = new CompletableFuture<>();
//
//    Request.ofString()
//        .url(this.baseUrlv2+"/Leaderboard")
//        .method(Method.POST)
//        .json(submission)
//        .async()
//        .execute(c -> {
//          if (c.hasException()) {
//            future.completeExceptionally(c.exception());
//            return;
//          }
//
//          if (c.getStatusCode() != 202) {
//            future.completeExceptionally(new Exception("Server returned status code " + c.getStatusCode()));
//          }
//
//          future.complete(null);
//        });
//
//    return future;
//  }

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
                      LOGGER.debug("Failed batch request to {}, {}", request.uri(), response.statusCode());
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
                        LOGGER.debug("Failed to make get request to {}, {}", url, response.statusCode());
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("CubepanionAPI returned a non 200 status code: " + response.statusCode())
                        );
                    }

                    if (response.body() == null || response.body().isEmpty()) {
                        LOGGER.debug("CubepanionAPI returned an empty response");
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
                        LOGGER.debug("Failed to make get request to {}, {}", url, response.statusCode());
                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException("CubepanionAPI returned a non 200 status code: " + response.statusCode())
                        );
                    }

                    if (response.body() == null || response.body().isEmpty()) {
                        LOGGER.debug("CubepanionAPI returned an empty response");
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