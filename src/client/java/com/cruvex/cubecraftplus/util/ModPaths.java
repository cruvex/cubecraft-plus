package com.cruvex.cubecraftplus.util;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/** Layout of everything the mod writes. Anything under cache/ is safe to delete. */
public final class ModPaths {

    private static final Path ROOT = FabricLoader.getInstance().getGameDir()
            .resolve(CubeCraftPlusClient.MOD_ID);

    /** Where the config lived before everything moved under one folder. */
    private static final Path LEGACY_CONFIG = FabricLoader.getInstance().getConfigDir()
            .resolve(CubeCraftPlusClient.MOD_ID + ".json");

    public static Path config() {
        return ROOT.resolve("config.json");
    }

    public static Path legacyConfig() {
        return LEGACY_CONFIG;
    }

    public static Path cache(String name) {
        return ROOT.resolve("cache").resolve(name + ".json");
    }

    private ModPaths() {
    }
}
