package com.cruvex.cubecraftplus.game;

import com.cruvex.cubecraftplus.debug.Debug;
import com.cruvex.cubecraftplus.events.HudEvents;
import com.cruvex.cubecraftplus.events.ScoreboardEvents;
import com.cruvex.cubecraftplus.util.Util;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tracks whether the client is on CubeCraft, and which of its servers it is on. */
public class CubeCraftManager {

    private static CubeCraftManager instance;

    /** Sidebar line CubeCraft puts the server id in, e.g. "05/08/26 (EU12B)". */
    private static final Pattern SERVER_ID_PATTERN = Pattern.compile("[0-9]{2}/[0-9]{2}/[0-9]{2} \\((.{5})\\)");

    /** The title CubeCraft shows while the player is AFK, under "Move to return to the game.". */
    private static final String AFK_TITLE = "You're AFK";
    /** Three of the one-second repeats: only the title going quiet says the player moved. */
    private static final long AFK_TIMEOUT_MS = 3000;

    private String serverId = "";
    private String lastServerId = "";
    private long afkTitleAt;
    private boolean afk;

    private boolean announcedCubeJoin;

    public static CubeCraftManager getInstance() {
        if (instance == null) {
            instance = new CubeCraftManager();
        }
        return instance;
    }

    public void init() {
        ScoreboardEvents.TEAM_CHANGE.register(this::onTeamChange);
        HudEvents.TITLE.register(this::onTitle);
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
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

    private void onTitle(Component title) {
        // Any other title means the AFK screen is gone, which is the only end CubeCraft announces
        afkTitleAt = title.getString().equals(AFK_TITLE) ? System.currentTimeMillis() : 0;
    }

    /** Coming back is the title going quiet, so the state can only expire on a tick. */
    private void onEndTick(Minecraft client) {
        boolean stillAfk = System.currentTimeMillis() - afkTitleAt < AFK_TIMEOUT_MS;
        if (stillAfk == afk) return;

        afk = stillAfk;
        Debug.log(afk ? "Went AFK" : "Back from AFK");
    }

    public boolean isOnCubeCraft() {
        return Util.isOnCubeCraft(Minecraft.getInstance());
    }

    /** Whether CubeCraft is showing its AFK title; stays true for {@value #AFK_TIMEOUT_MS}ms after the last one. */
    public boolean isAfk() {
        return afk;
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
        afkTitleAt = 0;
        afk = false;
        announcedCubeJoin = false;
    }
}
