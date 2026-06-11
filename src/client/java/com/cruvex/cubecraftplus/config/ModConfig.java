package com.cruvex.cubecraftplus.config;

/**
 * Root config model, serialized to JSON by {@link com.cruvex.cubecraftplus.managers.ConfigManager}.
 * Field initializers are the defaults; Gson only overwrites fields present in the file.
 *
 * Vote option slots differ per menu layout: two-option menus use 12/14, three-option
 * menus 11/13/15, four-option menus 10/12/14/16 and five-option menus 11-15.
 * {@code NONE} skips the category.
 */
public class ModConfig {

    public AutoVoteConfig autoVote = new AutoVoteConfig();

    public static class AutoVoteConfig {
        public boolean enabled = true;

        public EggWars eggWars = new EggWars();
        public SkyWars skyWars = new SkyWars();
        public LuckyIslands luckyIslands = new LuckyIslands();
        public PillarsOfFortune pillarsOfFortune = new PillarsOfFortune();
        public BedWars bedWars = new BedWars();
        public Ender ender = new Ender();
    }

    public static class EggWars {
        public ThreeOptionsMode items = ThreeOptionsMode.RIGHT;
        public TwoOptionsMode health = TwoOptionsMode.LEFT;
        public TwoOptionsMode perk = TwoOptionsMode.LEFT;
    }

    public static class SkyWars {
        public ThreeOptionsMode chests = ThreeOptionsMode.LEFT;        // Normal
        public ThreeOptionsMode projectiles = ThreeOptionsMode.MIDDLE; // Normal
        public ThreeOptionsMode time = ThreeOptionsMode.LEFT;          // Day
    }

    public static class LuckyIslands {
        public FourOptionsMode blocks = FourOptionsMode.RIGHT;
        public ThreeOptionsMode time = ThreeOptionsMode.LEFT;
    }

    public static class PillarsOfFortune {
        public FourOptionsMode gameMode = FourOptionsMode.LEFT;
        public FiveOptionsMode mapMode = FiveOptionsMode.LEFT;
    }

    public static class BedWars {
        public TwoOptionsMode modifier = TwoOptionsMode.RIGHT;
    }

    public static class Ender {
        public TwoOptionsMode mode = TwoOptionsMode.NONE;
    }

    public enum TwoOptionsMode {
        LEFT(12),
        RIGHT(14),
        NONE(-1);

        public final int slot;

        TwoOptionsMode(int slot) {
            this.slot = slot;
        }
    }

    public enum ThreeOptionsMode {
        LEFT(11),
        MIDDLE(13),
        RIGHT(15),
        NONE(-1);

        public final int slot;

        ThreeOptionsMode(int slot) {
            this.slot = slot;
        }
    }

    public enum FourOptionsMode {
        LEFT(10),
        MIDDLE_LEFT(12),
        MIDDLE_RIGHT(14),
        RIGHT(16),
        NONE(-1);

        public final int slot;

        FourOptionsMode(int slot) {
            this.slot = slot;
        }
    }

    public enum FiveOptionsMode {
        LEFT(11),
        MIDDLE_LEFT(12),
        MIDDLE(13),
        MIDDLE_RIGHT(14),
        RIGHT(15),
        NONE(-1);

        public final int slot;

        FiveOptionsMode(int slot) {
            this.slot = slot;
        }
    }

    /**
     * Repairs fields Gson left null or out of range (missing keys, unknown enum values),
     * so the rest of the mod never has to null-check config values.
     */
    public void validate() {
        if (autoVote == null) autoVote = new AutoVoteConfig();

        if (autoVote.eggWars == null) autoVote.eggWars = new EggWars();
        EggWars eggWarsDefaults = new EggWars();
        autoVote.eggWars.items = or(autoVote.eggWars.items, eggWarsDefaults.items);
        autoVote.eggWars.health = or(autoVote.eggWars.health, eggWarsDefaults.health);
        autoVote.eggWars.perk = or(autoVote.eggWars.perk, eggWarsDefaults.perk);

        if (autoVote.skyWars == null) autoVote.skyWars = new SkyWars();
        SkyWars skyWarsDefaults = new SkyWars();
        autoVote.skyWars.chests = or(autoVote.skyWars.chests, skyWarsDefaults.chests);
        autoVote.skyWars.projectiles = or(autoVote.skyWars.projectiles, skyWarsDefaults.projectiles);
        autoVote.skyWars.time = or(autoVote.skyWars.time, skyWarsDefaults.time);

        if (autoVote.luckyIslands == null) autoVote.luckyIslands = new LuckyIslands();
        LuckyIslands luckyIslandsDefaults = new LuckyIslands();
        autoVote.luckyIslands.blocks = or(autoVote.luckyIslands.blocks, luckyIslandsDefaults.blocks);
        autoVote.luckyIslands.time = or(autoVote.luckyIslands.time, luckyIslandsDefaults.time);

        if (autoVote.pillarsOfFortune == null) autoVote.pillarsOfFortune = new PillarsOfFortune();
        PillarsOfFortune pofDefaults = new PillarsOfFortune();
        autoVote.pillarsOfFortune.gameMode = or(autoVote.pillarsOfFortune.gameMode, pofDefaults.gameMode);
        autoVote.pillarsOfFortune.mapMode = or(autoVote.pillarsOfFortune.mapMode, pofDefaults.mapMode);

        if (autoVote.bedWars == null) autoVote.bedWars = new BedWars();
        autoVote.bedWars.modifier = or(autoVote.bedWars.modifier, new BedWars().modifier);

        if (autoVote.ender == null) autoVote.ender = new Ender();
        autoVote.ender.mode = or(autoVote.ender.mode, new Ender().mode);
    }

    private static <T> T or(T value, T fallback) {
        return value != null ? value : fallback;
    }
}
