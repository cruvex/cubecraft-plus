package com.cruvex.cubecraftplus.mixins;

import com.cruvex.cubecraftplus.autovote.AutoVoteManager;
import com.cruvex.cubecraftplus.autovote.RemoteMenu;
import com.cruvex.cubecraftplus.debug.Debug;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds {@link RemoteMenu} from the container packets and hides the voting menus AutoVote is waiting for. */
// Every inject sits past PacketUtils.ensureRunningOnSameThread, so it runs once, on the client thread
@Mixin(ClientPacketListener.class)
public class ContainerMenuMixin {

    /** Takes a wanted menu into {@link RemoteMenu}, cancelling before a screen is built for it. */
    @Inject(
            method = "handleOpenScreen",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/MenuScreens;create("
                            + "Lnet/minecraft/world/inventory/MenuType;"
                            + "Lnet/minecraft/client/Minecraft;I"
                            + "Lnet/minecraft/network/chat/Component;)V"),
            cancellable = true)
    private void onOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        String title = packet.getTitle().getString();
        AutoVoteManager autoVote = AutoVoteManager.getInstance();
        boolean wanted = autoVote.wantsMenu(title);
        if (autoVote.isVoting()) {
            Debug.log("AutoVote: menu open id={} taken={} '{}'",
                    packet.getContainerId(), wanted, title);
        }
        if (!wanted) return;

        RemoteMenu.getInstance().opened(packet.getContainerId(), title);
        ci.cancel();
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void onContainerContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        if (packet.containerId() != 0 && AutoVoteManager.getInstance().isVoting()) {
            Debug.log("AutoVote: menu contents id={} stateId={} slots={}",
                    packet.containerId(), packet.stateId(), packet.items().size());
        }
        RemoteMenu.getInstance()
                .setContents(packet.containerId(), packet.stateId(), packet.items());
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void onContainerSetSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        if (packet.getContainerId() != 0 && AutoVoteManager.getInstance().isVoting()) {
            Debug.log("AutoVote: menu slot id={} stateId={} slot={}",
                    packet.getContainerId(), packet.getStateId(), packet.getSlot());
        }
        RemoteMenu.getInstance().setSlot(
                packet.getContainerId(), packet.getStateId(), packet.getSlot(), packet.getItem());
    }

    /** Cancels vanilla's close for our own menus, which have no screen for it to clear. */
    @Inject(
            method = "handleContainerClose",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;clientSideCloseContainer()V"),
            cancellable = true)
    private void onContainerClose(ClientboundContainerClosePacket packet, CallbackInfo ci) {
        boolean ours = RemoteMenu.getInstance().closedByServer(packet.getContainerId());
        if (AutoVoteManager.getInstance().isVoting()) {
            Debug.log("AutoVote: menu close id={} ours={}", packet.getContainerId(), ours);
        }
        if (ours) {
            ci.cancel();
        }
    }
}
