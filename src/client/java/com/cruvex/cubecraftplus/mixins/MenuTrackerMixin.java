package com.cruvex.cubecraftplus.mixins;

import com.cruvex.cubecraftplus.autovote.MenuTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds {@link MenuTracker} the inbound menu packets, at TAIL so each runs once, on the client thread. */
@Mixin(ClientPacketListener.class)
public class MenuTrackerMixin {

    @Inject(method = "handleOpenScreen", at = @At("TAIL"))
    private void onOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        MenuTracker.getInstance().opened(packet.getContainerId(), packet.getTitle().getString());
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void onContainerContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        MenuTracker.getInstance().setContents(packet.containerId(), packet.stateId(), packet.items());
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void onContainerSetSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        MenuTracker.getInstance().setSlot(
                packet.getContainerId(), packet.getStateId(), packet.getSlot(), packet.getItem());
    }

    @Inject(method = "handleContainerClose", at = @At("TAIL"))
    private void onContainerClose(ClientboundContainerClosePacket packet, CallbackInfo ci) {
        MenuTracker.getInstance().closedByServer(packet.getContainerId());
    }
}
