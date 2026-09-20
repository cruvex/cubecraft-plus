package com.cruvex.cubecraftplus.commands;

import com.cruvex.cubecraftplus.gui.screen.ConfigScreen;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;

/** {@code /ccp config}, also what a bare {@code /ccp} runs. */
public final class ConfigCommand {

    public static LiteralArgumentBuilder<FabricClientCommandSource> node() {
        return ClientCommandManager.literal("config")
                .executes(ctx -> open(ctx.getSource().getClient()));
    }

    public static int open(Minecraft client) {
        // Opened next tick, once the chat screen this ran from has finished closing
        client.execute(() -> client.setScreen(new ConfigScreen(client.screen)));
        return 1;
    }

    private ConfigCommand() {
    }
}
