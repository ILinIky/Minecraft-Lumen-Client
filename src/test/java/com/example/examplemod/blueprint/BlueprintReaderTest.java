package com.example.examplemod.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ohne gestarteten Minecraft-Client ausführbare Prüfungen der Bauplan-Formate und der Platzierung. */
public final class BlueprintReaderTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        unpacking();
        litematic();
        litematicNegativeSize();
        spongeV2();
        spongeV3Varint();
        structureVariants();
        stateStrings();
        rejected();
        transforms();
        System.out.println("BlueprintReaderTest: " + assertions + " assertions passed");
    }

    private static void unpacking() {
        int bits = 5;
        int[] values = new int[40];
        for (int i = 0; i < values.length; i++) values[i] = (i * 7) % 32;
        long[] packed = pack(values, bits);
        boolean all = true;
        for (int i = 0; i < values.length; i++) all &= BlueprintReader.unpack(packed, i, bits) == values[i];
        check(all, "Litematic-Werte über Long-Grenzen hinweg korrekt entpackt");
    }

    private static void litematic() throws IOException {
        // 2×2×2, Index = (y * Z + z) * X + x
        int[] states = {1, 0, 0, 2, 0, 0, 1, 1};
        CompoundTag root = litematicRoot(0, 0, 0, 2, 2, 2, states,
                state("minecraft:air"), state("minecraft:stone"), state("minecraft:oak_stairs", "facing", "east"));
        Blueprint blueprint = BlueprintReader.parse(root, "haus.litematic");
        check(blueprint.name().equals("Testhaus"), "Name aus den Litematic-Metadaten");
        check(blueprint.sizeX() == 2 && blueprint.sizeY() == 2 && blueprint.sizeZ() == 2, "Litematic-Größe");
        check(blueprint.cells().size() == 4, "Luft wird nicht als Block gezählt");
        Map<String, String> at = cells(blueprint);
        check("minecraft:stone".equals(at.get("0,0,0")), "Stein an (0,0,0)");
        check("minecraft:oak_stairs[facing=east]".equals(at.get("1,0,1")), "Treppe mit Eigenschaft an (1,0,1)");
        check("minecraft:stone".equals(at.get("0,1,1")) && "minecraft:stone".equals(at.get("1,1,1")), "Obere Ebene korrekt");
    }

    private static void litematicNegativeSize() throws IOException {
        // Negative Größe: der Bereich reicht von Position + Größe + 1 bis Position.
        CompoundTag root = litematicRoot(5, 0, 0, -2, 1, 1, new int[]{1, 2},
                state("minecraft:air"), state("minecraft:stone"), state("minecraft:dirt"));
        Blueprint blueprint = BlueprintReader.parse(root, "neg.litematic");
        Map<String, String> at = cells(blueprint);
        check(blueprint.sizeX() == 2 && "minecraft:stone".equals(at.get("0,0,0")) && "minecraft:dirt".equals(at.get("1,0,0")),
                "Negative Litematic-Größe wird auf die untere Ecke umgerechnet");
    }

    private static void spongeV2() throws IOException {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 2);
        root.putShort("Width", (short) 2);
        root.putShort("Height", (short) 1);
        root.putShort("Length", (short) 2);
        CompoundTag palette = new CompoundTag();
        palette.putInt("minecraft:air", 0);
        palette.putInt("minecraft:stone", 1);
        palette.putInt("minecraft:oak_stairs[facing=north,half=bottom]", 2);
        root.put("Palette", palette);
        // Index = x + z * Breite + y * Breite * Länge
        root.putByteArray("BlockData", new byte[]{1, 0, 2, 1});
        Blueprint blueprint = BlueprintReader.parse(root, "turm.schem");
        Map<String, String> at = cells(blueprint);
        check(blueprint.cells().size() == 3, ".schem v2: Anzahl Blöcke");
        check("minecraft:stone".equals(at.get("0,0,0")) && "minecraft:stone".equals(at.get("1,0,1")), ".schem v2: Positionen");
        check("minecraft:oak_stairs[facing=north,half=bottom]".equals(at.get("0,0,1")), ".schem v2: Blockzustand aus Text");
    }

    private static void spongeV3Varint() throws IOException {
        CompoundTag schematic = new CompoundTag();
        schematic.putInt("Version", 3);
        schematic.putShort("Width", (short) 2);
        schematic.putShort("Height", (short) 1);
        schematic.putShort("Length", (short) 1);
        CompoundTag palette = new CompoundTag();
        palette.putInt("minecraft:air", 0);
        for (int i = 1; i < 130; i++) palette.putInt("minecraft:block_" + i, i);
        CompoundTag blocks = new CompoundTag();
        blocks.put("Palette", palette);
        // 129 braucht zwei Varint-Bytes (0x81 0x01), danach 5.
        blocks.putByteArray("Data", new byte[]{(byte) 0x81, 0x01, 0x05});
        schematic.put("Blocks", blocks);
        CompoundTag root = new CompoundTag();
        root.put("Schematic", schematic);
        Map<String, String> at = cells(BlueprintReader.parse(root, "gross.schem"));
        check("minecraft:block_129".equals(at.get("0,0,0")) && "minecraft:block_5".equals(at.get("1,0,0")),
                ".schem v3: Varint-Werte über 127");
    }

    private static void structureVariants() throws IOException {
        CompoundTag root = new CompoundTag();
        root.put("size", ints(1, 1, 2));
        ListTag first = new ListTag();
        first.add(state("minecraft:oak_planks"));
        ListTag second = new ListTag();
        second.add(state("minecraft:spruce_planks"));
        ListTag palettes = new ListTag();
        palettes.add(first);
        palettes.add(second);
        root.put("palettes", palettes);
        ListTag blocks = new ListTag();
        for (int z = 0; z < 2; z++) {
            CompoundTag block = new CompoundTag();
            block.put("pos", ints(0, 0, z));
            block.putInt("state", 0);
            blocks.add(block);
        }
        root.put("blocks", blocks);
        Blueprint blueprint = BlueprintReader.parse(root, "wrack.nbt");
        check(blueprint.cells().size() == 2 && cells(blueprint).get("0,0,1").equals("minecraft:oak_planks"),
                "Struktur mit mehreren Paletten nutzt die erste");
        check(blueprint.warnings().stream().anyMatch(w -> w.contains("Varianten")), "Hinweis auf Varianten");
    }

    private static void stateStrings() throws IOException {
        Blueprint.State state = BlueprintReader.parseState("oak_log[axis=x]");
        check(state.name().equals("minecraft:oak_log") && state.properties().equals(Map.of("axis", "x")),
                "Blockzustand ohne Namensraum wird ergänzt");
        check(BlueprintReader.parseState("minecraft:stone").properties().isEmpty(), "Blockzustand ohne Eigenschaften");
    }

    private static void rejected() {
        CompoundTag legacy = new CompoundTag();
        legacy.putShort("Width", (short) 1);
        legacy.putByteArray("Blocks", new byte[]{1});
        legacy.putString("Materials", "Alpha");
        check(throwsIo(() -> BlueprintReader.parse(legacy, "alt.schematic")), "Alte MCEdit-Datei wird mit Hinweis abgelehnt");
        CompoundTag truncated = litematicRoot(0, 0, 0, 2, 2, 2, new int[]{1, 1, 1, 1, 1, 1, 1, 1},
                state("minecraft:air"), state("minecraft:stone"));
        truncated.getCompound("Regions").get().getCompound("main").get().putLongArray("BlockStates", new long[0]);
        check(throwsIo(() -> BlueprintReader.parse(truncated, "kaputt.litematic")), "Unvollständige BlockStates werden abgelehnt");
        CompoundTag empty = new CompoundTag();
        check(throwsIo(() -> BlueprintReader.parse(empty, "leer.nbt")), "Unbekanntes Format wird abgelehnt");
    }

    private static void transforms() {
        boolean roundTrip = true, inside = true;
        for (int turns = 0; turns < 4; turns++) {
            for (boolean mirror : new boolean[]{false, true}) {
                BlueprintTransform transform = new BlueprintTransform(5, 3, turns, mirror);
                for (int x = 0; x < 5; x++) {
                    for (int z = 0; z < 3; z++) {
                        BlueprintTransform.XZ moved = transform.apply(x, z);
                        BlueprintTransform.XZ back = transform.inverse(moved.x(), moved.z());
                        roundTrip &= back.x() == x && back.z() == z;
                        inside &= moved.x() >= 0 && moved.x() < transform.width() && moved.z() >= 0 && moved.z() < transform.depth();
                    }
                }
            }
        }
        check(roundTrip, "Drehen und Spiegeln lassen sich exakt umkehren");
        check(inside, "Gedrehter Bauplan bleibt in seinem Rahmen");
        BlueprintTransform quarter = new BlueprintTransform(5, 3, 1, false);
        check(quarter.width() == 3 && quarter.depth() == 5, "90° tauscht Breite und Tiefe");
        // Minecraft dreht im Uhrzeigersinn: Osten (+X) wird zu Süden (+Z).
        check(quarter.apply(4, 0).equals(new BlueprintTransform.XZ(2, 4)), "Drehrichtung entspricht Rotation.CLOCKWISE_90");
    }

    // ---- Hilfen ----

    private static CompoundTag litematicRoot(int px, int py, int pz, int sx, int sy, int sz, int[] states, CompoundTag... palette) {
        CompoundTag region = new CompoundTag();
        region.put("Position", xyz(px, py, pz));
        region.put("Size", xyz(sx, sy, sz));
        ListTag paletteTag = new ListTag();
        for (CompoundTag entry : palette) paletteTag.add(entry);
        region.put("BlockStatePalette", paletteTag);
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.length - 1));
        region.putLongArray("BlockStates", pack(states, bits));
        CompoundTag regions = new CompoundTag();
        regions.put("main", region);
        CompoundTag metadata = new CompoundTag();
        metadata.putString("Name", "Testhaus");
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 6);
        root.put("Metadata", metadata);
        root.put("Regions", regions);
        return root;
    }

    private static long[] pack(int[] values, int bits) {
        long[] words = new long[(int) (((long) values.length * bits + 63) / 64)];
        for (int i = 0; i < values.length; i++) {
            long offset = (long) i * bits;
            int word = (int) (offset / 64), shift = (int) (offset % 64);
            words[word] |= (long) values[i] << shift;
            if (shift + bits > 64) words[word + 1] |= (long) values[i] >>> (64 - shift);
        }
        return words;
    }

    private static CompoundTag state(String name, String... properties) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        if (properties.length > 0) {
            CompoundTag props = new CompoundTag();
            for (int i = 0; i + 1 < properties.length; i += 2) props.put(properties[i], StringTag.valueOf(properties[i + 1]));
            tag.put("Properties", props);
        }
        return tag;
    }

    private static CompoundTag xyz(int x, int y, int z) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        return tag;
    }

    private static ListTag ints(int... values) {
        ListTag list = new ListTag();
        for (int value : values) list.add(IntTag.valueOf(value));
        return list;
    }

    /** "x,y,z" → "name[props]" für einfache Vergleiche. */
    private static Map<String, String> cells(Blueprint blueprint) {
        Map<String, String> result = new HashMap<>();
        for (Blueprint.Cell cell : blueprint.cells()) {
            Blueprint.State state = blueprint.palette().get(cell.state());
            StringBuilder text = new StringBuilder(state.name());
            if (!state.properties().isEmpty()) {
                // Map.copyOf hat keine feste Reihenfolge, daher sortiert vergleichen.
                List<String> parts = state.properties().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).sorted().toList();
                text.append('[').append(String.join(",", parts)).append(']');
            }
            result.put(cell.x() + "," + cell.y() + "," + cell.z(), text.toString());
        }
        return result;
    }

    private interface IoAction { void run() throws IOException; }

    private static boolean throwsIo(IoAction action) {
        try {
            action.run();
            return false;
        } catch (IOException expected) {
            return true;
        }
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
