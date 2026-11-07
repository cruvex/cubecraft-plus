package com.cruvex.cubecraftplus.autovote;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Casts every configured vote in one pass through the voting menus, moving on only when the server has answered. */
final class VoteRun {

    private static final int MIN_STEP_TIMEOUT_TICKS = 20;
    private static final int TIMEOUT_ROUND_TRIPS = 4;
    // The tab list ping is still 0 right after joining, so the timeout also grows with the slowest step seen
    private static final int TIMEOUT_SLOWEST_STEPS = 3;
    private static final int RESYNC_TICKS = 10;

    private enum Step {
        OPEN_CATEGORY_LIST("open the category list"),
        OPEN_VOTE_MENU("open the vote menu"),
        CONFIRM_VOTE("see the vote land"),
        FINISHED("");

        private final String waitingTo;

        Step(String waitingTo) {
            this.waitingTo = waitingTo;
        }
    }

    private static final String RATE_LIMITED = "You are voting too fast! Please slow down.";
    private static final int RATE_LIMIT_RETRIES = 2;
    private static final int RATE_LIMIT_BACKOFF_TICKS = 20;
    private static final String VOTED_FOR = " voted for ";
    private static final String CATEGORY_CLOSED = "The votes for this category have been closed.";

    private final List<GameVotes.VotePair> votes;
    private final int hotbarSlot;
    private final boolean silent;
    private final List<String> failures = new ArrayList<>();
    private final List<String> closed = new ArrayList<>();
    private final List<GameVotes.VotePair> retryable = new ArrayList<>();
    private int voted;
    private int alreadyVoted;
    private boolean stoppedByPlayer;
    private boolean stopped;
    private int index;
    private long startedAt;
    private int closesBefore;
    private int previousSlot = -1;

    private int stepTimeoutTicks = MIN_STEP_TIMEOUT_TICKS;
    private long slowestStepMs;
    private Step step = Step.FINISHED;
    private int stepTicks;
    private long stepStartedAt;
    // Confirmed by chat, waiting for the menu's resync so the next click is not on stale state
    private boolean awaitingResync;
    private int resyncTicks;
    // Only a menu that opened after we acted counts as the answer
    private int opensBefore;
    private boolean retried;
    private int voteContainerId;
    private String optionName = "";
    private int rateLimitRetriesLeft;
    private int retryInTicks;

    /** A vote that was already there counts as landed. */
    record Result(int total, int landed, List<String> closed, List<GameVotes.VotePair> retryable,
                  boolean stoppedByPlayer) {
    }

    VoteRun(List<GameVotes.VotePair> votes, int hotbarSlot, boolean silent) {
        this.votes = List.copyOf(votes);
        this.hotbarSlot = hotbarSlot;
        this.silent = silent;
    }

    boolean hidesMenus() {
        return silent && step != Step.FINISHED;
    }

    /** A silent run that stopped early can still get a late answer to its last item use or click. */
    boolean mayGetLateMenu() {
        return silent && stopped;
    }

    Result result() {
        return new Result(votes.size(), voted + alreadyVoted, List.copyOf(closed), List.copyOf(retryable),
                stoppedByPlayer);
    }

    void start() {
        startedAt = System.currentTimeMillis();
        closesBefore = MenuTracker.getInstance().clientCloses();
        LocalPlayer player = Minecraft.getInstance().player;
        previousSlot = player == null ? -1 : player.getInventory().getSelectedSlot();
        int ping = ping();
        stepTimeoutTicks = Math.max(MIN_STEP_TIMEOUT_TICKS, (int) Math.ceil(ping * TIMEOUT_ROUND_TRIPS / 50.0));
        AutoVoteManager.log("run: voting in {} categories, {} (ping {}ms, step timeout {}ms)",
                votes.size(), silent ? "silently" : "visibly", ping, stepTimeoutTicks * 50);
        beginVote();
        openVoting();
    }

