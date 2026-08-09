package com.cruvex.cubecraftplus.managers;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.external.CubepanionAPI;
import com.cruvex.cubecraftplus.model.Game;
import com.cruvex.cubecraftplus.model.LeaderboardConfiguration;
import com.cruvex.cubecraftplus.model.LeaderboardRow;
import com.cruvex.cubecraftplus.util.Debug;
import com.mojang.authlib.properties.Property;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads CubeCraft's leaderboard menus while the player pages through them and submits a
 * complete leaderboard to the Cubepanion API. Tick-driven like {@link AutoVoteManager},
 * because the server fills a menu a few ticks after the screen opens.
 */
public class LeaderboardSubmitManager {

    private static final Logger LOGGER = CubeCraftPlusClient.LOGGER;

    private static LeaderboardSubmitManager instance;

    private static final String TITLE_MARKER = "Leaderboard";
    private static final int FILL_TIMEOUT_TICKS = 40;

    /** "EggWars Leaderboard (3/10)" -> page 3. */
    private static final Pattern PAGE_PATTERN = Pattern.compile(".*\\((\\d+)/\\d+\\)");
    private static final Pattern PLAYER_NAME_PATTERN = Pattern.compile("[a-zA-Z0-9_]{2,16}");

    private final Set<Integer> pages = new HashSet<>();
    private final Set<LeaderboardRow> rows = new LinkedHashSet<>();
    private Game game;

    // The menu being watched, so each one is parsed only once
    private int menuContainerId = -1;
    private boolean menuHandled;
    private int menuTicks;

    public static LeaderboardSubmitManager getInstance() {
        if (instance == null) {
            instance = new LeaderboardSubmitManager();
        }
        return instance;
    }

