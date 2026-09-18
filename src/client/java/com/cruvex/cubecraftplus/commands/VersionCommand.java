package com.cruvex.cubecraftplus.commands;

import com.cruvex.cubecraftplus.update.UpdateChecker;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** {@code /ccp version}: the installed version, and whether GitHub has a newer one. */
public final class VersionCommand {

    public static LiteralArgumentBuilder<FabricClientCommandSource> node() {
        return ClientCommandManager.literal("version")
                .executes(ctx -> show(ctx.getSource()));
    }

    private static int show(FabricClientCommandSource source) {
        source.sendFeedback(Component.translatable("cubecraftplus.version.installed",
                UpdateChecker.installedVersion(), UpdateChecker.minecraftVersion()));

        // Asks even with the update check turned off, since the player asked
        UpdateChecker.getInstance().fetchLatest().whenComplete((result, error) -> source.getClient().execute(() -> {
            if (error != null) {
                source.sendError(Component.translatable("cubecraftplus.version.failed", UpdateChecker.failureReason(error)));
            } else if (result.updateAvailable()) {
                source.sendFeedback(UpdateChecker.availableMessage(result));
            } else if (result.newer()) {
                source.sendFeedback(Component.translatable("cubecraftplus.version.not_for_minecraft",
                        result.latest(), result.minecraft()).withStyle(ChatFormatting.YELLOW));
            } else {
                source.sendFeedback(Component.translatable("cubecraftplus.version.latest").withStyle(ChatFormatting.GREEN));
            }
        }));
        return 1;
    }

    private VersionCommand() {
    }
}
