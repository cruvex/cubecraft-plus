package com.cruvex.cubecraftplus.commands;

import com.cruvex.cubecraftplus.managers.FriendsManager;
import com.cruvex.cubecraftplus.model.Friend;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

/** {@code /ccp friends}, backed by {@link FriendsManager}. */
public final class FriendsCommand {

    public static LiteralArgumentBuilder<FabricClientCommandSource> node() {
        return ClientCommands.literal("friends")
                .then(ClientCommands.literal("refresh")
                        .executes(ctx -> refresh(ctx.getSource())));
    }

    private static int refresh(FabricClientCommandSource source) {
        source.sendFeedback(Component.translatable("cubecraftplus.friends.refreshing")
                .withStyle(ChatFormatting.GRAY));

        FriendsManager.getInstance().refresh().whenComplete((friends, error) -> {
            if (error != null) {
                source.sendError(Component.translatable("cubecraftplus.friends.refresh_failed", describe(error)));
                return;
            }

            long online = friends.stream().filter(Friend::online).count();
            source.sendFeedback(Component.translatable("cubecraftplus.friends.refreshed", friends.size(), online)
                    .withStyle(ChatFormatting.GREEN));
        });
        return 1;
    }

    /** Unwraps the CompletionException a failed page arrives in. */
    private static String describe(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        return cause instanceof CancellationException ? "disconnected" : cause.getMessage();
    }

    private FriendsCommand() {
    }
}
