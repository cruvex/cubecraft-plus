package com.cruvex.cubecraftplus.cubepanion;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.debug.Debug;
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
import java.util.Optional;

/** Offline copies of fetched data: the last good fetch on disk, and the copy bundled in the jar. */
public class ApiCache {

    private static final Logger LOGGER = CubeCraftPlusClient.LOGGER;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

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

    public <T> List<T> read(String name, TypeToken<List<T>> token, boolean requireCurrentVersion) {
        Path path = ModPaths.cache(name);
        if (!Files.exists(path)) {
            return List.of();
        }

        Envelope envelope;
        try (Reader reader = Files.newBufferedReader(path)) {
            envelope = GSON.fromJson(reader, Envelope.class);
        } catch (IOException | JsonParseException e) {
            LOGGER.warn("Failed to read cache {}, ignoring it", ModPaths.display(path), e);
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
            Debug.log("Loaded {} entries from cache {}", value.size(), ModPaths.display(path));
            return value;
        } catch (JsonParseException e) {
            LOGGER.warn("Cache {} does not match the expected shape, ignoring it", ModPaths.display(path), e);
            return List.of();
        }
    }

    public <T> void write(String name, List<T> value) {
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
            Debug.log("Saved {} entries to cache {}", value.size(), ModPaths.display(path));
        } catch (IOException e) {
            LOGGER.warn("Failed to save cache to {}", ModPaths.display(path), e);
        }
    }

    public <T> List<T> readBundled(String resource, TypeToken<List<T>> type) {
        Optional<Path> path = FabricLoader.getInstance()
                .getModContainer(CubeCraftPlusClient.MOD_ID)
                .flatMap(container -> container.findPath(resource));
        if (path.isEmpty()) {
            LOGGER.warn("Bundled {} is missing from the mod jar", resource);
            return List.of();
        }

        try (Reader reader = Files.newBufferedReader(path.get())) {
            List<T> value = GSON.fromJson(reader, type);
            return value == null ? List.of() : value;
        } catch (IOException | JsonParseException e) {
            LOGGER.warn("Failed to read bundled {}", resource, e);
            return List.of();
        }
    }

    /** Payload stays a tree so one reader works for every dataset. */
    private static class Envelope {
        String modVersion;
        JsonElement data;
    }
}
