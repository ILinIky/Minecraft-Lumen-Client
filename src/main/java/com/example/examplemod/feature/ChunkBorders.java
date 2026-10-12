package com.example.examplemod.feature;

import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import com.example.examplemod.render.Render3D;
import com.example.examplemod.render.WorldRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.SectionPos;

/** Zeigt die Grenzen des aktuellen Chunks (gelb, mit Raster) und der Nachbar-Chunks (rot). */
public class ChunkBorders extends Module {
    private static final int CURRENT = 0xFFFBBF24;
    private static final int GRID = 0x70FBBF24;
    private static final int NEIGHBOR = 0xB0F87171;

    public ChunkBorders() {
        super("chunk_borders", "Chunk-Grenzen", "Grenzen der Chunks als Linien anzeigen", Category.RENDER);
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        if (mc.player == null || mc.level == null) return;
        int chunkX = SectionPos.blockToSectionCoord(mc.player.getBlockX()) << 4;
        int chunkZ = SectionPos.blockToSectionCoord(mc.player.getBlockZ()) << 4;
        int minY = Math.max(mc.level.getMinY(), mc.player.getBlockY() - 48);
        int maxY = Math.min(mc.level.getMaxY() + 1, mc.player.getBlockY() + 48);
        VertexConsumer lines = Render3D.worldLines(context);
        PoseStack.Pose pose = context.poseStack().last();

        // Ecken der Nachbar-Chunks
        for (int dx = -16; dx <= 32; dx += 16) {
            for (int dz = -16; dz <= 32; dz += 16) {
                boolean currentCorner = (dx == 0 || dx == 16) && (dz == 0 || dz == 16);
                if (currentCorner) continue;
                vertical(context, pose, lines, chunkX + dx, chunkZ + dz, minY, maxY, NEIGHBOR);
            }
        }
        // Aktueller Chunk: Wände mit Raster alle 2 Blöcke, waagerecht an jeder Abschnittsgrenze (16 Blöcke)
        for (int i = 0; i <= 16; i += 2) {
            int color = i == 0 || i == 16 ? CURRENT : GRID;
            vertical(context, pose, lines, chunkX + i, chunkZ, minY, maxY, color);
            vertical(context, pose, lines, chunkX + i, chunkZ + 16, minY, maxY, color);
            vertical(context, pose, lines, chunkX, chunkZ + i, minY, maxY, color);
            vertical(context, pose, lines, chunkX + 16, chunkZ + i, minY, maxY, color);
        }
        for (int y = (minY + 15) & ~15; y <= maxY; y += 16) {
            float ry = context.ry(y);
            float x1 = context.rx(chunkX), x2 = context.rx(chunkX + 16);
            float z1 = context.rz(chunkZ), z2 = context.rz(chunkZ + 16);
            Render3D.line(pose, lines, x1, ry, z1, x2, ry, z1, CURRENT);
            Render3D.line(pose, lines, x2, ry, z1, x2, ry, z2, CURRENT);
            Render3D.line(pose, lines, x2, ry, z2, x1, ry, z2, CURRENT);
            Render3D.line(pose, lines, x1, ry, z2, x1, ry, z1, CURRENT);
        }
        Render3D.flushWorld(context);
    }

    private static void vertical(WorldRenderContext context, PoseStack.Pose pose, VertexConsumer lines,
                                 int x, int z, int minY, int maxY, int color) {
        float rx = context.rx(x), rz = context.rz(z);
        Render3D.line(pose, lines, rx, context.ry(minY), rz, rx, context.ry(maxY), rz, color);
    }
}
