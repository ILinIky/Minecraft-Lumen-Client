package com.example.examplemod.compat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ChestModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** Detached preview models: never insert block entities into the real world. */
public final class BlueprintSpecialModels {
    private EntityModelSet modelSet;
    private ChestModel single, left, right;
    private final Map<BlockState, SignBlockEntity> signs = new HashMap<>();
    private final Map<BlockState, Material> chestMaterials = new HashMap<>();

    public static boolean supports(BlockState state) {
        return state.getBlock() instanceof ChestBlock || state.getBlock() instanceof EnderChestBlock
                || state.getBlock() instanceof SignBlock;
    }

    public void clear() { signs.clear(); chestMaterials.clear(); modelSet = null; single = left = right = null; }

    public boolean render(BlockState state, PoseStack poses, Function<ResourceLocation, VertexConsumer> buffers) {
        Minecraft mc = Minecraft.getInstance();
        if (state.getBlock() instanceof SignBlock signBlock) {
            SignBlockEntity sign = signs.computeIfAbsent(state,
                    key -> (SignBlockEntity) signBlock.newBlockEntity(BlockPos.ZERO, key));
            var renderer = mc.getBlockEntityRenderDispatcher().getRenderer(sign);
            if (renderer == null) return false;
            Material material = sign instanceof HangingSignBlockEntity
                    ? Sheets.getHangingSignMaterial(signBlock.type()) : Sheets.getSignMaterial(signBlock.type());
            // Empty sign text: only the wooden model emits vertices. The native renderer handles
            // standing/wall/hanging transforms, wood variants, chains and attachment states.
            renderer.render(sign, 0, poses, type -> buffers.apply(material.atlasLocation()),
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, Vec3.ZERO);
            return true;
        }
        if (!(state.getBlock() instanceof ChestBlock) && !(state.getBlock() instanceof EnderChestBlock)) return false;
        if (modelSet != mc.getEntityModels()) {
            modelSet = mc.getEntityModels();
            single = new ChestModel(modelSet.bakeLayer(ModelLayers.CHEST));
            left = new ChestModel(modelSet.bakeLayer(ModelLayers.DOUBLE_CHEST_LEFT));
            right = new ChestModel(modelSet.bakeLayer(ModelLayers.DOUBLE_CHEST_RIGHT));
        }
        ChestType type = state.hasProperty(ChestBlock.TYPE) ? state.getValue(ChestBlock.TYPE) : ChestType.SINGLE;
        ChestModel model = switch (type) { case LEFT -> left; case RIGHT -> right; default -> single; };
        // Use the planned chest half, even if the other half has not been built yet.
        Material material = chestMaterials.computeIfAbsent(state, key -> {
            var entity = ((net.minecraft.world.level.block.EntityBlock) key.getBlock()).newBlockEntity(BlockPos.ZERO, key);
            return Sheets.chooseMaterial(entity, type, false);
        });
        poses.translate(0.5F, 0.5F, 0.5F);
        poses.mulPose(Axis.YP.rotationDegrees(-state.getValue(ChestBlock.FACING).toYRot()));
        poses.translate(-0.5F, -0.5F, -0.5F);
        model.setupAnim(0);
        model.renderToBuffer(poses, material.sprite().wrap(buffers.apply(material.atlasLocation())),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        return true;
    }
}
