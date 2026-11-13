package com.cruvex.cubecraftplus.commands;

import com.cruvex.cubecraftplus.api.AuthManager;
import com.cruvex.cubecraftplus.update.UpdateChecker;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * {@code /ccp auth}: whether this account is signed in to CubeCraft+, as the API sees it. Only reports;
 * it never starts a handshake, so it cannot run into Mojang's rate limits.
 */
public final class AuthCommand {

    public static LiteralArgumentBuilder<FabricClientCommandSource> node() {
        return ClientCommands.literal("auth")
                .executes(ctx -> show(ctx.getSource()));
    }

    private static int show(FabricClientCommandSource source) {
        AuthManager auth = AuthManager.getInstance();

        if (!auth.isAuthenticated()) {
            if (auth.isAuthenticating()) {
                source.sendFeedback(Component.translatable("cubecraftplus.auth.authenticating").withStyle(ChatFormatting.YELLOW));
            } else {
                source.sendError(auth.lastFailure()
                        .map(reason -> Component.translatable("cubecraftplus.auth.failed", reason))
                        .orElseGet(() -> Component.translatable("cubecraftplus.auth.signed_out")));
            }
            return 1;
        }

        auth.whoAmI().whenComplete((me, error) -> source.getClient().execute(() -> {
            if (error != null) {
                source.sendError(Component.translatable("cubecraftplus.auth.check_failed", UpdateChecker.failureReason(error)));
            } else {
                source.sendFeedback(Component.translatable("cubecraftplus.auth.signed_in", me.uuid().toString(), me.expiresAt())
                        .withStyle(ChatFormatting.GREEN));
            }
        }));
        return 1;
    }

    private AuthCommand() {
    }
}
