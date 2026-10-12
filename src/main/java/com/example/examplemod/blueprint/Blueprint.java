package com.example.examplemod.blueprint;

import java.util.List;
import java.util.Map;

/**
 * Immutable file data, independent of a running world or its block registry.
 * {@code previewImage}: quadratisches ARGB-Bild aus der Datei (Litematica), sonst {@code null}.
 */
public record Blueprint(String name, String source, int sizeX, int sizeY, int sizeZ,
                        List<State> palette, List<Cell> cells, List<String> warnings, int[] previewImage) {
    public Blueprint {
        palette = List.copyOf(palette);
        cells = List.copyOf(cells);
        warnings = List.copyOf(warnings);
    }

    public record State(String name, Map<String, String> properties) {
        public State { properties = Map.copyOf(properties); }
        public boolean air() {
            return name.equals("minecraft:air") || name.equals("minecraft:cave_air")
                    || name.equals("minecraft:void_air") || name.equals("minecraft:structure_void");
        }
    }

    public record Cell(int x, int y, int z, int state) {}
}
