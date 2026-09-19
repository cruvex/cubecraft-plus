package com.cruvex.cubecraftplus.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.loader.api.FabricLoader;

/** {@code /ccp} and {@code /cubecraftplus}: the root every subcommand hangs off. */
public final class CcpCommand {

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(build("ccp"));
        dispatcher.register(build("cubecraftplus"));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> build(String name) {
        LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal(name)
                .executes(ctx -> ConfigCommand.open(ctx.getSource().getClient()))
                .then(ConfigCommand.node())
                .then(FriendsCommand.node())
                .then(VersionCommand.node())
                .then(ChestFinderCommand.node())
                .then(DebugCommand.node());

        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            root.then(ProbeCommand.node())
                    .then(QueryCommand.node());
        }
        return root;
    }

    private CcpCommand() {
    }
}
