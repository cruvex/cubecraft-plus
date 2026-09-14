package com.cruvex.cubecraftplus.managers;

import com.cruvex.cubecraftplus.external.CubepanionAPI;
import com.cruvex.cubecraftplus.model.ChestLocation;
import com.cruvex.cubecraftplus.util.Chat;
import com.cruvex.cubecraftplus.util.Debug;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Optional;

public class ChestFinderManager {
    final String chestMessage = "A chest has been hidden somewhere in the lobby with some goodies inside!";

    private static final int POLL_INTERVAL_TICKS = 20;
    private static final int SEARCH_TICKS = 100; // Search for 5 seconds after chest message

    private int searchTicksLeft = 0;

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
        // Reset search ticks on server switch
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> searchTicksLeft = 0);
    }

    private void onMessage(Component message) {
        if (!ConfigManager.getInstance().getConfig().chestFinder.enabled) return;
        if (!message.getString().equals(chestMessage))
            return;

        // The chest might not be loaded in when the message arrives, so we search for a set period
        searchTicksLeft = SEARCH_TICKS;
    }

    private void onEndTick() {
        if (searchTicksLeft <= 0) {
            return;
        }

        searchTicksLeft--;
        if (searchTicksLeft % POLL_INTERVAL_TICKS != 0) {
            return;
        }

        Optional<ChestLocation> possibleChest = findLobbyChest();

        if (possibleChest.isEmpty()) {
            if (searchTicksLeft == 0) {
                Component notFound = Component.translatable("cubecraftplus.chestfinder.notfound").withStyle(ChatFormatting.RED);
                Chat.send(notFound);
            }

            return;
        }

        searchTicksLeft = 0;
        ChestLocation location = possibleChest.get();

        Chat.send(Component.translatable("cubecraftplus.chestfinder.found",
                        Component.literal(location.x() + ", " + location.y() + ", " + location.z())
                                .withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GREEN));
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
