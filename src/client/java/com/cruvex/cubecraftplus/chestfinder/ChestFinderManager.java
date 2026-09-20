package com.cruvex.cubecraftplus.chestfinder;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.chat.Chat;
import com.cruvex.cubecraftplus.config.ConfigManager;
import com.cruvex.cubecraftplus.config.ModConfig;
import com.cruvex.cubecraftplus.cubepanion.CubepanionAPI;
import com.cruvex.cubecraftplus.debug.Debug;
import com.cruvex.cubecraftplus.game.CubeCraftManager;
import com.cruvex.cubecraftplus.game.CubeEvents;
import com.cruvex.cubecraftplus.game.Game;
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
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

public class ChestFinderManager {
    private static final Logger LOGGER = CubeCraftPlusClient.LOGGER;

    final String chestMessage = "A chest has been hidden somewhere in the lobby with some goodies inside!";

    private static final int POLL_INTERVAL_TICKS = 20;
    private static final int SEARCH_TICKS = 100; // 5 seconds

    private static final int HIGHLIGHT_STROKE = 0xFF55FF55;
    private static final int HIGHLIGHT_FILL = 0x4055FF55;

    private int searchTicksLeft = 0;
    private boolean reportNotFound;
    private BlockPos foundChest;

    // Replaced on reload, never mutated: the client thread reads this while an HTTP thread writes
    private volatile List<ChestLocation> locations = List.of();

    static ChestFinderManager instance;

    public static ChestFinderManager getInstance() {
        if (instance == null) {
            instance = new ChestFinderManager();
        }
        return instance;
    }

    public void init() {
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onMessage(message));
        ClientTickEvents.END_CLIENT_TICK.register(client -> onEndTick());
        // Searched on landing in a lobby too, the announcement not always being sent
        CubeEvents.GAME_JOIN.register(this::onGameJoin);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            searchTicksLeft = 0;
            foundChest = null;
        });
    }

    public void loadLocations() {
        CubepanionAPI.getInstance().fetchChestLocations()
                .thenAccept(fetched -> {
                    if (fetched == null || fetched.isEmpty()) {
                        LOGGER.warn("Chest locations came back empty, keeping the {} locations already loaded",
                                locations.size());
                        return;
                    }

                    locations = List.copyOf(fetched);
                    // Any entry's season will do: the endpoint only serves the active one
                    String season = fetched.getFirst().seasonName();
                    Debug.info("Loaded {} chest locations for season {}", fetched.size(), season);
                })
                .exceptionally(ex -> {
                    LOGGER.error("Failed to load chest locations, keeping the {} locations already loaded",
                            locations.size(), ex);
                    return null;
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

        // Silent on a miss: most lobbies have no chest
        startSearch(false);
    }

    public void startSearch() {
        startSearch(true);
    }

    /** Searches for {@value #SEARCH_TICKS} ticks, since the chest's chunk may not be loaded yet. */
    private void startSearch(boolean reportNotFound) {
        if (!CubeCraftManager.getInstance().isOnCubeCraft()) return;

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
        // Dropped once the chest is claimed, but kept while its chunk is unloaded
        if (level == null || (level.isLoaded(foundChest) && level.getBlockState(foundChest).getBlock() != Blocks.CHEST)) {
            foundChest = null;
            return;
        }

        // Re-emitted every tick, a gizmo only living for the tick it was made on
        Gizmos.cuboid(foundChest, 0.02F, GizmoStyle.strokeAndFill(HIGHLIGHT_STROKE, 2.5F, HIGHLIGHT_FILL))
                .setAlwaysOnTop();

        // A 4-pixel beam, the width being in screen pixels rather than blocks
        Vec3 beamStart = Vec3.atBottomCenterOf(foundChest.above());
        Gizmos.line(beamStart, beamStart.add(0, 255, 0), HIGHLIGHT_STROKE, 4F)
                .setAlwaysOnTop();
    }

    private Optional<ChestLocation> findLobbyChest() {
        List<ChestLocation> locations = this.locations;
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

            // Out of range or not loaded yet, rather than absent
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
