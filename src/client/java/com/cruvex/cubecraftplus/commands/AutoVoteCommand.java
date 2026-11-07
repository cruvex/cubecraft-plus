package com.cruvex.cubecraftplus.commands;

import com.cruvex.cubecraftplus.autovote.AutoVoteManager;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** {@code /ccp autovote run}, which casts every configured vote by hand. */
public final class AutoVoteCommand {

    public static LiteralArgumentBuilder<FabricClientCommandSource> node() {
        return ClientCommands.literal("autovote")
                .then(ClientCommands.literal("run")
                        .executes(ctx -> run(ctx.getSource())));
    }

    private static int run(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal("AutoVote: ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(AutoVoteManager.getInstance().runOnce()).withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    private AutoVoteCommand() {
    }
}
