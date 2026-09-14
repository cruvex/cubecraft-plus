package com.cruvex.cubecraftplus.mixins;

import com.cruvex.cubecraftplus.managers.AutoVoteManager;
import com.cruvex.cubecraftplus.managers.RemoteMenu;
import com.cruvex.cubecraftplus.util.Debug;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds {@link RemoteMenu} from the container packets and hides the voting menus AutoVote is
 * waiting for.
 *
 * Every handle* method runs twice, once per thread, until PacketUtils.ensureRunningOnSameThread
 * throws on the netty pass. Only injects past that check are on the client thread, so never
 * move these to HEAD.
 */
@Mixin(ClientPacketListener.class)
public class ContainerMenuMixin {

    /** Cancelling here means no screen is built and player.containerMenu is never assigned. */
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

    /** Our menus have no screen, so vanilla must not clear whatever the player does have open. */
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
