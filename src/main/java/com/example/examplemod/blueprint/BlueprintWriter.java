package com.example.examplemod.blueprint;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Schreibt einen {@link Blueprint} als .litematic (Litematica, Version 6) oder als Vanilla-Struktur-.nbt.
 * Gegenstück zu {@link BlueprintReader}; der Rundweg Schreiben → Lesen ist getestet.
 */
public final class BlueprintWriter {
    private static final int LITEMATIC_VERSION = 6;

    public enum Format {
        LITEMATIC(".litematic"), STRUCTURE(".nbt");
        public final String extension;
        Format(String extension) { this.extension = extension; }
    }

    private BlueprintWriter() {}

    public static CompoundTag toNbt(Blueprint blueprint, Format format, String author, int dataVersion) {
        return format == Format.LITEMATIC ? toLitematic(blueprint, author, dataVersion) : toStructure(blueprint, dataVersion);
    }

    /** Gzip-komprimiert speichern, wie Litematica und Strukturblöcke es erwarten. */
    public static void write(Path file, CompoundTag tag) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        NbtIo.writeCompressed(tag, temp);
        Files.move(temp, file);
    }

    /**
     * Ein Bereich "Region" ab (0,0,0). Palette mit Luft an Index 0, Blöcke dicht gepackt
     * (Index = (y * Z + z) * X + x, ohne Auffüllen über Long-Grenzen, wie Litematica es liest).
     */
    public static CompoundTag toLitematic(Blueprint blueprint, String author, int dataVersion) {
        int sx = blueprint.sizeX(), sy = blueprint.sizeY(), sz = blueprint.sizeZ();
        long volume = (long) sx * sy * sz;
        int paletteSize = blueprint.palette().size() + 1;
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
        long[] packed = new long[(int) ((volume * bits + 63) / 64)];
        for (Blueprint.Cell cell : blueprint.cells()) {
            long index = ((long) cell.y() * sz + cell.z()) * sx + cell.x();
            pack(packed, index, bits, cell.state() + 1);
        }

        ListTag palette = new ListTag();
        palette.add(state("minecraft:air", Map.of()));
        for (Blueprint.State state : blueprint.palette()) palette.add(state(state.name(), state.properties()));

        CompoundTag region = new CompoundTag();
        region.put("Position", xyz(0, 0, 0));
        region.put("Size", xyz(sx, sy, sz));
        region.put("BlockStatePalette", palette);
        region.putLongArray("BlockStates", packed);
        region.put("TileEntities", new ListTag());
        region.put("Entities", new ListTag());
        region.put("PendingBlockTicks", new ListTag());
        region.put("PendingFluidTicks", new ListTag());
        CompoundTag regions = new CompoundTag();
        regions.put(blueprint.name(), region);

        long now = System.currentTimeMillis();
        CompoundTag metadata = new CompoundTag();
        metadata.putString("Name", blueprint.name());
        metadata.putString("Author", author == null ? "" : author);
        metadata.putString("Description", "Gespeichert mit Lumen Client");
        metadata.putInt("RegionCount", 1);
        metadata.putInt("TotalBlocks", blueprint.cells().size());
        metadata.putInt("TotalVolume", (int) Math.min(Integer.MAX_VALUE, volume));
        metadata.putLong("TimeCreated", now);
        metadata.putLong("TimeModified", now);
        metadata.put("EnclosingSize", xyz(sx, sy, sz));
        if (blueprint.previewImage() != null) metadata.putIntArray("PreviewImageData", blueprint.previewImage());

        CompoundTag root = new CompoundTag();
        root.putInt("Version", LITEMATIC_VERSION);
        if (dataVersion > 0) root.putInt("MinecraftDataVersion", dataVersion);
        root.put("Metadata", metadata);
        root.put("Regions", regions);
        return root;
    }

    /** Vanilla-Struktur: nur Blöcke ohne Luft, damit beim Einsetzen Vorhandenes stehen bleibt. */
    public static CompoundTag toStructure(Blueprint blueprint, int dataVersion) {
        ListTag palette = new ListTag();
        for (Blueprint.State state : blueprint.palette()) palette.add(state(state.name(), state.properties()));
        ListTag blocks = new ListTag();
        for (Blueprint.Cell cell : blueprint.cells()) {
            CompoundTag block = new CompoundTag();
            block.put("pos", ints(cell.x(), cell.y(), cell.z()));
            block.putInt("state", cell.state());
            blocks.add(block);
        }
        CompoundTag root = new CompoundTag();
        if (dataVersion > 0) root.putInt("DataVersion", dataVersion);
        root.put("size", ints(blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ()));
        root.put("palette", palette);
        root.put("blocks", blocks);
        root.put("entities", new ListTag());
        return root;
    }

    /** Gegenstück zu {@link BlueprintReader#unpack}. */
    static void pack(long[] words, long index, int bits, int value) {
        long offset = index * bits;
        int word = (int) (offset / 64), shift = (int) (offset % 64);
        words[word] |= ((long) value) << shift;
        if (shift + bits > 64) words[word + 1] |= ((long) value) >>> (64 - shift);
    }

    private static CompoundTag state(String name, Map<String, String> properties) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        if (!properties.isEmpty()) {
            CompoundTag props = new CompoundTag();
            properties.forEach((key, value) -> props.put(key, StringTag.valueOf(value)));
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
}
