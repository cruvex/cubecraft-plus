package com.cruvex.cubecraftplus.commands;

import com.cruvex.cubecraftplus.profile.PlayerObservation;
import com.cruvex.cubecraftplus.profile.ProfileParser;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CancellationException;

/** {@code /ccp players}: prints {@code /online} for the current lobby or game as {@link ProfileParser} reads it. */
public final class PlayersCommand {

    public static LiteralArgumentBuilder<FabricClientCommandSource> node() {
        return ClientCommands.literal("players")
                .executes(ctx -> list(ctx.getSource()));
    }

    private static int list(FabricClientCommandSource source) {
        ProfileParser.fetchOnline().whenComplete((players, error) -> {
            if (error != null) {
                source.sendError(Component.literal("/online: "
                        + (error instanceof CancellationException ? "cancelled" : error.getMessage())));
                return;
            }
            print(source, players);
        });
        return 1;
    }

    private static void print(FabricClientCommandSource source, List<PlayerObservation> players) {
        source.sendFeedback(Component.literal("Players: ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(String.valueOf(players.size())).withStyle(ChatFormatting.GRAY)));
        for (PlayerObservation player : players) {
            String details = " " + player.uuid() + " " + player.ranks() + (Boolean.TRUE.equals(player.legacy()) ? " legacy" : "")
                    + ", level " + player.level();
            source.sendFeedback(Component.literal(player.name()).withStyle(ChatFormatting.WHITE)
                    .append(Component.literal(details).withStyle(ChatFormatting.GRAY)));
        }
    }

    private PlayersCommand() {
    }
}
