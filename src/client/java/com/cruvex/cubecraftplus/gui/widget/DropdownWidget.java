package com.cruvex.cubecraftplus.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Vanilla-styled dropdown (vanilla has no dropdown widget of its own). Collapsed it
 * renders exactly like a vanilla button showing "Name: Value ▾"; clicking expands an
 * option list whose entries are also drawn as vanilla buttons. The list is rendered
 * by the owning screen in a second pass (renderPopup) so it draws on top of
 * neighboring widgets, and opens upward when there is not enough room below.
 *
 * Mouse routing: the popup lies outside this widget's bounds (it may overlap other
 * widgets or scrolling list rows), so the owning screen must call handlePopupClick
 * from its mouseClicked override before the normal bounds-based widget routing.
 */
public class DropdownWidget<T> extends AbstractButton {

    private static final int ENTRY_HEIGHT = 14;

    private final Font font;
    private final Component name;
    private final List<T> values;
    private final Function<T, Component> labelGetter;
    private final Consumer<T> onSelect;
    private final Runnable closeOthers;

    private T value;
    private boolean open;
    private boolean openUp;

    public DropdownWidget(Font font, int width, int height, Component name, List<T> values, T initial,
                          Function<T, Component> labelGetter, Consumer<T> onSelect, Runnable closeOthers) {
        super(0, 0, width, height, Component.empty());
        this.font = font;
        this.name = name;
        this.values = values;
        this.labelGetter = labelGetter;
        this.onSelect = onSelect;
        this.closeOthers = closeOthers;
        this.value = initial;
        updateMessage();
    }

    public T getValue() {
        return value;
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        setOpen(false);
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (open) {
            setOpen(false);
            return;
        }
        closeOthers.run();
        // Open upward if the list would run off the bottom of the screen
        int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        openUp = getY() + getHeight() + popupHeight() > screenHeight;
        setOpen(true);
    }

    /**
     * Routes a click that may belong to the open option list. Returns true if the
     * click selected an entry. A click anywhere else closes the list and returns
     * false so the click still reaches whatever was clicked — except on this
     * widget's own face, where the normal routing handles the close-toggle.
     */
    public boolean handlePopupClick(MouseButtonEvent event) {
        if (!open || event.button() != 0) return false;

        int index = entryIndexAt(event.x(), event.y());
        if (index >= 0) {
            value = values.get(index);
            onSelect.accept(value);
            setOpen(false);
            playDownSound(Minecraft.getInstance().getSoundManager());
            return true;
        }
        if (!isMouseOver(event.x(), event.y())) {
            setOpen(false);
        }
        return false;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Same as vanilla Button$Plain: button sprite plus centered scrolling label
        extractDefaultSprite(graphics);
        extractDefaultLabel(graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE));
    }

    /** Called by the screen in a later stratum, so the list draws on top. */
    public void extractPopup(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!open) return;

        int x = getX();
        int y = popupY();
        int width = getWidth();
        int height = popupHeight();
        int hovered = entryIndexAt(mouseX, mouseY);

        graphics.fill(x, y, x + width, y + height, 0xF0100010);
        outline(graphics, x, y, width, height, 0xFF8A8A8A);

        for (int i = 0; i < values.size(); i++) {
            int entryY = y + 1 + i * ENTRY_HEIGHT;
            if (i == hovered) {
                graphics.fill(x + 1, entryY, x + width - 1, entryY + ENTRY_HEIGHT, 0x50FFFFFF);
            }
            int color = values.get(i).equals(value) ? 0xFFFFFF55 : 0xFFFFFFFF;
            graphics.text(font, labelGetter.apply(values.get(i)), x + 5, entryY + 3, color);
        }
    }

    /** GuiGraphicsExtractor has no outline helper, so draw the four edges. */
    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    private void setOpen(boolean open) {
        this.open = open;
        updateMessage();
    }

    private void updateMessage() {
        setMessage(Component.empty().append(name).append(": ").append(labelGetter.apply(value))
                .append(open ? " ▴" : " ▾"));
    }

    private int popupHeight() {
        return values.size() * ENTRY_HEIGHT + 2;
    }

    private int popupY() {
        return openUp ? getY() - popupHeight() : getY() + getHeight();
    }

    private boolean isInPopup(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getX() + getWidth()
                && mouseY >= popupY() && mouseY < popupY() + popupHeight();
    }

    private int entryIndexAt(double mouseX, double mouseY) {
        if (!open || !isInPopup(mouseX, mouseY)) return -1;
        int relative = (int) (mouseY - popupY() - 1);
        int index = relative / ENTRY_HEIGHT;
        return relative >= 0 && index < values.size() ? index : -1;
    }
}
