package com.cruvex.cubecraftplus.autovote;

import com.cruvex.cubecraftplus.config.ConfigManager;
import com.cruvex.cubecraftplus.config.ModConfig;
import com.cruvex.cubecraftplus.debug.Debug;
import com.cruvex.cubecraftplus.debug.SignalProbe;
import com.cruvex.cubecraftplus.game.CubeCraftManager;
import com.cruvex.cubecraftplus.game.CubeEvents;
import com.cruvex.cubecraftplus.game.GameManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.helpers.MessageFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Starts a round once the voting item has settled in the hotbar, which for now only reports what it would vote.
 * Votes are cast by hand through {@link #runOnce()} until the flow is proven.
 */
public class AutoVoteManager {

    private static final String VOTING_ITEM_NAME = "Voting";
    /** How long the item has to stay, and then stay gone: a join can drop it for a tick. */
    private static final int SETTLE_TICKS = 10;

    private static AutoVoteManager instance;

    private int presentTicks;
    private int absentTicks;
    private boolean roundActive;
    private long roundStartedAt;
    private @Nullable VoteRun run;

    public static AutoVoteManager getInstance() {
        if (instance == null) {
            instance = new AutoVoteManager();
        }
        return instance;
    }

    public void init() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (run != null && !overlay) {
                run.onChat(message.getString());
            }
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> endRound("joined server"));
        CubeEvents.GAME_JOIN.register(game -> endRound("joined game " + game.name()));
    }

    private void onEndTick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || !CubeCraftManager.getInstance().isOnCubeCraft()) return;

        if (run != null && !run.tick()) {
            run = null;
        }

        // Team games swap Team Selection for the voting item late, so this watches the slot rather than the join
        AutoVoteConfiguration configuration =
                AutoVoteConfigs.getInstance().find(GameManager.getInstance().getCurrentGame());
        boolean present = configuration != null
                && isVotingItem(player.getInventory().getItem(configuration.hotbarSlot()));

        if (present) {
            absentTicks = 0;
            if (!roundActive && ++presentTicks >= SETTLE_TICKS) {
                startRound(configuration);
            }
        } else {
            presentTicks = 0;
            if (roundActive && ++absentTicks >= SETTLE_TICKS) {
                endRound("voting item gone");
            }
        }
    }

    private void startRound(AutoVoteConfiguration configuration) {
        roundActive = true;
        roundStartedAt = System.currentTimeMillis();

        ModConfig.AutoVoteConfig config = ConfigManager.getInstance().getConfig().autoVote;
        log("round started for {} ({}, {}), would vote: {}",
                configuration.gameName(),
                config.enabled ? "enabled" : "disabled",
                config.silent ? "silent" : "visible",
                describeVotes(configuration, config));
    }

    /** Casts the first configured vote now, for testing the flow by hand. Returns what happened, for the command. */
    public String runOnce() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null || !CubeCraftManager.getInstance().isOnCubeCraft()) {
            return "Not on CubeCraft";
        }
        if (run != null) return "A run is already going";

        AutoVoteConfiguration configuration =
                AutoVoteConfigs.getInstance().find(GameManager.getInstance().getCurrentGame());
        if (configuration == null) return "No voting config for this game";
        if (!isVotingItem(player.getInventory().getItem(configuration.hotbarSlot()))) {
            return "No voting item in hotbar slot " + (configuration.hotbarSlot() + 1);
        }

        List<GameVotes.VotePair> votes =
                GameVotes.forGame(GameManager.getInstance().getCurrentGame(), ConfigManager.getInstance().getConfig().autoVote);
        if (votes.isEmpty()) return "Every category is set to don't vote";

        run = new VoteRun(votes.get(0), configuration.hotbarSlot());
        run.start(client, player);
        return "Voting in '" + votes.get(0).submenuTitle() + "', see debug chat";
    }

    private void endRound(String reason) {
        if (run != null) {
            run.abandon(reason);
            run = null;
        }
        if (roundActive) {
            log("round over ({}) after {}s", reason,
                    String.format(Locale.ROOT, "%.1f", (System.currentTimeMillis() - roundStartedAt) / 1000.0));
        }
        roundActive = false;
        presentTicks = 0;
        absentTicks = 0;
    }

    private static String describeVotes(AutoVoteConfiguration configuration, ModConfig.AutoVoteConfig config) {
        List<String> votes = new ArrayList<>();
        for (AutoVoteCategory category : configuration.categories()) {
            int slot = GameVotes.slotFor(config, category);
            if (slot < 0) {
                votes.add(category.name() + " -> don't vote");
                continue;
            }

            String route = category.choiceIndex() < 0 ? "direct" : "category slot " + category.choiceIndex();
            votes.add(String.format(Locale.ROOT, "%s -> %s (menu '%s', %s, vote slot %d)",
                    category.name(), optionName(category, slot), category.menuTitle(), route, slot));
        }
        return votes.isEmpty() ? "nothing" : String.join("; ", votes);
    }

    private static String optionName(AutoVoteCategory category, int slot) {
        for (AutoVoteCategoryOption option : category.options()) {
            if (option.slot() == slot) {
                return option.name();
            }
        }
        return "?";
    }

    static boolean isVotingItem(ItemStack stack) {
        return !stack.isEmpty() && VOTING_ITEM_NAME.equals(stack.getHoverName().getString());
    }

    /** To chat in debug mode, and into the signal log so a round lines up with the packets. */
    static void log(String message, Object... args) {
        Debug.log("AutoVote: " + message, args);
        if (SignalProbe.getInstance().active()) {
            SignalProbe.getInstance().signal("AUTOVOTE", "{}", MessageFormatter.arrayFormat(message, args).getMessage());
        }
    }
}
