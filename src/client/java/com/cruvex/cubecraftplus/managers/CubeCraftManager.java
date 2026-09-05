package com.cruvex.cubecraftplus.managers;

import com.cruvex.cubecraftplus.events.CubeEvents;
import com.cruvex.cubecraftplus.events.ScoreboardEvents;
import com.cruvex.cubecraftplus.util.Debug;
import com.cruvex.cubecraftplus.util.Util;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.PlayerTeam;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tracks whether the client is on CubeCraft, and which of its servers it is on. */
public class CubeCraftManager {

    private static CubeCraftManager instance;

    /** Sidebar line CubeCraft puts the server id in, e.g. "05/08/26 (EU12B)". */
    private static final Pattern SERVER_ID_PATTERN = Pattern.compile("[0-9]{2}/[0-9]{2}/[0-9]{2} \\((.{5})\\)");

    private String serverId = "";
    private String lastServerId = "";

    private boolean announcedCubeJoin;

    public static CubeCraftManager getInstance() {
        if (instance == null) {
            instance = new CubeCraftManager();
        }
        return instance;
    }

    public void init() {
        ScoreboardEvents.TEAM_CHANGE.register(this::onTeamChange);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onServerJoin(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset());
    }

    private void onServerJoin(Minecraft client) {
        String ip = Util.getServerIp(client);
        if (!Util.isKubusMaken(ip)) {
            Debug.log("Joined {}, not CubeCraft", ip == null ? "singleplayer" : ip);
            reset();
            return;
        }

        // Every server switch arrives as a fresh login, so only the first one is a CubeCraft join
        if (announcedCubeJoin) {
            Debug.log("Switched CubeCraft server");
            return;
        }

        Debug.log("Joined CubeCraft ({})", ip);
        announcedCubeJoin = true;
        CubeEvents.CUBE_JOIN.invoker().onCubeJoin();
    }

    private void onTeamChange(PlayerTeam team) {
        Matcher matcher = SERVER_ID_PATTERN.matcher(team.getPlayerPrefix().getString());
        if (matcher.matches()) {
            setServerId(matcher.group(1));
        }
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
        serverId = "";
        lastServerId = "";
        announcedCubeJoin = false;
    }
}
