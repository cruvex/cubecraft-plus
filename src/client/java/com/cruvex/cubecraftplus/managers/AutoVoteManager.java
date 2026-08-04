package com.cruvex.cubecraftplus.managers;

import com.cruvex.cubecraftplus.config.ModConfig;
import com.cruvex.cubecraftplus.events.CubeEvents;
import com.cruvex.cubecraftplus.model.GameVotes;
import com.cruvex.cubecraftplus.model.GameVotes.VotePair;
import com.cruvex.cubecraftplus.util.Debug;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Automatically votes in CubeCraft pre-game lobbies for the games defined in {@link GameVotes}.
 *
 * Tick-driven state machine: menus are populated slightly after the screen opens, so it
 * clicks only once the target slot holds an item. States waiting for a menu also require a
 * new container id, because submenu titles can pass the main-menu check (e.g. EggWars'
 * "Perk Voting") while the old screen is still open.
 */
public class AutoVoteManager {

    private static AutoVoteManager instance;

    private static final String VOTING_ITEM_NAME = "Voting";
    private static final int VOTING_HOTBAR_SLOT = 0;
    // Large chest plus player inventory; fewer slots means a different container
    private static final int MIN_MENU_SLOTS = 70;
    private static final int ARM_DELAY_TICKS = 2;
    private static final int RETURN_DELAY_TICKS = 2;
    private static final int STATE_TIMEOUT_TICKS = 100;
    private static final int USE_RETRY_TICKS = 20;

    private static final Pattern VOTE_CONFIRMED_PATTERN =
            Pattern.compile("(?:.{0,5} |)([a-zA-Z0-9_]{2,16})(?: .{0,5}|) voted for [a-zA-Z ]+\\. \\d+ votes?");
    private static final Pattern GAME_STARTING_PATTERN =
            Pattern.compile("[a-zA-Z ]+ is starting in 5 seconds\\.");

    private enum State {
        IDLE,         // not in a votable pre-game lobby, or this round was already handled
        ARMED,        // voting item detected; short countdown before using it
        OPENING_MAIN, // waiting for the main voting menu; clicks the next category once populated
        OPENING_SUB,  // waiting for the vote menu (category submenu, or the direct vote menu
                      // for games without categories); clicks the vote option once populated
        VOTING        // vote clicked; short delay, then return to main menu or close
    }

    private State state = State.IDLE;
    private int stateTicks;
    private int delayTicks;
    private int voteIndex;
    private List<VotePair> votes = List.of();
    // Menu we last clicked in, so a stale screen can't be re-clicked
    private int lastContainerId = -1;
    private boolean attemptedThisRound;
    private boolean voteConfirmed;

    public static AutoVoteManager getInstance() {
        if (instance == null) {
            instance = new AutoVoteManager();
        }
        return instance;
    }

