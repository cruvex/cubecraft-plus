package com.cruvex.cubecraftplus.mixins;

import com.cruvex.cubecraftplus.debug.MenuProbe;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSetCursorItemPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds {@link MenuProbe} the inbound menu packets, at TAIL so each runs once, on the client thread. */
@Mixin(ClientPacketListener.class)
public class MenuProbeMixin {

    @Inject(method = "handleOpenScreen", at = @At("TAIL"))
    private void onOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        MenuProbe.getInstance().onOpenScreen(packet);
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void onContainerContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        MenuProbe.getInstance().onContent(packet);
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void onContainerSetSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        MenuProbe.getInstance().onSetSlot(packet);
    }

    @Inject(method = "handleSetCursorItem", at = @At("TAIL"))
    private void onSetCursorItem(ClientboundSetCursorItemPacket packet, CallbackInfo ci) {
        MenuProbe.getInstance().onCursor(packet);
    }

    @Inject(method = "handleContainerClose", at = @At("TAIL"))
    private void onContainerClose(ClientboundContainerClosePacket packet, CallbackInfo ci) {
        MenuProbe.getInstance().onClose(packet);
    }
}
