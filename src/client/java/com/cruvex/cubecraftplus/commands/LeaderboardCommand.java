package com.cruvex.cubecraftplus.commands;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.external.CubepanionAPI;
import com.cruvex.cubecraftplus.managers.CubeCraftManager;
import com.cruvex.cubecraftplus.model.CubeGame;
import com.cruvex.cubecraftplus.model.Game;
import com.cruvex.cubecraftplus.model.Leaderboard;
import com.cruvex.cubecraftplus.model.LeaderboardRow;
import com.cruvex.cubecraftplus.model.PlayerLeaderboard;
import com.cruvex.cubecraftplus.util.Util;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LeaderboardCommand {

    private static final Logger LOGGER = CubeCraftPlusClient.LOGGER;

    private static final Component HELP_MESSAGE = Component.empty()
        .append(Component.literal("Leaderboard commands\n").withStyle(s -> s.withColor(ChatFormatting.GREEN).withBold(true)))
        .append(Component.literal("/lb player <name>").withStyle(ChatFormatting.AQUA))
        .append(Component.literal(" — show all leaderboards a player is on\n").withStyle(ChatFormatting.GRAY))
        .append(Component.literal("/lb players").withStyle(ChatFormatting.AQUA))
        .append(Component.literal(" — current-game leaderboard for all online players\n").withStyle(ChatFormatting.GRAY))
        .append(Component.literal("/lb game <game> [start]").withStyle(ChatFormatting.AQUA))
        .append(Component.literal(" — 10 rows for a game starting at [start] (default 1)\n").withStyle(ChatFormatting.GRAY))
        .append(Component.literal("/lb help").withStyle(ChatFormatting.AQUA))
        .append(Component.literal(" — show this message").withStyle(ChatFormatting.GRAY));

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(buildCommand("leaderboard"));
        dispatcher.register(buildCommand("lb"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildCommand(String name) {
        return ClientCommandManager.literal(name)
                .then(ClientCommandManager.literal("player")
                        .then(ClientCommandManager.argument("name", StringArgumentType.string())
                                .suggests(LeaderboardCommand::suggestPlayerNames)
                                .executes(LeaderboardCommand::executePlayer)))
                .then(ClientCommandManager.literal("players")
                        .executes(LeaderboardCommand::executePlayers))
                .then(ClientCommandManager.literal("game")
                        .then(ClientCommandManager.argument("game", StringArgumentType.string())
                                .suggests(LeaderboardCommand::suggestGameNames)
                                .executes(ctx -> executeGame(ctx, 1))
                                .then(ClientCommandManager.argument("start", IntegerArgumentType.integer(1, 200))
                                        .executes(ctx -> executeGame(ctx, IntegerArgumentType.getInteger(ctx, "start"))))))
                .then(ClientCommandManager.literal("help")
                        .executes(LeaderboardCommand::executeHelp));
    }

    private static CompletableFuture<Suggestions> suggestPlayerNames(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        for (String n : ctx.getSource().getOnlinePlayerNames()) {
            builder.suggest(n);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestGameNames(CommandContext<FabricClientCommandSource> ctx, SuggestionsBuilder builder) {
        for (Game game : CubepanionAPI.getInstance().getAllGames()) {
            builder.suggest(game.name());
        }
        return builder.buildFuture();
    }

    private static int executeHelp(CommandContext<FabricClientCommandSource> ctx) {
        ctx.getSource().sendFeedback(HELP_MESSAGE);
        return 1;
    }

    private static int executePlayer(CommandContext<FabricClientCommandSource> ctx) {
        String player = StringArgumentType.getString(ctx, "name");

        if (!player.matches("[a-zA-Z0-9_]{2,16}")) {
            ctx.getSource().sendFeedback(
                    Component.literal("Invalid username: " + player).withStyle(s -> s.withColor(0xFF5555))
            );
            return 1;
        }

        CubepanionAPI.getInstance().getPlayerLeaderboard(player)
                .thenAcceptAsync(lb -> {
                    Component response = displayPlayerLeaderboards(player, lb);
                    Minecraft.getInstance().execute(() -> ctx.getSource().sendFeedback(response));
                })
                .exceptionally(e -> {
                    ctx.getSource().sendError(Component.literal("Something went wrong while fetching leaderboards for " + player));
                    LOGGER.error("Failed to load leaderboard", e);
                    return null;
                });

        return 1;
    }

    private static int executePlayers(CommandContext<FabricClientCommandSource> ctx) {
        CubeGame currentGame = CubeCraftManager.getInstance().getCurrentGame();
        if (currentGame == null || currentGame == CubeGame.NONE) {
            ctx.getSource().sendError(Component.literal("No current game detected"));
            return 1;
        }

        if (currentGame == CubeGame.LOBBY) {
            ctx.getSource().sendError(Component.literal("Cannot search leaderboards in the lobby"));
            return 1;
        }

        Game game = CubepanionAPI.getInstance().tryGame(currentGame.getString());
        if (game == null) {
            ctx.getSource().sendError(Component.literal("Unknown game: " + currentGame.getString()));
            return 1;
        }

        List<String> players = new ArrayList<>(ctx.getSource().getOnlinePlayerNames());
        if (players.isEmpty()) {
            ctx.getSource().sendFeedback(Component.literal("No online players to look up").withStyle(s -> s.withColor(ChatFormatting.YELLOW)));
            return 1;
        }

        CubepanionAPI.getInstance().batch(game, players)
                .thenAcceptAsync(rows -> {
                    Component response = displayBatchLeaderboard(game, rows);
                    Minecraft.getInstance().execute(() -> ctx.getSource().sendFeedback(response));
                })
                .exceptionally(e -> {
                    ctx.getSource().sendError(Component.literal("Something went wrong while fetching leaderboards for " + game.displayName()));
                    LOGGER.error("Failed to load batch leaderboard", e);
                    return null;
                });

        return 1;
    }

    private static int executeGame(CommandContext<FabricClientCommandSource> ctx, int start) {
        String name = StringArgumentType.getString(ctx, "game");
        Game game = CubepanionAPI.getInstance().tryGame(name);
        if (game == null) {
            ctx.getSource().sendError(Component.literal("Unknown game: " + name));
            return 1;
        }

        int upper = Math.min(200, start + 9);
        CubepanionAPI.getInstance().getLeaderboard(game, start, upper)
                .thenAcceptAsync(lb -> {
                    Component response = displayGameLeaderboard(game, start, upper, lb);
                    Minecraft.getInstance().execute(() -> ctx.getSource().sendFeedback(response));
                })
                .exceptionally(e -> {
                    ctx.getSource().sendError(Component.literal("Something went wrong while fetching leaderboards for " + game.displayName()));
                    LOGGER.error("Failed to load game leaderboard", e);
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

        MutableComponent message = Component.empty()
            .append(Component.literal("Leaderboards for ")
                .withStyle(s -> s.withColor(ChatFormatting.GREEN)))
            .append(Component.literal(player).withStyle(s -> s.withColor(ChatFormatting.AQUA).withBold(true)))
            .append(Component.literal(" ("))
            .append(Component.literal(String.valueOf(lb.leaderboards().size())).withStyle(ChatFormatting.GREEN))
            .append(Component.literal(")"))
            .append(Component.literal(":"));

        lb.leaderboards().forEach(row -> {
            var game = CubepanionAPI.getInstance().getGameById(row.gameId());
            String gameName = game == null ? "Unknown" : game.displayName();
            String scoreType = game == null ? "Unknown" : game.scoreType();

            message.append(
                Component.literal("\n  › ")
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

    private static Component displayBatchLeaderboard(Game game, List<LeaderboardRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return Component.literal("No leaderboard entries found for ").withStyle(s -> s.withColor(ChatFormatting.YELLOW))
                    .append(Component.literal(game.displayName()).withStyle(s -> s.withColor(ChatFormatting.AQUA).withBold(true)));
        }

        MutableComponent title = Component.empty()
            .append(Component.literal("Leaderboards for ").withStyle(s -> s.withColor(ChatFormatting.GREEN)))
            .append(Component.literal(game.displayName()).withStyle(s -> s.withColor(ChatFormatting.AQUA).withBold(true)))
            .append(Component.literal(" ("))
            .append(Component.literal(String.valueOf(rows.size())).withStyle(ChatFormatting.GREEN))
            .append(Component.literal(")"))
            .append(Component.literal(":"));

        MutableComponent message = title.append(Component.literal("\n"));
        String scoreType = Util.capitalize(game.scoreType());

        for (int idx = 0; idx < rows.size(); idx++) {
            LeaderboardRow row = rows.get(idx);

            if (idx > 0) {
                message.append(Component.literal("\n"));
            }

            message.append(
                Component.literal("  › ")
                    .withStyle(ChatFormatting.DARK_AQUA)
            ).append(
                Component.literal(row.player())
                    .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
            ).append(
                Component.literal(" #" + row.position() + "  ")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
            ).append(
                Component.literal(String.valueOf(row.score()))
                    .withStyle(ChatFormatting.GREEN)
            ).append(
                Component.literal(" " + scoreType)
                    .withStyle(ChatFormatting.DARK_GREEN)
            );
        }

        return message;
    }

    private static Component displayGameLeaderboard(Game game, int lower, int upper, Leaderboard lb) {
        if (lb == null || lb.rows() == null || lb.rows().isEmpty()) {
            return Component.literal("No leaderboard entries for ").withStyle(s -> s.withColor(ChatFormatting.YELLOW))
                    .append(Component.literal(game.displayName()).withStyle(s -> s.withColor(ChatFormatting.AQUA).withBold(true)))
                    .append(Component.literal(" between #" + lower + " and #" + upper));
        }

        MutableComponent title = Component.empty()
            .append(Component.literal("Leaderboard for ").withStyle(s -> s.withColor(ChatFormatting.GREEN)))
            .append(Component.literal(game.displayName()).withStyle(s -> s.withColor(ChatFormatting.AQUA).withBold(true)))
            .append(Component.literal(" (#" + lower + "–#" + upper + "):"));

        MutableComponent message = title;
        String scoreType = Util.capitalize(game.scoreType());
        List<LeaderboardRow> rows = lb.rows();

        for (int idx = 0; idx < rows.size(); idx++) {
            LeaderboardRow row = rows.get(idx);
            message.append(Component.literal("\n"))
                   .append(Component.literal("  › ").withStyle(ChatFormatting.DARK_AQUA))
                   .append(Component.literal(row.player()).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD))
                   .append(Component.literal(" #" + row.position() + "  ").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD))
                   .append(Component.literal(String.valueOf(row.score())).withStyle(ChatFormatting.GREEN))
                   .append(Component.literal(" " + scoreType).withStyle(ChatFormatting.DARK_GREEN));
        }

        return message;
    }
}
