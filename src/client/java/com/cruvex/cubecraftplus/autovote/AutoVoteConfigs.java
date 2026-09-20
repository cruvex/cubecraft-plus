package com.cruvex.cubecraftplus.autovote;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.cubepanion.ApiCache;
import com.cruvex.cubecraftplus.cubepanion.CubepanionAPI;
import com.cruvex.cubecraftplus.game.Game;
import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/** Each game's vote definitions, from Cubepanion's GitHub; malformed entries are dropped on the way in. */
public class AutoVoteConfigs {

    private static final Logger LOGGER = CubeCraftPlusClient.LOGGER;

    private static final String CACHE = "auto_vote";
    private static final String BUNDLED = "assets/cubecraft-plus/auto_vote.json";
    private static final TypeToken<List<AutoVoteConfiguration>> CONFIGS = new TypeToken<>() {};

    private static AutoVoteConfigs instance;

    // Replaced on reload, never mutated: the client thread reads this while an HTTP thread writes
    private volatile List<AutoVoteConfiguration> configurations = List.of();

    public static AutoVoteConfigs getInstance() {
        if (instance == null) {
            instance = new AutoVoteConfigs();
        }
        return instance;
    }

    public List<AutoVoteConfiguration> all() {
        return configurations;
    }

    public @Nullable AutoVoteConfiguration find(@Nullable Game game) {
        if (game == null) {
            return null;
        }

        for (AutoVoteConfiguration configuration : configurations) {
            if (configuration.gameId() == game.id()) {
                return configuration;
            }
        }
        return null;
    }

    /** Fills in from the version-checked cache, falling back to the copy bundled in the jar. */
    public void seed() {
        List<AutoVoteConfiguration> cached = sanitize(ApiCache.getInstance().read(CACHE, CONFIGS, true));
        configurations = cached.isEmpty() ? sanitize(ApiCache.getInstance().readBundled(BUNDLED, CONFIGS)) : cached;
        LOGGER.info("Seeded {} autovote configurations from {}",
                configurations.size(), cached.isEmpty() ? "the bundled copy" : "cache");
    }

    public void load() {
        CubepanionAPI.getInstance().fetchAutoVoteConfigs()
                .thenAccept(fetched -> {
                    List<AutoVoteConfiguration> sanitized = sanitize(fetched);
                    if (sanitized.isEmpty()) {
                        LOGGER.warn("Autovote config came back empty, keeping the {} configurations already loaded",
                                configurations.size());
                        return;
                    }

                    configurations = sanitized;
                    ApiCache.getInstance().write(CACHE, sanitized);
                    LOGGER.info("Loaded {} autovote configurations", sanitized.size());
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to load the autovote config, keeping the {} configurations already loaded",
                            configurations.size(), ex);
                    return null;
                });
    }

    /** Drops entries with a missing field or an out-of-range slot. */
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
}
