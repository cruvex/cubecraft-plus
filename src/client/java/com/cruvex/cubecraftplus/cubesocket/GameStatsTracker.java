package com.cruvex.cubecraftplus.cubesocket;

import com.cruvex.cubecraftplus.config.ConfigManager;
import com.cruvex.cubecraftplus.cubesocket.protocol.packets.PacketGameStatUpdate;
import com.cruvex.cubecraftplus.debug.Debug;
import com.cruvex.cubecraftplus.game.CubeCraftManager;
import com.cruvex.cubecraftplus.game.CubeEvents;
import com.cruvex.cubecraftplus.game.Game;
import com.cruvex.cubecraftplus.game.GameFlag;
import com.cruvex.cubecraftplus.game.GameRegistry;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads the player counts off the lobby's game nametags and reports them over the socket. */
public class GameStatsTracker {

    private static final double RADIUS = 64.0;
    /** Delay before a lobby's first scan, and between retries after one does not run. */
    private static final int FIRST_SCAN_TICKS = 60;
    private static final int RESCAN_TICKS = 20 * 60;
    private static final int SEND_INTERVAL_TICKS = 20;
    private static final long RESEND_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1);

    /** "UPDATED + NEW MAPS - 63 players" to 63. */
    private static final Pattern COUNT = Pattern.compile("(\\d[\\d,]*)\\s+players?$");

    private static GameStatsTracker instance;

    private final Deque<PacketGameStatUpdate> pending = new ArrayDeque<>();

    private int ticksUntilScan;
    private int ticksUntilSend;
    private long lastScanAt;

    public static GameStatsTracker getInstance() {
        if (instance == null) {
            instance = new GameStatsTracker();
        }
        return instance;
    }

    public void init() {
        CubeEvents.GAME_JOIN.register(this::onGameJoin);
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onGameJoin(Game game) {
        this.ticksUntilScan = game.hasFlag(GameFlag.LOBBY) ? FIRST_SCAN_TICKS : 0;
    }

    private void onTick(Minecraft client) {
        this.drain();

        if (this.ticksUntilScan <= 0 || --this.ticksUntilScan > 0) {
            return;
        }

        this.ticksUntilScan = this.scan(client) ? RESCAN_TICKS : FIRST_SCAN_TICKS;
    }

    /** Sends one queued packet a second, dropping the queue if the socket has gone. */
    private void drain() {
        if (this.pending.isEmpty() || --this.ticksUntilSend > 0) {
            return;
        }

        CubeSocket socket = CubeSocket.getInstance();
        if (!socket.isConnected()) {
            this.pending.clear();
            return;
        }

        socket.sendPacket(this.pending.poll());
        this.ticksUntilSend = SEND_INTERVAL_TICKS;
    }

    /** Whether the scan ran. */
    private boolean scan(Minecraft client) {
        if (!ConfigManager.getInstance().getConfig().gameStats.enabled
                || !CubeSocket.isEnabled()
                || !CubeCraftManager.getInstance().isOnCubeCraft()) {
            return true;
        }

        if (System.currentTimeMillis() - this.lastScanAt < RESEND_INTERVAL_MS) {
            return false;
        }

        CubeSocket socket = CubeSocket.getInstance();
        ClientLevel level = client.level;
        if (!socket.isConnected() || level == null || client.player == null) {
            return false;
        }

        Map<Game, Integer> counts = match(read(level, client.player.position()));
        if (counts.isEmpty()) {
            return false;
        }

        this.pending.clear();
        counts.forEach((game, players) -> this.pending.add(new PacketGameStatUpdate(game, players)));
        this.ticksUntilSend = SEND_INTERVAL_TICKS;
        this.lastScanAt = System.currentTimeMillis();

        Debug.log("Queued {} game player counts", counts.size());
        return true;
    }

    /** Resolves labels to games, the exact names first and the branded ones after. */
    private static Map<Game, Integer> match(List<Label> labels) {
        GameRegistry registry = GameRegistry.getInstance();

        Map<Game, Integer> found = new LinkedHashMap<>();
        Set<Integer> certain = new HashSet<>();
        List<Label> branded = new ArrayList<>();

        for (Label label : labels) {
            Game game = registry.find(label.name());
            if (game == null) {
                game = registry.find(beforeHyphen(label.name()));
            }

            if (game == null) {
                branded.add(label);
                continue;
            }

            certain.add(game.id());
            found.putIfAbsent(game, label.players());
        }

        for (Label label : branded) {
            Game game = findWithin(label.name(), certain);
            if (game == null) {
                Debug.log("No game matches portal: {}", label.name());
                continue;
            }

            certain.add(game.id());
            found.putIfAbsent(game, label.players());
        }
        return found;
    }

    /** Every text display in range that advertises a player count. */
    private static List<Label> read(ClientLevel level, Vec3 eye) {
        double radiusSqr = RADIUS * RADIUS;

        List<Label> labels = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof Display.TextDisplay display)
                    || entity.position().distanceToSqr(eye) > radiusSqr) {
                continue;
            }

            Display.TextDisplay.TextRenderState state = display.textRenderState();
            if (state == null) {
                continue;
            }

            Label label = parse(state.text());
            if (label != null) {
                labels.add(label);
            }
        }
        return labels;
    }

    /** The name a display leads with and the first count below it. */
    private static @Nullable Label parse(Component text) {
        List<String> lines = Arrays.stream(text.getString().split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
        if (lines.size() < 2) {
            return null;
        }

        for (String line : lines.subList(1, lines.size())) {
            Integer players = players(line);
            if (players != null) {
                return new Label(lines.getFirst(), players);
            }
        }
        return null;
    }

    private static @Nullable Integer players(String line) {
        Matcher matcher = COUNT.matcher(line);
        if (!matcher.find()) {
            return null;
        }

        try {
            return Integer.parseInt(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** "Mob Hunt - BETA v0.2" to "Mob Hunt". */
    private static String beforeHyphen(String name) {
        int hyphen = name.indexOf('-');
        return hyphen < 0 ? name : name.substring(0, hyphen).trim();
    }

    /**
     * The game a branded label carries, e.g. Skyblock out of "Skyblock Reborn". Matches display
     * names only, longest first, and never one already claimed.
     */
    private static @Nullable Game findWithin(String label, Set<Integer> claimed) {
        String haystack = normalize(label);

        Game best = null;
        int longest = 0;
        for (Game game : GameRegistry.getInstance().all()) {
            String candidate = normalize(game.displayName());
            if (claimed.contains(game.id()) || candidate.length() <= longest) {
                continue;
            }

            if (containsWord(haystack, candidate)) {
                best = game;
                longest = candidate.length();
            }
        }
        return best;
    }

    /** Whether the needle sits in the haystack as a whole word, bounded by underscores or the ends. */
    private static boolean containsWord(String haystack, String needle) {
        for (int at = haystack.indexOf(needle); at >= 0; at = haystack.indexOf(needle, at + 1)) {
            int end = at + needle.length();
            if ((at == 0 || haystack.charAt(at - 1) == '_')
                    && (end == haystack.length() || haystack.charAt(end) == '_')) {
                return true;
            }
        }
        return false;
    }

    /** Lowercased with spaces as underscores, the way the registry keys its lookups. */
    private static String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private record Label(String name, int players) {
    }
}
