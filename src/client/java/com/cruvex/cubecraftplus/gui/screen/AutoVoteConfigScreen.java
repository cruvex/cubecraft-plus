package com.cruvex.cubecraftplus.gui.screen;

import com.cruvex.cubecraftplus.config.ModConfig;
import com.cruvex.cubecraftplus.gui.widget.ConfigOptionsList;
import com.cruvex.cubecraftplus.gui.widget.DropdownWidget;
import com.cruvex.cubecraftplus.managers.ConfigManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Settings screen for the mod, laid out like vanilla settings screens: a tab bar at
 * the top (only an Auto Vote tab for now), a scrollable options list as the tab
 * content, and a Save/Cancel footer. Edits go into a draft copy of the config and are
 * only persisted when Save is clicked; Cancel/Esc discards them.
 */
public class AutoVoteConfigScreen extends Screen {

    private static final String LANG_PREFIX = "cubecraftplus.autovote.";

    private static final int BUTTON_WIDTH = 150;
    private static final int BUTTON_HEIGHT = 20;

    private final Screen parent;
    private final ModConfig draft;
    private final List<DropdownWidget<?>> dropdowns = new ArrayList<>();
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final TabManager tabManager = new TabManager(widget -> addRenderableWidget(widget), widget -> removeWidget(widget));
    private TabNavigationBar tabNavigationBar;

    public AutoVoteConfigScreen(Screen parent) {
        super(Component.translatable(LANG_PREFIX + "title"));
        this.parent = parent;
        this.draft = ConfigManager.getInstance().copyConfig();
    }

    @Override
    protected void init() {
        dropdowns.clear();
        this.tabNavigationBar = TabNavigationBar.builder(tabManager, this.width)
                .addTabs(new AutoVoteTab())
                .build();
        addRenderableWidget(tabNavigationBar);

        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(Component.translatable("cubecraftplus.text.save"), button -> saveAndClose()).build());
        footer.addChild(Button.builder(Component.translatable("cubecraftplus.text.cancel"), button -> onClose()).build());
        layout.visitWidgets(widget -> addRenderableWidget(widget));

        tabNavigationBar.selectTab(0, false);
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        if (tabNavigationBar == null) return;
        tabNavigationBar.setWidth(this.width);
        tabNavigationBar.arrangeElements();
        int tabAreaTop = tabNavigationBar.getRectangle().bottom();
        tabManager.setTabArea(new ScreenRectangle(0, tabAreaTop, this.width, this.height - layout.getFooterHeight() - tabAreaTop));
        layout.setHeaderHeight(tabAreaTop);
        layout.arrangeElements();
    }

    private class AutoVoteTab implements Tab {
        private final ConfigOptionsList list;

        AutoVoteTab() {
            this.list = new ConfigOptionsList(minecraft, AutoVoteConfigScreen.this, width, height, 0);
            ModConfig.AutoVoteConfig av = draft.autoVote;

            list.addBig(CycleButton.onOffBuilder(av.enabled)
                    .create(0, 0, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.translatable(LANG_PREFIX + "enabled"),
                            (button, value) -> av.enabled = value));

            list.addHeader(Component.translatable(LANG_PREFIX + "group.eggWars"));
            list.addSmall(
                    dropdown("eggWarsItems", ModConfig.ThreeOptionsMode.values(), av.eggWars.items, v -> av.eggWars.items = v),
                    dropdown("eggWarsHealth", ModConfig.TwoOptionsMode.values(), av.eggWars.health, v -> av.eggWars.health = v));
            list.addSmall(
                    dropdown("eggWarsPerk", ModConfig.TwoOptionsMode.values(), av.eggWars.perk, v -> av.eggWars.perk = v), null);

            list.addHeader(Component.translatable(LANG_PREFIX + "group.skyWars"));
            list.addSmall(
                    dropdown("skyWarsChests", ModConfig.ThreeOptionsMode.values(), av.skyWars.chests, v -> av.skyWars.chests = v),
                    dropdown("skyWarsProjectiles", ModConfig.ThreeOptionsMode.values(), av.skyWars.projectiles, v -> av.skyWars.projectiles = v));
            list.addSmall(
                    dropdown("skyWarsTime", ModConfig.ThreeOptionsMode.values(), av.skyWars.time, v -> av.skyWars.time = v), null);

            list.addHeader(Component.translatable(LANG_PREFIX + "group.luckyIslands"));
            list.addSmall(
                    dropdown("luckyIslandsBlocks", ModConfig.FourOptionsMode.values(), av.luckyIslands.blocks, v -> av.luckyIslands.blocks = v),
                    dropdown("luckyIslandsTime", ModConfig.ThreeOptionsMode.values(), av.luckyIslands.time, v -> av.luckyIslands.time = v));

            list.addHeader(Component.translatable(LANG_PREFIX + "group.pillarsOfFortune"));
            list.addSmall(
                    dropdown("pofGameMode", ModConfig.FourOptionsMode.values(), av.pillarsOfFortune.gameMode, v -> av.pillarsOfFortune.gameMode = v),
                    dropdown("pofMapMode", ModConfig.FiveOptionsMode.values(), av.pillarsOfFortune.mapMode, v -> av.pillarsOfFortune.mapMode = v));

            list.addHeader(Component.translatable(LANG_PREFIX + "group.bedWars"));
            list.addSmall(
                    dropdown("bedWarsModifier", ModConfig.TwoOptionsMode.values(), av.bedWars.modifier, v -> av.bedWars.modifier = v), null);
        }

        @Override
        public Component getTabTitle() {
            return AutoVoteConfigScreen.this.getTitle();
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

    private <T extends Enum<T>> DropdownWidget<T> dropdown(String key, T[] values, T initial, Consumer<T> setter) {
        DropdownWidget<T> dropdown = new DropdownWidget<>(this.font, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.translatable(LANG_PREFIX + key),
                List.of(values), initial,
                value -> Component.translatable(LANG_PREFIX + key + "." + entryKey(value)),
                setter,
                this::closeAllDropdowns);
        dropdowns.add(dropdown);
        return dropdown;
    }

    private void closeAllDropdowns() {
        dropdowns.forEach(DropdownWidget::close);
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

    private void saveAndClose() {
        ConfigManager.getInstance().update(draft);
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Popups can overlap other rows and widgets, so route their clicks before the
        // normal bounds-based widget routing gets a chance to hit what's behind them
        for (DropdownWidget<?> dropdown : dropdowns) {
            if (dropdown.handlePopupClick(event)) {
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Scrolling moves the rows out from under an open popup — just close it
        closeAllDropdowns();
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        // Second pass so open dropdown lists draw on top of everything else
        for (DropdownWidget<?> dropdown : dropdowns) {
            dropdown.renderPopup(graphics, mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
