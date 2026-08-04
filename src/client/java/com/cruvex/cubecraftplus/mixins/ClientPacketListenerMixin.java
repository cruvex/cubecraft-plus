package com.cruvex.cubecraftplus.mixins;

import com.cruvex.cubecraftplus.events.ScoreboardEvents;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(method = "handleAddObjective", at = @At("TAIL"))
    private void onHandleAddObjective(ClientboundSetObjectivePacket packet, CallbackInfo ci) {
        if (packet.getMethod() == ClientboundSetObjectivePacket.METHOD_REMOVE) return;

        ClientPacketListener self = (ClientPacketListener) (Object) this;
        Objective sidebar = self.getLevel().getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar != null && sidebar.getName().equals(packet.getObjectiveName())) {
            ScoreboardEvents.ADD_OBJECTIVE.invoker().onAddObjective(sidebar);
        }
    }

    @Inject(method = "handleSetPlayerTeamPacket", at = @At("TAIL"))
    private void onHandleSetPlayerTeam(ClientboundSetPlayerTeamPacket packet, CallbackInfo ci) {
        // At TAIL the team is already gone on REMOVE, so there is nothing to report
        if (packet.getTeamAction() == ClientboundSetPlayerTeamPacket.Action.REMOVE) return;

        ClientPacketListener self = (ClientPacketListener) (Object) this;
        PlayerTeam team = self.getLevel().getScoreboard().getPlayerTeam(packet.getName());
        if (team != null) {
            ScoreboardEvents.TEAM_CHANGE.invoker().onTeamChange(team);
        }
    }
}
