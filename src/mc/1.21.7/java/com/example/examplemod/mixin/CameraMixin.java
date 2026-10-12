package com.example.examplemod.mixin;

import com.example.examplemod.feature.Freecam;
import com.example.examplemod.module.ModuleManager;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freecam: Kamera an die freie Position setzen und den eigenen Spieler sichtbar machen. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow private boolean detached;

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Shadow protected abstract void setPosition(double x, double y, double z);

    @Inject(method = "setup", at = @At("TAIL"), remap = false)
    private void examplemod$freecam(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse,
                                    float partialTick, CallbackInfo ci) {
        if (!Freecam.active()) return;
        Freecam freecam = ModuleManager.FREECAM;
        Vec3 pos = freecam.getCameraPos(partialTick);
        setRotation(freecam.getYaw(), freecam.getPitch());
        setPosition(pos.x, pos.y, pos.z);
        this.detached = true;
    }
}
