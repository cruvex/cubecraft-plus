package com.cruvex.cubecraftplus.modmenu;

import com.cruvex.cubecraftplus.gui.screen.ConfigScreen;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Opens the config screen from ModMenu's mod list, which works off CubeCraft too. */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::new;
    }
}
