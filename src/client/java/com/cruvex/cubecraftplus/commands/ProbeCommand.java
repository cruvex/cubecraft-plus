package com.cruvex.cubecraftplus.commands;

import com.cruvex.cubecraftplus.util.ModPaths;
import com.cruvex.cubecraftplus.util.SignalProbe;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/** {@code /ccp probe}, the controls for {@link SignalProbe}. */
public final class ProbeCommand {

    public static LiteralArgumentBuilder<FabricClientCommandSource> node() {
        return ClientCommands.literal("probe")
                .executes(ctx -> setEnabled(ctx.getSource(), !SignalProbe.isEnabled()))
                .then(ClientCommands.literal("on")
                        .executes(ctx -> setEnabled(ctx.getSource(), true)))
                .then(ClientCommands.literal("off")
                        .executes(ctx -> setEnabled(ctx.getSource(), false)))
                .then(ClientCommands.literal("chat")
                        .executes(ctx -> setChat(ctx.getSource(),
                                !SignalProbe.getInstance().isMirroringToChat()))
                        .then(ClientCommands.literal("on")
                                .executes(ctx -> setChat(ctx.getSource(), true)))
                        .then(ClientCommands.literal("off")
                                .executes(ctx -> setChat(ctx.getSource(), false))))
                .then(ClientCommands.literal("summary")
                        .executes(ctx -> summary(ctx.getSource())))
                .then(ClientCommands.literal("sidebar")
                        .executes(ctx -> sidebar(ctx.getSource())))
                .then(ClientCommands.literal("mark")
                        .then(ClientCommands.argument("note", StringArgumentType.greedyString())
                                .executes(ctx -> mark(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "note")))));
    }

    private static int setEnabled(FabricClientCommandSource source, boolean enabled) {
        SignalProbe.getInstance().setEnabled(enabled);
        source.sendFeedback(Component.literal("Signal probe ")
                .append(state(enabled))
                .append(Component.literal(enabled
                        ? ". Play through a few games, then /ccp probe summary."
                        : ".")));
        return 1;
    }

    private static int setChat(FabricClientCommandSource source, boolean enabled) {
        SignalProbe.getInstance().setMirrorToChat(enabled);
        source.sendFeedback(Component.literal("Signal mirroring to chat ").append(state(enabled)));
        return 1;
    }

    /** How many of each signal the session has seen. */
    private static int summary(FabricClientCommandSource source) {
        SignalProbe probe = SignalProbe.getInstance();
        Map<String, Integer> counts = probe.counts();

        if (counts.isEmpty()) {
            source.sendFeedback(Component.literal("No signals recorded yet")
                    .withStyle(ChatFormatting.GRAY));
            return 1;
        }

        String breakdown = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> entry.getKey() + " " + entry.getValue())
                .collect(Collectors.joining(", "));

        Path path = probe.logPath();
        source.sendFeedback(Component.literal("Signals: ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(breakdown).withStyle(ChatFormatting.GRAY)));
        source.sendFeedback(Component.literal("Log: ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(path == null ? "not written yet" : ModPaths.display(path))
                        .withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    private static int sidebar(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal(SignalProbe.getInstance().describeSidebar())
                .withStyle(ChatFormatting.GRAY));
        return 1;
    }

    private static int mark(FabricClientCommandSource source, String note) {
        SignalProbe.getInstance().mark(note);
        source.sendFeedback(Component.literal("Marked: ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(note).withStyle(ChatFormatting.GRAY)));
        return 1;
    }

    private static Component state(boolean enabled) {
        return Component.literal(enabled ? "enabled" : "disabled")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private ProbeCommand() {
    }
}
