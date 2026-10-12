package com.example.examplemod.mixin;

import com.example.examplemod.feature.XRay;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** X-Ray: nicht ausgewählte Blöcke und Flüssigkeiten beim Chunk-Bau überspringen. */
@Mixin(BlockRenderDispatcher.class)
public abstract class BlockRenderDispatcherMixin {
    @Inject(method = "renderBatched", at = @At("HEAD"), cancellable = true, remap = false)
    private void examplemod$xrayBlock(BlockState state, BlockPos pos, BlockAndTintGetter level, PoseStack poseStack,
                                      VertexConsumer consumer, boolean checkSides, List<BlockModelPart> parts,
                                      CallbackInfo ci) {
        if (XRay.hides(state)) ci.cancel();
    }

    @Inject(method = "renderLiquid", at = @At("HEAD"), cancellable = true, remap = false)
    private void examplemod$xrayFluid(BlockPos pos, BlockAndTintGetter level, VertexConsumer consumer,
                                      BlockState blockState, FluidState fluidState, CallbackInfo ci) {
        if (XRay.hidesFluid(fluidState)) ci.cancel();
    }
}
