package com.example.examplemod.render;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.blueprint.Blueprint;
import com.example.examplemod.blueprint.BlueprintPlan;
import com.example.examplemod.gui.Theme;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Real block-model geometry/textures, depth-tested and alpha blended on the final main target. */
public final class BlueprintRenderer {
    private static final int MAX_VISIBLE = 4096;
    /** Blaue "fehlt"-Konturen nur in der Nähe; weiter weg reicht die Geistervorschau. */
    private static final double NEAR_OUTLINE_SQ = 8 * 8;
    private static final RenderPipeline PIPELINE = RenderPipeline.builder(RenderPipelines.TERRAIN_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "pipeline/blueprint"))
            .withBlend(BlendFunction.TRANSLUCENT).withDepthWrite(false)
            .withShaderDefine("ALPHA_CUTOUT", 0.01F).build();
    private static final RenderType GHOST = RenderType.create("examplemod_blueprint", 2 * 1024 * 1024, false, true, PIPELINE,
            RenderType.CompositeState.builder().setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
                    .setLightmapState(RenderStateShard.LIGHTMAP).setOutputState(RenderStateShard.MAIN_TARGET)
                    .createCompositeState(false));
    private record Candidate(int index, BlockPos pos, double distance) {}
    private List<Candidate> candidates = List.of();
    private final Set<BlockState> failedModels = new HashSet<>();
    private long refreshed;
    private BlueprintPlan previous;
    private BlockPos anchor;
    private int turns, layer, range;
    private boolean mirrored, onlyMissing;
    private BlueprintPlan.Layers layers;
    private boolean limited;
    private final RandomSource random = RandomSource.create();
    private final List<BlockModelPart> parts = new ArrayList<>();

    public boolean limited() { return limited; }
    public void clear() { candidates = List.of(); previous = null; refreshed = 0; failedModels.clear(); limited = false; }

    public void render(BlueprintPlan plan, WorldRenderContext context, float opacity, int distance, boolean outlines) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        long now = Util.getMillis();
        if (previous != plan || now - refreshed > 200 || !plan.origin().equals(anchor) || turns != plan.turns()
                || mirrored != plan.mirrored() || layers != plan.layers || layer != plan.layer || range != distance || onlyMissing != plan.onlyMissing) {
            List<Candidate> nearby = new ArrayList<>();
            var indices = plan.nearby(BlockPos.containing(context.cameraPos()), distance + 2);
            for (int n = 0; n < indices.size(); n++) {
                int i = indices.getInt(n);
                Blueprint.Cell cell = plan.blueprint.cells().get(i);
                if (!plan.visibleLayer(cell.y())) continue;
                BlockPos pos = plan.position(cell);
                double d = Vec3.atCenterOf(pos).distanceToSqr(context.cameraPos());
                if (d > (double) distance * distance || !level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
                if (plan.onlyMissing && plan.compare(level, cell, pos) == BlueprintPlan.Status.CORRECT) continue;
                nearby.add(new Candidate(i, pos, d));
            }
            nearby.sort(Comparator.comparingDouble(Candidate::distance));
            limited = nearby.size() > MAX_VISIBLE;
            candidates = new ArrayList<>(nearby.subList(0, Math.min(MAX_VISIBLE, nearby.size())));
            // Back-to-front, additionally sorted per quad by the translucent RenderType.
            candidates.sort(Comparator.comparingDouble(Candidate::distance).reversed());
            previous = plan; anchor = plan.origin(); turns = plan.turns(); mirrored = plan.mirrored();
            layer = plan.layer; layers = plan.layers; range = distance; onlyMissing = plan.onlyMissing; refreshed = now;
        }
        PreviewWorld view = new PreviewWorld(plan, level);
        var poses = context.poseStack();
        var dispatcher = mc.getBlockRenderer();
        VertexConsumer ghost = new AlphaConsumer(context.buffers().getBuffer(GHOST), opacity);
        // Keep only lightweight error boxes; do not interleave line and quad buffers.
        List<Candidate> markers = new ArrayList<>();
        List<Candidate> fills = new ArrayList<>();
        for (Candidate candidate : candidates) {
            Blueprint.Cell cell = plan.blueprint.cells().get(candidate.index);
            BlueprintPlan.Status status = plan.compare(level, cell, candidate.pos);
            if (status == BlueprintPlan.Status.UNCHECKED || (plan.onlyMissing && status == BlueprintPlan.Status.CORRECT)) continue;
            BlockState state = plan.state(cell);
            boolean model = state != null && state.getRenderShape() == RenderShape.MODEL && !failedModels.contains(state);
            // A real wrong block occludes the ghost; its outline remains visible at the block surface.
            if (model && status != BlueprintPlan.Status.WRONG_BLOCK && status != BlueprintPlan.Status.WRONG_STATE) {
                poses.pushPose();
                poses.translate(context.rx(candidate.pos.getX()), context.ry(candidate.pos.getY()), context.rz(candidate.pos.getZ()));
                try {
                    parts.clear();
                    random.setSeed(state.getSeed(candidate.pos));
                    dispatcher.getBlockModel(state).collectParts(random, parts);
                    dispatcher.getModelRenderer().tesselateWithoutAO(view, parts, state, candidate.pos, poses, ghost, true, OverlayTexture.NO_OVERLAY);
                } catch (RuntimeException failure) {
                    if (failedModels.add(state)) ExampleMod.LOGGER.warn("Bauplan: Blockmodell wird als Kontur dargestellt: {}", state, failure);
                    model = false;
                } finally { poses.popPose(); }
            }
            // Truhen, Betten, Schilder, Köpfe und Flüssigkeiten haben kein normales Blockmodell:
            // halbtransparent in ihrer Kartenfarbe füllen, damit sie trotzdem als Körper erkennbar sind.
            if (!model && state != null && status == BlueprintPlan.Status.MISSING) fills.add(candidate);
            boolean nearMissing = status != BlueprintPlan.Status.MISSING || candidate.distance <= NEAR_OUTLINE_SQ;
            if ((!model || (outlines && status != BlueprintPlan.Status.CORRECT)) && nearMissing) markers.add(candidate);
        }
        context.buffers().endBatch(GHOST);

        if (!fills.isEmpty()) {
            RenderType quadsType = RenderType.debugQuads();
            VertexConsumer quads = context.buffers().getBuffer(quadsType);
            int alpha = Math.round(Math.min(1F, opacity * 0.8F) * 255);
            for (Candidate fill : fills) {
                BlockState state = plan.state(plan.blueprint.cells().get(fill.index));
                int color = (alpha << 24) | (fillColor(state, view, level, fill.pos) & 0xFFFFFF);
                for (AABB box : boxes(state, view, fill.pos)) {
                    Render3D.filledBox(poses.last(), quads, box.move(context.rx(fill.pos.getX()),
                            context.ry(fill.pos.getY()), context.rz(fill.pos.getZ())).deflate(0.01), color);
                }
            }
            context.buffers().endBatch(quadsType);
        }

        VertexConsumer lines = Render3D.worldLines(context);
        for (Candidate marker : markers) {
            Blueprint.Cell cell = plan.blueprint.cells().get(marker.index);
            BlockState state = plan.state(cell);
            BlueprintPlan.Status status = plan.compare(level, cell, marker.pos);
            int color = status.color;
            List<AABB> boxes = List.of(new AABB(0, 0, 0, 1, 1, 1));
            if (state != null && status != BlueprintPlan.Status.WRONG_BLOCK && status != BlueprintPlan.Status.WRONG_STATE) {
                boxes = boxes(state, view, marker.pos);
            }
            for (AABB box : boxes) Render3D.box(context, lines, box.move(marker.pos).inflate(0.003), color);
        }
        var size = plan.transform();
        BlockPos o = plan.origin();
        Render3D.box(context, lines, new AABB(o.getX(), o.getY(), o.getZ(), o.getX() + size.width(),
                o.getY() + plan.blueprint.sizeY(), o.getZ() + size.depth()), Theme.alpha(Theme.ACCENT, 0.45F));
        // Ursprung als kleiner Würfel in der zweiten Akzentfarbe.
        Render3D.box(context, lines, new AABB(o).deflate(0.3), Theme.ACCENT_2);
        Render3D.flushWorld(context);
    }

    private static List<AABB> boxes(BlockState state, BlockAndTintGetter view, BlockPos pos) {
        if (state.getBlock() instanceof LiquidBlock) return List.of(new AABB(0, 0, 0, 1, 0.875, 1));
        try {
            VoxelShape shape = state.getShape(view, pos);
            if (!shape.isEmpty()) return shape.toAabbs();
        } catch (RuntimeException ignored) { /* Modded special models get a unit outline. */ }
        return List.of(new AABB(0, 0, 0, 1, 1, 1));
    }

    private static int fillColor(BlockState state, BlockAndTintGetter view, ClientLevel level, BlockPos pos) {
        if (state.is(Blocks.WATER)) return BiomeColors.getAverageWaterColor(level, pos);
        if (state.is(Blocks.LAVA)) return 0xFF7A1A;
        try {
            int color = state.getMapColor(view, pos).col;
            return color == 0 ? 0xA3A6B8 : color;
        } catch (RuntimeException ignored) {
            return 0xA3A6B8;
        }
    }

    private record PreviewWorld(BlueprintPlan plan, ClientLevel actual) implements BlockAndTintGetter {
        @Override public BlockState getBlockState(BlockPos pos) {
            BlockState state = plan.stateAt(pos);
            return state == null ? Blocks.AIR.defaultBlockState() : state;
        }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
        @Override public int getHeight() { return actual.getHeight(); }
        @Override public int getMinY() { return actual.getMinY(); }
        @Override public float getShade(Direction direction, boolean shade) { return actual.getShade(direction, shade); }
        @Override public LevelLightEngine getLightEngine() { return actual.getLightEngine(); }
        @Override public int getBlockTint(BlockPos pos, ColorResolver resolver) { return actual.getBlockTint(pos, resolver); }
        @Override public int getBrightness(LightLayer layer, BlockPos pos) { return 15; }
        @Override public int getRawBrightness(BlockPos pos, int darkness) { return 15; }
    }

    private record AlphaConsumer(VertexConsumer target, float alpha) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) { target.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { target.setColor(r, g, b, Math.round(a * alpha)); return this; }
        @Override public VertexConsumer setUv(float u, float v) { target.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { target.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { target.setUv2(240, 240); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { target.setNormal(x, y, z); return this; }
    }
}
