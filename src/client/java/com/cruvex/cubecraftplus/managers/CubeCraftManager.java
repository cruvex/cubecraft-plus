package com.cruvex.cubecraftplus.managers;

import com.cruvex.cubecraftplus.CubeCraftPlusClient;
import com.cruvex.cubecraftplus.events.ScoreboardEvents;
import com.cruvex.cubecraftplus.model.CubeGame;
import net.minecraft.world.scores.Objective;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

public class CubeCraftManager {

    private final Logger LOGGER = CubeCraftPlusClient.LOGGER;

    private static CubeCraftManager instance;

    private CubeGame currentGame;

    public static CubeCraftManager getInstance() {
        if (instance == null) {
            instance = new CubeCraftManager();
        }
        return instance;
    }

    public void init() {
        registerListeners();
    }

    private void registerListeners() {
        ScoreboardEvents.ADD_OBJECTIVE.register(this::onAddObjective);
    }

    private void onAddObjective(@NotNull Objective objective) {
        var gameOptional = CubeGame.fromObjectiveTitle(objective.getDisplayName());

        if (gameOptional.isEmpty()) {
            LOGGER.debug("onAddObjective: No game found for objective: {}", objective.getDisplayName() == null ? "(none)" : objective.getDisplayName().getString());
            return;
        }

        CubeGame game = gameOptional.get();
        LOGGER.debug("onAddObjective: Game found: {}", game.name());
        this.setCurrentGame(game);
    }

    public CubeGame getCurrentGame() {
        return currentGame;
    }

    public void setCurrentGame(CubeGame currentGame) {
        this.currentGame = currentGame;
    }
}
