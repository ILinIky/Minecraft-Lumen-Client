package com.example.examplemod.render;

import com.example.examplemod.ExampleMod;
import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Kartendaten der Minimap. Jeder geladene Chunk wird einmal in 16x16 Farben und Höhen
 * übersetzt und zwischengespeichert; nur wenige Chunks pro Tick, damit die Bildrate nicht leidet.
 * Daraus wird eine Textur rund um den Spieler zusammengesetzt (1 Pixel = 1 Block).
 */
public final class MinimapData {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "minimap");
    /** Ebene für die Ansicht von oben; sonst die Höhe, ab der im Höhlenmodus gesucht wird. */
    private static final int SURFACE = Integer.MIN_VALUE;
    private static final int NEW_PER_TICK = 8;
    private static final int NEW_PER_TICK_CAVE = 4;
    private static final int REFRESH_PER_TICK = 2;
    private static final long REFRESH_NEAR_MS = 1000;
    private static final long REFRESH_FAR_MS = 6000;
    private static final int COMPOSE_INTERVAL = 4;
    private static final int CAVE_SOLID = 0xFF24252B;
    private static final float[] SHADE = {0.71F, 0.86F, 1.0F};

    private static final class Tile {
        final int[] colors = new int[256];
        final int[] heights = new int[256];
        int layer;
        long scanned;
    }

    private final Long2ObjectOpenHashMap<Tile> tiles = new Long2ObjectOpenHashMap<>();
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final List<int[]> offsets = new ArrayList<>();
    private int offsetsRadius = -1;

    private ClientLevel level;
    private DynamicTexture texture;
    private int size;
    private int originX, originZ;
    private int centerChunkX = Integer.MIN_VALUE, centerChunkZ;
    private int layer = SURFACE;
    private boolean ready, changed;
    private int composeTimer, ticks;

    public boolean ready() { return ready && texture != null; }
    public int size() { return size; }
    public int originX() { return originX; }
    public int originZ() { return originZ; }
    public boolean caveView() { return layer != SURFACE; }

    /**
     * @param radius   sichtbarer Radius in Blöcken
     * @param rotating Karte dreht sich mit; dann wird für die Ecken mehr Fläche gebraucht
     * @param caveMode "Auto", "An" oder "Aus"
     */
    public void tick(Minecraft mc, int radius, boolean rotating, String caveMode) {
        ClientLevel current = mc.level;
        LocalPlayer player = mc.player;
        if (current == null || player == null) return;
        if (current != level) {
            // Neue Welt oder Dimension: alte Daten passen nicht mehr.
            tiles.clear();
            level = current;
            ready = false;
            centerChunkX = Integer.MIN_VALUE;
        }

        boolean cave = switch (caveMode) {
            case "An" -> true;
            case "Aus" -> false;
            default -> autoCave(player);
        };
        // In 4er-Schritten, damit nicht jede Treppenstufe alles neu einlesen lässt.
        layer = cave ? ((player.getBlockY() >> 2) << 2) + 4 : SURFACE;

        int needed = (int) Math.ceil(radius * 2 * (rotating ? 1.42 : 1.0)) + 32;
        int newSize = (needed + 15) & ~15;
        if (texture == null || newSize != size) createTexture(mc, newSize);

        int pcx = player.getBlockX() >> 4, pcz = player.getBlockZ() >> 4;
        int chunkRadius = size / 32 + 1;
        scan(mc, pcx, pcz, chunkRadius, cave);

        if (++ticks % 200 == 0) evict(pcx, pcz, chunkRadius * 2 + 4);

        boolean moved = pcx != centerChunkX || pcz != centerChunkZ;
        if (moved || (changed && ++composeTimer >= COMPOSE_INTERVAL)) {
            compose(pcx, pcz);
            composeTimer = 0;
            changed = false;
        }
    }

    /** Unter der Erde (kein Himmelslicht, weit unter der Oberfläche) oder in Dimensionen mit Decke. */
    private boolean autoCave(LocalPlayer player) {
        if (level.dimensionType().hasCeiling()) return true;
        BlockPos head = BlockPos.containing(player.getEyePosition());
        if (level.getBrightness(LightLayer.SKY, head) > 0) return false;
        return level.getHeight(Heightmap.Types.WORLD_SURFACE, head.getX(), head.getZ()) - head.getY() > 5;
    }

    private void createTexture(Minecraft mc, int newSize) {
        if (texture != null) mc.getTextureManager().release(TEXTURE);
        texture = new DynamicTexture(() -> "examplemod minimap", newSize, newSize, true);
        mc.getTextureManager().register(TEXTURE, texture);
        size = newSize;
        centerChunkX = Integer.MIN_VALUE;
    }

    private List<int[]> offsets(int radius) {
        if (radius != offsetsRadius) {
            offsets.clear();
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) offsets.add(new int[]{dx, dz});
            }
            offsets.sort(Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
            offsetsRadius = radius;
        }
        return offsets;
    }

    private void scan(Minecraft mc, int pcx, int pcz, int radius, boolean cave) {
        long now = Util.getMillis();
        int budgetNew = cave ? NEW_PER_TICK_CAVE : NEW_PER_TICK;
        int budgetRefresh = REFRESH_PER_TICK;
        for (int[] offset : offsets(radius)) {
            if (budgetNew <= 0 && budgetRefresh <= 0) break;
            int cx = pcx + offset[0], cz = pcz + offset[1];
            Tile tile = tiles.get(ChunkPos.asLong(cx, cz));
            boolean missing = tile == null || tile.layer != layer;
            if (missing ? budgetNew <= 0 : budgetRefresh <= 0) continue;
            if (!missing) {
                boolean near = Math.abs(offset[0]) <= 2 && Math.abs(offset[1]) <= 2;
                if (now - tile.scanned < (near ? REFRESH_NEAR_MS : REFRESH_FAR_MS)) continue;
            }
            LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
            if (chunk == null) continue;
            if (tile == null) {
                tile = new Tile();
                tiles.put(ChunkPos.asLong(cx, cz), tile);
            }
            scanChunk(mc, chunk, cx, cz, tile);
            tile.layer = layer;
            tile.scanned = now;
            changed = true;
            if (missing) budgetNew--; else budgetRefresh--;
        }
    }

    private void scanChunk(Minecraft mc, LevelChunk chunk, int cx, int cz, Tile tile) {
        int minY = level.getMinY();
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int i = lz * 16 + lx;
                int bx = (cx << 4) + lx, bz = (cz << 4) + lz;
                int y = layer == SURFACE ? chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz) : caveStart(chunk, bx, bz, minY);
                if (y == Integer.MIN_VALUE) {
                    tile.colors[i] = CAVE_SOLID;
                    tile.heights[i] = layer;
                    continue;
                }
                // Durchsichtige Blöcke ohne Kartenfarbe (Glas, Luft) überspringen.
                BlockState state = null;
                MapColor mapColor = MapColor.NONE;
                for (; y >= minY; y--) {
                    state = chunk.getBlockState(pos.set(bx, y, bz));
                    mapColor = state.getMapColor(level, pos);
                    if (mapColor != MapColor.NONE) break;
                }
                if (mapColor == MapColor.NONE) {
                    tile.colors[i] = 0;
                    tile.heights[i] = minY;
                    continue;
                }
                tile.colors[i] = color(mc, chunk, state, mapColor, bx, y, bz, minY);
                tile.heights[i] = y;
            }
        }
    }

    /**
     * Höhlenmodus: von der Suchhöhe abwärts erst durch Gestein bis zu einem Hohlraum, dann bis
     * zu dessen Boden. Ohne Hohlraum in Reichweite gilt die Stelle als massiv.
     */
    private int caveStart(LevelChunk chunk, int bx, int bz, int minY) {
        int y = Math.min(layer, level.getMaxY());
        int limit = Math.max(minY, y - 64);
        int solid = 0;
        while (y > limit && !open(chunk, bx, y, bz)) {
            if (++solid > 12) return Integer.MIN_VALUE;
            y--;
        }
        while (y > limit && open(chunk, bx, y, bz)) y--;
        return y;
    }

    private boolean open(LevelChunk chunk, int bx, int y, int bz) {
        BlockState state = chunk.getBlockState(pos.set(bx, y, bz));
        return state.isAir() || state.getMapColor(level, pos) == MapColor.NONE;
    }

    private int color(Minecraft mc, LevelChunk chunk, BlockState state, MapColor mapColor, int bx, int y, int bz, int minY) {
        int base;
        pos.set(bx, y, bz);
        if (mapColor == MapColor.GRASS || mapColor == MapColor.PLANT || mapColor == MapColor.WATER) {
            // Biomfarben für Gras, Laub und Wasser statt der festen Kartenfarbe.
            int tint = mc.getBlockColors().getColor(state, level, pos, 0);
            base = tint != -1 ? 0xFF000000 | tint : mapColor.calculateARGBColor(MapColor.Brightness.HIGH);
        } else {
            base = mapColor.calculateARGBColor(MapColor.Brightness.HIGH);
        }
        if (state.getFluidState().is(FluidTags.WATER)) {
            int depth = 0;
            while (depth < 10 && y - depth - 1 >= minY
                    && chunk.getBlockState(pos.set(bx, y - depth - 1, bz)).getFluidState().is(FluidTags.WATER)) depth++;
            base = shade(base, 1.0F - depth * 0.045F);
        }
        return base;
    }

    private static int shade(int argb, float factor) {
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((argb & 0xFF) * factor));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Setzt die Textur aus den Chunk-Daten zusammen; Schattierung wie bei Karten (Höhe zum nördlichen Nachbarn). */
    private void compose(int pcx, int pcz) {
        int chunks = size / 16;
        int ocx = pcx - chunks / 2, ocz = pcz - chunks / 2;
        originX = ocx << 4;
        originZ = ocz << 4;
        centerChunkX = pcx;
        centerChunkZ = pcz;
        NativeImage image = texture.getPixels();
        if (image == null) return;
        for (int tz = 0; tz < chunks; tz++) {
            for (int tx = 0; tx < chunks; tx++) {
                Tile tile = tiles.get(ChunkPos.asLong(ocx + tx, ocz + tz));
                Tile north = tiles.get(ChunkPos.asLong(ocx + tx, ocz + tz - 1));
                for (int lz = 0; lz < 16; lz++) {
                    for (int lx = 0; lx < 16; lx++) {
                        int argb = 0;
                        if (tile != null) {
                            int i = lz * 16 + lx;
                            int c = tile.colors[i];
                            if (c != 0) {
                                int h = tile.heights[i];
                                int nh = h;
                                if (lz > 0) {
                                    if (tile.colors[i - 16] != 0) nh = tile.heights[i - 16];
                                } else if (north != null && north.colors[i + 240] != 0) {
                                    nh = north.heights[i + 240];
                                }
                                argb = c == CAVE_SOLID ? c : shade(c, SHADE[Integer.compare(h, nh) + 1]);
                            }
                        }
                        image.setPixel(tx * 16 + lx, tz * 16 + lz, argb);
                    }
                }
            }
        }
        texture.upload();
        ready = true;
    }

    private void evict(int pcx, int pcz, int keep) {
        tiles.long2ObjectEntrySet().removeIf(entry -> {
            long key = entry.getLongKey();
            return Math.abs(ChunkPos.getX(key) - pcx) > keep || Math.abs(ChunkPos.getZ(key) - pcz) > keep;
        });
    }

    /** Daten und Textur freigeben (Modul aus, Welt verlassen). */
    public void close(Minecraft mc) {
        tiles.clear();
        level = null;
        ready = false;
        centerChunkX = Integer.MIN_VALUE;
        if (texture != null) {
            mc.getTextureManager().release(TEXTURE);
            texture = null;
        }
    }
}
