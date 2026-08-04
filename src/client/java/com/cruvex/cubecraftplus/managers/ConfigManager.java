package com.cruvex.cubecraftplus.managers;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.config.ModConfig;
import com.cruvex.cubecraftplus.util.Debug;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
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

    private final Path configPath = FabricLoader.getInstance().getConfigDir()
            .resolve(CubeCraftPlusClient.MOD_ID + ".json");

    private ModConfig config = new ModConfig();

    public static ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    public void init() {
        load();
    }

    public void load() {
        if (Files.exists(configPath)) {
            try (Reader reader = Files.newBufferedReader(configPath)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (IOException | JsonParseException e) {
                LOGGER.warn("Failed to read config from {}, using defaults", configPath, e);
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
