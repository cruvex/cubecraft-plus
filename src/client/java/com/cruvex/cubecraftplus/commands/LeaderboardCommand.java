package com.cruvex.cubecraftplus.commands;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.external.CubepanionAPI;
import com.cruvex.cubecraftplus.model.PlayerLeaderboard;
import com.cruvex.cubecraftplus.util.Util;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;

import java.util.Collection;

public class LeaderboardCommand {

    private static final Logger LOGGER = CubeCraftPlusClient.LOGGER;

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(buildCommand("leaderboard"));
        dispatcher.register(buildCommand("lb"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildCommand(String name) {
        return ClientCommandManager.literal(name)
                .then(ClientCommandManager.argument("player", StringArgumentType.string())
                        .suggests((ctx, builder) -> {
                            builder.suggest("players");
                            builder.suggest("help");

                            FabricClientCommandSource source = ctx.getSource();

                            Collection<String> playerNames = source.getOnlinePlayerNames();

                            for (String playerName : playerNames) {
                                builder.suggest(playerName);
                            }

                            return builder.buildFuture();
                        })
                        .executes(LeaderboardCommand::leaderboard))
                .executes(LeaderboardCommand::leaderboard);
    }

    private static int leaderboard(CommandContext<FabricClientCommandSource> ctx) {
        String player = StringArgumentType.getString(ctx, "player");

        if (!player.matches("[a-zA-Z0-9_]{2,16}")) {
            ctx.getSource().sendFeedback(
                    Component.literal("Invalid username: " + player).withStyle(s -> s.withColor(0xFF5555))
            );
            return 1;
        }

        CubepanionAPI.I().getPlayerLeaderboard(player)
                .thenAcceptAsync(lb -> {
                    Component commandResponse = displayPlayerLeaderboards(player, lb);
                    Minecraft.getInstance().execute(() -> ctx.getSource().sendFeedback(commandResponse));
                })
                .exceptionally(e -> {
                    ctx.getSource().sendError(Component.literal("Something went wrong while fetching leaderboards for " + player));
                    LOGGER.error("Failed to load leaderboard", e);
                    return null;
                });

        return 1;
    }

    private static Component displayPlayerLeaderboards(String player, PlayerLeaderboard lb) {
        if (lb == null) {
            return Component.literal("Something went wrong while fetching leaderboards for " + player).withStyle(s -> s.withColor(ChatFormatting.RED));
        }

        if (lb.leaderboards().isEmpty()) {
            return Component.literal("No leaderboards found for ").withStyle(s -> s.withColor(ChatFormatting.YELLOW))
                    .append(Component.literal(player).withStyle(s -> s.withColor(ChatFormatting.AQUA).withBold(true)));
        }

        MutableComponent title = Component.empty()
            .append(Component.literal("Leaderboards for ")
                .withStyle(s -> s.withColor(ChatFormatting.GREEN)))
            .append(Component.literal(player).withStyle(s -> s.withColor(ChatFormatting.AQUA).withBold(true)))
            .append(Component.literal(" ("))
            .append(Component.literal(String.valueOf(lb.leaderboards().size())).withStyle(ChatFormatting.GREEN))
            .append(Component.literal(")"))
            .append(Component.literal(":"));

        MutableComponent message = title.append(Component.literal("\n"));

        lb.leaderboards().forEach(row -> {
            var game = CubepanionAPI.I().getGameById(row.gameId());
            String gameName = game == null ? "Unknown" : game.displayName();
            String scoreType = game == null ? "Unknown" : game.scoreType();

            message.append(
                Component.literal("  › ")
                    .withStyle(ChatFormatting.DARK_AQUA)
            ).append(
                Component.literal(gameName)
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
            ).append(
                Component.literal(" #" + row.position() + "  ")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            ).append(
                Component.literal(String.valueOf(row.score()))
                    .withStyle(ChatFormatting.GREEN)
            ).append(
                Component.literal(" " + Util.capitalize(scoreType))
                    .withStyle(ChatFormatting.DARK_GREEN)
            );
        });

        return message;
    }
}
