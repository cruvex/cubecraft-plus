package com.cruvex.cubecraftplus.autovote;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * One pass through the voting menus that only moves on when the server has answered. For now it casts a
 * single vote, with the menus on screen.
 */
final class VoteRun {

    /** The slowest recorded answer was 378ms, and team games leave about 10 seconds for every vote. */
    private static final int STEP_TIMEOUT_TICKS = 20;

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

    private final GameVotes.VotePair vote;
    private final int hotbarSlot;

    private Step step = Step.FINISHED;
    private int stepTicks;
    private long stepStartedAt;
    // Opens the tracker had seen when we acted, so only a menu that opened after counts as the answer
    private int opensBefore;
    private int voteContainerId;
    private String optionName = "";
    private int retriesLeft = RATE_LIMIT_RETRIES;
    // Ticks until the vote is clicked again after a rate limit, 0 while no retry is pending
    private int retryInTicks;

    VoteRun(GameVotes.VotePair vote, int hotbarSlot) {
        this.vote = vote;
        this.hotbarSlot = hotbarSlot;
    }

    /** Uses the voting item, the way a right click in the air does. */
    void start(Minecraft client, LocalPlayer player) {
        player.getInventory().setSelectedSlot(hotbarSlot);
        opensBefore = MenuTracker.getInstance().opens();
        client.gameMode.useItem(player, InteractionHand.MAIN_HAND);

        AutoVoteManager.log("run: used the voting item, voting slot {} in '{}'", vote.voteSlot(), vote.submenuTitle());
        setStep(vote.hasSubmenu() ? Step.OPEN_CATEGORY_LIST : Step.OPEN_VOTE_MENU);
    }

    /** Returns whether the run is still going. */
    boolean tick() {
        if (++stepTicks > STEP_TIMEOUT_TICKS) {
            fail("timed out after " + elapsed() + "ms waiting to " + step.waitingTo);
            return false;
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
        if (step != Step.CONFIRM_VOTE) return;

        if (text.equals(RATE_LIMITED)) {
            if (retriesLeft-- <= 0) {
                fail("the server kept saying we vote too fast");
                return;
            }
            AutoVoteManager.log("run: server says we vote too fast, clicking again in {} ticks", RATE_LIMIT_BACKOFF_TICKS);
            retryInTicks = RATE_LIMIT_BACKOFF_TICKS;
            // The wait for the retry does not count against the step's timeout
            stepTicks = -RATE_LIMIT_BACKOFF_TICKS;
        } else if (isOwnVote(text)) {
            finish("voted for '" + optionName + "', confirmed by chat after " + elapsed() + "ms");
        }
    }

    /** Ends the run early, for when the round it belonged to is over. */
    void abandon(String reason) {
        if (step != Step.FINISHED) {
            fail("abandoned while waiting to " + step.waitingTo + " (" + reason + ")");
        }
    }

    private void tickCategoryList(@Nullable VoteMenuView menu) {
        if (!isNew(menu) || !menu.isCategoryList() || !menu.hasItem(vote.categorySlot())) return;

        AutoVoteManager.log("run: category list open after {}ms, clicking slot {} ('{}')",
                elapsed(), vote.categorySlot(), menu.name(vote.categorySlot()));
        opensBefore = MenuTracker.getInstance().opens();
        if (click(menu, vote.categorySlot())) {
            setStep(Step.OPEN_VOTE_MENU);
        }
    }

    private void tickVoteMenu(@Nullable VoteMenuView menu) {
        if (!isNew(menu) || !menu.isVoteMenu(vote.submenuTitle(), vote.hasSubmenu())) return;

        int slot = vote.voteSlot();
        if (!menu.isVoteOption(slot)) {
            fail("slot " + slot + " in '" + vote.submenuTitle() + "' is '" + menu.name(slot) + "', not a vote option");
            return;
        }
        if (menu.hasFoil(slot)) {
            finish("already voted for '" + menu.name(slot) + "', nothing to click");
            return;
        }

        AutoVoteManager.log("run: vote menu open after {}ms, clicking slot {} ('{}')", elapsed(), slot, menu.name(slot));
        voteContainerId = menu.containerId();
        optionName = menu.name(slot);
        if (click(menu, slot)) {
            setStep(Step.CONFIRM_VOTE);
        }
    }

    /** Waits for foil on the option, or for the chat line through onChat, whichever comes first. */
    private void tickConfirm(@Nullable VoteMenuView menu) {
        boolean menuOpen = menu != null && menu.containerId() == voteContainerId;
        if (menuOpen && menu.hasFoil(vote.voteSlot())) {
            finish("voted for '" + optionName + "', confirmed by foil after " + elapsed() + "ms");
            return;
        }

        if (retryInTicks > 0 && --retryInTicks == 0) {
            if (!menuOpen) {
                fail("the vote menu closed before the vote could be retried");
                return;
            }
            AutoVoteManager.log("run: clicking slot {} again", vote.voteSlot());
            click(menu, vote.voteSlot());
        }
    }

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

    /** Clicks through the screen, the way the player would. */
    private boolean click(VoteMenuView menu, int slot) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.gameMode == null) {
            fail("no player to click with");
            return false;
        }
        if (player.containerMenu.containerId != menu.containerId()) {
            fail("the screen shows menu " + player.containerMenu.containerId
                    + " but the server has " + menu.containerId() + " open");
            return false;
        }

        client.gameMode.handleContainerInput(menu.containerId(), slot, 0, ContainerInput.PICKUP, player);
        return true;
    }

    private void finish(String result) {
        AutoVoteManager.log("run: done, {}", result);
        step = Step.FINISHED;
    }

    private void fail(String reason) {
        AutoVoteManager.log("run: failed, {}", reason);
        step = Step.FINISHED;
    }

    private void setStep(Step next) {
        step = next;
        stepTicks = 0;
        stepStartedAt = System.currentTimeMillis();
    }

    private long elapsed() {
        return System.currentTimeMillis() - stepStartedAt;
    }
}
