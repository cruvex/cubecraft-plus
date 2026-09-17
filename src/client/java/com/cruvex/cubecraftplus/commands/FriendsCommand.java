package com.cruvex.cubecraftplus.commands;

import com.cruvex.cubecraftplus.gui.screen.FriendsScreen;
import com.cruvex.cubecraftplus.managers.FriendsManager;
import com.cruvex.cubecraftplus.model.Friend;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** {@code /ccp friends} opens the friends screen, backed by {@link FriendsManager}. */
public final class FriendsCommand {

    public static LiteralArgumentBuilder<FabricClientCommandSource> node() {
        return ClientCommands.literal("friends")
                .executes(ctx -> open(ctx.getSource().getClient()))
                .then(ClientCommands.literal("refresh")
                        .executes(ctx -> refresh(ctx.getSource())));
    }

    private static int open(Minecraft client) {
        // Same as ConfigCommand: open next tick, once the chat screen is gone
        client.execute(() -> client.setScreen(new FriendsScreen(client.screen)));
        return 1;
    }

    private static int refresh(FabricClientCommandSource source) {
        source.sendFeedback(Component.translatable("cubecraftplus.friends.refreshing")
                .withStyle(ChatFormatting.GRAY));

        FriendsManager.getInstance().refresh().whenComplete((friends, error) -> {
            if (error != null) {
                source.sendError(Component.translatable("cubecraftplus.friends.refresh_failed",
                        FriendsManager.failureReason(error)));
                return;
            }

            long online = friends.stream().filter(Friend::online).count();
            source.sendFeedback(Component.translatable("cubecraftplus.friends.refreshed", friends.size(), online)
                    .withStyle(ChatFormatting.GREEN));
        });
        return 1;
    }

    private FriendsCommand() {
    }
}
