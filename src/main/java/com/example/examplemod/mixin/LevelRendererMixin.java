package com.example.examplemod.mixin;

import com.example.examplemod.module.ModuleManager;
import com.example.examplemod.render.WorldRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    // Forge 1.21.7 has no NeoForge RenderLevelStageEvent. Draw once, after the world
    // frame graph finishes and before Minecraft restores the camera matrix.
    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lorg/joml/Matrix4fStack;popMatrix()Lorg/joml/Matrix4fStack;",
            shift = At.Shift.BEFORE, remap = false), remap = false)
    private void examplemod$renderOverlays(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Camera camera = mc.gameRenderer.getMainCamera();
        WorldRenderContext context = new WorldRenderContext(new PoseStack(), camera, camera.getPosition(),
                mc.renderBuffers().bufferSource(), mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        ModuleManager.onWorldRender(context);
    }
}
