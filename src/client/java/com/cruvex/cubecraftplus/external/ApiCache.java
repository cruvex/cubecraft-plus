package com.cruvex.cubecraftplus.external;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.model.AutoVoteConfiguration;
import com.cruvex.cubecraftplus.model.Game;
import com.cruvex.cubecraftplus.util.Debug;
import com.cruvex.cubecraftplus.util.ModPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Last known good copies of the fetched data, so a launch with GitHub or the API unreachable
 * still has vote definitions and game ids at tick 0. One file per dataset; reads hit disk.
 */
public class ApiCache {

    private static final Logger LOGGER = CubeCraftPlusClient.LOGGER;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String AUTO_VOTE = "auto_vote";
    private static final String GAMES = "games";

    private static final TypeToken<List<AutoVoteConfiguration>> AUTO_VOTE_TOKEN = new TypeToken<>() {};
    private static final TypeToken<List<Game>> GAMES_TOKEN = new TypeToken<>() {};

    private static ApiCache instance;

    private final String modVersion = FabricLoader.getInstance()
            .getModContainer(CubeCraftPlusClient.MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");

    public static ApiCache getInstance() {
        if (instance == null) {
            instance = new ApiCache();
        }
        return instance;
    }

    /** Version checked: the copy bundled in a new release can be newer than the cache. */
    public List<AutoVoteConfiguration> getAutoVoteConfigurations() {
        return read(AUTO_VOTE, AUTO_VOTE_TOKEN, true);
    }

    /** Not version checked: there is no bundled copy to be newer than the cache. */
    public List<Game> getGames() {
        return read(GAMES, GAMES_TOKEN, false);
    }

    public void putAutoVoteConfigurations(List<AutoVoteConfiguration> configurations) {
        write(AUTO_VOTE, configurations);
    }

    public void putGames(List<Game> games) {
        write(GAMES, games);
    }

    private <T> List<T> read(String name, TypeToken<List<T>> token, boolean requireCurrentVersion) {
        Path path = ModPaths.cache(name);
        if (!Files.exists(path)) {
            return List.of();
        }

        Envelope envelope;
        try (Reader reader = Files.newBufferedReader(path)) {
            envelope = GSON.fromJson(reader, Envelope.class);
        } catch (IOException | JsonParseException e) {
            LOGGER.warn("Failed to read cache {}, ignoring it", path, e);
            return List.of();
        }

        if (envelope == null || envelope.data == null) {
            return List.of();
        }

        if (requireCurrentVersion && !modVersion.equals(envelope.modVersion)) {
            LOGGER.info("Cache {} was written by mod version {}, ignoring it so the copy bundled "
                    + "with {} wins until a fetch succeeds", name, envelope.modVersion, modVersion);
            return List.of();
        }

        try {
            List<T> value = GSON.fromJson(envelope.data, token);
            if (value == null) {
                return List.of();
            }
            Debug.log("Loaded {} entries from cache {}", value.size(), path);
            return value;
        } catch (JsonParseException e) {
            LOGGER.warn("Cache {} does not match the expected shape, ignoring it", path, e);
            return List.of();
        }
    }

    private <T> void write(String name, List<T> value) {
        // Cached garbage would be stickier than no cache at all
        if (value == null || value.isEmpty()) {
            return;
        }

        Envelope envelope = new Envelope();
        envelope.modVersion = modVersion;
        envelope.data = GSON.toJsonTree(value);

        Path path = ModPaths.cache(name);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(envelope, writer);
            }
            Debug.log("Saved {} entries to cache {}", value.size(), path);
        } catch (IOException e) {
            LOGGER.warn("Failed to save cache to {}", path, e);
        }
    }

    /** Payload stays a tree so one reader works for every dataset. */
    private static class Envelope {
        String modVersion;
        JsonElement data;
    }
}
