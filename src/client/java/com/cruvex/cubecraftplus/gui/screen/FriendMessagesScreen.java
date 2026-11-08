package com.cruvex.cubecraftplus.gui.screen;

import com.cruvex.cubecraftplus.friends.Friend;
import com.cruvex.cubecraftplus.friends.FriendMessages;
import com.cruvex.cubecraftplus.friends.FriendsManager;
import com.cruvex.cubecraftplus.gui.widget.MessageHistoryList;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LayoutSettings;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** The messages saved with one friend, newest at the bottom, with a box to reply while they are online. */
public class FriendMessagesScreen extends Screen {

    private static final int REPLY_HEIGHT = 20;
    private static final int FOOTER_SPACING = 4;
    private static final int FOOTER_HEIGHT =
            HeaderAndFooterLayout.DEFAULT_HEADER_AND_FOOTER_HEIGHT + REPLY_HEIGHT + FOOTER_SPACING;
    // The longest command the vanilla chat box allows, without its slash
    private static final int MAX_COMMAND_LENGTH = 255;

    private final Screen parent;
    private final String friendName;
    private final UUID friendId;
    private final HeaderAndFooterLayout layout =
            new HeaderAndFooterLayout(this, HeaderAndFooterLayout.DEFAULT_HEADER_AND_FOOTER_HEIGHT, FOOTER_HEIGHT);
    private MessageHistoryList list;
    private EditBox reply;
    private int loadedSaves;

    public FriendMessagesScreen(Screen parent, Friend friend, UUID friendId) {
        super(Component.translatable("cubecraftplus.friends.history.title", friend.name()));
        this.parent = parent;
        this.friendName = friend.name();
        this.friendId = friendId;
    }

    @Override
    protected void init() {
        layout.addToHeader(new StringWidget(title, font));
        list = layout.addToContents(new MessageHistoryList(minecraft, width, layout.getContentHeight(), layout.getHeaderHeight()));

        LinearLayout footer = layout.addToFooter(LinearLayout.vertical().spacing(FOOTER_SPACING));
        reply = footer.addChild(new EditBox(font, list.getRowWidth(), REPLY_HEIGHT,
                Component.translatable("cubecraftplus.friends.message", friendName)), LayoutSettings::alignHorizontallyCenter);
        reply.setMaxLength(MAX_COMMAND_LENGTH - replyCommand("").length());
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).build(), LayoutSettings::alignHorizontallyCenter);

        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
        updateReply();
        reload();
    }

    @Override
    protected void setInitialFocus() {
        setInitialFocus(reply);
    }

    @Override
    public void tick() {
        updateReply();
        if (FriendMessages.getInstance().saveCount(friendId) != loadedSaves) {
            reload();
        }
    }

    private void reload() {
        loadedSaves = FriendMessages.getInstance().saveCount(friendId);
        FriendMessages.getInstance().load(friendId).thenAcceptAsync(messages -> list.setMessages(messages), minecraft);
    }

    private void updateReply() {
        boolean online = isFriendOnline();
        reply.setEditable(online);
        String hint = online ? "cubecraftplus.friends.message" : "cubecraftplus.friends.history.offline";
        reply.setHint(Component.translatable(hint, friendName).withStyle(ChatFormatting.DARK_GRAY));
    }

    private boolean isFriendOnline() {
        return FriendsManager.getInstance().getFriends().stream()
                .anyMatch(friend -> friendId.equals(friend.id()) && friend.online());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() && getFocused() == reply) {
            send();
            return true;
        }
        return super.keyPressed(event);
    }

    private void send() {
        String text = reply.getValue().trim();
        ClientPacketListener connection = minecraft.getConnection();
        if (text.isEmpty() || connection == null || !isFriendOnline()) return;

        connection.sendCommand(replyCommand(text));
        reply.setValue("");
        // Shows the reply when it comes back, even after scrolling up
        list.setScrollAmount(list.maxScrollAmount());
    }

    private String replyCommand(String text) {
        return "fmsg " + friendName + " " + text;
    }

    @Override
    protected void repositionElements() {
        if (list == null) return;
        layout.arrangeElements();
        list.updateSize(width, layout);
        // Matches the bubbles' width, which depends on the list's new size
        reply.setWidth(list.getRowWidth());
        layout.arrangeElements();
        list.rewrap();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
