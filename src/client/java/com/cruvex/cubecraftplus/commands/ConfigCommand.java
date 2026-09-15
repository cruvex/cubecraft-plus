package com.cruvex.cubecraftplus.commands;

import com.cruvex.cubecraftplus.gui.screen.ConfigScreen;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;

/** {@code /ccp config}, also what a bare {@code /ccp} runs. */
public final class ConfigCommand {

    public static LiteralArgumentBuilder<FabricClientCommandSource> node() {
        return ClientCommands.literal("config")
                .executes(ctx -> open(ctx.getSource().getClient()));
    }

    public static int open(Minecraft client) {
        // Commands run while the chat screen is closing — open the screen next tick,
        // and capture the parent then so we don't return to the dead chat screen
        client.execute(() -> client.gui.setScreen(new ConfigScreen(client.gui.screen())));
        return 1;
    }

    private ConfigCommand() {
    }
}
