package com.cruvex.cubecraftplus.gui.screen;

import com.cruvex.cubecraftplus.gui.widget.FriendsList;
import com.cruvex.cubecraftplus.managers.FriendsManager;
import com.cruvex.cubecraftplus.model.Friend;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The friends list {@link FriendsManager} holds, filtered by name. Not a config tab: it is live
 * server state, so there is nothing to save or cancel (friends-gui.md §9).
 */
public class FriendsScreen extends Screen {

    private static final int HEADER_HEIGHT = 64;
    private static final int FILTER_WIDTH = 200;
    /** CubeCraft's own order: online first, then by name. */
    private static final Comparator<Friend> ORDER = Comparator.comparing(Friend::online).reversed()
            .thenComparing(Friend::name, String.CASE_INSENSITIVE_ORDER);

    private final Screen parent;
    private final HeaderAndFooterLayout layout =
            new HeaderAndFooterLayout(this, HEADER_HEIGHT, HeaderAndFooterLayout.DEFAULT_HEADER_AND_FOOTER_HEIGHT);
    private StringWidget summary;
    private EditBox filter;
    private FriendsList list;
    private Button refreshButton;

    /** The list the rows were built from; the manager replaces it on every change. */
    private List<Friend> shown = List.of();
    private @Nullable Component failure;

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
        filter = header.addChild(new EditBox(font, FILTER_WIDTH, 20, Component.translatable("cubecraftplus.friends.filter")),
                LayoutSettings::alignHorizontallyCenter);
        filter.setHint(Component.translatable("cubecraftplus.friends.filter").withStyle(ChatFormatting.DARK_GRAY));
        filter.setResponder(value -> showFriends());

        list = layout.addToContents(new FriendsList(minecraft, width, layout.getContentHeight(), layout.getHeaderHeight()));

        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        refreshButton = footer.addChild(Button.builder(Component.translatable("cubecraftplus.friends.refresh"),
                button -> refresh()).build());
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).build());

        layout.visitWidgets(this::addRenderableWidget);
        showFriends();
        repositionElements();

        // Statuses can be a minute old, so read them fresh for whoever just opened the screen
        FriendsManager.getInstance().checkOnlineNow();
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
        // Only while the screen is open: nothing else shows where friends are
        FriendsManager.getInstance().checkOnline();

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
        updateStatus();
    }

    private void refresh() {
        failure = null;
        FriendsManager.getInstance().refresh().whenComplete((friends, error) -> {
            if (error != null) {
                failure = Component.translatable("cubecraftplus.friends.refresh_failed", FriendsManager.failureReason(error))
                        .withStyle(ChatFormatting.RED);
            }
        });
        updateStatus();
    }

    private void updateStatus() {
        boolean refreshing = FriendsManager.getInstance().isRefreshing();
        refreshButton.active = !refreshing;
        if (refreshing) {
            // A newer refresh, perhaps from /ccp friends refresh, makes an old failure irrelevant
            failure = null;
        }

        Component text;
        if (refreshing) {
            text = Component.translatable("cubecraftplus.friends.refreshing").withStyle(ChatFormatting.GRAY);
        } else if (failure != null) {
            text = failure;
        } else {
            long online = shown.stream().filter(Friend::online).count();
            text = Component.translatable("cubecraftplus.friends.summary", shown.size(), online).withStyle(ChatFormatting.GRAY);
        }

        // Its width follows the text, so the header has to be centred again when it changes
        if (!text.equals(summary.getMessage())) {
            summary.setMessage(text);
            repositionElements();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (!list.children().isEmpty()) return;

        String key = !shown.isEmpty() ? "cubecraftplus.friends.no_match"
                : FriendsManager.getInstance().isRefreshing() ? "cubecraftplus.friends.loading"
                : "cubecraftplus.friends.empty";
        graphics.centeredText(font, Component.translatable(key), width / 2, layout.getHeaderHeight() + 20, CommonColors.GRAY);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
