package com.cruvex.cubecraftplus.util;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.slf4j.helpers.MessageFormatter;

/**
 * Debug mode, toggled in-game with /ccp debug. {@link #log} writes to the logger at debug
 * level and {@link #info} at info level; both mirror to chat while enabled. Takes slf4j-style
 * {@code {}} placeholders.
 */
public class Debug {

    private static boolean enabled = FabricLoader.getInstance().isDevelopmentEnvironment();;

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
        toChat(message, args);
    }

    public static void info(String message, Object... args) {
        CubeCraftPlusClient.LOGGER.info(message, args);
        toChat(message, args);
    }

    private static void toChat(String message, Object... args) {
        if (!enabled) {
            return;
        }

        String formatted = MessageFormatter.arrayFormat(message, args).getMessage();
        Chat.send(Component.literal("[Debug] ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(formatted).withStyle(ChatFormatting.GRAY)));
    }
}
