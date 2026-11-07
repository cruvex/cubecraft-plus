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

/**
 * One pass through the voting menus, casting every configured vote, that only moves on when the server has
 * answered. Visible runs click through the screens; silent ones take the menus over and send their own packets.
 */
final class VoteRun {

    /**
     * The slowest answer on EU servers was 378ms and on Asia 500ms, while team games leave about 10 seconds for
     * every vote; a high ping stretches it to four round trips.
     */
    private static final int MIN_STEP_TIMEOUT_TICKS = 20;
    private static final int TIMEOUT_ROUND_TRIPS = 4;
    /**
     * The tab list ping is still 0 a second after joining, so the run also learns from its own steps: one took
     * 799ms on a private server, against the usual 50-150ms.
     */
    private static final int TIMEOUT_SLOWEST_STEPS = 3;
    /** How long a vote confirmed by chat waits for the menu's own resync before moving on. */
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

    /** The server drops a vote with this line when one category gets several votes in a few seconds. */
    private static final String RATE_LIMITED = "You are voting too fast! Please slow down.";
    private static final int RATE_LIMIT_RETRIES = 2;
    private static final int RATE_LIMIT_BACKOFF_TICKS = 20;
    private static final String VOTED_FOR = " voted for ";
    /** Categories close at different times: SkyWars settles projectiles at "starting in 5 seconds". */
    private static final String CATEGORY_CLOSED = "The votes for this category have been closed.";

    private final List<GameVotes.VotePair> votes;
    private final int hotbarSlot;
    private final boolean silent;
    private final List<String> failures = new ArrayList<>();
    private final List<String> closed = new ArrayList<>();
    // Failed votes and the ones a stop left untried, which a later run can try again
    private final List<GameVotes.VotePair> retryable = new ArrayList<>();
    private int voted;
    private int alreadyVoted;
    private boolean stoppedByPlayer;
    private int index;
    private long startedAt;
    // Closes the player had sent before the run, so their Esc stops it
    private int closesBefore;
    private int previousSlot = -1;

    private int stepTimeoutTicks = MIN_STEP_TIMEOUT_TICKS;
    private long slowestStepMs;
    private Step step = Step.FINISHED;
    private int stepTicks;
    private long stepStartedAt;
    // Counted already through chat, waiting for the menu to catch up so the next click is not on stale state
    private boolean awaitingResync;
    private int resyncTicks;
    // Opens the tracker had seen when we acted, so only a menu that opened after counts as the answer
    private int opensBefore;
    // Each vote gets one retry after a timeout, on top of the rate limit retries
    private boolean retried;
    private int voteContainerId;
    private String optionName = "";
    private int rateLimitRetriesLeft;
    // Ticks until the vote is clicked again after a rate limit, 0 while no retry is pending
    private int retryInTicks;

    /** How a finished run went, for the toast and for retrying what did not land. */
    record Result(int total, int landed, List<String> closed, List<GameVotes.VotePair> retryable,
                  boolean stoppedByPlayer) {
    }

    VoteRun(List<GameVotes.VotePair> votes, int hotbarSlot, boolean silent) {
        this.votes = List.copyOf(votes);
        this.hotbarSlot = hotbarSlot;
        this.silent = silent;
    }

    /** Whether menus opened during this run should be taken over instead of shown. */
    boolean hidesMenus() {
        return silent && step != Step.FINISHED;
    }

    /** Counts a vote that was already there as landed: either way it is in. */
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
            // A vote already clicked can still land, and its chat line still comes, so wait for that one
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

    /** Server chat, which confirms a vote even when the menu closed before its foil arrived. */
    void onChat(String text) {
        // Answers a click on the category, or on an option in it
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
            // The wait for the retry does not count against the step's timeout
            stepTicks = -RATE_LIMIT_BACKOFF_TICKS;
        } else if (isOwnVote(text)) {
            voteLanded("chat");
        }
    }

    /** Ends the run early, for when the round it belonged to is over. */
    void abandon(String reason) {
        if (step != Step.FINISHED) {
            stop("abandoned while waiting to " + step.waitingTo + " (" + reason + ")");
        }
    }

    // Steps

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

    /** Waits for foil on the option, or for the chat line through onChat, whichever comes first. */
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

    /** Tries the step's action once more, the first time a vote times out, and gives up the second time. */
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

    // Moving between votes

    private void voteLanded(String confirmedBy) {
        voted++;
        // A rate limit retry waits on purpose, which says nothing about the server's speed
        if (rateLimitRetriesLeft == RATE_LIMIT_RETRIES) {
            noteResponse();
        }
        AutoVoteManager.log("run: voted for '{}' in '{}', confirmed by {} after {}ms",
                optionName, current().submenuTitle(), confirmedBy, elapsed());

        // Chat can beat the menu's resync; clicking Back before it lands sends a click on stale state
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

    /**
     * Clicks the next category straight away when the category list is still open, goes Back from a submenu, and
     * reopens voting with the item when neither is on screen.
     */
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

    /** Uses the voting item, the way a right click in the air does, closing whatever menu is open first. */
    private void openVoting() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null) {
            stop("no player to use the voting item with");
            return;
        }

        // An item use on top of an open submenu nests the new list under it, so leave it the way a player would
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

    /** Ends the run without trying the votes that are left, the current one included. */
    private void stop(String reason) {
        if (step != Step.FINISHED) {
            retryable.addAll(votes.subList(Math.min(index, votes.size()), votes.size()));
        }
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

    // Plumbing

    /** "<name> voted for <option> <category>. N votes", where the name can carry rank decoration on both sides. */
    private boolean isOwnVote(String text) {
        LocalPlayer player = Minecraft.getInstance().player;
        int at = text.indexOf(VOTED_FOR);
        if (player == null || at < 0 || !text.startsWith(optionName + " ", at + VOTED_FOR.length())) return false;

        return Arrays.asList(text.substring(0, at).split("[^A-Za-z0-9_]+")).contains(player.getGameProfile().name());
    }

    /** A menu that opened after we acted, as opposed to the one we acted in. */
    private boolean isNew(@Nullable VoteMenuView menu) {
        return menu != null && menu.openCount() > opensBefore;
    }

    /** Sends our own click for a hidden menu, and otherwise clicks through the screen the way the player would. */
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

    /**
     * Closes the menu we left open: a hidden one with a close packet of our own, which leaves whatever the player
     * has on screen alone, and a visible one through the screen.
     */
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

    /** Puts the hotbar back on the slot the player had, unless they picked another one meanwhile. */
    private void restoreSelectedSlot() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && previousSlot >= 0 && player.getInventory().getSelectedSlot() == hotbarSlot) {
            player.getInventory().setSelectedSlot(previousSlot);
        }
    }

    /** The ping the server reports for us in the tab list, or 0 before it has. */
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

    /** Stretches the timeout when the server answers slower than the minimum allows for. */
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