    public void init() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset("joined server"));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset("disconnected"));
    }

    private void onEndTick(Minecraft client) {
        if (client.player == null || !CubeCraftManager.getInstance().isOnCubeCraft()) return;
        if (!ConfigManager.getInstance().getConfig().leaderboardSubmit.enabled) return;
        if (!CubepanionAPI.getInstance().getLeaderboardConfiguration().canSubmit()) return;

        // Forget the menu once it closes, so a reused container id isn't taken for a parsed one
        if (!(client.gui.screen() instanceof ContainerScreen screen)) {
            menuContainerId = -1;
            return;
        }

        String title = screen.getTitle().getString();
        if (!title.contains(TITLE_MARKER)) {
            menuContainerId = -1;
            return;
        }

        ChestMenu menu = screen.getMenu();
        if (menu.containerId != menuContainerId) {
            menuContainerId = menu.containerId;
            menuHandled = false;
            menuTicks = 0;
        }
        if (menuHandled) return;

        List<ItemStack> heads = playerHeads(menu);
        if (heads.isEmpty()) {
            if (++menuTicks > FILL_TIMEOUT_TICKS) {
                Debug.log("Leaderboard: '{}' never filled in, ignoring it", title);
                menuHandled = true;
            }
            return;
        }

        menuHandled = true;
        readPage(title, heads);
    }

    private void readPage(String title, List<ItemStack> heads) {
        // Drop the colours and decoration the server puts around the title
        String cleaned = title.replaceAll("[^a-zA-Z0-9 ()/]", "").trim();
        int marker = cleaned.indexOf(TITLE_MARKER);
        if (marker <= 0) return;

        String gameName = cleaned.substring(0, marker).trim();
        Game pageGame = CubepanionAPI.getInstance().tryGame(gameName);
        if (pageGame == null) {
            Debug.log("Leaderboard: no game matches '{}'", gameName);
            return;
        }

        Matcher matcher = PAGE_PATTERN.matcher(cleaned);
        if (!matcher.matches()) {
            Debug.log("Leaderboard: no page number in '{}'", cleaned);
            return;
        }

        int page;
        try {
            page = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            Debug.log("Leaderboard: unreadable page number '{}'", matcher.group(1));
            return;
        }

        // Pages of different leaderboards can't be mixed into one submission
        if (!pageGame.equals(game)) {
            clearCollected();
            game = pageGame;
        }

        if (!pages.add(page)) return;

        LeaderboardConfiguration layout = CubepanionAPI.getInstance().getLeaderboardConfiguration();
        for (ItemStack head : heads) {
            LeaderboardRow row = parseRow(head, layout.playerCount());
            if (row != null) rows.add(row);
        }

        Debug.log("Leaderboard: read {} page {}, {}/{} pages and {}/{} places collected",
                game.displayName(), page, pages.size(), layout.pageCount(), rows.size(), layout.playerCount());

        if (pages.size() == layout.pageCount() && rows.size() == layout.playerCount()) {
            submit();
        }
    }

    /** Player heads carry the name on the first tooltip line and place/score below it. */
    private @Nullable LeaderboardRow parseRow(ItemStack head, int playerCount) {
        List<String> tooltip = tooltipLines(head);
        if (tooltip.size() < 3) return null;

        String player = tooltip.get(0).trim();
        if (!PLAYER_NAME_PATTERN.matcher(player).matches()) {
            Debug.log("Leaderboard: skipping place, '{}' is not a player name", player);
            return null;
        }

        Integer position = parseNumber(tooltip.get(1));
        Integer score = parseNumber(tooltip.get(2));
        if (position == null || score == null) {
            Debug.log("Leaderboard: skipping {}, no place/score in {} / {}", player, tooltip.get(1), tooltip.get(2));
            return null;
        }

        // Menu decoration can read as a place with a nonsense position
        if (position < 1 || position > playerCount) {
            Debug.log("Leaderboard: skipping {}, place {} is outside the leaderboard", player, position);
            return null;
        }

        return new LeaderboardRow(game.id(), player, position, score, texture(head));
    }

    private void submit() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        Game submittingFor = game;
        List<LeaderboardRow> entries = List.copyOf(rows);
        String uuid = player.getStringUUID();

        // Clear up front: a failed submit is retried by paging through the leaderboard again
        clearCollected();

        LOGGER.info("Submitting {} leaderboard places for {}", entries.size(), submittingFor.name());
        CubepanionAPI.getInstance().submit(submittingFor, entries, uuid)
                .thenRun(() -> Minecraft.getInstance().execute(() -> onSubmitted(submittingFor, entries.size())))
                .exceptionally(e -> {
                    LOGGER.error("Failed to submit leaderboard for {}", submittingFor.name(), e);
                    Debug.log("Leaderboard: submit for {} failed: {}", submittingFor.displayName(), e.getMessage());
                    return null;
                });
    }

    private void onSubmitted(Game game, int places) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        player.sendSystemMessage(
                Component.literal("Submitted " + places + " ")
                        .append(Component.literal(game.displayName()).withStyle(ChatFormatting.AQUA))
                        .append(Component.literal(" leaderboard places to Cubepanion."))
                        .withStyle(ChatFormatting.GREEN));
    }

    /** The chest part of the menu only — heads in the inventory below aren't places. */
    private static List<ItemStack> playerHeads(ChestMenu menu) {
        List<ItemStack> heads = new ArrayList<>();
        int chestSlots = Math.min(menu.getRowCount() * 9, menu.slots.size());
        for (int slot = 0; slot < chestSlots; slot++) {
            ItemStack stack = menu.slots.get(slot).getItem();
            if (!stack.isEmpty() && stack.is(Items.PLAYER_HEAD)) {
                heads.add(stack);
            }
        }
        return heads;
    }

    private static List<String> tooltipLines(ItemStack stack) {
        Minecraft client = Minecraft.getInstance();
        List<String> lines = new ArrayList<>();
        for (Component line : stack.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL)) {
            lines.add(line.getString());
        }
        return lines;
    }

    private static @Nullable String texture(ItemStack head) {
        ResolvableProfile profile = head.get(DataComponents.PROFILE);
        if (profile == null) return null;

        return profile.partialProfile().properties().get("textures").stream()
                .findFirst()
                .map(Property::value)
                .orElse(null);
    }

    private static @Nullable Integer parseNumber(String line) {
        String digits = line.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return null;

        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void clearCollected() {
        pages.clear();
        rows.clear();
        game = null;
    }

    private void reset(String reason) {
        if (!pages.isEmpty()) {
            Debug.log("Leaderboard: dropped {} collected pages ({})", pages.size(), reason);
        }
        clearCollected();
        menuContainerId = -1;
        menuHandled = false;
        menuTicks = 0;
    }
}
