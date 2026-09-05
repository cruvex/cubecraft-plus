package com.cruvex.cubecraftplus.managers;

import com.cruvex.cubecraftplus.events.CubeEvents;
import com.cruvex.cubecraftplus.events.PlayerEvents;
import com.cruvex.cubecraftplus.events.ScoreboardEvents;
import com.cruvex.cubecraftplus.external.CubepanionAPI;
import com.cruvex.cubecraftplus.model.Game;
import com.cruvex.cubecraftplus.model.GameFlag;
import com.cruvex.cubecraftplus.util.Debug;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tracks which game the client is in and where both it and the game are in their lives.
 *
 * <p>The signals behind each rule, and the section numbers referenced below, are in
 * {@code docs/game-timeline-signals.md}.
 */
public class GameManager {

    private static GameManager instance;

    private static final String GAME_START_MESSAGE = "Let the games begin!";
    /** Sent when the client's own run ends, by elimination or by winning. */
    private static final String CLIENT_DONE_MESSAGE = "Thank you for playing";
    private static final String WIN_MESSAGE = "won the game";
    /** The winner line addressed to the client, which names nobody. */
    private static final String OWN_WIN_SUBJECT = "Congratulations, you";
    /** The winner line of the broadcast. */
    private static final Pattern WINNER_PATTERN =
            Pattern.compile("^(.+) won the game!$", Pattern.MULTILINE);

    /** Packets this close to a connect belong to the handover, not to play (§4.1). */
    private static final int HANDOVER_TICKS = 1;
    /** A death that has not respawned within this is final. */
    private static final int RESPAWN_TIMEOUT_TICKS = 160;
    /** A moment that has not happened. */
    private static final long NEVER = Long.MIN_VALUE;

    /** Where the client is in the life of one game instance. */
    public enum Phase {
        /** Not in a game. */
        NONE,
        /** Joined, waiting for the game to start. */
        PRE_GAME,
        /** The game is running and the client is playing. */
        IN_GAME,
        /** The client is out, but the game is still running and it is watching. */
        SPECTATING,
        /** The game finished. */
        ENDED
    }

    private Game currentGame;
    private Phase phase = Phase.NONE;

    // Signals seen since the last decision, client thread only
    private boolean sawConnect;
    private boolean sawRespawn;
    // -1 while no decision is pending
    private int decisionDelay = -1;

    /** Client ticks since init. */
    private long tick;
    private long connectTick = NEVER;
    /** Last respawn that kept nothing. */
    private long deathRespawnTick = NEVER;
    /** Set while waiting to see whether a death respawns. */
    private long pendingDeathTick = NEVER;

    public static GameManager getInstance() {
        if (instance == null) {
            instance = new GameManager();
        }
        return instance;
    }

