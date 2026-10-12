package com.example.examplemod.blueprint;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Isometrische Ansicht eines Bauplans (schräg von Südosten oben) als quadratisches ARGB-Bild.
 * Ohne Minecraft-Abhängigkeit: die Farbe je Paletteneintrag kommt vom Aufrufer, daher testbar.
 *
 * <p>Jeder Block ist ein Würfel mit drei sichtbaren Flächen (oben, Süden, Osten), unterschiedlich hell.
 * Gezeichnet wird von hinten nach vorne (Maleralgorithmus); vollständig verdeckte Blöcke werden übersprungen.
 */
public final class BlueprintPreview {
    private static final float TOP = 1.0F, SOUTH = 0.80F, EAST = 0.62F, EDGE = 0.86F;

    private BlueprintPreview() {}

    /**
     * @param colors ARGB je Paletteneintrag; Alpha unter 255 = durchsichtig (Glas), 0 = nicht zeichnen
     * @param size   Kantenlänge des Bildes in Pixeln
     */
    public static int[] render(Blueprint blueprint, int[] colors, int size) {
        int[] out = new int[size * size];
        int sx = blueprint.sizeX(), sy = blueprint.sizeY(), sz = blueprint.sizeZ();
        if (blueprint.cells().isEmpty() || sx <= 0 || sy <= 0 || sz <= 0) return out;

        // Undurchsichtige Blöcke merken, um verdeckte Flächen und Blöcke zu überspringen.
        // Bei sehr weit verteilten Plänen (riesiger Rahmen) ohne Verdeckungsprüfung zeichnen.
        long volume = (long) sx * sy * sz;
        BitSet solid = volume <= 16_000_000 ? new BitSet((int) volume) : null;
        if (solid != null) {
            for (Blueprint.Cell cell : blueprint.cells()) {
                if ((color(colors, cell.state()) >>> 24) == 255) solid.set(index(cell.x(), cell.y(), cell.z(), sx, sz));
            }
        }

        // Maßstab so, dass der ganze Bau ins Bild passt (Breite: X+Z, Höhe: (X+Z)/2 + Y Würfelkanten).
        float margin = 3;
        float tile = Math.min((size - 2 * margin) / (sx + sz), (size - 2 * margin) / ((sx + sz) / 2F + sy));
        float offU = (size - (sx + sz) * tile) / 2F + sz * tile;
        float offV = (size - ((sx + sz) / 2F + sy) * tile) / 2F + sy * tile;

        // Von hinten (klein x+y+z) nach vorne zeichnen.
        long[] order = new long[blueprint.cells().size()];
        for (int i = 0; i < order.length; i++) {
            Blueprint.Cell cell = blueprint.cells().get(i);
            order[i] = ((long) (cell.x() + cell.y() + cell.z()) << 32) | i;
        }
        Arrays.sort(order);

        for (long entry : order) {
            Blueprint.Cell cell = blueprint.cells().get((int) entry);
            int color = color(colors, cell.state());
            if (color == 0) continue;
            int x = cell.x(), y = cell.y(), z = cell.z();
            boolean top = !occupied(solid, x, y + 1, z, sx, sy, sz);
            boolean east = !occupied(solid, x + 1, y, z, sx, sy, sz);
            boolean south = !occupied(solid, x, y, z + 1, sx, sy, sz);
            if (!top && !east && !south) continue;
            if (tile < 1.5F) {
                // Sehr große Baupläne: ein Punkt pro sichtbarem Block.
                int px = Math.round(offU + (x - z) * tile), py = Math.round(offV + ((x + z + 1) / 2F - y - 1) * tile);
                plot(out, size, px, py, shade(color, top ? TOP : SOUTH));
                continue;
            }
            drawCube(out, size, x, y, z, tile, offU, offV, color, top, east, south);
        }
        return out;
    }

    private static void drawCube(int[] out, int size, int x, int y, int z, float t, float offU, float offV,
                                 int color, boolean top, boolean east, boolean south) {
        int u0 = (int) Math.floor(offU + (x - z - 1) * t), u1 = (int) Math.ceil(offU + (x - z + 1) * t);
        int v0 = (int) Math.floor(offV + ((x + z) / 2F - y - 1) * t), v1 = (int) Math.ceil(offV + ((x + z + 2) / 2F - y) * t);
        float edge = t >= 4 ? 0.9F / t : -1;
        for (int pv = Math.max(0, v0); pv <= Math.min(size - 1, v1); pv++) {
            float v = (pv + 0.5F - offV) / t;
            for (int pu = Math.max(0, u0); pu <= Math.min(size - 1, u1); pu++) {
                float u = (pu + 0.5F - offU) / t;
                // Oberseite: Punkt (x+a, y+1, z+b)
                float sum = 2 * (v + y + 1) - (x + z), diff = u - (x - z);
                float a = (sum + diff) / 2, b = (sum - diff) / 2;
                if (a >= 0 && a <= 1 && b >= 0 && b <= 1) {
                    if (top) plot(out, size, pu, pv, shade(color, near(edge, a, b) ? TOP * EDGE : TOP));
                    continue;
                }
                // Ostseite: Punkt (x+1, y+c, z+b)
                b = (x + 1 - z) - u;
                float c = (x + 1 + z + b) / 2 - y - v;
                if (b >= 0 && b <= 1 && c >= 0 && c <= 1) {
                    if (east) plot(out, size, pu, pv, shade(color, near(edge, b, c) ? EAST * EDGE : EAST));
                    continue;
                }
                // Südseite: Punkt (x+a, y+c, z+1)
                a = u - (x - z - 1);
                c = (x + a + z + 1) / 2 - y - v;
                if (a >= 0 && a <= 1 && c >= 0 && c <= 1 && south) {
                    plot(out, size, pu, pv, shade(color, near(edge, a, c) ? SOUTH * EDGE : SOUTH));
                }
            }
        }
    }

    private static boolean near(float edge, float a, float b) {
        return edge > 0 && (a < edge || b < edge || a > 1 - edge || b > 1 - edge);
    }

    private static int color(int[] colors, int state) { return state < colors.length ? colors[state] : 0; }

    private static int index(int x, int y, int z, int sx, int sz) { return (y * sz + z) * sx + x; }

    private static boolean occupied(BitSet solid, int x, int y, int z, int sx, int sy, int sz) {
        return solid != null && x >= 0 && y >= 0 && z >= 0 && x < sx && y < sy && z < sz && solid.get(index(x, y, z, sx, sz));
    }

    private static int shade(int argb, float factor) {
        int r = Math.min(255, Math.round(((argb >> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((argb >> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((argb & 0xFF) * factor));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** Setzt einen Pixel; durchsichtige Farben werden über das Vorhandene gemischt. */
    private static void plot(int[] out, int size, int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= size || y >= size) return;
        int alpha = argb >>> 24;
        int i = y * size + x;
        if (alpha >= 255 || out[i] == 0) {
            out[i] = alpha >= 255 ? argb : (Math.max(alpha, 96) << 24) | (argb & 0xFFFFFF);
            return;
        }
        int under = out[i];
        float f = alpha / 255F;
        int r = Math.round(((under >> 16) & 0xFF) * (1 - f) + ((argb >> 16) & 0xFF) * f);
        int g = Math.round(((under >> 8) & 0xFF) * (1 - f) + ((argb >> 8) & 0xFF) * f);
        int b = Math.round((under & 0xFF) * (1 - f) + (argb & 0xFF) * f);
        out[i] = (under & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
