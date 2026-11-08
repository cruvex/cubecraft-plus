package com.cruvex.cubecraftplus.gui.screen;

import com.cruvex.cubecraftplus.friends.Friend;
import com.cruvex.cubecraftplus.friends.FriendMessages;
import com.cruvex.cubecraftplus.gui.widget.MessageHistoryList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/** The messages saved with one friend, newest at the bottom. */
public class FriendMessagesScreen extends Screen {

    private final Screen parent;
    private final UUID friendId;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this,
            HeaderAndFooterLayout.DEFAULT_HEADER_AND_FOOTER_HEIGHT, HeaderAndFooterLayout.DEFAULT_HEADER_AND_FOOTER_HEIGHT);
    private MessageHistoryList list;

    public FriendMessagesScreen(Screen parent, Friend friend, UUID friendId) {
        super(Component.translatable("cubecraftplus.friends.history.title", friend.name()));
        this.parent = parent;
        this.friendId = friendId;
    }

    @Override
    protected void init() {
        layout.addToHeader(new StringWidget(title, font));
        list = layout.addToContents(new MessageHistoryList(minecraft, width, layout.getContentHeight(), layout.getHeaderHeight()));
        layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> onClose()).build());

        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
        FriendMessages.getInstance().load(friendId).thenAcceptAsync(messages -> list.setMessages(messages), minecraft);
    }

    @Override
    protected void repositionElements() {
        if (list == null) return;
        layout.arrangeElements();
        list.updateSize(width, layout);
        list.rewrap();
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
