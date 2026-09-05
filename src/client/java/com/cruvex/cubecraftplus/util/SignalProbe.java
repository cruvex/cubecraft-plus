package com.cruvex.cubecraftplus.util;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.events.CubeEvents;
import com.cruvex.cubecraftplus.external.CubepanionAPI;
import com.cruvex.cubecraftplus.managers.GameManager;
import com.cruvex.cubecraftplus.model.Game;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.CommonPlayerSpawnInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jetbrains.annotations.Nullable;
import org.slf4j.helpers.MessageFormatter;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Records every signal that could mark a game boundary, alongside what the mod made of it, to
 * {@code cubecraft-plus/debug/signals-*.log}. Observes only, and never writes to chat unless
 * {@code /ccp probe chat} asks. On by default in development, {@code /ccp probe on} elsewhere.
 */
public class SignalProbe {

    /** Below this a position packet is an ordinary correction, not a move between arenas. */
    private static final double TELEPORT_DISTANCE = 16.0;
    private static final int CHAT_LIMIT = 140;
    private static final int DETAIL_LIMIT = 90;
    /** How many of each noisy signal to keep after a boundary, where they are informative. */
    private static final int TEAM_BUDGET = 30;
    private static final int ACTION_BAR_BUDGET = 4;
    /** Players stream in and out constantly in a lobby; a line every few seconds is plenty. */
    private static final long PLAYER_COUNT_INTERVAL = 3000;

    /** Sidebar line CubeCraft puts the server id in, e.g. "05/08/26 (EU12B)". */
    private static final Pattern SERVER_ID_PATTERN = Pattern.compile("\\d{2}/\\d{2}/\\d{2} \\((.{2,10})\\)");
    /** Real rosters are colour-named teams; all-caps alone also matches the lobby's NPC team. */
    private static final Pattern ROSTER_TEAM_PATTERN = Pattern.compile(
            "(?:DARK_|LIGHT_)?(?:RED|BLUE|GREEN|YELLOW|AQUA|CYAN|PINK|MAGENTA|ORANGE|PURPLE"
                    + "|WHITE|BLACK|GRAY|GREY|LIME|BROWN|GOLD)");
    private static final Pattern COUNTDOWN_PATTERN =
            Pattern.compile(".{0,40} (?:is starting|will start) in \\d+ seconds?\\.?");
    /** Colours as chat writes them: title case and spaced. */
    private static final String CHAT_COLOUR =
            "(?:Dark |Light )?(?:Red|Blue|Green|Yellow|Aqua|Cyan|Pink|Magenta|Orange|Purple"
                    + "|White|Black|Gray|Grey|Lime|Brown|Gold)";
    /** Team assignment, e.g. "You have joined Light Blue." */
    private static final Pattern TEAM_JOIN_PATTERN =
            Pattern.compile("You have joined " + CHAT_COLOUR + "\\.");
    /** Party membership and party chat, which share the "You have joined" phrasing with teams. */
    private static final Pattern PARTY_PATTERN = Pattern.compile(
            "You have joined .+'s party!"
                    + "|You have received a party invite from .+"
                    + "|(?:Party chat is now enabled|Use /p chat to disable party chat)\\W*"
                    + "|Only the party owner can join games\\."
                    + "|The game you have joined has a team size too small for your party!.*"
                    + "|\\[Party] .*");

    private record ChatMarker(String type, Predicate<String> matches) {
    }

    /** Chat lines worth their own signal type; everything else is still logged as CHAT. */
    private static final List<ChatMarker> CHAT_MARKERS = List.of(
            new ChatMarker("CHAT_START", text -> text.equals("Let the games begin!")),
            new ChatMarker("CHAT_COUNTDOWN", text -> COUNTDOWN_PATTERN.matcher(text).matches()),
            new ChatMarker("CHAT_THANKS", text -> text.startsWith("Thank you for playing")),
            new ChatMarker("CHAT_WIN", text -> text.contains("won the game")),
            new ChatMarker("CHAT_PARTY", text -> PARTY_PATTERN.matcher(text).matches()),
            new ChatMarker("CHAT_TEAM", text -> TEAM_JOIN_PATTERN.matcher(text).matches()),
            new ChatMarker("CHAT_VOTE", text -> text.contains(" voted for ")),
            new ChatMarker("CHAT_ELIM", text -> text.contains("eliminated")),
            new ChatMarker("CHAT_RESPAWN", text -> text.startsWith("You are now invincible")),
            new ChatMarker("CHAT_WAITING", text -> text.contains("Waiting for") || text.contains("more player")));

    private static SignalProbe instance;

    /** Development runs are the ones started to look at this, so they get it for free. */
    private static volatile boolean enabled = FabricLoader.getInstance().isDevelopmentEnvironment();

    private final SignalLog log = new SignalLog(this::context);
    private final Map<String, Integer> counts = new LinkedHashMap<>();

