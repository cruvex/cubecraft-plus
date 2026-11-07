package com.cruvex.cubecraftplus.mixins;

import com.cruvex.cubecraftplus.debug.MenuProbe;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds {@link MenuProbe} the outbound packets, which all leave through this one method. */
@Mixin(ClientCommonPacketListenerImpl.class)
public class ClientCommonPacketListenerMixin {

    @Inject(method = "send", at = @At("HEAD"))
    private void onSend(Packet<?> packet, CallbackInfo ci) {
        MenuProbe.getInstance().onSend(packet);
    }
}