    /** Returns whether the run is still going. */
    boolean tick() {
        if (step == Step.FINISHED) return false;
        if (!stoppedByPlayer && MenuTracker.getInstance().clientCloses() > closesBefore) {
            stoppedByPlayer = true;
            // A vote already clicked can still land, so wait for its chat line
            if (step != Step.CONFIRM_VOTE) {
                stop("the menu was closed by the player");
                return false;
            }
            AutoVoteManager.log("run: menu closed by the player, waiting for the last vote to confirm");
        }
        if (++stepTicks > stepTimeoutTicks) {
            onTimeout();
            return step != Step.FINISHED;
        }

        VoteMenuView menu = MenuTracker.getInstance().view();
        switch (step) {
            case OPEN_CATEGORY_LIST -> tickCategoryList(menu);
            case OPEN_VOTE_MENU -> tickVoteMenu(menu);
            case CONFIRM_VOTE -> tickConfirm(menu);
            case FINISHED -> {
            }
        }
        return step != Step.FINISHED;
    }

    void onChat(String text) {
        if (text.equals(CATEGORY_CLOSED) && (step == Step.OPEN_VOTE_MENU || step == Step.CONFIRM_VOTE)) {
            closed.add(current().submenuTitle());
            AutoVoteManager.log("run: '{}' is closed for voting, skipping it", current().submenuTitle());
            nextVote();
            return;
        }
        if (step != Step.CONFIRM_VOTE || awaitingResync) return;

        if (text.equals(RATE_LIMITED)) {
            if (rateLimitRetriesLeft-- <= 0) {
                failVote("the server kept saying we vote too fast");
                return;
            }
            AutoVoteManager.log("run: server says we vote too fast, clicking again in {} ticks", RATE_LIMIT_BACKOFF_TICKS);
            retryInTicks = RATE_LIMIT_BACKOFF_TICKS;
            // The backoff does not count against the step's timeout
            stepTicks = -RATE_LIMIT_BACKOFF_TICKS;
        } else if (isOwnVote(text)) {
            voteLanded("chat");
        }
    }

    void abandon(String reason) {
        if (step != Step.FINISHED) {
            stop("abandoned while waiting to " + step.waitingTo + " (" + reason + ")");
        }
    }

    private void tickCategoryList(@Nullable VoteMenuView menu) {
        GameVotes.VotePair vote = current();
        if (!isNew(menu) || !menu.isCategoryList() || !menu.hasItem(vote.categorySlot())) return;

        noteResponse();
        AutoVoteManager.log("run: category list open after {}ms, clicking slot {} ('{}')",
                elapsed(), vote.categorySlot(), menu.name(vote.categorySlot()));
        opensBefore = MenuTracker.getInstance().opens();
        if (click(menu, vote.categorySlot())) {
            setStep(Step.OPEN_VOTE_MENU);
        }
    }

    private void tickVoteMenu(@Nullable VoteMenuView menu) {
        GameVotes.VotePair vote = current();
        if (!isNew(menu) || !menu.isVoteMenu(vote.submenuTitle(), vote.hasSubmenu())) return;

        int slot = vote.voteSlot();
        voteContainerId = menu.containerId();
        optionName = menu.name(slot);
        if (!menu.isVoteOption(slot)) {
            failVote("slot " + slot + " is '" + optionName + "', not a vote option");
            return;
        }
        if (menu.hasFoil(slot)) {
            alreadyVoted++;
            AutoVoteManager.log("run: already voted for '{}' in '{}'", optionName, vote.submenuTitle());
            nextVote();
            return;
        }

        noteResponse();
        AutoVoteManager.log("run: vote menu open after {}ms, clicking slot {} ('{}')", elapsed(), slot, optionName);
        if (click(menu, slot)) {
            setStep(Step.CONFIRM_VOTE);
        }
    }

    /** Waits for foil on the option; the chat line arrives through onChat. */
    private void tickConfirm(@Nullable VoteMenuView menu) {
        boolean menuOpen = menu != null && menu.containerId() == voteContainerId;
        if (awaitingResync) {
            if (!menuOpen || menu.hasFoil(current().voteSlot()) || --resyncTicks <= 0) {
                awaitingResync = false;
                nextVote();
            }
            return;
        }
        if (menuOpen && menu.hasFoil(current().voteSlot())) {
            voteLanded("foil");
            return;
        }

        if (retryInTicks > 0 && --retryInTicks == 0) {
            if (!menuOpen) {
                failVote("the vote menu closed before the vote could be retried");
                return;
            }
            AutoVoteManager.log("run: clicking slot {} again", current().voteSlot());
            click(menu, current().voteSlot());
        }
    }

