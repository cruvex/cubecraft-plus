package com.cruvex.cubecraftplus.commands;

import com.cruvex.cubecraftplus.gui.screen.AutoVoteConfigScreen;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;

public class ConfigCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(buildCommand("ccp"));
        dispatcher.register(buildCommand("cubecraftplus"));
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> buildCommand(String name) {
        return ClientCommandManager.literal(name)
                .executes(ctx -> openConfigScreen(ctx.getSource().getClient()))
                .then(ClientCommandManager.literal("config")
                        .executes(ctx -> openConfigScreen(ctx.getSource().getClient())));
    }

    private static int openConfigScreen(Minecraft client) {
        // Commands run while the chat screen is closing — open the screen next tick,
        // and capture the parent then so we don't return to the dead chat screen
        client.execute(() -> client.setScreen(new AutoVoteConfigScreen(client.screen)));
        return 1;
    }
}
