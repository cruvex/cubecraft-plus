package com.cruvex.cubecraftplus.commands;

import com.cruvex.cubecraftplus.managers.ChatQueryManager;
import com.cruvex.cubecraftplus.util.ChatDump;
import com.cruvex.cubecraftplus.util.ModPaths;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** {@code /ccp query}: debug commands for {@link ChatQueryManager} and {@link ChatDump}. */
public final class QueryCommand {

    /** How long a dump keeps recording after its last command. */
    private static final long DUMP_WINDOW_MS = 5000;

    public static LiteralArgumentBuilder<FabricClientCommandSource> node() {
        return ClientCommands.literal("query")
                .then(ClientCommands.literal("dump")
                        .then(ClientCommands.argument("commands", StringArgumentType.greedyString())
                                .executes(ctx -> dump(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "commands"), 0))))
                // Own literal: under dump it would be ambiguous with the greedy argument
                .then(ClientCommands.literal("spaced")
                        .then(ClientCommands.argument("ms", IntegerArgumentType.integer(0, 5000))
                                .then(ClientCommands.argument("commands", StringArgumentType.greedyString())
                                        .executes(ctx -> dump(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "commands"),
                                                IntegerArgumentType.getInteger(ctx, "ms"))))))
                .then(queryNode("run", false))
                .then(queryNode("hide", true));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> queryNode(String name, boolean hide) {
        return ClientCommands.literal(name)
                .then(ClientCommands.argument("header", StringArgumentType.string())
                        .then(ClientCommands.argument("line", StringArgumentType.string())
                                .then(ClientCommands.argument("commands", StringArgumentType.greedyString())
                                        .executes(ctx -> query(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "header"),
                                                StringArgumentType.getString(ctx, "line"),
                                                StringArgumentType.getString(ctx, "commands"),
                                                hide)))));
    }

    private static int dump(FabricClientCommandSource source, String commands, int everyMs) {
        List<String> sent = split(commands);
        if (sent.isEmpty()) return 0;

        long windowMs = DUMP_WINDOW_MS + (long) everyMs * (sent.size() - 1);
        String label = "/" + String.join(", /", sent) + (everyMs > 0 ? " every " + everyMs + "ms" : " at once");
        Path path = ChatDump.start(label, windowMs);

        // Bypasses queries, so nothing is matched or hidden
        Minecraft client = source.getClient();
        for (int i = 0; i < sent.size(); i++) {
            String command = sent.get(i);
            CompletableFuture.delayedExecutor((long) everyMs * i, TimeUnit.MILLISECONDS, client::execute)
                    .execute(() -> {
                        ClientPacketListener connection = client.getConnection();
                        if (connection == null) return;

                        ChatDump.sent(command);
                        connection.sendCommand(command);
                    });
        }

        source.sendFeedback(Component.literal(String.format(Locale.ROOT, "Sending %s, recording server chat for %.1fs to ",
                        label, windowMs / 1000.0))
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(ModPaths.display(path)).withStyle(ChatFormatting.AQUA)));
        return 1;
    }

    private static int query(FabricClientCommandSource source, String headerRegex, String lineRegex,
                             String commands, boolean hide) {
        Pattern header = compile(source, headerRegex);
        Pattern line = compile(source, lineRegex);
        if (header == null || line == null) return 0;

        for (String command : split(commands)) {
            ChatQueryManager.getInstance().send(command, header, line, hide)
                    .whenComplete((reply, error) -> report(source, command, reply, error));
        }
        return 1;
    }

    private static @Nullable Pattern compile(FabricClientCommandSource source, String regex) {
        try {
            return Pattern.compile(regex);
        } catch (PatternSyntaxException e) {
            source.sendError(Component.literal("Invalid pattern '" + regex + "': " + e.getDescription()));
            return null;
        }
    }

    private static void report(FabricClientCommandSource source, String command,
                               @Nullable ChatQueryManager.Reply reply, @Nullable Throwable error) {
        if (error != null) {
            source.sendError(Component.literal("/" + command + ": "
                    + (error instanceof CancellationException ? "cancelled" : error.getMessage())));
            return;
        }

        source.sendFeedback(Component.literal("/" + command).withStyle(ChatFormatting.AQUA)
                .append(Component.literal(" answered in " + reply.millis() + "ms, " + reply.lines().size() + " lines")
                        .withStyle(ChatFormatting.GRAY)));
        source.sendFeedback(row("H", reply.header()));
        for (int i = 0; i < reply.lines().size(); i++) {
            source.sendFeedback(row(String.valueOf(i + 1), reply.lines().get(i)));
        }
    }

    /** Plain text, so the message's click events aren't live here. */
    private static Component row(String label, Component message) {
        MutableComponent row = Component.literal(String.format(Locale.ROOT, "%2s ", label))
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(message.getString()).withStyle(ChatFormatting.WHITE));
        for (String event : ChatDump.events(message)) {
            row.append(Component.literal(" [" + event + "]").withStyle(ChatFormatting.DARK_AQUA));
        }
        return row;
    }

    private static List<String> split(String commands) {
        return Arrays.stream(commands.split(";"))
                .map(String::trim)
                .map(command -> command.startsWith("/") ? command.substring(1) : command)
                .filter(command -> !command.isEmpty())
                .toList();
    }

    private QueryCommand() {
    }
}
