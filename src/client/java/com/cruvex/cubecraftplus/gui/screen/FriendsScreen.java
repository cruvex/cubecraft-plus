package com.cruvex.cubecraftplus.gui.screen;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.friends.Friend;
import com.cruvex.cubecraftplus.friends.FriendsManager;
import com.cruvex.cubecraftplus.gui.widget.FriendsList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.CommonColors;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** The friends list {@link FriendsManager} holds, filtered by name. */
public class FriendsScreen extends Screen {

    private static final int HEADER_HEIGHT = 64;
    /** With the refresh button beside it, the search row is still the 200 wide the filter used to be. */
    private static final int FILTER_WIDTH = 176;
    private static final int BUTTON_SIZE = 20;
    private static final int ICON_SIZE = 20;
    private static final Identifier REFRESH_ICON =
            Identifier.fromNamespaceAndPath(CubeCraftPlusClient.MOD_ID, "icon/refresh");
    /** How often to re-read where online friends are while the screen is open. */
    private static final long ONLINE_CHECK_MS = 10_000;
    /** CubeCraft's own order: online first, then by name. */
    private static final Comparator<Friend> ORDER = Comparator.comparing(Friend::online).reversed()
            .thenComparing(Friend::name, String.CASE_INSENSITIVE_ORDER);

    private final Screen parent;
    private final HeaderAndFooterLayout layout =
            new HeaderAndFooterLayout(this, HEADER_HEIGHT, HeaderAndFooterLayout.DEFAULT_HEADER_AND_FOOTER_HEIGHT);
    private StringWidget summary;
    private EditBox filter;
    private FriendsList list;
    private SpriteIconButton refreshButton;

    /** The list the rows were built from; the manager replaces it on every change. */
    private List<Friend> shown = List.of();
    private @Nullable Component failure;
    /** 0 until the first tick, so statuses are read fresh as soon as the screen opens. */
    private long nextOnlineCheckAt;

    public FriendsScreen(Screen parent) {
        super(Component.translatable("cubecraftplus.friends.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        LinearLayout header = layout.addToHeader(LinearLayout.vertical().spacing(4));
        header.addChild(new StringWidget(title, font), LayoutSettings::alignHorizontallyCenter);
        summary = header.addChild(new StringWidget(Component.empty(), font).setMaxWidth(width - 20),
                LayoutSettings::alignHorizontallyCenter);
        LinearLayout search = header.addChild(LinearLayout.horizontal().spacing(4),
                LayoutSettings::alignHorizontallyCenter);
        filter = search.addChild(
                new EditBox(font, FILTER_WIDTH, BUTTON_SIZE, Component.translatable("cubecraftplus.friends.filter")));
        filter.setHint(Component.translatable("cubecraftplus.friends.filter").withStyle(ChatFormatting.DARK_GRAY));
        filter.setResponder(value -> showFriends());
        refreshButton = search.addChild(SpriteIconButton
                .builder(Component.translatable("cubecraftplus.friends.refresh"), button -> refresh(), true)
                .sprite(REFRESH_ICON, ICON_SIZE, ICON_SIZE)
                .size(BUTTON_SIZE, BUTTON_SIZE)
                .withTootip()
                .build());

        list = layout.addToContents(new FriendsList(minecraft, width, layout.getContentHeight(), layout.getHeaderHeight()));

        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).build());

        layout.visitWidgets(this::addRenderableWidget);
        showFriends();
        updateStatus();
        repositionElements();
    }

    @Override
    protected void setInitialFocus() {
        setInitialFocus(filter);
    }

    @Override
    protected void repositionElements() {
        if (list == null) return;
        layout.arrangeElements();
        list.updateSize(width, layout);
    }

    @Override
    public void tick() {
        long now = System.currentTimeMillis();
        if (now >= nextOnlineCheckAt) {
            FriendsManager.getInstance().checkOnline();
            nextOnlineCheckAt = now + ONLINE_CHECK_MS;
        }

        if (FriendsManager.getInstance().getFriends() != shown) {
            showFriends();
        }
        updateStatus();
    }

    private void showFriends() {
        shown = FriendsManager.getInstance().getFriends();
        String query = filter.getValue().trim().toLowerCase(Locale.ROOT);
        list.setFriends(shown.stream()
                .filter(friend -> friend.name().toLowerCase(Locale.ROOT).contains(query))
                .sorted(ORDER)
                .toList());
    }

    private void refresh() {
        FriendsManager.getInstance().refresh().whenComplete((friends, error) -> {
            if (error != null) {
                failure = Component.translatable("cubecraftplus.friends.refresh_failed", FriendsManager.failureReason(error))
                        .withStyle(ChatFormatting.RED);
            }
        });
    }

    private void updateStatus() {
        boolean refreshing = FriendsManager.getInstance().isRefreshing();
        // This version's icon buttons have no loading spinner, so it just stops responding
        refreshButton.active = !refreshing;

        Component text;
        if (refreshing) {
            // A running refresh, from here or /ccp friends refresh, replaces the last one's failure
            failure = null;
            text = Component.translatable("cubecraftplus.friends.refreshing").withStyle(ChatFormatting.GRAY);
        } else if (failure != null) {
            text = failure;
        } else {
            long online = shown.stream().filter(Friend::online).count();
            text = Component.translatable("cubecraftplus.friends.summary", shown.size(), online).withStyle(ChatFormatting.GRAY);
        }

        // The widget sizes itself to its text, so the header needs centring again
        if (!text.equals(summary.getMessage())) {
            summary.setMessage(text);
            repositionElements();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        if (!list.children().isEmpty()) return;

        String key = !shown.isEmpty() ? "cubecraftplus.friends.no_match"
                : FriendsManager.getInstance().isRefreshing() ? "cubecraftplus.friends.loading"
                : "cubecraftplus.friends.empty";
        graphics.drawCenteredString(font, Component.translatable(key), width / 2, layout.getHeaderHeight() + 20, CommonColors.GRAY);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
