package com.cruvex.cubecraftplus.gui.screen;

import com.cruvex.cubecraftplus.config.ModConfig;
import com.cruvex.cubecraftplus.gui.tab.AutoVoteTab;
import com.cruvex.cubecraftplus.gui.tab.LeaderboardTab;
import com.cruvex.cubecraftplus.gui.widget.DropdownWidget;
import com.cruvex.cubecraftplus.managers.ConfigManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
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

/**
 * Settings screen, one tab per feature. Edits go into a draft copy of the config and are
 * only persisted when Save is clicked.
 */
public class ConfigScreen extends Screen {

    private final Screen parent;
    private final ModConfig draft;
    private final List<DropdownWidget<?>> dropdowns = new ArrayList<>();
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final TabManager tabManager = new TabManager(widget -> addRenderableWidget(widget), widget -> removeWidget(widget));
    private TabNavigationBar tabNavigationBar;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("cubecraftplus.config.title"));
        this.parent = parent;
        this.draft = ConfigManager.getInstance().copyConfig();
    }

    @Override
    protected void init() {
        dropdowns.clear();
        this.tabNavigationBar = TabNavigationBar.builder(tabManager, this.width)
                .addTabs(
                        new AutoVoteTab(this, draft.autoVote),
                        new LeaderboardTab(this, draft.leaderboardSubmit))
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
        tabNavigationBar.updateWidth(this.width);
        tabNavigationBar.arrangeElements();
        int tabAreaTop = tabNavigationBar.getRectangle().bottom();
        tabManager.setTabArea(new ScreenRectangle(0, tabAreaTop, this.width, this.height - layout.getFooterHeight() - tabAreaTop));
        layout.setHeaderHeight(tabAreaTop);
        layout.arrangeElements();
    }

    /** Tabs hand their dropdowns over so the screen can route popup clicks and rendering. */
    public void addDropdown(DropdownWidget<?> dropdown) {
        dropdowns.add(dropdown);
    }

    public void closeAllDropdowns() {
        dropdowns.forEach(DropdownWidget::close);
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        if (dropdowns.stream().noneMatch(DropdownWidget::isOpen)) return;

        // A later stratum draws over everything extracted so far, which is what an open
        // dropdown list needs — it overhangs the rows and widgets beneath it
        graphics.nextStratum();
        for (DropdownWidget<?> dropdown : dropdowns) {
            dropdown.extractPopup(graphics, mouseX, mouseY);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
