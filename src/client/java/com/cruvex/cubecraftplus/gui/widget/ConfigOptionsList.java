package com.cruvex.cubecraftplus.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Scrollable options list modeled on vanilla's OptionsList (the Video Settings layout):
 * rows of one or two 150-wide widgets centered in a 310-wide column, plus section
 * header rows. Vanilla's OptionsList itself is hard-wired to OptionsSubScreen and
 * OptionInstances, so this is a small standalone clone for plain widgets.
 */
public class ConfigOptionsList extends ContainerObjectSelectionList<ConfigOptionsList.AbstractEntry> {

    private static final int ITEM_HEIGHT = 25;
    private static final int ROW_WIDTH = 310;
    private static final int WIDGET_X_OFFSET = 160;
    private static final int LINE_HEIGHT = 9;

    private final Screen screen;

    public ConfigOptionsList(Minecraft minecraft, Screen screen, int width, int height, int y) {
        super(minecraft, width, height, y, ITEM_HEIGHT);
        this.centerListVertically = false;
        this.screen = screen;
    }

    public void addHeader(Component text) {
        // Vanilla's OptionsList pads two text lines above a section; one reads tighter here
        int paddingTop = this.children().isEmpty() ? 0 : LINE_HEIGHT;
        this.addEntry(new HeaderEntry(this.screen, text, paddingTop), paddingTop + LINE_HEIGHT + 4);
    }

    /** Adds a single widget spanning the full row width. */
    public void addBig(AbstractWidget widget) {
        widget.setWidth(ROW_WIDTH);
        this.addEntry(new WidgetEntry(this.screen, widget, null));
    }

    public void addSmall(AbstractWidget first, @Nullable AbstractWidget second) {
        this.addEntry(new WidgetEntry(this.screen, first, second));
    }

    @Override
    public int getRowWidth() {
        return ROW_WIDTH;
    }

    protected abstract static class AbstractEntry extends ContainerObjectSelectionList.Entry<AbstractEntry> {
    }

    protected static class WidgetEntry extends AbstractEntry {
        private final Screen screen;
        private final List<AbstractWidget> widgets;

        WidgetEntry(Screen screen, AbstractWidget first, @Nullable AbstractWidget second) {
            this.screen = screen;
            this.widgets = second == null ? List.of(first) : List.of(first, second);
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int x = this.screen.width / 2 - ROW_WIDTH / 2;
            for (AbstractWidget widget : this.widgets) {
                widget.setPosition(x, this.getContentY());
                widget.render(graphics, mouseX, mouseY, partialTick);
                x += WIDGET_X_OFFSET;
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.widgets;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.widgets;
        }
    }

    protected static class HeaderEntry extends AbstractEntry {
        private final Screen screen;
        private final int paddingTop;
        private final StringWidget widget;

        HeaderEntry(Screen screen, Component text, int paddingTop) {
            this.screen = screen;
            this.paddingTop = paddingTop;
            this.widget = new StringWidget(text, screen.getFont());
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            this.widget.setPosition(this.screen.width / 2 - ROW_WIDTH / 2, this.getContentY() + this.paddingTop);
            this.widget.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.widget);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(this.widget);
        }
    }
}
