package com.cruvex.cubecraftplus.keybinds;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.game.CubeCraftManager;
import com.cruvex.cubecraftplus.gui.screen.ConfigScreen;
import com.cruvex.cubecraftplus.gui.screen.FriendsScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/** The mod's section under Controls > Key Binds; keys start unbound so none clash. */
public class KeyBindManager {

    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(CubeCraftPlusClient.MOD_ID, "main"));

    private static KeyBindManager instance;

    // Registered on construction, so the manager has to be created during client init
    private final KeyMapping openConfig = register("key.cubecraftplus.config");
    private final KeyMapping openFriends = register("key.cubecraftplus.friends");

    public static KeyBindManager getInstance() {
        if (instance == null) {
            instance = new KeyBindManager();
        }
        return instance;
    }

    public void init() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
    }

    private void onEndTick(Minecraft client) {
        // Presses are drained off CubeCraft too, rather than queueing up until it is joined
        boolean onCubeCraft = CubeCraftManager.getInstance().isOnCubeCraft();
        while (openConfig.consumeClick()) {
            if (onCubeCraft) client.setScreen(new ConfigScreen(client.screen));
        }
        while (openFriends.consumeClick()) {
            if (onCubeCraft) client.setScreen(new FriendsScreen(client.screen));
        }
    }

    private static KeyMapping register(String name) {
        return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, InputConstants.UNKNOWN.getValue(), CATEGORY));
    }
}