    /** Retries the step once per vote, and gives up the second time. */
    private void onTimeout() {
        String waited = "timed out after " + elapsed() + "ms waiting to " + step.waitingTo;
        if (stoppedByPlayer) {
            stop("the menu was closed by the player, and the last vote never confirmed");
            return;
        }
        if (retried) {
            if (step == Step.OPEN_CATEGORY_LIST) {
                stop(waited);
            } else {
                failVote(waited);
            }
            return;
        }

        retried = true;
        AutoVoteManager.log("run: {}, trying once more", waited);
        VoteMenuView menu = MenuTracker.getInstance().view();
        GameVotes.VotePair vote = current();
        switch (step) {
            case OPEN_CATEGORY_LIST -> openVoting();
            case OPEN_VOTE_MENU -> {
                if (vote.hasSubmenu() && menu != null && menu.isCategoryList()) {
                    opensBefore = MenuTracker.getInstance().opens();
                    click(menu, vote.categorySlot());
                    setStep(Step.OPEN_VOTE_MENU);
                } else {
                    openVoting();
                }
            }
            case CONFIRM_VOTE -> {
                if (menu == null || menu.containerId() != voteContainerId) {
                    failVote("the vote menu closed before the vote showed");
                    return;
                }
                click(menu, vote.voteSlot());
                setStep(Step.CONFIRM_VOTE);
            }
            case FINISHED -> {
            }
        }
    }

    private void voteLanded(String confirmedBy) {
        voted++;
        // A rate limit retry waits on purpose, which says nothing about the server's speed
        if (rateLimitRetriesLeft == RATE_LIMIT_RETRIES) {
            noteResponse();
        }
        AutoVoteManager.log("run: voted for '{}' in '{}', confirmed by {} after {}ms",
                optionName, current().submenuTitle(), confirmedBy, elapsed());

        VoteMenuView menu = MenuTracker.getInstance().view();
        if (menu != null && menu.containerId() == voteContainerId && !menu.hasFoil(current().voteSlot())) {
            awaitingResync = true;
            resyncTicks = RESYNC_TICKS;
            stepTicks = 0;
            return;
        }
        nextVote();
    }

    private void failVote(String reason) {
        failures.add(current().submenuTitle() + " (" + reason + ")");
        retryable.add(current());
        AutoVoteManager.log("run: '{}' failed, {}", current().submenuTitle(), reason);
        nextVote();
    }

    private void nextVote() {
        index++;
        if (index >= votes.size()) {
            finish();
            return;
        }
        if (stoppedByPlayer) {
            stop("the menu was closed by the player");
            return;
        }

        beginVote();
        GameVotes.VotePair next = current();
        VoteMenuView menu = MenuTracker.getInstance().view();
        if (next.hasSubmenu() && menu != null && menu.isCategoryList() && menu.hasItem(next.categorySlot())) {
            AutoVoteManager.log("run: category list still open, clicking slot {} ('{}')",
                    next.categorySlot(), menu.name(next.categorySlot()));
            opensBefore = MenuTracker.getInstance().opens();
            if (click(menu, next.categorySlot())) {
                setStep(Step.OPEN_VOTE_MENU);
            }
        } else if (next.hasSubmenu() && menu != null && menu.isSubmenu()) {
            AutoVoteManager.log("run: back to the category list for '{}'", next.submenuTitle());
            opensBefore = MenuTracker.getInstance().opens();
            if (click(menu, VoteMenuView.NAV_SLOT)) {
                setStep(Step.OPEN_CATEGORY_LIST);
            }
        } else {
            openVoting();
        }
    }

    private void beginVote() {
        awaitingResync = false;
        retried = false;
        rateLimitRetriesLeft = RATE_LIMIT_RETRIES;
        retryInTicks = 0;
        optionName = "";
    }