    private boolean mirrorToChat;

    private int connections;
    private long connectedAt = -1;

    private String serverId = "";
    private String sidebar = "";
    private String actionBar = "";
    private @Nullable Vec3 position;
    private boolean sidebarDirty;
    private boolean rosterSeen;
    private int playerCount = -1;
    private long playerCountLoggedAt;

    private int teamBudget = TEAM_BUDGET;
    private int actionBarBudget = ACTION_BAR_BUDGET;

    public static SignalProbe getInstance() {
        if (instance == null) {
            instance = new SignalProbe();
        }
        return instance;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public void init() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> onConnect(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> onDisconnect());
        ClientReceiveMessageEvents.GAME.register(this::onGameMessage);
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> log.close());

        // What the live detection concluded, timestamped alongside the raw signals
        CubeEvents.CUBE_JOIN.register(() -> signal("CUBE_JOIN", ""));
        CubeEvents.GAME_JOIN.register(game -> signal("GAME_JOIN", "game={}", game.name()));
        CubeEvents.GAME_START.register(game -> signal("GAME_START", "game={}", game.name()));
        CubeEvents.CLIENT_DEATH.register(game -> signal("CLIENT_DEATH", "game={}", game.name()));
        CubeEvents.CLIENT_ELIMINATED.register(
                game -> signal("CLIENT_ELIMINATED", "game={}", game.name()));
        CubeEvents.GAME_END.register((game, winner) ->
                signal("GAME_END", "game={} winner={}", game.name(), winner == null ? "?" : winner));
    }

    // Control, from /ccp probe

    public void setEnabled(boolean value) {
        if (enabled == value) return;

        if (value) {
            enabled = true;
            signal("PROBE", "enabled");
        } else {
            signal("PROBE", "disabled");
            enabled = false;
            log.close();
        }
    }

    public void setMirrorToChat(boolean value) {
        mirrorToChat = value;
    }

    public boolean isMirroringToChat() {
        return mirrorToChat;
    }

    /** Operator's note in the log, for pinning what actually happened on screen to a time. */
    public void mark(String note) {
        signal("MARK", "{}", note);
    }

    public @Nullable Path logPath() {
        return log.path();
    }

    /** Signal name to times seen, in the order the session first saw them. */
    public synchronized Map<String, Integer> counts() {
        return new LinkedHashMap<>(counts);
    }

    /** The sidebar as it stands right now, also used for {@code /ccp probe sidebar}. */
    public String describeSidebar() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return "no world";

        Scoreboard scoreboard = client.level.getScoreboard();
        Objective objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return "no sidebar";

        String title = objective.getDisplayName().getString();
        // Rows carry newlines of their own, which would break the one-line-per-signal format
        String rows = scoreboard.listPlayerScores(objective).stream()
                .filter(entry -> !entry.isHidden())
                .sorted(Comparator.comparingInt(PlayerScoreEntry::value).reversed())
                .limit(20)
                .map(entry -> flatten(PlayerTeam
                        .formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName())
                        .getString()))
                .collect(Collectors.joining(" | "));

        return String.format(Locale.ROOT, "objective=%s title='%s' parsed=%s rows=[%s]",
                objective.getName(), flatten(title), parseGame(title), rows);
    }

    // Connection signals

    private void onConnect(Minecraft client) {
        if (!enabled) return;

        connections++;
        connectedAt = System.currentTimeMillis();
        resetPerServer();
        // Deliberately not gated on being on CubeCraft: leaving it is worth a line too
        signal("CONNECT", "server={} cubecraft={}", Util.getServerIp(client), Util.isOnCubeCraft(client));
    }

    private void onDisconnect() {
        if (!enabled) return;

        signal("DISCONNECT", "");
        connectedAt = -1;
        serverId = "";
        resetPerServer();
    }

    /** A full login: how the proxy hands the client to another game server. */
    public void onLogin(ClientboundLoginPacket packet) {
        if (!active()) return;

        resetPerServer();
        CommonPlayerSpawnInfo spawn = packet.commonPlayerSpawnInfo();
        signal("LOGIN", "dimension={} gamemode={} entityId={} flat={}",
                spawn.dimension().identifier(), spawn.gameType(), packet.playerId(), spawn.isFlat());
    }

    /** In-place moves (lobby to cage, death) come as a respawn rather than a login. */
    public void onRespawn(ClientboundRespawnPacket packet) {
        if (!active()) return;

        CommonPlayerSpawnInfo spawn = packet.commonPlayerSpawnInfo();
        signal("RESPAWN", "dimension={} gamemode={} keep={}",
                spawn.dimension().identifier(), spawn.gameType(), packet.dataToKeep());
    }

    public void onGameEvent(ClientboundGameEventPacket packet) {
        if (!active()) return;

        ClientboundGameEventPacket.Type event = packet.getEvent();
        String name;
        if (event == ClientboundGameEventPacket.WIN_GAME) {
            name = "win_game";
        } else if (event == ClientboundGameEventPacket.CHANGE_GAME_MODE) {
            name = "change_gamemode";
        } else if (event == ClientboundGameEventPacket.LEVEL_CHUNKS_LOAD_START) {
            name = "chunks_load_start";
        } else if (event == ClientboundGameEventPacket.IMMEDIATE_RESPAWN) {
            name = "immediate_respawn";
        } else {
            return; // weather and the rest say nothing about games
        }

        signal("GAME_EVENT", "{} param={}", name, packet.getParam());
    }

    /** The moves between lobby, pre-game lobby and cage all show up as a long jump. */
    public void onPlayerPosition(ClientboundPlayerPositionPacket packet) {
        if (!active()) return;
        // Relative packets nudge the player around; only absolute jumps are moves
        if (!packet.relatives().isEmpty()) return;

        Vec3 to = packet.change().position();
        Vec3 from = position;
        position = to;
        if (from != null && from.distanceTo(to) < TELEPORT_DISTANCE) return;

        signal("TELEPORT", "to={} distance={}", formatPosition(to),
                from == null ? "?" : String.format(Locale.ROOT, "%.0f", from.distanceTo(to)));
    }

    // Scoreboard signals

    public void onObjective(ClientboundSetObjectivePacket packet) {
        if (!active()) return;

        sidebarDirty = true;
        String title = packet.getDisplayName().getString();
        signal("OBJECTIVE", "{} name={} title='{}' parsed={}",
                objectiveMethod(packet.getMethod()), packet.getObjectiveName(), title, parseGame(title));
    }

    /** The sidebar being pointed at another objective is how a game handed out in place shows up. */
    public void onDisplayObjective(ClientboundSetDisplayObjectivePacket packet) {
        if (!active()) return;

        sidebarDirty = true;
        signal("DISPLAY_SLOT", "slot={} objective={}", packet.getSlot(), packet.getObjectiveName());
    }

    /** Scores are the sidebar rows; the snapshot on the next tick reports what they became. */
    public void onScoreChanged() {
        if (!active()) return;
        sidebarDirty = true;
    }

    public void onTeam(ClientboundSetPlayerTeamPacket packet) {
        if (!active()) return;

        sidebarDirty = true;
        String name = packet.getName();
        String action = packet.getTeamAction() != null
                ? packet.getTeamAction().name().toLowerCase(Locale.ROOT)
                : "players_" + (packet.getPlayerAction() == null ? "?"
                        : packet.getPlayerAction().name().toLowerCase(Locale.ROOT));

        // At TAIL the scoreboard is already updated, so the team carries the applied values
        Minecraft client = Minecraft.getInstance();
        PlayerTeam team = client.level == null ? null : client.level.getScoreboard().getPlayerTeam(name);
        String prefix = team == null ? "" : team.getPlayerPrefix().getString();
        String suffix = team == null ? "" : team.getPlayerSuffix().getString();

        trackServerId(prefix);
        boolean roster = ROSTER_TEAM_PATTERN.matcher(name).matches();
        trackRoster(name, roster);

        // Nametag and sidebar helper teams arrive in the hundreds, so they wait for a boundary
        if (!roster) {
            if (teamBudget <= 0) return;
            if (--teamBudget == 0) {
                signal("TEAM", "budget spent, further team updates muted until the next boundary");
                return;
            }
        }

        signal("TEAM", "{} name={} prefix='{}' suffix='{}' players={}",
                action, name, abbreviate(prefix, DETAIL_LIMIT), abbreviate(suffix, 30),
                packet.getPlayers().size());
    }

    private void trackServerId(String prefix) {
        Matcher matcher = SERVER_ID_PATTERN.matcher(prefix.trim());
        if (!matcher.matches() || matcher.group(1).equals(serverId)) return;

        String previous = serverId;
        serverId = matcher.group(1);
        // Budgets are per game server: the updates right after a switch are the ones to keep
        resetBudgets();
        signal("SERVER_ID", "{} (was '{}')", serverId, previous);
    }

    /** Colour-named teams are the real rosters, and they only arrive once a game starts. */
    private void trackRoster(String teamName, boolean roster) {
        if (rosterSeen || !roster) return;

        rosterSeen = true;
        signal("ROSTER", "first colour team {}", teamName);
    }

    // Hud signals

    public void onTitle(Component text) {
        if (!active()) return;
        signal("TITLE", "'{}'", abbreviate(flatten(text), DETAIL_LIMIT));
    }

    public void onSubtitle(Component text) {
        if (!active()) return;
        signal("SUBTITLE", "'{}'", abbreviate(flatten(text), DETAIL_LIMIT));
    }

    public void onActionBar(Component text) {
        if (!active()) return;

        String flattened = flatten(text);
        if (flattened.equals(actionBar) || actionBarBudget <= 0) return;

        actionBar = flattened;
        actionBarBudget--;
        signal("ACTION_BAR", "'{}'{}", abbreviate(flattened, DETAIL_LIMIT),
                actionBarBudget == 0 ? " (further changes muted)" : "");
    }

    // Chat, sidebar snapshots and player counts

    private void onGameMessage(Component message, boolean overlay) {
        if (overlay || !active()) return;

        String text = message.getString().trim();
        if (text.isEmpty()) return;

        String type = CHAT_MARKERS.stream()
                .filter(marker -> marker.matches().test(text))
                .map(ChatMarker::type)
                .findFirst()
                .orElse("CHAT");
        signal(type, "'{}'", abbreviate(text, CHAT_LIMIT));
    }

    private void onEndTick(Minecraft client) {
        if (!active() || client.level == null) return;

        flushSidebar();
        trackPlayerCount(client);
    }

    /** One snapshot per tick, only when the layout changed; digits are masked so timers are not news. */
    private void flushSidebar() {
        if (!sidebarDirty) return;
        sidebarDirty = false;

        String snapshot = describeSidebar();
        String layout = snapshot.replaceAll("\\d", "#");
        if (layout.equals(sidebar)) return;

        sidebar = layout;
        signal("SIDEBAR", "{}", snapshot);
    }

    private void trackPlayerCount(Minecraft client) {
        ClientPacketListener connection = client.getConnection();
        if (connection == null) return;

        int count = connection.getOnlinePlayers().size();
        long now = System.currentTimeMillis();
        if (count == playerCount || now - playerCountLoggedAt < PLAYER_COUNT_INTERVAL) return;

        int previous = playerCount;
        playerCount = count;
        playerCountLoggedAt = now;
        signal("PLAYERS", "count={} (was {})", count, previous < 0 ? "?" : previous);
    }

    // Plumbing

    private boolean active() {
        return enabled && Util.isOnCubeCraft(Minecraft.getInstance());
    }

    private void resetPerServer() {
        sidebar = "";
        actionBar = "";
        position = null;
        rosterSeen = false;
        sidebarDirty = false;
        playerCount = -1;
        resetBudgets();
    }

    private void resetBudgets() {
        teamBudget = TEAM_BUDGET;
        actionBarBudget = ACTION_BAR_BUDGET;
    }

    /** What the live detection would make of a sidebar title right now. */
    private static String parseGame(String title) {
        String cleaned = title.replaceAll("[^a-zA-Z .]", "").trim();
        if (cleaned.isEmpty()) return "none";

        Game game = CubepanionAPI.getInstance().tryGame(cleaned);
        return game == null ? "none" : game.name();
    }

    private String context() {
        return String.format(Locale.ROOT, "%s server=%s conn=%d",
                GameManager.getInstance().describeState(),
                serverId.isEmpty() ? "?" : serverId, connections);
    }

    // Synchronized because a disconnect can be reported from the netty thread, while
    // everything else arrives on the client thread
    private synchronized void signal(String type, String message, Object... args) {
        if (!enabled) return;

        counts.merge(type, 1, Integer::sum);
        String detail = MessageFormatter.arrayFormat(message, args).getMessage();
        long sinceConnect = connectedAt < 0 ? -1 : System.currentTimeMillis() - connectedAt;

        log.write(type, detail, sinceConnect);
        CubeCraftPlusClient.LOGGER.debug("Signal {} {}", type, detail);

        if (mirrorToChat) {
            mirror(type, detail);
        }
    }

    private static void mirror(String type, String detail) {
        Component line = Component.literal("[" + type + "] ").withStyle(ChatFormatting.DARK_AQUA)
                .append(Component.literal(detail).withStyle(ChatFormatting.GRAY));

        // Signals can arrive off the client thread (disconnect), so always hop over
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendSystemMessage(line);
            }
        });
    }

    private static String objectiveMethod(int method) {
        return switch (method) {
            case ClientboundSetObjectivePacket.METHOD_ADD -> "add";
            case ClientboundSetObjectivePacket.METHOD_REMOVE -> "remove";
            case ClientboundSetObjectivePacket.METHOD_CHANGE -> "change";
            default -> "method" + method;
        };
    }

    private static String formatPosition(Vec3 position) {
        return String.format(Locale.ROOT, "%.0f,%.0f,%.0f", position.x, position.y, position.z);
    }

    private static String flatten(Component component) {
        return flatten(component.getString());
    }

    private static String flatten(String text) {
        return text.replace('\n', '/').trim();
    }

    private static String abbreviate(String text, int limit) {
        String single = text.replace('\n', '/');
        return single.length() <= limit ? single : single.substring(0, limit) + "...";
    }
}
