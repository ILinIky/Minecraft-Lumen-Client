package com.example.examplemod.feature;

import com.example.examplemod.gui.BlockSelectScreen;
import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.Setting;
import com.example.examplemod.render.Render3D;
import com.example.examplemod.render.WorldRenderContext;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Markiert ausgewählte Blöcke durch Wände. Statt jeden Block einzeln abzufragen werden nur
 * Chunk-Abschnitte durchsucht, deren Palette einen gesuchten Block enthalten kann.
 */
public class BlockEsp extends Module {
    private static final int MAX_RESULTS = 1500;
    private static final int MAX_TRACERS = 64;

    private final Setting.Slider radius;
    private final Setting.Bool tracers;
    private final Setting.Bool fill;

    private volatile Set<Block> targets = Set.of(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.ANCIENT_DEBRIS);
    private List<Found> found = List.of();
    private int ticksUntilScan;

    private record Found(BlockPos pos, int color) {}

    public BlockEsp() {
        super("block_esp", "Block-ESP", "Findet ausgewählte Blöcke durch Wände", Category.RENDER);
        radius = add(new Setting.Slider("Radius", "Suchradius in Blöcken", 64, 16, 160, 8, "Bl.").onChange(this::rescanSoon));
        tracers = add(new Setting.Bool("Tracer", "Linien zu den nächsten Funden", false));
        fill = add(new Setting.Bool("Doppelrahmen", "Zusätzlicher innerer Rahmen für bessere Sichtbarkeit", false));
        add(new Setting.Action("Blöcke auswählen", "Welche Blöcke gesucht werden", () ->
                mc.setScreen(new BlockSelectScreen(mc.screen, "Block-ESP Auswahl", targets, blocks -> {
                    targets = Set.copyOf(blocks);
                    rescanSoon();
                }))));
    }

    private void rescanSoon() { ticksUntilScan = 0; }

    @Override
    protected void onEnable() { rescanSoon(); }

    @Override
    protected void onDisable() { found = List.of(); }

    @Override
    public void onLeaveWorld() { found = List.of(); }

    @Override
    public String getHudInfo() { return String.valueOf(found.size()); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null) return;
        if (--ticksUntilScan > 0) return;
        ticksUntilScan = 20;
        found = scan();
    }

    private List<Found> scan() {
        Set<Block> wanted = targets;
        if (wanted.isEmpty()) return List.of();
        BlockPos center = mc.player.blockPosition();
        int r = radius.getInt();
        long maxDistSq = (long) r * r;
        int minSection = mc.level.getMinSectionY();
        int maxSection = mc.level.getMaxSectionY();
        int centerChunkX = SectionPos.blockToSectionCoord(center.getX());
        int centerChunkZ = SectionPos.blockToSectionCoord(center.getZ());
        int chunkRadius = (r >> 4) + 1;
        int sectionFrom = Math.max(minSection, SectionPos.blockToSectionCoord(center.getY() - r));
        int sectionTo = Math.min(maxSection, SectionPos.blockToSectionCoord(center.getY() + r));

        List<Found> results = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
            for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                for (int sy = sectionFrom; sy <= sectionTo; sy++) {
                    LevelChunkSection section = chunk.getSection(sy - minSection);
                    if (section.hasOnlyAir() || !section.maybeHas(state -> wanted.contains(state.getBlock()))) continue;
                    int baseX = cx << 4, baseY = sy << 4, baseZ = cz << 4;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                BlockState state = section.getBlockState(x, y, z);
                                if (!wanted.contains(state.getBlock())) continue;
                                cursor.set(baseX + x, baseY + y, baseZ + z);
                                if (cursor.distSqr(center) > maxDistSq) continue;
                                results.add(new Found(cursor.immutable(), BlockLists.color(state.getBlock())));
                            }
                        }
                    }
                }
            }
        }
        if (results.size() > MAX_RESULTS) {
            results.sort(Comparator.comparingDouble(f -> f.pos.distSqr(center)));
            results = new ArrayList<>(results.subList(0, MAX_RESULTS));
        }
        return results;
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        List<Found> snapshot = found;
        if (snapshot.isEmpty()) return;
        VertexConsumer lines = Render3D.lines(context);
        for (Found entry : snapshot) {
            AABB box = new AABB(entry.pos);
            Render3D.box(context, lines, box, entry.color);
            if (fill.get()) Render3D.box(context, lines, box.deflate(0.12), entry.color & 0x80FFFFFF);
        }
        if (tracers.get()) {
            Vec3 eye = context.cameraPos();
            snapshot.stream()
                    .sorted(Comparator.comparingDouble(f -> f.pos.getCenter().distanceToSqr(eye)))
                    .limit(MAX_TRACERS)
                    .forEach(f -> Render3D.tracer(context, lines, f.pos.getCenter(), f.color & 0xB0FFFFFF));
        }
        Render3D.flush(context);
    }

    @Override
    public void saveExtra(JsonObject json) {
        json.add("blocks", BlockLists.toJson(targets));
    }

    @Override
    public void loadExtra(JsonObject json) {
        if (json.has("blocks")) targets = Set.copyOf(BlockLists.fromJson(json.getAsJsonArray("blocks")));
    }
}
