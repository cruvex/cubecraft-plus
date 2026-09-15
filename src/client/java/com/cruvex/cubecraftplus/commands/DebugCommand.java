package com.cruvex.cubecraftplus.commands;

import com.cruvex.cubecraftplus.util.Debug;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** {@code /ccp debug}, toggling {@link Debug}. */
public final class DebugCommand {

    public static LiteralArgumentBuilder<FabricClientCommandSource> node() {
        return ClientCommandManager.literal("debug")
                .executes(ctx -> setEnabled(ctx.getSource(), !Debug.isEnabled()))
                .then(ClientCommandManager.literal("on")
                        .executes(ctx -> setEnabled(ctx.getSource(), true)))
                .then(ClientCommandManager.literal("off")
                        .executes(ctx -> setEnabled(ctx.getSource(), false)));
    }

    private static int setEnabled(FabricClientCommandSource source, boolean enabled) {
        Debug.setEnabled(enabled);
        source.sendFeedback(Component.literal("Debug mode ")
                .append(Component.literal(enabled ? "enabled" : "disabled")
                        .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)));
        return 1;
    }

    private DebugCommand() {
    }
}
