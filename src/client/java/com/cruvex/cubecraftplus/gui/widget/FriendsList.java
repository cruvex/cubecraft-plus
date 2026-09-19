package com.cruvex.cubecraftplus.gui.widget;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.friends.Friend;
import com.cruvex.cubecraftplus.friends.HeadResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.PopupScreen;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.CommonColors;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.List;

/** The friends screen's rows: a head, the name, for online friends CubeCraft's status line under it, and actions. */
public class FriendsList extends ContainerObjectSelectionList<FriendsList.Entry> {

    private static final int ITEM_HEIGHT = 28;
    /** Rows grow into wide screens so a status line loses less to the buttons, but never shrink below this. */
    private static final int MIN_ROW_WIDTH = 300;
    private static final int MAX_ROW_WIDTH = 360;
    /** Space either side of a row; the scrollbar sits in the right one. */
    private static final int ROW_MARGIN = 20;
    private static final int HEAD_SIZE = 16;
    private static final int HEAD_GAP = 6;
    private static final int TEXT_GAP = 2;
    private static final int BUTTON_SIZE = 20;
    private static final int BUTTON_GAP = 4;
    /** {@code ChatFormatting.GREEN}, which reads better on the dark list than {@link CommonColors#GREEN}. */
    private static final int ONLINE_STATUS = 0xFF55FF55;
    private static final String ELLIPSIS = "...";

    /** A vanilla sprite at its own size. */
    private record Icon(Identifier sprite, int width, int height) {
    }

    // Realms' invite envelope, the friends tab's two faces, and the friends overlay's unfriend
    private static final Icon MESSAGE_ICON = new Icon(Identifier.withDefaultNamespace("icon/invite"), 14, 14);
    // Vanilla only has the friends sprites from 26.2 on, so this version bundles copies of them
    private static final Icon PARTY_ICON = new Icon(Identifier.fromNamespaceAndPath(CubeCraftPlusClient.MOD_ID, "friends/friends"), 16, 16);
    private static final Icon REMOVE_ICON = new Icon(Identifier.fromNamespaceAndPath(CubeCraftPlusClient.MOD_ID, "friends/remove"), 14, 13);

    /** Where the remove confirmation returns to. */
    private final Screen screen;

    public FriendsList(Minecraft minecraft, Screen screen, int width, int height, int y) {
        super(minecraft, width, height, y, ITEM_HEIGHT);
        this.screen = screen;
    }

    public void setFriends(List<Friend> friends) {
        replaceEntries(friends.stream().map(Entry::new).toList());
    }

    @Override
    public int getRowWidth() {
        return Math.clamp(width - ROW_MARGIN * 2, MIN_ROW_WIDTH, MAX_ROW_WIDTH);
    }

    /** Through ALLOW_COMMAND, so a friends command waits for the list's own queries like a typed one. */
    private void sendCommand(String command) {
        ClientPacketListener connection = minecraft.getConnection();
        if (connection != null) {
            connection.sendCommand(command);
        }
    }

    public class Entry extends ContainerObjectSelectionList.Entry<Entry> {

        private final Friend friend;
        /** Left to right, ending with remove so it lines up on every row. */
        private final List<SpriteIconButton> buttons;

        private Entry(Friend friend) {
            this.friend = friend;
            SpriteIconButton remove = button("cubecraftplus.friends.remove", REMOVE_ICON, button -> confirmRemove());
            // Messages and invites only reach a friend who is online
            this.buttons = friend.online()
                    ? List.of(button("cubecraftplus.friends.message", MESSAGE_ICON, button -> message()),
                            button("cubecraftplus.friends.invite", PARTY_ICON, this::invite),
                            remove)
                    : List.of(remove);
        }

        private SpriteIconButton button(String key, Icon icon, Button.OnPress onPress) {
            return SpriteIconButton.builder(Component.translatable(key, friend.name()), onPress, true)
                    .sprite(icon.sprite(), icon.width(), icon.height())
                    .size(BUTTON_SIZE, BUTTON_SIZE)
                    .withTootip()
                    .build();
        }

        /** What CubeCraft's own list suggests when an online friend's name is clicked. */
        private void message() {
            minecraft.setScreen(new ChatScreen("/fmsg " + friend.name() + " ", false));
        }

        private void invite(Button button) {
            sendCommand("party invite " + friend.name());
            // The reply lands in chat behind the screen, so at least stop a second invite
            button.active = false;
        }

        private void confirmRemove() {
            minecraft.setScreen(new PopupScreen.Builder(screen, Component.translatable("cubecraftplus.friends.remove.title"))
                    .addMessage(Component.translatable("cubecraftplus.friends.remove.confirm", friend.name()))
                    .addButton(Component.translatable("cubecraftplus.friends.remove.button"), popup -> {
                        // The list drops the row when CubeCraft confirms, see FriendsManager
                        sendCommand("friend remove " + friend.name());
                        minecraft.setScreen(screen);
                    })
                    .addButton(CommonComponents.GUI_CANCEL, popup -> minecraft.setScreen(screen))
                    .build());
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            Font font = minecraft.font;
            int x = getContentX();
            int middle = getContentYMiddle();

            ResolvableProfile head = HeadResolver.getInstance().headFor(friend.name());
            int headY = middle - HEAD_SIZE / 2;
            if (head != null) {
                PlayerFaceExtractor.extractRenderState(graphics, minecraft.playerSkinRenderCache().getOrDefault(head).playerSkin(), x, headY, HEAD_SIZE);
            } else {
                PlayerFaceExtractor.extractRenderState(graphics, DefaultPlayerSkin.getDefaultSkin(), x, headY, HEAD_SIZE);
            }

            // Right to left from the row's edge; the text ends where the buttons start
            int buttonX = getContentRight();
            for (SpriteIconButton button : buttons.reversed()) {
                buttonX -= BUTTON_SIZE;
                button.setPosition(buttonX, middle - BUTTON_SIZE / 2);
                button.extractRenderState(graphics, mouseX, mouseY, partialTick);
                buttonX -= BUTTON_GAP;
            }

            int textX = x + HEAD_SIZE + HEAD_GAP;
            // "Offline" on nearly every row is noise; the grey name already says it
            if (!friend.online() || friend.status().isEmpty()) {
                graphics.text(font, friend.name(), textX, middle - font.lineHeight / 2,
                        friend.online() ? CommonColors.WHITE : CommonColors.LIGHT_GRAY);
                return;
            }

            int top = middle - (font.lineHeight * 2 + TEXT_GAP) / 2;
            String status = fit(font, friend.status(), buttonX - textX);
            graphics.text(font, friend.name(), textX, top, CommonColors.WHITE);
            graphics.text(font, status, textX, top + font.lineHeight + TEXT_GAP, ONLINE_STATUS);

            // A cut status loses its end, which is usually the map or the player count
            if (hovered && !status.equals(friend.status()) && mouseX >= textX && mouseX < buttonX) {
                graphics.setTooltipForNextFrame(font, Component.literal(friend.status()), mouseX, mouseY);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return buttons;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return buttons;
        }
    }

    private static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, width - font.width(ELLIPSIS)) + ELLIPSIS;
    }
}
