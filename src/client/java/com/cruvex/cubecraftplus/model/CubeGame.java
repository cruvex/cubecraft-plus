package com.cruvex.cubecraftplus.model;

import net.minecraft.network.chat.Component;

import java.util.Optional;

// ALSO ADD IN CubeGame#stringToGame !!!
public enum CubeGame {
    TEAM_EGGWARS("Team EggWars"),
    SOLO_LUCKYISLANDS("Solo Lucky Islands"),
    TEAM_LUCKY_ISLANDS("Team Lucky Islands"),
    SOLO_SKYWARS("Solo SkyWars"),
    FFA("Free For All"),
    SIMPLE_PARKOUR("Simple Parkour"),
    EASY_PARKOUR("Easy Parkour"),
    MEDIUM_PARKOUR("Medium Parkour"),
    HARD_PARKOUR("Hard Parkour"),
    PARKOUR("Parkour"),
    SKYBLOCK("Skyblock"),
    SNOWMAN_SURVIVAL("Snowman Survival"),
    LOBBY("Main Lobby"),
    PILLARS_OF_FORTUNE("Pillars of Fortune"),
    BEDWARS("BedWars"),
    ENDER("Ender"),
    DISASTERS("Disasters"),
    NONE("");

    private final String string;

    CubeGame(String s) {
        this.string = s;
    }

    public static CubeGame stringToGame(String s) {
        return switch (s.toLowerCase().replace("_", " ")) {
            case "skyblock" -> SKYBLOCK;
            case "team eggwars", "eggwars" -> TEAM_EGGWARS;
            case "solo skywars", "skywars" -> SOLO_SKYWARS;
            case "solo lucky islands" -> SOLO_LUCKYISLANDS;
            case "team lucky islands", "lucky islands" -> TEAM_LUCKY_ISLANDS;
            case "free for all", "ffa" -> FFA;
            case "simple parkour" -> SIMPLE_PARKOUR;
            case "easy parkour" -> EASY_PARKOUR;
            case "medium parkour" -> MEDIUM_PARKOUR;
            case "hard parkour" -> HARD_PARKOUR;
            case "parkour" -> PARKOUR;
            case "snowman survival" -> SNOWMAN_SURVIVAL;
            case "cubecraft" -> LOBBY;
            case "pillars of fortune" -> PILLARS_OF_FORTUNE;
            case "team bedwars", "bedwars" -> BEDWARS;
            case "ender" -> ENDER;
            case "disasters" -> DISASTERS;
            default -> NONE;
        };
    }

    public static Optional<CubeGame> fromObjectiveTitle(Component title) {
        String cleaned = title.getString().replaceAll("[^a-zA-Z \\.]", "").trim();

        if (!cleaned.isEmpty() && cleaned.matches("[a-zA-Z ]+")) {
            return Optional.of(stringToGame(cleaned));
        }
        return Optional.empty();
    }

    public String getString() {
        return string;
    }
}
