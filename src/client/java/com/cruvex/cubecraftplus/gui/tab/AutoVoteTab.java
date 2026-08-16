package com.cruvex.cubecraftplus.gui.tab;

import com.cruvex.cubecraftplus.config.ModConfig;
import com.cruvex.cubecraftplus.external.CubepanionAPI;
import com.cruvex.cubecraftplus.gui.screen.ConfigScreen;
import com.cruvex.cubecraftplus.gui.widget.DropdownWidget;
import com.cruvex.cubecraftplus.model.AutoVoteCategory;
import com.cruvex.cubecraftplus.model.AutoVoteCategoryOption;
import com.cruvex.cubecraftplus.model.AutoVoteConfiguration;
import com.cruvex.cubecraftplus.model.CubeGame;
import com.cruvex.cubecraftplus.model.Game;
import com.cruvex.cubecraftplus.model.GameVotes;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Built from the autovote config, so headers and option names are its strings, untranslated. */
public class AutoVoteTab extends ConfigTab {

    private static final String LANG_PREFIX = "cubecraftplus.autovote.";

    public AutoVoteTab(ConfigScreen screen, ModConfig.AutoVoteConfig config) {
        super(screen);

        list.addBig(toggle(LANG_PREFIX + "enabled", config.enabled, value -> config.enabled = value));

        List<AutoVoteConfiguration> configurations = supportedConfigurations();
        if (configurations.isEmpty()) {
            list.addHeader(Component.translatable(LANG_PREFIX + "unavailable"));
            return;
        }

        for (AutoVoteConfiguration configuration : configurations) {
            list.addHeader(Component.literal(configuration.gameName()));
            addCategoryRows(config, configuration);
        }
    }

    /** Two dropdowns per row. */
    private void addCategoryRows(ModConfig.AutoVoteConfig config, AutoVoteConfiguration configuration) {
        List<AbstractWidget> widgets = new ArrayList<>();
        for (AutoVoteCategory category : configuration.categories()) {
            widgets.add(categoryDropdown(config, category));
        }

        for (int i = 0; i < widgets.size(); i += 2) {
            list.addSmall(widgets.get(i), i + 1 < widgets.size() ? widgets.get(i + 1) : null);
        }
    }

    private DropdownWidget<AutoVoteCategoryOption> categoryDropdown(ModConfig.AutoVoteConfig config,
                                                                   AutoVoteCategory category) {
        return dropdown(
                Component.literal(category.name()),
                category.options(),
                selectedOption(config, category),
                option -> Component.literal(option.name()),
                option -> config.slots.put(category.id(), option.slot()));
    }

    private static AutoVoteCategoryOption selectedOption(ModConfig.AutoVoteConfig config, AutoVoteCategory category) {
        int target = GameVotes.slotFor(config, category);
        for (AutoVoteCategoryOption option : category.options()) {
            if (option.slot() == target) {
                return option;
            }
        }
        // Saved slot no longer exists in the config; show the first option rather than nothing
        return category.options().getFirst();
    }

    private static List<AutoVoteConfiguration> supportedConfigurations() {
        List<AutoVoteConfiguration> configurations = new ArrayList<>();
        for (AutoVoteConfiguration configuration : CubepanionAPI.getInstance().getAutoVoteConfigurations()) {
            if (isConfigurable(configuration)) {
                configurations.add(configuration);
            }
        }

        configurations.sort(Comparator.comparing(AutoVoteConfiguration::gameName));
        return configurations;
    }

    private static boolean isConfigurable(AutoVoteConfiguration configuration) {
        // Without a CubeGame constant the game is never detected, so voting could never fire
        if (GameVotes.cubeGameFor(configuration) == CubeGame.NONE) {
            return false;
        }

        // Retired games can't be joined; null (games not loaded) shows them rather than nothing
        Game game = CubepanionAPI.getInstance().getGameById(configuration.gameId());
        return game == null || game.active();
    }

    @Override
    public Component getTabTitle() {
        return Component.translatable(LANG_PREFIX + "title");
    }
}
