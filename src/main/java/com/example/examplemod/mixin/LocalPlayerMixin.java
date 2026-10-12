package com.example.examplemod.mixin;

import com.example.examplemod.feature.NoFall;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** NoFall: Während eines Sturzes "am Boden" melden, damit der Server keine Fallhöhe sammelt. */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Redirect(method = "sendPosition", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;onGround()Z", remap = false), remap = false)
    private boolean examplemod$spoofOnGround(LocalPlayer player) {
        return player.onGround() || NoFall.shouldSpoofOnGround(player);
    }
}