    public void init() {
        ScoreboardEvents.SET_DISPLAY_OBJECTIVE.register(this::onSetDisplayObjective);
        PlayerEvents.RESPAWN.register(this::onRespawn);
        PlayerEvents.GAME_MODE_CHANGE.register(this::onGameModeChange);
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        ClientReceiveMessageEvents.GAME.register(this::onGameMessage);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onServerJoin());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset("disconnected"));
    }

    private void onServerJoin() {
        connectTick = tick;

        if (!CubeCraftManager.getInstance().isOnCubeCraft()) {
            reset("left CubeCraft");
            return;
        }

        // The rebuild that consumes this can land a tick or more later
        sawConnect = true;
    }

    private void onRespawn(byte dataToKeep) {
        sawRespawn = true;
        if (dataToKeep == 0) {
            deathRespawnTick = tick;
        }
    }

    private void onSetDisplayObjective(DisplaySlot slot, String objectiveName) {
        if (!CubeCraftManager.getInstance().isOnCubeCraft()) return;

        if (slot == DisplaySlot.SIDEBAR) {
            // The respawn that classifies this rebuild follows it by a few milliseconds
            decisionDelay = 1;
        } else if (slot == DisplaySlot.LIST && since(connectTick) > HANDOVER_TICKS) {
            // The tab list kill counter is created when a game starts (§3.1)
            startGame(readSidebarGame(), "kill counter added");
        }
    }

    private void onEndTick(Minecraft client) {
        tick++;

        if (since(pendingDeathTick) > RESPAWN_TIMEOUT_TICKS) {
            pendingDeathTick = NEVER;
            eliminate("died without respawning");
        }

        if (decisionDelay < 0) {
            // A respawn only counts towards the rebuild it arrives with
            sawRespawn = false;
            return;
        }
        if (decisionDelay-- > 0) return;

        decide();
    }

    private void decide() {
        decisionDelay = -1;
        boolean connected = sawConnect;
        boolean respawned = sawRespawn;
        sawConnect = false;
        sawRespawn = false;

        Game game = readSidebarGame();

        // A server switch respawns the client too, so the connection is judged first
        if (connected) {
            joinGame(game, "server switch");
        } else if (respawned && phase == Phase.PRE_GAME) {
            refineGame(game);
        } else if (respawned) {
            joinGame(game, "handed out in place");
        } else {
            startGame(game, "sidebar rebuilt");
        }
    }

    /** Chat announces the game ending, and is a fallback for its start. */
    private void onGameMessage(Component message, boolean overlay) {
        if (overlay || !CubeCraftManager.getInstance().isOnCubeCraft()) return;

        String text = message.getString().trim();
        if (text.equals(GAME_START_MESSAGE)) {
            startGame(readSidebarGame(), "chat");
        } else if (text.contains(WIN_MESSAGE)) {
            endGame(parseWinner(text));
        } else if (text.startsWith(CLIENT_DONE_MESSAGE)) {
            // Backstop for a run that ended without a death or a win
            eliminate("run ended");
        }
    }

    /** Spectator means the client died; leaving spectator means it respawned (§4). */
    private void onGameModeChange(GameType mode) {
        if (!CubeCraftManager.getInstance().isOnCubeCraft()) return;

        if (mode == GameType.CREATIVE) {
            // Creative is never handed out on CubeCraft
            Debug.log("Unexpected creative game mode, game timeline may be wrong");
            return;
        }

        if (mode == GameType.SPECTATOR) {
            onSpectator();
        } else if (pendingDeathTick != NEVER) {
            pendingDeathTick = NEVER;
            Debug.log("Respawned into {}", currentGame == null ? "none" : currentGame.name());
        }
    }

    private void onSpectator() {
        // A handover passes through spectator without a death (§2.5)
        if (since(connectTick) <= HANDOVER_TICKS) return;
        if (phase != Phase.IN_GAME || currentGame == null) return;

        // A survivable death arrives with a respawn that kept nothing (§4.4)
        if (since(deathRespawnTick) <= HANDOVER_TICKS) {
            pendingDeathTick = tick;
            Debug.log("Died in {}", currentGame.name());
            CubeEvents.CLIENT_DEATH.invoker().onClientDeath(currentGame);
        } else {
            eliminate("moved to spectator");
        }
    }

    /** A new instance of a game: another server, or the next round on this one. */
    private void joinGame(@Nullable Game game, String reason) {
        leaveGame();

        currentGame = game;
        phase = Phase.PRE_GAME;

        if (game == null) {
            // The next boundary names it
            Debug.log("Joined a game ({}), name not readable yet", reason);
            return;
        }

        Debug.log("Joined {} ({})", game.name(), reason);
        CubeEvents.GAME_JOIN.invoker().onGameJoin(game);

        // No cages and no pre-game lobby: arriving is the start
        if (game.hasFlag(GameFlag.NO_PRE_GAME_STATE)) {
            startGame(game, "no pre-game state");
        } else if (isGameRunning()) {
            joinedInProgress();
        }
    }

    /** Pulled into a game that had already started, as a party does to its members (§2.5). */
    private void joinedInProgress() {
        if (currentGame == null || currentGame.isLobby()) return;

        // A game in progress can only ever be watched, never joined into play
        phase = Phase.SPECTATING;
        Debug.log("Joined {} already in progress, as a spectator", currentGame.name());
    }

    /** Same instance under a more specific name, e.g. " EggWars" becoming " Team EggWars". */
    private void refineGame(@Nullable Game game) {
        if (game == null || Objects.equals(game, currentGame)) return;

        Debug.log("Game name refined from {} to {}",
                currentGame == null ? "none" : currentGame.name(), game.name());
        currentGame = game;
        CubeEvents.GAME_JOIN.invoker().onGameJoin(game);
    }

    private void startGame(@Nullable Game game, String reason) {
        if (phase != Phase.PRE_GAME) return;
        // The lobby is a game to the API, but it never starts
        if (currentGame != null && currentGame.isLobby()) return;

        if (game != null) {
            currentGame = game;
        }
        if (currentGame == null) return;

        phase = Phase.IN_GAME;
        Debug.log("{} started ({})", currentGame.name(), reason);
        CubeEvents.GAME_START.invoker().onGameStart(currentGame);
    }

    /** The client is out of the game for good, whether or not the game itself is over. */
    private void eliminate(String reason) {
        if (phase != Phase.IN_GAME || currentGame == null) return;

        phase = Phase.SPECTATING;
        pendingDeathTick = NEVER;
        Debug.log("Eliminated from {} ({})", currentGame.name(), reason);
        CubeEvents.CLIENT_ELIMINATED.invoker().onClientEliminated(currentGame);
    }

    /** The game finished, as announced by the winner broadcast (§6). */
    private void endGame(@Nullable String winner) {
        if (currentGame == null || currentGame.isLobby()) return;
        if (phase != Phase.IN_GAME && phase != Phase.SPECTATING) return;

        phase = Phase.ENDED;
        pendingDeathTick = NEVER;
        Debug.log("{} ended, won by {}", currentGame.name(), winner == null ? "unknown" : winner);
        CubeEvents.GAME_END.invoker().onGameEnd(currentGame, winner);
    }

    /** Drop the current game without ending it. */
    private void leaveGame() {
        if (currentGame != null && !currentGame.isLobby()
                && phase != Phase.NONE && phase != Phase.ENDED) {
            Debug.log("Left {} before it finished", currentGame.name());
        }

        currentGame = null;
        phase = Phase.NONE;
        pendingDeathTick = NEVER;
    }

    /** Ticks since a moment, or {@link Long#MAX_VALUE} when it never happened. */
    private long since(long moment) {
        return moment == NEVER ? Long.MAX_VALUE : tick - moment;
    }

    /** The winner the broadcast names: a player, or a team colour. */
    private static @Nullable String parseWinner(String text) {
        Matcher matcher = WINNER_PATTERN.matcher(text);
        while (matcher.find()) {
            String winner = matcher.group(1).trim();
            if (!winner.equals(OWN_WIN_SUBJECT)) return winner;
        }
        return null;
    }

    /** The kill counter in the tab list exists only while a game is running (§3.1). */
    private static boolean isGameRunning() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return false;

        return client.level.getScoreboard().getDisplayObjective(DisplaySlot.LIST) != null;
    }

    /** The game the sidebar names right now, or null while it names nothing recognisable. */
    private @Nullable Game readSidebarGame() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return null;

        Scoreboard scoreboard = client.level.getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) return null;

        String title = sidebar.getDisplayName().getString();
        // Strip the colours and decoration CubeCraft wraps the sidebar title in
        String cleaned = title.replaceAll("[^a-zA-Z .]", "").trim();
        if (cleaned.isEmpty() || !cleaned.matches("[a-zA-Z ]+")) {
            Debug.log("Sidebar title is not a game name: {}", title);
            return null;
        }

        Game game = CubepanionAPI.getInstance().tryGame(cleaned);
        if (game == null) {
            Debug.log("No game matches sidebar title: {}", cleaned);
        }
        return game;
    }

    public @Nullable Game getCurrentGame() {
        return currentGame;
    }

    public Phase getPhase() {
        return phase;
    }

    /** Playing right now, not waiting in a lobby or spectating. */
    public boolean isInGame() {
        return phase == Phase.IN_GAME;
    }

    /** Forget the game, without claiming it ended. */
    private void reset(String reason) {
        Debug.log("Reset ({})", reason);

        leaveGame();
        sawConnect = false;
        sawRespawn = false;
        decisionDelay = -1;
        connectTick = NEVER;
        deathRespawnTick = NEVER;
    }

    /** Compact one-line state for logs. */
    public String describeState() {
        return String.format(Locale.ROOT, "game=%s phase=%s",
                currentGame == null ? "none" : currentGame.name(),
                phase.name().toLowerCase(Locale.ROOT));
    }
}
