package com.cruvex.cubecraftplus.mixins;

import com.cruvex.cubecraftplus.events.PlayerEvents;
import com.cruvex.cubecraftplus.events.ScoreboardEvents;
import com.cruvex.cubecraftplus.util.SignalProbe;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundResetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetDisplayObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundSetScorePacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fires mod events from inbound packets, at TAIL so the packet's change is already applied. */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleSetDisplayObjective", at = @At("TAIL"))
    private void onHandleSetDisplayObjective(ClientboundSetDisplayObjectivePacket packet, CallbackInfo ci) {
        SignalProbe.getInstance().onDisplayObjective(packet);
        ScoreboardEvents.SET_DISPLAY_OBJECTIVE.invoker()
                .onSetDisplayObjective(packet.getSlot(), packet.getObjectiveName());
    }

    @Inject(method = "handleRespawn", at = @At("TAIL"))
    private void onHandleRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        SignalProbe.getInstance().onRespawn(packet);
        PlayerEvents.RESPAWN.invoker().onRespawn(packet.dataToKeep());
    }

    @Inject(method = "handleSetPlayerTeamPacket", at = @At("TAIL"))
    private void onHandleSetPlayerTeam(ClientboundSetPlayerTeamPacket packet, CallbackInfo ci) {
        SignalProbe.getInstance().onTeam(packet);

        // At TAIL the team is already gone on REMOVE, so there is nothing to report
        if (packet.getTeamAction() == ClientboundSetPlayerTeamPacket.Action.REMOVE) return;

        ClientPacketListener self = (ClientPacketListener) (Object) this;
        PlayerTeam team = self.getLevel().getScoreboard().getPlayerTeam(packet.getName());
        if (team != null) {
            ScoreboardEvents.TEAM_CHANGE.invoker().onTeamChange(team);
        }
    }

    @Inject(method = "handleGameEvent", at = @At("TAIL"))
    private void onHandleGameEvent(ClientboundGameEventPacket packet, CallbackInfo ci) {
        SignalProbe.getInstance().onGameEvent(packet);

        if (packet.getEvent() != ClientboundGameEventPacket.CHANGE_GAME_MODE) return;

        // The param is the game mode id as a float
        int id = (int) packet.getParam();
        if (GameType.isValidId(id)) {
            PlayerEvents.GAME_MODE_CHANGE.invoker().onGameModeChange(GameType.byId(id));
        }
    }

    // The rest is diagnostics, see SignalProbe

    @Inject(method = "handleAddObjective", at = @At("TAIL"))
    private void onHandleAddObjective(ClientboundSetObjectivePacket packet, CallbackInfo ci) {
        SignalProbe.getInstance().onObjective(packet);
    }

    @Inject(method = "handleSetScore", at = @At("TAIL"))
    private void onHandleSetScore(ClientboundSetScorePacket packet, CallbackInfo ci) {
        SignalProbe.getInstance().onScoreChanged();
    }

    @Inject(method = "handleResetScore", at = @At("TAIL"))
    private void onHandleResetScore(ClientboundResetScorePacket packet, CallbackInfo ci) {
        SignalProbe.getInstance().onScoreChanged();
    }

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void onHandleLogin(ClientboundLoginPacket packet, CallbackInfo ci) {
        SignalProbe.getInstance().onLogin(packet);
    }

    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void onHandleMovePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        SignalProbe.getInstance().onPlayerPosition(packet);
    }

    @Inject(method = "setTitleText", at = @At("TAIL"))
    private void onSetTitleText(ClientboundSetTitleTextPacket packet, CallbackInfo ci) {
        SignalProbe.getInstance().onTitle(packet.text());
    }

    @Inject(method = "setSubtitleText", at = @At("TAIL"))
    private void onSetSubtitleText(ClientboundSetSubtitleTextPacket packet, CallbackInfo ci) {
        SignalProbe.getInstance().onSubtitle(packet.text());
    }

    @Inject(method = "setActionBarText", at = @At("TAIL"))
    private void onSetActionBarText(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
        SignalProbe.getInstance().onActionBar(packet.text());
    }
}
