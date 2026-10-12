package com.example.examplemod.blueprint;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nimmt einen Bereich der geladenen Welt als {@link Blueprint} auf. Liest nur, was der Client kennt:
 * Blockzustände ja, Inhalte von Kisten oder Schildtexte nicht.
 */
public final class BlueprintCapture {
    /** Obergrenze für die Aufnahme; größere Bereiche bitte in Teile zerlegen. */
    public static final long MAX_VOLUME = 2_000_000;

    private BlueprintCapture() {}

    public static long volume(BlockPos a, BlockPos b) {
        return (long) (Math.abs(a.getX() - b.getX()) + 1) * (Math.abs(a.getY() - b.getY()) + 1) * (Math.abs(a.getZ() - b.getZ()) + 1);
    }

    /** Liest den Bereich zwischen zwei Ecken (beide eingeschlossen). Muss auf dem Haupt-Thread laufen. */
    public static Blueprint capture(ClientLevel level, BlockPos a, BlockPos b, String name) throws IOException {
        int x0 = Math.min(a.getX(), b.getX()), y0 = Math.min(a.getY(), b.getY()), z0 = Math.min(a.getZ(), b.getZ());
        int x1 = Math.max(a.getX(), b.getX()), y1 = Math.max(a.getY(), b.getY()), z1 = Math.max(a.getZ(), b.getZ());
        if (volume(a, b) > MAX_VOLUME) {
            throw new IOException("Bereich ist zu groß (" + volume(a, b) + " Positionen, maximal 2 Millionen). Bitte in Teile aufteilen.");
        }
        if (y0 < level.getMinY() || y1 > level.getMaxY()) throw new IOException("Bereich liegt außerhalb der Bauhöhe.");
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
                if (!level.hasChunk(cx, cz)) throw new IOException("Teile des Bereichs sind nicht geladen. Näher herangehen.");
            }
        }

        Map<BlockState, Integer> indices = new HashMap<>();
        List<Blueprint.State> palette = new ArrayList<>();
        List<Blueprint.Cell> cells = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.isAir()) continue;
                    Integer index = indices.get(state);
                    if (index == null) {
                        index = palette.size();
                        indices.put(state, index);
                        palette.add(describe(state));
                    }
                    cells.add(new Blueprint.Cell(x - x0, y - y0, z - z0, index));
                    if (cells.size() > BlueprintReader.MAX_BLOCKS) {
                        throw new IOException("Mehr als 250.000 Blöcke. Bitte in Teile aufteilen.");
                    }
                }
            }
        }
        if (cells.isEmpty()) throw new IOException("Im Bereich ist nur Luft.");
        List<String> warnings = List.of("Inhalte von Kisten und Texte von Schildern werden nicht gespeichert.");
        return new Blueprint(name, name, x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1, palette, cells, warnings, null);
    }

    /** Blockzustand als Name + Eigenschaften, wie in Bauplan-Dateien. */
    public static Blueprint.State describe(BlockState state) {
        Map<String, String> properties = new LinkedHashMap<>();
        for (Property<?> property : state.getProperties()) properties.put(property.getName(), value(state, property));
        return new Blueprint.State(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(), properties);
    }

    private static <T extends Comparable<T>> String value(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }
}
