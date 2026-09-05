package com.cruvex.cubecraftplus.commands;

import com.cruvex.cubecraftplus.gui.screen.ConfigScreen;
import com.cruvex.cubecraftplus.util.Debug;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class ConfigCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(buildCommand("ccp"));
        dispatcher.register(buildCommand("cubecraftplus"));
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> buildCommand(String name) {
        return ClientCommands.literal(name)
                .executes(ctx -> openConfigScreen(ctx.getSource().getClient()))
                .then(ClientCommands.literal("config")
                        .executes(ctx -> openConfigScreen(ctx.getSource().getClient())))
                .then(ClientCommands.literal("debug")
                        .executes(ctx -> setDebug(ctx.getSource(), !Debug.isEnabled()))
                        .then(ClientCommands.literal("on")
                                .executes(ctx -> setDebug(ctx.getSource(), true)))
                        .then(ClientCommands.literal("off")
                                .executes(ctx -> setDebug(ctx.getSource(), false))))
                .then(ProbeCommand.node());
    }

    private static int setDebug(FabricClientCommandSource source, boolean enabled) {
        Debug.setEnabled(enabled);
        source.sendFeedback(Component.literal("Debug mode ")
                .append(Component.literal(enabled ? "enabled" : "disabled")
                        .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED)));
        return 1;
    }

    private static int openConfigScreen(Minecraft client) {
        // Commands run while the chat screen is closing — open the screen next tick,
        // and capture the parent then so we don't return to the dead chat screen
        client.execute(() -> client.gui.setScreen(new ConfigScreen(client.gui.screen())));
        return 1;
    }
}
