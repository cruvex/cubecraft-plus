package com.cruvex.cubecraftplus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/** {@code /ccp} and {@code /cubecraftplus}: the root every subcommand hangs off. */
public final class CcpCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(build("ccp"));
        dispatcher.register(build("cubecraftplus"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> build(String name) {
        return ClientCommands.literal(name)
                .executes(ctx -> ConfigCommand.open(ctx.getSource().getClient()))
                .then(ConfigCommand.node())
                .then(DebugCommand.node())
                .then(ChestFinderCommand.node())
                .then(ProbeCommand.node())
                .then(QueryCommand.node())
                .then(FriendsCommand.node())
                .then(VersionCommand.node());
    }

    private CcpCommand() {
    }
}
