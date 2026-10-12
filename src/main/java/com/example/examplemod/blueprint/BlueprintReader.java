package com.example.examplemod.blueprint;

import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bounded NBT reader for .litematic, Sponge/WorldEdit .schem (v1–v3) and vanilla structure .nbt.
 * No world loading, commands, entities or block-entity contents are executed. Block states from
 * older Minecraft versions are upgraded with Minecraft's own DataFixer (e.g. grass → short_grass).
 */
public final class BlueprintReader {
    public static final int MAX_BLOCKS = 250_000;
    private static final long MAX_VOLUME = 4_000_000;
    private static final long MAX_NBT_BYTES = 128L * 1024 * 1024;
    private static final int MAX_SPAN = 4096;
    private static final String LEGACY = "Alte MCEdit-.schematic (vor 1.13) wird nicht unterstützt. Bitte als .schem oder .litematic exportieren.";
    private BlueprintReader() {}

    public static boolean supported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".litematic") || name.endsWith(".schem") || name.endsWith(".nbt");
    }

    public static Blueprint read(Path path) throws IOException {
        if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".schematic")) throw new IOException(LEGACY);
        if (!supported(path)) throw new IOException("Bitte eine .litematic-, .schem- oder Struktur-.nbt-Datei wählen.");
        if (Files.size(path) > 32L * 1024 * 1024) throw new IOException("Datei ist größer als 32 MB.");
        try (var stream = new BufferedInputStream(Files.newInputStream(path))) {
            stream.mark(2);
            int first = stream.read(), second = stream.read();
            stream.reset();
            NbtAccounter limit = NbtAccounter.create(MAX_NBT_BYTES);
            CompoundTag root = first == 0x1f && second == 0x8b
                    ? NbtIo.readCompressed(stream, limit) : NbtIo.read(new DataInputStream(stream), limit);
            return parse(root, path.getFileName().toString());
        } catch (RuntimeException failure) {
            throw new IOException("Ungültige oder zu große Bauplan-Datei.", failure);
        }
    }

    public static Blueprint parse(CompoundTag root, String source) throws IOException {
        Builder builder = new Builder(source);
        if (root.contains("Regions")) readLitematic(root, builder);
        else if (root.getCompound("Schematic").isPresent()) readSponge(root.getCompound("Schematic").get(), builder);
        else if (root.contains("Palette") && root.contains("BlockData")) readSponge(root, builder);
        else if (root.contains("Blocks") && root.contains("Materials")) throw new IOException(LEGACY);
        else if ((root.contains("palette") || root.contains("palettes")) && root.contains("blocks")) readStructure(root, builder);
        else throw new IOException("Kein unterstützter Bauplan. Unterstützt: .litematic, .schem und Struktur-.nbt.");
        return builder.build();
    }

    private static void readLitematic(CompoundTag root, Builder builder) throws IOException {
        int version = integer(root, "Version");
        if (version < 4 || version > 7) throw new IOException("Litematic-Version " + version + " wird nicht unterstützt (4–7).");
        builder.name = root.getCompoundOrEmpty("Metadata").getStringOr("Name", builder.name);
        builder.dataVersion = root.getIntOr("MinecraftDataVersion", 0);
        CompoundTag regions = compound(root, "Regions");
        if (regions.size() > 1024) throw new IOException("Zu viele Teilbereiche (maximal 1024).");
        long volume = 0;
        for (String key : regions.keySet().stream().sorted().toList()) {
            CompoundTag region = compound(regions, key);
            CompoundTag pos = compound(region, "Position"), size = compound(region, "Size");
            int sx = integer(size, "x"), sy = integer(size, "y"), sz = integer(size, "z");
            long count = volume(sx, sy, sz);
            volume += count;
            if (volume > MAX_VOLUME) throw new IOException("Bauplan hat mehr als 4 Millionen Blockpositionen.");
            int x0 = lower(integer(pos, "x"), sx), y0 = lower(integer(pos, "y"), sy), z0 = lower(integer(pos, "z"), sz);
            int nx = Math.abs(sx), ny = Math.abs(sy), nz = Math.abs(sz);
            builder.bounds(x0, y0, z0, nx, ny, nz);
            List<Blueprint.State> palette = palette(list(region, "BlockStatePalette"), builder);
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
            long[] packed = region.getLongArray("BlockStates").orElseThrow(() -> new IOException("BlockStates fehlen."));
            long required = (count * bits + 63) / 64;
            if (packed.length != required) throw new IOException("Unvollständige oder ungültige BlockStates in " + key);
            for (int i = 0; i < count; i++) {
                if ((i & 8191) == 0 && Thread.currentThread().isInterrupted()) throw new IOException("Laden abgebrochen.");
                int state = unpack(packed, i, bits);
                if (state >= palette.size()) throw new IOException("Ungültiger Palettenindex in " + key);
                builder.put(x0 + i % nx, y0 + i / (nx * nz), z0 + (i / nx) % nz, palette.get(state));
            }
            if (!region.getListOrEmpty("Entities").isEmpty()) builder.entities = true;
            if (!region.getListOrEmpty("TileEntities").isEmpty()) builder.blockEntities = true;
        }
    }

    /** Litematic entries span long boundaries; unlike modern chunk storage there is no padding. */
    public static int unpack(long[] words, int index, int bits) {
        long offset = (long) index * bits;
        int word = (int) (offset / 64), shift = (int) (offset % 64);
        long result = words[word] >>> shift;
        if (shift + bits > 64) result |= words[word + 1] << (64 - shift);
        return (int) (result & ((1L << bits) - 1));
    }

    /** WorldEdit/Sponge: Version 1 und 2 direkt im Wurzel-Tag, Version 3 unter "Schematic" mit "Blocks". */
    private static void readSponge(CompoundTag root, Builder builder) throws IOException {
        int version = root.getIntOr("Version", 1);
        if (version < 1 || version > 3) throw new IOException(".schem-Version " + version + " wird nicht unterstützt (1–3).");
        int nx = integer(root, "Width") & 0xFFFF, ny = integer(root, "Height") & 0xFFFF, nz = integer(root, "Length") & 0xFFFF;
        long count = volume(nx, ny, nz);
        builder.bounds(0, 0, 0, nx, ny, nz);
        builder.dataVersion = root.getIntOr("DataVersion", 0);
        String metaName = root.getCompoundOrEmpty("Metadata").getStringOr("Name", "");
        if (!metaName.isBlank()) builder.name = metaName;

        CompoundTag blocks = version >= 3 ? compound(root, "Blocks") : root;
        CompoundTag paletteTag = compound(blocks, "Palette");
        if (paletteTag.isEmpty() || paletteTag.size() > 65536) throw new IOException("Ungültige Blockpalette.");
        Blueprint.State[] palette = new Blueprint.State[paletteTag.size()];
        for (String key : paletteTag.keySet()) {
            int id = integer(paletteTag, key);
            if (id < 0 || id >= palette.length || palette[id] != null) throw new IOException("Ungültiger Paletteneintrag: " + key);
            palette[id] = builder.upgrade(parseState(key));
        }
        byte[] data = blocks.getByteArray(version >= 3 ? "Data" : "BlockData")
                .orElseThrow(() -> new IOException("Blockdaten fehlen."));
        int offset = 0;
        for (int i = 0; i < count; i++) {
            if ((i & 8191) == 0 && Thread.currentThread().isInterrupted()) throw new IOException("Laden abgebrochen.");
            int value = 0, shift = 0;
            byte b;
            do {
                if (offset >= data.length) throw new IOException("Blockdaten sind unvollständig.");
                b = data[offset++];
                value |= (b & 0x7F) << shift;
                shift += 7;
                if (shift > 28 && (b & 0x80) != 0) throw new IOException("Ungültige Blockdaten.");
            } while ((b & 0x80) != 0);
            if (value < 0 || value >= palette.length || palette[value] == null) throw new IOException("Ungültiger Palettenindex.");
            builder.put(i % nx, i / (nx * nz), (i / nx) % nz, palette[value]);
        }
        if (!blocks.getListOrEmpty("BlockEntities").isEmpty() || !root.getListOrEmpty("TileEntities").isEmpty())
            builder.blockEntities = true;
        if (!root.getListOrEmpty("Entities").isEmpty()) builder.entities = true;
    }

    /** "minecraft:oak_stairs[facing=north,half=bottom]" → Name und Eigenschaften. */
    public static Blueprint.State parseState(String text) throws IOException {
        String value = text.trim();
        int open = value.indexOf('[');
        String name = open < 0 ? value : value.substring(0, open);
        if (!name.contains(":")) name = "minecraft:" + name;
        if (name.length() > 256 || name.isBlank()) throw new IOException("Ungültiger Blockname.");
        Map<String, String> properties = new LinkedHashMap<>();
        if (open >= 0) {
            if (!value.endsWith("]")) throw new IOException("Ungültiger Blockzustand: " + text);
            for (String pair : value.substring(open + 1, value.length() - 1).split(",")) {
                if (pair.isBlank()) continue;
                int eq = pair.indexOf('=');
                if (eq <= 0) throw new IOException("Ungültige Blockeigenschaft: " + pair);
                properties.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
            }
        }
        return new Blueprint.State(name, properties);
    }

    private static void readStructure(CompoundTag root, Builder builder) throws IOException {
        ListTag size = list(root, "size");
        if (size.size() != 3) throw new IOException("Ungültige Strukturgröße.");
        int nx = listInt(size, 0), ny = listInt(size, 1), nz = listInt(size, 2);
        if (nx <= 0 || ny <= 0 || nz <= 0) throw new IOException("Strukturgröße muss positiv sein.");
        volume(nx, ny, nz);
        builder.bounds(0, 0, 0, nx, ny, nz);
        builder.dataVersion = root.getIntOr("DataVersion", 0);
        // Strukturen mit Varianten (z.B. Schiffswracks) haben "palettes"; die erste Variante wird gezeigt.
        ListTag paletteTag;
        if (root.contains("palette")) {
            paletteTag = list(root, "palette");
        } else {
            paletteTag = list(root, "palettes").getList(0).orElseThrow(() -> new IOException("Ungültige Blockpalette."));
            builder.variants = true;
        }
        List<Blueprint.State> palette = palette(paletteTag, builder);
        ListTag blocks = list(root, "blocks");
        if (blocks.size() > MAX_VOLUME) throw new IOException("Zu viele Strukturblöcke.");
        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag block = blocks.getCompound(i).orElseThrow(() -> new IOException("Ungültiger Strukturblock."));
            ListTag pos = list(block, "pos");
            if (pos.size() != 3) throw new IOException("Ungültige Blockposition.");
            int x = listInt(pos, 0), y = listInt(pos, 1), z = listInt(pos, 2), state = integer(block, "state");
            if (x < 0 || x >= nx || y < 0 || y >= ny || z < 0 || z >= nz || state < 0 || state >= palette.size())
                throw new IOException("Block liegt außerhalb der Struktur oder Palette.");
            builder.put(x, y, z, palette.get(state));
            if (block.contains("nbt")) builder.blockEntities = true;
        }
        builder.entities = !root.getListOrEmpty("entities").isEmpty();
    }

    private static List<Blueprint.State> palette(ListTag tags, Builder builder) throws IOException {
        if (tags.isEmpty() || tags.size() > 65536) throw new IOException("Ungültige Blockpalette.");
        List<Blueprint.State> result = new ArrayList<>();
        for (int i = 0; i < tags.size(); i++) {
            CompoundTag state = tags.getCompound(i).orElseThrow(() -> new IOException("Ungültiger Paletteneintrag."));
            String name = state.getString("Name").orElseThrow(() -> new IOException("Blockname fehlt."));
            if (name.length() > 256 || name.isBlank()) throw new IOException("Ungültiger Blockname.");
            Map<String, String> properties = new LinkedHashMap<>();
            CompoundTag props = state.getCompoundOrEmpty("Properties");
            for (String property : props.keySet()) properties.put(property,
                    props.getString(property).orElseThrow(() -> new IOException("Ungültige Blockeigenschaft.")));
            result.add(builder.upgrade(new Blueprint.State(name, properties)));
        }
        return result;
    }

    private static long volume(int x, int y, int z) throws IOException {
        if (x == 0 || y == 0 || z == 0 || Math.abs((long) x) > MAX_SPAN
                || Math.abs((long) y) > MAX_SPAN || Math.abs((long) z) > MAX_SPAN)
            throw new IOException("Ungültige Größe (maximal 4096 pro Achse).");
        long volume = Math.abs((long) x * y * z);
        if (volume > MAX_VOLUME) throw new IOException("Teilbereich hat mehr als 4 Millionen Blockpositionen.");
        return volume;
    }

    private static int lower(int position, int size) throws IOException {
        long lower = (long) position + Math.min(0, size + 1);
        if (Math.abs(lower) > 1_000_000 || Math.abs((long) position) > 1_000_000)
            throw new IOException("Teilbereich liegt zu weit vom Bauplan-Ursprung entfernt.");
        return (int) lower;
    }

    private static int integer(CompoundTag tag, String key) throws IOException {
        return tag.getInt(key).orElseThrow(() -> new IOException("Zahl fehlt: " + key));
    }
    private static int listInt(ListTag tag, int index) throws IOException {
        return tag.getInt(index).orElseThrow(() -> new IOException("Koordinate fehlt."));
    }
    private static CompoundTag compound(CompoundTag tag, String key) throws IOException {
        return tag.getCompound(key).orElseThrow(() -> new IOException("Bereich fehlt: " + key));
    }
    private static ListTag list(CompoundTag tag, String key) throws IOException {
        return tag.getList(key).orElseThrow(() -> new IOException("Liste fehlt: " + key));
    }

    private record Position(int x, int y, int z) {}
    private static final class Builder {
        private final String source;
        private String name;
        private final Map<Position, Blueprint.State> cells = new LinkedHashMap<>();
        private final Map<Blueprint.State, Blueprint.State> upgrades = new HashMap<>();
        private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        private boolean entities, blockEntities, variants;
        private int dataVersion, upgraded;

        private Builder(String source) { this.source = source; name = source; }
        private void bounds(int x, int y, int z, int nx, int ny, int nz) throws IOException {
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x + nx); maxY = Math.max(maxY, y + ny); maxZ = Math.max(maxZ, z + nz);
            if (maxX - minX > MAX_SPAN || maxY - minY > MAX_SPAN || maxZ - minZ > MAX_SPAN)
                throw new IOException("Teilbereiche liegen zu weit auseinander (maximal 4096 Blöcke).");
        }

        /** Blockzustände älterer Versionen mit dem DataFixer von Minecraft auf den aktuellen Stand bringen. */
        private Blueprint.State upgrade(Blueprint.State state) {
            if (dataVersion <= 0) return state;
            int current = SharedConstants.getCurrentVersion().dataVersion().version();
            if (dataVersion >= current) return state;
            return upgrades.computeIfAbsent(state, original -> {
                try {
                    CompoundTag tag = new CompoundTag();
                    tag.putString("Name", original.name());
                    if (!original.properties().isEmpty()) {
                        CompoundTag props = new CompoundTag();
                        original.properties().forEach(props::putString);
                        tag.put("Properties", props);
                    }
                    Tag fixed = DataFixers.getDataFixer().update(References.BLOCK_STATE,
                            new Dynamic<>(NbtOps.INSTANCE, (Tag) tag), dataVersion, current).getValue();
                    if (!(fixed instanceof CompoundTag result)) return original;
                    Map<String, String> properties = new LinkedHashMap<>();
                    CompoundTag props = result.getCompoundOrEmpty("Properties");
                    for (String key : props.keySet()) properties.put(key, props.getStringOr(key, ""));
                    Blueprint.State upgradedState = new Blueprint.State(result.getStringOr("Name", original.name()), properties);
                    if (!upgradedState.equals(original)) upgraded++;
                    return upgradedState;
                } catch (RuntimeException failure) {
                    return original;
                }
            });
        }

        private void put(int x, int y, int z, Blueprint.State state) throws IOException {
            Position key = new Position(x, y, z);
            if (state.air()) { cells.remove(key); return; }
            Blueprint.State previous = cells.put(key, state);
            if (previous != null && !previous.equals(state))
                throw new IOException("Überlappende Teilbereiche mit unterschiedlichen Blöcken. Bitte vorher zusammenführen.");
            if (cells.size() > MAX_BLOCKS) throw new IOException("Bauplan enthält mehr als 250.000 Blöcke. Bitte aufteilen.");
        }
        private Blueprint build() throws IOException {
            if (cells.isEmpty()) throw new IOException("Bauplan enthält keine darstellbaren Blöcke.");
            Map<Blueprint.State, Integer> indices = new HashMap<>();
            List<Blueprint.State> palette = new ArrayList<>();
            List<Blueprint.Cell> result = new ArrayList<>();
            cells.forEach((pos, state) -> {
                int index = indices.computeIfAbsent(state, s -> { palette.add(s); return palette.size() - 1; });
                result.add(new Blueprint.Cell(pos.x - minX, pos.y - minY, pos.z - minZ, index));
            });
            List<String> warnings = new ArrayList<>();
            if (entities) warnings.add("Entities wie Tiere, Rüstungsständer und Bilder sind nicht Teil der Vorschau.");
            if (blockEntities) warnings.add("Inhalte und Texte von Block-Entities werden nicht übernommen.");
            if (variants) warnings.add("Struktur hat mehrere Varianten; die erste wird angezeigt.");
            if (upgraded > 0) warnings.add(upgraded + " Blockzustände aus einer älteren Minecraft-Version wurden umgewandelt.");
            return new Blueprint(name.replaceAll("[\\p{Cntrl}§]", " "), source, maxX - minX, maxY - minY, maxZ - minZ,
                    palette, result, warnings);
        }
    }
}
