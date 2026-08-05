package com.cruvex.cubecraftplus.gui.tab;

import com.cruvex.cubecraftplus.gui.screen.ConfigScreen;
import com.cruvex.cubecraftplus.gui.widget.ConfigOptionsList;
import com.cruvex.cubecraftplus.gui.widget.DropdownWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Base for {@link ConfigScreen} tabs: holds the options list and the widget builders, so a
 * tab only has to declare its own options. Translation keys are passed in full.
 */
public abstract class ConfigTab implements Tab {

    protected static final int BUTTON_WIDTH = 150;
    protected static final int BUTTON_HEIGHT = 20;


    protected final ConfigScreen screen;
    protected final ConfigOptionsList list;

    protected ConfigTab(ConfigScreen screen) {
        this.screen = screen;
        this.list = new ConfigOptionsList(Minecraft.getInstance(), screen, screen.width, screen.height, 0);
    }

    protected CycleButton<Boolean> toggle(String key, boolean initial, Consumer<Boolean> setter) {
        return CycleButton.onOffBuilder(initial)
                .create(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable(key),
                        (button, value) -> setter.accept(value));
    }

    protected CycleButton<Boolean> toggle(String key, String tooltipKey, boolean initial, Consumer<Boolean> setter) {
        return CycleButton.onOffBuilder(initial)
                .withTooltip(value -> Tooltip.create(Component.translatable(tooltipKey)))
                .create(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, Component.translatable(key),
                        (button, value) -> setter.accept(value));
    }

    protected <T extends Enum<T>> DropdownWidget<T> dropdown(String key, T[] values, T initial, Consumer<T> setter) {
        DropdownWidget<T> dropdown = new DropdownWidget<>(screen.getFont(), BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable(key),
                List.of(values), initial,
                value -> Component.translatable(key + "." + entryKey(value)),
                setter,
                screen::closeAllDropdowns);
        screen.addDropdown(dropdown);
        return dropdown;
    }

    /** LEFT -> "left", MIDDLE_LEFT -> "middleLeft", matching the lang file entry keys. */
    private static String entryKey(Enum<?> value) {
        String[] parts = value.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder key = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            key.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
        }
        return key.toString();
    }

    @Override
    public Component getTabExtraNarration() {
        return Component.empty();
    }

    @Override
    public void visitChildren(Consumer<AbstractWidget> consumer) {
        consumer.accept(list);
    }

    @Override
    public void doLayout(ScreenRectangle area) {
        list.updateSizeAndPosition(area.width(), area.height(), area.top());
    }
}
