package com.cruvex.cubecraftplus.game;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.cubepanion.ApiCache;
import com.cruvex.cubecraftplus.cubepanion.CubepanionAPI;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Every game the API knows, by id and by any way of writing its name. */
public class GameRegistry {

    private static final Logger LOGGER = CubeCraftPlusClient.LOGGER;

    private static final String CACHE = "games";
    private static final String BUNDLED = "assets/cubecraft-plus/games.json";
    private static final TypeToken<List<Game>> GAMES = new TypeToken<>() {};

    private static GameRegistry instance;

    // Replaced on reload, never mutated: the client thread reads these while an HTTP thread writes
    private volatile Map<String, Game> gamesByName = Map.of();
    private volatile Map<Integer, Game> gamesById = Map.of();

    public static GameRegistry getInstance() {
        if (instance == null) {
            instance = new GameRegistry();
        }
        return instance;
    }

    public @Nullable Game find(String name) {
        return gamesByName.get(normalize(name));
    }

    public @Nullable Game byId(int id) {
        return gamesById.get(id);
    }

    public Collection<Game> all() {
        return gamesById.values();
    }

    /** Fills in from the last good fetch, falling back to the copy bundled in the jar. */
    public void seed() {
        List<Game> cached = ApiCache.getInstance().read(CACHE, GAMES, false);
        List<Game> games = cached.isEmpty() ? ApiCache.getInstance().readBundled(BUNDLED, GAMES) : cached;
        if (!games.isEmpty()) {
            index(games);
            LOGGER.info("Seeded {} games from {}", games.size(), cached.isEmpty() ? "the bundled copy" : "cache");
        }
    }

    public void load() {
        CubepanionAPI.getInstance().fetchGames()
                .thenAcceptAsync(games -> {
                    if (games == null) {
                        return;
                    }

                    index(games);
                    ApiCache.getInstance().write(CACHE, games);
                    LOGGER.info("Loaded {} games", games.size());
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to load games, some features may not work correctly", ex);
                    return null;
                });
    }

    private void index(List<Game> games) {
        Map<String, Game> byName = new HashMap<>();
        Map<Integer, Game> byId = new HashMap<>();
        for (Game game : games) {
            byId.put(game.id(), game);
            put(byName, game.name(), game);
            put(byName, game.displayName(), game);
            game.aliases().forEach(alias -> put(byName, alias, game));
        }
        this.gamesByName = byName;
        this.gamesById = byId;
    }

    private static void put(Map<String, Game> byName, @Nullable String key, Game game) {
        if (key != null && !key.isBlank()) {
            byName.put(normalize(key), game);
        }
    }

    /** Lowercases and turns spaces into underscores; both sides of the lookup go through it. */
    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
