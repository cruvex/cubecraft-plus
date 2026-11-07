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
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.helpers.MessageFormatter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Votes once per round as soon as the voting item settles in the hotbar, and retries what failed once. */
public class AutoVoteManager {

    private static final String VOTING_ITEM_NAME = "Voting";
    private static final int PRESENT_TICKS = 2;
    // A join can drop the item for a tick
    private static final int GONE_TICKS = 10;
    private static final int RETRY_DELAY_TICKS = 40;
    private static final long LATE_MENU_MS = 3000;

    private static AutoVoteManager instance;

    private int presentTicks;
    private int absentTicks;
    private boolean roundActive;
    private long roundStartedAt;
    private @Nullable AutoVoteConfiguration roundConfiguration;

    private @Nullable VoteRun run;
    private boolean runIsAutomatic;
    private boolean autoPending;
    private boolean waitingLogged;
    private boolean retried;
    private List<GameVotes.VotePair> retryVotes = List.of();
    private int retryInTicks;
    private long hideLateMenusUntil;

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
            List<GameVotes.VotePair> toRetry = run.toRetry();
            if (run.mayGetLateMenu()) {
                hideLateMenusUntil = System.currentTimeMillis() + LATE_MENU_MS;
            }
            run = null;
            if (runIsAutomatic) {
                scheduleRetry(toRetry);
            }
        }
        if (run == null) {
            closeStrayMenu(client);
        }

        // Team games hand out the voting item late, so this watches the slot rather than the join
        AutoVoteConfiguration configuration =
                AutoVoteConfigs.getInstance().find(GameManager.getInstance().getCurrentGame());
        boolean present = configuration != null
                && isVotingItem(player.getInventory().getItem(configuration.hotbarSlot()));

        if (present) {
            absentTicks = 0;
            if (!roundActive && ++presentTicks >= PRESENT_TICKS) {
                startRound(configuration);
            }
        } else {
            presentTicks = 0;
            if (roundActive && ++absentTicks >= GONE_TICKS) {
                endRound("voting item gone");
            }
        }

        tickAutomatic(client);
    }

    private void startRound(AutoVoteConfiguration configuration) {
        roundActive = true;
        roundStartedAt = System.currentTimeMillis();
        roundConfiguration = configuration;

        ModConfig.AutoVoteConfig config = ConfigManager.getInstance().getConfig().autoVote;
        autoPending = config.enabled;
        log("round started for {} ({}, {}), votes: {}",
                configuration.gameName(),
                config.enabled ? "enabled" : "disabled",
                config.silent ? "silent" : "visible",
                describeVotes(configuration, config));
    }

    private void tickAutomatic(Minecraft client) {
        if (!roundActive || run != null || roundConfiguration == null) return;

        if (retryInTicks > 0) {
            if (--retryInTicks == 0) {
                log("retrying {} vote(s) that did not land", retryVotes.size());
                startRun(retryVotes, true);
            }
            return;
        }
        if (!autoPending) return;
        // The server ignores item uses until the client reports the level loaded
        ClientPacketListener connection = client.getConnection();
        if (connection == null || !connection.hasClientLoaded()) return;

        if (blockedByScreen(client, ConfigManager.getInstance().getConfig().autoVote.silent)) {
            if (!waitingLogged) {
                waitingLogged = true;
                log("waiting for the open screen to close before voting");
            }
            return;
        }

        autoPending = false;
        List<GameVotes.VotePair> votes = GameVotes.forGame(
                GameManager.getInstance().getCurrentGame(), ConfigManager.getInstance().getConfig().autoVote);
        if (votes.isEmpty()) {
            log("every category is set to don't vote, nothing to do");
            return;
        }
        startRun(votes, true);
    }

    private void scheduleRetry(List<GameVotes.VotePair> toRetry) {
        if (retried || toRetry.isEmpty()) return;

        retried = true;
        retryVotes = toRetry;
        retryInTicks = RETRY_DELAY_TICKS;
    }

    /** Casts every configured vote now, for testing by hand; returns what happened, for the command. */
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

        roundConfiguration = configuration;
        startRun(votes, false);
        return "Voting in " + votes.size() + " categories, see debug chat";
    }

    private void startRun(List<GameVotes.VotePair> votes, boolean automatic) {
        if (roundConfiguration == null) return;

        runIsAutomatic = automatic;
        run = new VoteRun(votes, roundConfiguration.hotbarSlot(), ConfigManager.getInstance().getConfig().autoVote.silent);
        run.start();
    }

    /** Whether a menu the server opens now gets no screen. */
    public boolean hidesMenus() {
        return run != null ? run.hidesMenus() : System.currentTimeMillis() < hideLateMenusUntil;
    }

    /** A hidden menu with no run to use it is a late answer, which the server would otherwise keep open. */
    private static void closeStrayMenu(Minecraft client) {
        ClientPacketListener connection = client.getConnection();
        VoteMenuView menu = MenuTracker.getInstance().view();
        if (connection == null || menu == null || !MenuTracker.getInstance().isHidden(menu.containerId())) return;

        log("closing menu '{}', which opened after the run had ended", menu.title());
        connection.send(new ServerboundContainerClosePacket(menu.containerId()));
    }

    /** The pause menu never blocks: it opens by itself when the player tabs out. */
    private static boolean blockedByScreen(Minecraft client, boolean silent) {
        Screen screen = client.gui.screen();
        if (screen instanceof PauseScreen) return false;
        return silent ? screen instanceof AbstractContainerScreen<?> : screen != null;
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
        roundConfiguration = null;
        presentTicks = 0;
        absentTicks = 0;
        autoPending = false;
        waitingLogged = false;
        retried = false;
        retryVotes = List.of();
        retryInTicks = 0;
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
