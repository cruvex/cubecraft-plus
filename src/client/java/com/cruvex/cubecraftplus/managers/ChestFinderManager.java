package com.cruvex.cubecraftplus.managers;

import com.cruvex.cubecraftplus.config.ModConfig;
import com.cruvex.cubecraftplus.events.CubeEvents;
import com.cruvex.cubecraftplus.external.CubepanionAPI;
import com.cruvex.cubecraftplus.model.ChestLocation;
import com.cruvex.cubecraftplus.model.Game;
import com.cruvex.cubecraftplus.util.Chat;
import com.cruvex.cubecraftplus.util.Debug;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

public class ChestFinderManager {
    final String chestMessage = "A chest has been hidden somewhere in the lobby with some goodies inside!";

    private static final int POLL_INTERVAL_TICKS = 20;
    private static final int SEARCH_TICKS = 100; // Search for 5 seconds after chest message

    private static final int HIGHLIGHT_STROKE = 0xFF55FF55;
    private static final int HIGHLIGHT_FILL = 0x4055FF55;

    private int searchTicksLeft = 0;
    private boolean reportNotFound;
    private BlockPos foundChest;

    static ChestFinderManager instance;

    public static ChestFinderManager getInstance() {
        if (instance == null) {
            instance = new ChestFinderManager();
        }
        return instance;
    }

    public void init() {
        ClientReceiveMessageEvents.GAME.register((message, _) -> onMessage(message));
        ClientTickEvents.END_CLIENT_TICK.register(_ -> onEndTick());
        // The announcement isn't always sent, so also look whenever we land in a lobby
        CubeEvents.GAME_JOIN.register(this::onGameJoin);
        // Reset search and highlight on server switch
        ClientPlayConnectionEvents.JOIN.register((_, _, _) -> {
            searchTicksLeft = 0;
            foundChest = null;
        });
    }

    private void onMessage(Component message) {
        if (!ConfigManager.getInstance().getConfig().chestFinder.enabled) return;
        if (!message.getString().equals(chestMessage))
            return;

        startSearch();
    }

    private void onGameJoin(Game game) {
        if (!ConfigManager.getInstance().getConfig().chestFinder.enabled) return;
        if (!game.isLobby()) return;

        // Most lobbies have no chest, so a miss here isn't worth reporting
        startSearch(false);
    }

    public void startSearch() {
        startSearch(true);
    }

    private void startSearch(boolean reportNotFound) {
        // The chest might not be loaded in when the search starts, so we search for a set period
        searchTicksLeft = SEARCH_TICKS;
        this.reportNotFound = reportNotFound;
        foundChest = null;
    }

    private void onEndTick() {
        highlightFoundChest();

        if (searchTicksLeft <= 0) {
            return;
        }

        searchTicksLeft--;
        if (searchTicksLeft % POLL_INTERVAL_TICKS != 0) {
            return;
        }

        Optional<ChestLocation> possibleChest = findLobbyChest();

        if (possibleChest.isEmpty()) {
            if (searchTicksLeft == 0 && reportNotFound) {
                Component notFound = Component.translatable("cubecraftplus.chestfinder.notfound").withStyle(ChatFormatting.RED);
                Chat.send(notFound);
            }

            return;
        }

        searchTicksLeft = 0;
        ChestLocation location = possibleChest.get();
        foundChest = new BlockPos(location.x(), location.y(), location.z());

        Chat.send(Component.translatable("cubecraftplus.chestfinder.found",
                        Component.literal(location.x() + ", " + location.y() + ", " + location.z())
                                .withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GREEN));
    }

    private void highlightFoundChest() {
        ModConfig.ChestFinderConfig config = ConfigManager.getInstance().getConfig().chestFinder;
        if (foundChest == null || !config.enabled || !config.highlight) {
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        // Drop the highlight once the chest is claimed, but keep it while its chunk is unloaded
        if (level == null || (level.isLoaded(foundChest) && level.getBlockState(foundChest).getBlock() != Blocks.CHEST)) {
            foundChest = null;
            return;
        }

        // Tick gizmos only live for one tick, so re-emit every tick to keep the box drawn
        Gizmos.cuboid(foundChest, 0.02F, GizmoStyle.strokeAndFill(HIGHLIGHT_STROKE, 2.5F, HIGHLIGHT_FILL))
                .setAlwaysOnTop();

        // Line width is in screen pixels, so a tall beam stays visible from far away
        Vec3 beamStart = Vec3.atBottomCenterOf(foundChest.above());
        Gizmos.line(beamStart, beamStart.add(0, 255, 0), HIGHLIGHT_STROKE, 4F)
                .setAlwaysOnTop();
    }

    private Optional<ChestLocation> findLobbyChest() {
        List<ChestLocation> locations = CubepanionAPI.getInstance().getChestLocations();
        ClientLevel level = Minecraft.getInstance().level;

        if (level == null) {
            return Optional.empty();
        }

        if (locations.isEmpty()) {
            Debug.log("No chest locations loaded");
            return Optional.empty();
        }

        for (ChestLocation location : locations) {
            BlockPos pos = new BlockPos(location.x(), location.y(), location.z());

            // out of range or not loaded yet, not absent
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (level.getBlockState(pos).getBlock() == Blocks.CHEST) {
                return Optional.of(location);
            }
        }

        return Optional.empty();
    }
}
