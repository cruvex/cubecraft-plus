package com.cruvex.cubecraftplus.managers;

import com.cruvex.cubecraftplus.events.CubeEvents;
import com.cruvex.cubecraftplus.events.ScoreboardEvents;
import com.cruvex.cubecraftplus.model.CubeGame;
import com.cruvex.cubecraftplus.util.Debug;
import com.cruvex.cubecraftplus.util.Util;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import org.jetbrains.annotations.NotNull;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tracks which CubeCraft game the player is in, read off the sidebar. */
public class CubeCraftManager {

    private static CubeCraftManager instance;

    /** Sidebar line CubeCraft puts the server id in, e.g. "05/08/26 (EU12B)". */
    private static final Pattern SERVER_ID_PATTERN = Pattern.compile("[0-9]{2}/[0-9]{2}/[0-9]{2} \\((.{5})\\)");

    private CubeGame currentGame;
    private String serverId = "";
    private String lastServerId = "";

    public static CubeCraftManager getInstance() {
        if (instance == null) {
            instance = new CubeCraftManager();
        }
        return instance;
    }

    public void init() {
        registerListeners();
    }

    private void registerListeners() {
        ScoreboardEvents.ADD_OBJECTIVE.register(this::onAddObjective);
        ScoreboardEvents.TEAM_CHANGE.register(this::onTeamChange);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private void onTeamChange(PlayerTeam team) {
        Matcher matcher = SERVER_ID_PATTERN.matcher(team.getPlayerPrefix().getString());
        if (!matcher.matches()) return;

        setServerId(matcher.group(1));
    }

    private void onAddObjective(@NotNull Objective objective) {
        var gameOptional = CubeGame.fromObjectiveTitle(objective.getDisplayName());

        if (gameOptional.isEmpty()) {
            Debug.log("onAddObjective: No game found for objective: {}", objective.getDisplayName() == null ? "(none)" : objective.getDisplayName().getString());
            return;
        }

        CubeGame game = gameOptional.get();
        Debug.log("onAddObjective: Game found: {}", game.name());
        this.setCurrentGame(game);
    }

    public CubeGame getCurrentGame() {
        return currentGame;
    }

    public void setCurrentGame(CubeGame currentGame) {
        if (this.currentGame == currentGame) return;
        this.currentGame = currentGame;
        CubeEvents.GAME_JOIN.invoker().onGameJoin(currentGame);
    }

    public boolean isOnCubeCraft() {
        return Util.isOnCubeCraft(Minecraft.getInstance());
    }

    public String getServerId() {
        return serverId;
    }

    public String getLastServerId() {
        return lastServerId;
    }

    private void setServerId(String serverId) {
        if (this.serverId.equals(serverId)) return;

        Debug.log("Server id changed from {} to {}", this.serverId, serverId);
        this.lastServerId = this.serverId;
        this.serverId = serverId;
    }

    private void reset() {
        // Not via setCurrentGame: leaving isn't a game join
        currentGame = null;
        serverId = "";
        lastServerId = "";
    }
}
