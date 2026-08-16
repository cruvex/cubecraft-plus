package com.cruvex.cubecraftplus.managers;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.config.ModConfig;
import com.cruvex.cubecraftplus.util.Debug;
import com.cruvex.cubecraftplus.util.ModPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and saves {@code config/cubecraft-plus.json}. A missing or corrupt file falls back
 * to defaults and is rewritten, so existing files pick up new fields.
 */
public class ConfigManager {

    private final Logger LOGGER = CubeCraftPlusClient.LOGGER;

    private static ConfigManager instance;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configPath = ModPaths.config();

    private ModConfig config = new ModConfig();

    public static ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    public void init() {
        migrateFromOldLocation();
        load();
    }

    /** Moves the config over from where it lived before everything moved under one folder. */
    private void migrateFromOldLocation() {
        Path legacy = ModPaths.legacyConfig();
        if (Files.exists(configPath) || !Files.exists(legacy)) {
            return;
        }

        try {
            Files.createDirectories(configPath.getParent());
            Files.move(legacy, configPath);
            LOGGER.info("Moved config from {} to {}", legacy, configPath);
        } catch (IOException e) {
            // Not fatal: load() falls back to the old path and save() writes the new one
            LOGGER.warn("Could not move config from {} to {}, reading it in place instead",
                    legacy, configPath, e);
        }
    }

    public void load() {
        Path source = Files.exists(configPath) ? configPath : ModPaths.legacyConfig();
        if (Files.exists(source)) {
            try (Reader reader = Files.newBufferedReader(source)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (IOException | JsonParseException e) {
                LOGGER.warn("Failed to read config from {}, using defaults", source, e);
            }
        }
        config.validate();
        // Write back so a fresh install gets a file and existing files pick up new fields
        save();
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(config, writer);
            }
            Debug.log("Saved config to {}", configPath);
        } catch (IOException e) {
            LOGGER.warn("Failed to save config to {}", configPath, e);
        }
    }

    public ModConfig getConfig() {
        return config;
    }

    public ModConfig copyConfig() {
        return GSON.fromJson(GSON.toJson(config), ModConfig.class);
    }

    public void update(ModConfig newConfig) {
        newConfig.validate();
        this.config = newConfig;
        save();
    }
}
