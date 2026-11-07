package com.cruvex.cubecraftplus.mixins;

import com.cruvex.cubecraftplus.autovote.AutoVoteManager;
import com.cruvex.cubecraftplus.autovote.MenuTracker;
import com.cruvex.cubecraftplus.debug.MenuProbe;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds {@link MenuTracker} and hides silent votes' menus; every inject sits past the thread handoff, so runs once. */
@Mixin(ClientPacketListener.class)
public class MenuTrackerMixin {

    /** Builds no screen, so player.containerMenu stays the inventory; TAIL never runs, so this feeds tracker and probe. */
    @Inject(
            method = "handleOpenScreen",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/MenuScreens;create("
                            + "Lnet/minecraft/world/inventory/MenuType;"
                            + "Lnet/minecraft/client/Minecraft;I"
                            + "Lnet/minecraft/network/chat/Component;)V"),
            cancellable = true)
    private void hideVoteMenu(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        if (!AutoVoteManager.getInstance().hidesMenus()) return;

        MenuTracker.getInstance().opened(packet.getContainerId(), packet.getTitle().getString(), true);
        MenuProbe.getInstance().onOpenScreen(packet);
        ci.cancel();
    }

    @Inject(method = "handleOpenScreen", at = @At("TAIL"))
    private void onOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        MenuTracker.getInstance().opened(packet.getContainerId(), packet.getTitle().getString(), false);
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

    /** Vanilla closes whatever screen is open, which would shut the player's chat for our hidden menu. */
    @Inject(
            method = "handleContainerClose",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;clientSideCloseContainer()V"),
            cancellable = true)
    private void keepScreenForHiddenMenu(ClientboundContainerClosePacket packet, CallbackInfo ci) {
        if (!MenuTracker.getInstance().isHidden(packet.getContainerId())) return;

        MenuTracker.getInstance().closedByServer(packet.getContainerId());
        MenuProbe.getInstance().onClose(packet);
        ci.cancel();
    }

    @Inject(method = "handleContainerClose", at = @At("TAIL"))
    private void onContainerClose(ClientboundContainerClosePacket packet, CallbackInfo ci) {
        MenuTracker.getInstance().closedByServer(packet.getContainerId());
    }
}
