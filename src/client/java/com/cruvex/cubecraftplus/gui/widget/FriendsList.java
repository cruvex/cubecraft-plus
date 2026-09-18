package com.cruvex.cubecraftplus.gui.widget;

import com.cruvex.cubecraftplus.friends.Friend;
import com.cruvex.cubecraftplus.friends.HeadResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.List;

/** The friends screen's rows: a head, the name, and for online friends CubeCraft's status line under it. */
public class FriendsList extends ObjectSelectionList<FriendsList.Entry> {

    private static final int ITEM_HEIGHT = 28;
    private static final int ROW_WIDTH = 300;
    private static final int HEAD_SIZE = 16;
    private static final int HEAD_GAP = 6;
    private static final int TEXT_GAP = 2;
    /** {@code ChatFormatting.GREEN}, which reads better on the dark list than {@link CommonColors#GREEN}. */
    private static final int ONLINE_STATUS = 0xFF55FF55;
    private static final String ELLIPSIS = "...";

    public FriendsList(Minecraft minecraft, int width, int height, int y) {
        super(minecraft, width, height, y, ITEM_HEIGHT);
    }

    public void setFriends(List<Friend> friends) {
        replaceEntries(friends.stream().map(Entry::new).toList());
    }

    @Override
    public int getRowWidth() {
        return ROW_WIDTH;
    }

    public class Entry extends ObjectSelectionList.Entry<Entry> {

        private final Friend friend;

        private Entry(Friend friend) {
            this.friend = friend;
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

            int textX = x + HEAD_SIZE + HEAD_GAP;
            // "Offline" on nearly every row is noise; the grey name already says it
            if (!friend.online() || friend.status().isEmpty()) {
                graphics.text(font, friend.name(), textX, middle - font.lineHeight / 2,
                        friend.online() ? CommonColors.WHITE : CommonColors.LIGHT_GRAY);
                return;
            }

            int top = middle - (font.lineHeight * 2 + TEXT_GAP) / 2;
            graphics.text(font, friend.name(), textX, top, CommonColors.WHITE);
            graphics.text(font, fit(font, friend.status(), getContentRight() - textX), textX,
                    top + font.lineHeight + TEXT_GAP, ONLINE_STATUS);
        }

        @Override
        public Component getNarration() {
            return Component.literal(friend.status().isEmpty() ? friend.name() : friend.name() + ", " + friend.status());
        }
    }

    private static String fit(Font font, String text, int width) {
        if (font.width(text) <= width) return text;
        return font.plainSubstrByWidth(text, width - font.width(ELLIPSIS)) + ELLIPSIS;
    }
}
