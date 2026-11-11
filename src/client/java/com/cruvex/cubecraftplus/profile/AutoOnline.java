package com.cruvex.cubecraftplus.profile;

import com.cruvex.cubecraftplus.debug.Debug;
import com.cruvex.cubecraftplus.game.CubeCraftManager;
import com.cruvex.cubecraftplus.game.GameManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.UUID;

/** Sends {@code /online} when the tab list holds players without an observation. Client thread only. */
public class AutoOnline {

    private static final long CHECK_INTERVAL_MS = 2000;
    /** How long the tab list must hold still, so a server switch has finished arriving. */
    private static final long SETTLE_MS = 2000;
    private static final long COOLDOWN_MS = 60_000;
    /** Commands do not end CubeCraft's AFK state, so this only slows down while away. */
    private static final long AFK_COOLDOWN_MS = 300_000;
    private static final int MIN_UNOBSERVED = 5;

    private static AutoOnline instance;

    private long nextCheckAt;
    private int lastCount = -1;
    private long settledAt;
    private long lastSentAt;
    private boolean joined;
    private boolean asking;

    public static AutoOnline getInstance() {
        if (instance == null) {
            instance = new AutoOnline();
        }
        return instance;
    }

    public void init() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            joined = true;
            lastCount = -1;
        });
    }

    private void tick(Minecraft client) {
        long now = System.currentTimeMillis();
        if (now < nextCheckAt) return;
        nextCheckAt = now + CHECK_INTERVAL_MS;

        ClientPacketListener connection = client.getConnection();
        if (asking || connection == null || client.player == null) return;
        if (!CubeCraftManager.getInstance().isOnCubeCraft()) return;

        int count = connection.getListedOnlinePlayers().size();
        if (count != lastCount) {
            lastCount = count;
            settledAt = now + SETTLE_MS;
            return;
        }
        if (now < settledAt) return;

        int unobserved = unobserved(connection, client.player.getUUID());
        if (joined) {
            joined = false;
            if (unobserved > 0) ask(unobserved, now);
            return;
        }

        long cooldown = CubeCraftManager.getInstance().isAfk() ? AFK_COOLDOWN_MS : COOLDOWN_MS;
        if (!GameManager.getInstance().isInGame() && unobserved >= MIN_UNOBSERVED && now - lastSentAt >= cooldown) {
            ask(unobserved, now);
        }
    }

    private static int unobserved(ClientPacketListener connection, UUID self) {
        int count = 0;
        for (PlayerInfo info : connection.getListedOnlinePlayers()) {
            UUID id = info.getProfile().id();
            if (!id.equals(self) && PlayerProfiles.getInstance().get(id) == null) count++;
        }
        return count;
    }

    // The reply itself is observed by ProfileParser.onServerMessage
    private void ask(int unobserved, long now) {
        Debug.log("AutoOnline: {} players not observed yet, sending /online", unobserved);
        asking = true;
        lastSentAt = now;
        ProfileParser.fetchOnline().whenComplete((reply, error) -> {
            asking = false;
            if (error != null) {
                Debug.log("AutoOnline: /online failed: {}", error.toString());
            }
        });
    }
}