    public void init() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        ScreenEvents.AFTER_INIT.register(this::onScreenInit);
        ClientReceiveMessageEvents.GAME.register(this::onGameMessage);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset("joined server"));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset("disconnected"));
        CubeEvents.GAME_JOIN.register(game -> reset("joined game " + game.name()));
    }

    private void onEndTick(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null || !CubeCraftManager.getInstance().isOnCubeCraft()) {
            return;
        }

        if (state != State.IDLE && ++stateTicks > STATE_TIMEOUT_TICKS) {
            Debug.log("AutoVote: state {} timed out, aborting", state);
            attemptedThisRound = true; // retried via the "starting in 5 seconds" chat line
            setState(State.IDLE);
            return;
        }

        switch (state) {
            case IDLE -> tickIdle(client, player);
            case ARMED -> tickArmed(client, player);
            case OPENING_MAIN -> tickOpeningMain(client, player);
            case OPENING_SUB -> tickOpeningSub(client, player);
            case VOTING -> tickVoting(client, player);
        }
    }

    private void tickIdle(Minecraft client, LocalPlayer player) {
        boolean hasVotingItem = isVotingItem(player.getInventory().getItem(VOTING_HOTBAR_SLOT));

        if (!hasVotingItem) {
            // The voting item disappearing means the round started or we left the lobby
            if (attemptedThisRound || voteConfirmed) {
                reset("voting item gone");
            }
            return;
        }

        if (attemptedThisRound || voteConfirmed) return;
        if (client.screen != null) return;

        ModConfig.AutoVoteConfig config = ConfigManager.getInstance().getConfig().autoVote;
        if (!config.enabled) return;
        votes = GameVotes.forGame(CubeCraftManager.getInstance().getCurrentGame(), config);
        if (votes.isEmpty()) return; // no votable game, or every category set to NONE

        Debug.log("AutoVote: voting item detected for {}, arming", CubeCraftManager.getInstance().getCurrentGame());
        // Select the slot now so the carried-item sync reaches the server before the use packet
        player.getInventory().setSelectedSlot(VOTING_HOTBAR_SLOT);
        delayTicks = ARM_DELAY_TICKS;
        setState(State.ARMED);
    }

    private void tickArmed(Minecraft client, LocalPlayer player) {
        if (client.screen != null || !isVotingItem(player.getInventory().getItem(VOTING_HOTBAR_SLOT))) {
            setState(State.IDLE);
            return;
        }
        if (--delayTicks > 0) return;

        useVotingItem(client, player);
        attemptedThisRound = true;
        voteIndex = 0;
        Debug.log("AutoVote: used voting item, waiting for first menu");
        setState(votes.get(0).hasSubmenu() ? State.OPENING_MAIN : State.OPENING_SUB);
    }

    /**
     * Uses the held voting item the way {@code Minecraft#startUseItem} does for a manual
     * right click: the targeted block first, since the server may only react to that packet.
     */
    private void useVotingItem(Minecraft client, LocalPlayer player) {
        player.getInventory().setSelectedSlot(VOTING_HOTBAR_SLOT);
        lastContainerId = player.containerMenu.containerId;

        InteractionResult result = InteractionResult.PASS;
        if (client.hitResult instanceof BlockHitResult blockHit && client.hitResult.getType() == HitResult.Type.BLOCK) {
            result = client.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, blockHit);
        }
        if (!result.consumesAction()) {
            result = client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        }
        if (result.consumesAction()) {
            player.swing(InteractionHand.MAIN_HAND);
        }
    }

    /** The first use can be ignored (e.g. while still spawning in), so retry every second. */
    private boolean retryUseIfNoMenu(Minecraft client, LocalPlayer player) {
        if (client.screen != null) return false;
        if (stateTicks % USE_RETRY_TICKS != 0) return false;
        if (!isVotingItem(player.getInventory().getItem(VOTING_HOTBAR_SLOT))) return false;

        Debug.log("AutoVote: no menu after {} ticks, using voting item again", stateTicks);
        useVotingItem(client, player);
        return true;
    }

    private void tickOpeningMain(Minecraft client, LocalPlayer player) {
        if (retryUseIfNoMenu(client, player)) return;

        VotePair vote = votes.get(voteIndex);
        String submenuTitle = vote.submenuTitle().toLowerCase(Locale.ROOT);
        ChestMenu menu = openMenu(client, title -> title.contains("voting") && !title.contains(submenuTitle), true);
        if (menu == null || !isPopulated(menu, vote.categorySlot())) return; // retry next tick

        clickSlot(client, player, menu, vote.categorySlot());
        lastContainerId = menu.containerId;
        Debug.log("AutoVote: clicked category slot {} ({})", vote.categorySlot(), vote.submenuTitle());
        setState(State.OPENING_SUB);
    }

    private void tickOpeningSub(Minecraft client, LocalPlayer player) {
        VotePair vote = votes.get(voteIndex);
        // Entry state for games without a category menu, so the use may need retrying here too
        if (!vote.hasSubmenu() && retryUseIfNoMenu(client, player)) return;

        ChestMenu menu = openMenu(client, title -> title.contains(vote.submenuTitle().toLowerCase(Locale.ROOT)), true);
        if (menu == null || !isPopulated(menu, vote.voteSlot())) return; // retry next tick

        clickSlot(client, player, menu, vote.voteSlot());
        Debug.log("AutoVote: voted slot {} in '{}'", vote.voteSlot(), vote.submenuTitle());
        delayTicks = RETURN_DELAY_TICKS;
        setState(State.VOTING);
    }

    private void tickVoting(Minecraft client, LocalPlayer player) {
        if (--delayTicks > 0) return;

        VotePair vote = votes.get(voteIndex);
        ChestMenu menu = openMenu(client, title -> title.contains(vote.submenuTitle().toLowerCase(Locale.ROOT)), false);
        if (menu == null) return;

        voteIndex++;
        if (voteIndex < votes.size()) {
            if (vote.hasSubmenu()) {
                clickSlot(client, player, menu, GameVotes.RETURN_SLOT);
                lastContainerId = menu.containerId;
            }
            setState(votes.get(voteIndex).hasSubmenu() ? State.OPENING_MAIN : State.OPENING_SUB);
        } else {
            player.closeContainer();
            Debug.log("AutoVote: all votes cast");
            setState(State.IDLE);
        }
    }

    private void onScreenInit(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
        if (state == State.IDLE) return;
        Debug.log("AutoVote: screen opened in state {}: '{}'", state, screen.getTitle().getString());
    }

    private void onGameMessage(Component message, boolean overlay) {
        if (overlay) return;
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || !CubeCraftManager.getInstance().isOnCubeCraft()) return;

        String text = message.getString();

        Matcher voteMatcher = VOTE_CONFIRMED_PATTERN.matcher(text);
        if (voteMatcher.find()) {
            if (voteMatcher.group(1).equalsIgnoreCase(client.player.getGameProfile().name())) {
                Debug.log("AutoVote: own vote confirmed in chat");
                voteConfirmed = true;
            }
            return;
        }

        if (!voteConfirmed && state == State.IDLE && GAME_STARTING_PATTERN.matcher(text).find()) {
            Debug.log("AutoVote: game starting without confirmed vote, allowing retry");
            attemptedThisRound = false;
        }
    }

    private ChestMenu openMenu(Minecraft client, Predicate<String> titleMatcher, boolean requireNewContainer) {
        if (!(client.screen instanceof ContainerScreen screen)) return null;
        ChestMenu menu = screen.getMenu();
        if (requireNewContainer && menu.containerId == lastContainerId) return null;
        String title = screen.getTitle().getString().toLowerCase(Locale.ROOT);
        if (!titleMatcher.test(title)) return null;
        return menu.slots.size() >= MIN_MENU_SLOTS ? menu : null;
    }

    private boolean isPopulated(ChestMenu menu, int slot) {
        return slot < menu.slots.size() && !menu.slots.get(slot).getItem().isEmpty();
    }

    private void clickSlot(Minecraft client, LocalPlayer player, ChestMenu menu, int slot) {
        client.gameMode.handleInventoryMouseClick(menu.containerId, slot, 0, ClickType.PICKUP, player);
    }

    private boolean isVotingItem(ItemStack stack) {
        return !stack.isEmpty() && VOTING_ITEM_NAME.equals(stack.getHoverName().getString());
    }

    private void setState(State newState) {
        this.state = newState;
        this.stateTicks = 0;
    }

    private void reset(String reason) {
        Debug.log("AutoVote: reset ({})", reason);
        setState(State.IDLE);
        delayTicks = 0;
        voteIndex = 0;
        votes = List.of();
        lastContainerId = -1;
        attemptedThisRound = false;
        voteConfirmed = false;
    }
}