    private void openVoting() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null) {
            stop("no player to use the voting item with");
            return;
        }

        // Using the item on top of an open submenu nests the new list under it
        closeMenu();
        closesBefore = MenuTracker.getInstance().clientCloses();

        player.getInventory().setSelectedSlot(hotbarSlot);
        opensBefore = MenuTracker.getInstance().opens();
        client.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        AutoVoteManager.log("run: used the voting item for '{}'", current().submenuTitle());
        setStep(current().hasSubmenu() ? Step.OPEN_CATEGORY_LIST : Step.OPEN_VOTE_MENU);
    }

    private void finish() {
        step = Step.FINISHED;
        closeMenu();
        restoreSelectedSlot();
        AutoVoteManager.log("run: finished, {}", summary());
    }

    /** Ends the run, leaving the current vote and the ones after it for a retry. */
    private void stop(String reason) {
        if (step != Step.FINISHED) {
            retryable.addAll(votes.subList(Math.min(index, votes.size()), votes.size()));
        }
        stopped = true;
        step = Step.FINISHED;
        closeMenu();
        restoreSelectedSlot();
        AutoVoteManager.log("run: stopped, {}; {}", reason, summary());
    }

    private String summary() {
        String result = String.format(Locale.ROOT, "%d voted, %d already voted, %d closed, %d failed of %d in %dms",
                voted, alreadyVoted, closed.size(), failures.size(), votes.size(),
                System.currentTimeMillis() - startedAt);
        if (!closed.isEmpty()) {
            result += "; closed: " + String.join(", ", closed);
        }
        return failures.isEmpty() ? result : result + "; failed: " + String.join(", ", failures);
    }

    /** "<name> voted for <option> <category>. N votes", with possible rank decoration around the name. */
    private boolean isOwnVote(String text) {
        LocalPlayer player = Minecraft.getInstance().player;
        int at = text.indexOf(VOTED_FOR);
        if (player == null || at < 0 || !text.startsWith(optionName + " ", at + VOTED_FOR.length())) return false;

        return Arrays.asList(text.substring(0, at).split("[^A-Za-z0-9_]+")).contains(player.getGameProfile().name());
    }

    private boolean isNew(@Nullable VoteMenuView menu) {
        return menu != null && menu.openCount() > opensBefore;
    }

    /** Sends our own click for a hidden menu, and clicks through the screen otherwise. */
    private boolean click(VoteMenuView menu, int slot) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        ClientPacketListener connection = client.getConnection();
        if (player == null || client.gameMode == null || connection == null) {
            stop("no player to click with");
            return false;
        }
        if (MenuTracker.getInstance().isHidden(menu.containerId())) {
            connection.send(SilentClick.build(menu, slot, connection));
            return true;
        }
        if (player.containerMenu.containerId != menu.containerId()) {
            stop("the screen shows menu " + player.containerMenu.containerId
                    + " but the server has " + menu.containerId() + " open");
            return false;
        }

        client.gameMode.handleContainerInput(menu.containerId(), slot, 0, ContainerInput.PICKUP, player);
        return true;
    }

    /** A hidden menu gets our own close packet, so the player's screen is left alone. */
    private void closeMenu() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        ClientPacketListener connection = client.getConnection();
        VoteMenuView menu = MenuTracker.getInstance().view();
        if (player == null || connection == null || menu == null) return;

        if (MenuTracker.getInstance().isHidden(menu.containerId())) {
            connection.send(new ServerboundContainerClosePacket(menu.containerId()));
        } else if (player.containerMenu.containerId == menu.containerId()) {
            player.closeContainer();
        }
    }

    /** Leaves the slot alone when the player picked another one meanwhile. */
    private void restoreSelectedSlot() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && previousSlot >= 0 && player.getInventory().getSelectedSlot() == hotbarSlot) {
            player.getInventory().setSelectedSlot(previousSlot);
        }
    }

    private static int ping() {
        Minecraft client = Minecraft.getInstance();
        ClientPacketListener connection = client.getConnection();
        if (connection == null || client.player == null) return 0;

        PlayerInfo info = connection.getPlayerInfo(client.player.getUUID());
        return info == null ? 0 : info.getLatency();
    }

    private GameVotes.VotePair current() {
        return votes.get(Math.min(index, votes.size() - 1));
    }

    private void setStep(Step next) {
        step = next;
        stepTicks = 0;
        stepStartedAt = System.currentTimeMillis();
    }

    private void noteResponse() {
        long took = elapsed();
        if (took <= slowestStepMs) return;

        slowestStepMs = took;
        int ticks = (int) Math.ceil(took * TIMEOUT_SLOWEST_STEPS / 50.0);
        if (ticks > stepTimeoutTicks) {
            stepTimeoutTicks = ticks;
            AutoVoteManager.log("run: a step took {}ms, step timeout now {}ms", took, ticks * 50);
        }
    }

    private long elapsed() {
        return System.currentTimeMillis() - stepStartedAt;
    }
}
