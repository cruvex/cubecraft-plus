package com.cruvex.cubecraftplus.gui.widget;

import com.cruvex.cubecraftplus.friends.FriendMessages.Message;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import net.minecraft.util.FormattedCharSequence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;

/** A friend's saved messages as chat bubbles: theirs on the left, yours on the right, with a line for each new day. */
public class MessageHistoryList extends ContainerObjectSelectionList<MessageHistoryList.Row> {

    private static final int DEFAULT_ROW_HEIGHT = 20;
    private static final int MIN_ROW_WIDTH = 200;
    private static final int MAX_ROW_WIDTH = 420;
    /** Either side of a row; the scrollbar sits in the right one. */
    private static final int ROW_MARGIN = 20;
    /** Rows have 2 of padding above and below, which is also the gap between bubbles. */
    private static final int ROW_PADDING = 4;
    private static final int DATE_ROW_HEIGHT = 18;
    private static final int BUBBLE_PADDING = 4;
    private static final int LINE_GAP = 1;
    private static final int TIME_GAP = 4;
    private static final int OWN_BUBBLE = 0xCC2B5C8A;
    private static final int THEIR_BUBBLE = 0xCC3A3A3A;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

    private List<Message> messages = List.of();

    public MessageHistoryList(Minecraft minecraft, int width, int height, int y) {
        super(minecraft, width, height, y, DEFAULT_ROW_HEIGHT);
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
        rewrap();
    }

    /** Lays the messages out for the current width and shows the newest. */
    public void rewrap() {
        int maxTextWidth = getRowWidth() * 7 / 10;
        clearEntries();
        LocalDate day = null;
        for (Message message : messages) {
            ZonedDateTime at = Instant.ofEpochMilli(message.time()).atZone(ZoneId.systemDefault());
            if (!at.toLocalDate().equals(day)) {
                day = at.toLocalDate();
                addEntry(new DateRow(DATE.format(at)), DATE_ROW_HEIGHT);
            }
            BubbleRow bubble = new BubbleRow(message, TIME.format(at), maxTextWidth);
            addEntry(bubble, bubble.rowHeight());
        }
        setScrollAmount(maxScrollAmount());
    }

    @Override
    public int getRowWidth() {
        return Math.clamp(width - ROW_MARGIN * 2, MIN_ROW_WIDTH, MAX_ROW_WIDTH);
    }

    public abstract class Row extends ContainerObjectSelectionList.Entry<Row> {

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    private class DateRow extends Row {

        private final String date;

        private DateRow(String date) {
            this.date = date;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            Font font = minecraft.font;
            graphics.text(font, date, getContentXMiddle() - font.width(date) / 2,
                    getContentYMiddle() - font.lineHeight / 2, CommonColors.LIGHT_GRAY);
        }
    }

    private class BubbleRow extends Row {

        private final boolean own;
        private final String time;
        private final List<FormattedCharSequence> lines;
        private final int textWidth;

        private BubbleRow(Message message, String time, int maxTextWidth) {
            Font font = minecraft.font;
            this.own = message.out();
            this.time = time;
            this.lines = font.split(Component.literal(message.text()), maxTextWidth);
            this.textWidth = lines.stream().mapToInt(font::width).max().orElse(0);
        }

        private int bubbleHeight() {
            return lines.size() * (minecraft.font.lineHeight + LINE_GAP) - LINE_GAP + BUBBLE_PADDING * 2;
        }

        private int rowHeight() {
            return bubbleHeight() + ROW_PADDING;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            Font font = minecraft.font;
            int bubbleWidth = textWidth + BUBBLE_PADDING * 2;
            int left = own ? getContentRight() - bubbleWidth : getContentX();
            int top = getContentY();
            int bottom = top + bubbleHeight();
            graphics.fill(left, top, left + bubbleWidth, bottom, own ? OWN_BUBBLE : THEIR_BUBBLE);

            int y = top + BUBBLE_PADDING;
            for (FormattedCharSequence line : lines) {
                graphics.text(font, line, left + BUBBLE_PADDING, y, CommonColors.WHITE);
                y += font.lineHeight + LINE_GAP;
            }

            int timeX = own ? left - TIME_GAP - font.width(time) : left + bubbleWidth + TIME_GAP;
            graphics.text(font, time, timeX, bottom - BUBBLE_PADDING - font.lineHeight, CommonColors.GRAY);
        }
    }
}
