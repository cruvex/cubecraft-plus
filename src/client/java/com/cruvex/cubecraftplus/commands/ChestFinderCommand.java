package com.cruvex.cubecraftplus.commands;

import com.cruvex.cubecraftplus.managers.ChestFinderManager;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** {@code /ccp chestfinder}, searching for the lobby chest now. */
public final class ChestFinderCommand {

    public static LiteralArgumentBuilder<FabricClientCommandSource> node() {
        return ClientCommands.literal("chestfinder")
                .executes(ctx -> findChest(ctx.getSource()));
    }

    private static int findChest(FabricClientCommandSource source) {
        ChestFinderManager.getInstance().startSearch();
        source.sendFeedback(Component.translatable("cubecraftplus.chestfinder.searching")
                .withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private ChestFinderCommand() {
    }
}
