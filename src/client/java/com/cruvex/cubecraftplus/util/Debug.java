package com.cruvex.cubecraftplus.util;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.helpers.MessageFormatter;

/**
 * Debug mode, toggled in-game with /ccp debug. {@link #log} writes to the logger at debug
 * level and mirrors to chat while enabled. Takes slf4j-style {@code {}} placeholders.
 */
public class Debug {

    private static boolean enabled = false;

    private Debug() {
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static void log(String message, Object... args) {
        CubeCraftPlusClient.LOGGER.debug(message, args);
        if (!enabled) {
            return;
        }

        String formatted = MessageFormatter.arrayFormat(message, args).getMessage();
        Component chatMessage = Component.literal("[Debug] ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(formatted).withStyle(ChatFormatting.GRAY));

        // Callers may be off-thread (HTTP futures) or mid-tick — always hop to the client thread
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> client.gui.getChat().addMessage(chatMessage));
    }
}
