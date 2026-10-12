package com.example.examplemod.mixin;

import com.example.examplemod.feature.Freecam;
import com.example.examplemod.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mausbewegung des eigenen Spielers: bei Freecam dreht sie die Kamera (der Spieler behält seine
 * Blickrichtung), beim Zoomen wird sie verlangsamt, damit man genauer zielt.
 */
@Mixin(Entity.class)
public abstract class EntityTurnMixin {
    @Unique
    private static boolean examplemod$scaling;

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true, remap = false)
    private void examplemod$turn(double yaw, double pitch, CallbackInfo ci) {
        if ((Object) this != Minecraft.getInstance().player || examplemod$scaling) return;
        if (Freecam.active()) {
            ModuleManager.FREECAM.turn(yaw, pitch);
            ci.cancel();
            return;
        }
        double factor = ModuleManager.ZOOM == null ? 1.0 : ModuleManager.ZOOM.mouseFactor();
        if (factor < 1.0) {
            examplemod$scaling = true;
            try {
                ((Entity) (Object) this).turn(yaw * factor, pitch * factor);
            } finally {
                examplemod$scaling = false;
            }
            ci.cancel();
        }
    }
}
