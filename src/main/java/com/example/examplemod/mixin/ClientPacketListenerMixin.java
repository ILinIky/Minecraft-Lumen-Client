package com.example.examplemod.mixin;

import com.example.examplemod.waypoint.TeleportService;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Beobachtet die von Vanilla bereits angewendete und bestätigte Server-Position. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleMovePlayer", at = @At("RETURN"), remap = false)
    private void examplemod$afterServerPosition(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        TeleportService.onServerPosition((ClientPacketListener) (Object) this);
    }
}
