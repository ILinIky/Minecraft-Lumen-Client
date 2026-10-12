package com.example.examplemod.mixin;

import com.example.examplemod.feature.XRay;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * X-Ray: Bei sichtbaren Blöcken alle Seiten zeichnen (sonst verdeckt der ausgeblendete Stein sie)
 * und ohne Umgebungsverdeckung, damit Erze nicht schwarz wirken.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {
    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true, remap = false)
    private static void examplemod$xrayAllFaces(BlockAndTintGetter level, BlockState state, boolean checkSides,
                                                Direction direction, BlockPos neighborPos, CallbackInfoReturnable<Boolean> cir) {
        if (XRay.isActive()) cir.setReturnValue(true);
    }

    @Inject(method = "tesselateWithAO", at = @At("HEAD"), cancellable = true, remap = false)
    private void examplemod$xrayFlatLighting(BlockAndTintGetter level, List<BlockModelPart> parts, BlockState state,
                                             BlockPos pos, PoseStack poseStack, VertexConsumer consumer,
                                             boolean checkSides, int packedOverlay, CallbackInfo ci) {
        if (XRay.isActive()) {
            ((ModelBlockRenderer) (Object) this).tesselateWithoutAO(level, parts, state, pos, poseStack, consumer,
                    checkSides, packedOverlay);
            ci.cancel();
        }
    }
}
