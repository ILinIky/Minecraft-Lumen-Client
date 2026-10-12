package com.example.examplemod.blueprint;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Client-side placement and incremental comparison; never modifies the actual world. */
public final class BlueprintPlan {
    public enum Status {
        UNCHECKED(0xFF94A3B8), MISSING(0xFF38BDF8), WRONG_BLOCK(0xFFF87171), WRONG_STATE(0xFFFBBF24),
        CORRECT(0xFF34D399), UNSUPPORTED(0xFFC084FC);
        public final int color;
        Status(int color) { this.color = color; }
    }
    public enum Layers { ALL, SINGLE, BELOW }
    public static final class Material {
        public final String name;
        public final Item item;
        public int total, correct;
        private Material(String name, Item item) { this.name = name; this.item = item; }
        public ItemStack icon() { return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item); }
        public int remaining() { return Math.max(0, total - correct); }
    }

    public final Blueprint blueprint;
    public final String dimension;
    public final List<String> warnings;
    public final List<Material> materials;
    private final BlockState[] original, rotated;
    private final boolean[] unsupported;
    private final Material[] materialByState;
    private final int[] units;
    private final Long2IntOpenHashMap index = new Long2IntOpenHashMap();
    private final Long2ObjectOpenHashMap<IntArrayList> sections = new Long2ObjectOpenHashMap<>();
    private final byte[] status;
    private final int[] counts = new int[Status.values().length];
    private BlockPos origin;
    private int turns;
    private boolean mirrored;
    private int cursor;
    private ClientLevel lastLevel;
    public Layers layers = Layers.ALL;
    public int layer;
    public boolean onlyMissing = true;

    public BlueprintPlan(Blueprint blueprint, BlockPos origin, String dimension) {
        this.blueprint = blueprint;
        this.origin = origin.immutable();
        this.dimension = dimension;
        warnings = new ArrayList<>(blueprint.warnings());
        int paletteSize = blueprint.palette().size();
        original = new BlockState[paletteSize]; rotated = new BlockState[paletteSize];
        unsupported = new boolean[paletteSize];
        materialByState = new Material[paletteSize]; units = new int[paletteSize];
        Map<String, Material> materialsById = new LinkedHashMap<>();
        int unknown = 0;
        for (int i = 0; i < paletteSize; i++) {
            Blueprint.State spec = blueprint.palette().get(i);
            ResourceLocation id = ResourceLocation.tryParse(spec.name());
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
                unsupported[i] = true;
            } else {
                BlockState state = BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
                for (var property : spec.properties().entrySet()) {
                    Property<?> key = state.getBlock().getStateDefinition().getProperty(property.getKey());
                    BlockState changed = key == null ? null : applyProperty(state, key, property.getValue());
                    if (changed == null) unsupported[i] = true;
                    else state = changed;
                }
                original[i] = state;
            }
            if (unsupported[i]) unknown++;
            Item item = original[i] == null ? Items.AIR : materialItem(original[i]);
            String key = item == Items.AIR ? "block:" + spec.name() : BuiltInRegistries.ITEM.getKey(item).toString();
            String name = item != Items.AIR ? new ItemStack(item).getHoverName().getString()
                    : original[i] == null ? spec.name() : original[i].getBlock().getName().getString();
            materialByState[i] = materialsById.computeIfAbsent(key, k -> new Material(name, item));
            units[i] = original[i] == null ? 1 : materialUnits(original[i]);
        }
        if (unknown > 0) warnings.add(unknown + " unbekannte Blockzustände: violett markiert, nicht als korrekt gewertet.");
        index.defaultReturnValue(-1);
        for (int i = 0; i < blueprint.cells().size(); i++) {
            Blueprint.Cell cell = blueprint.cells().get(i);
            index.put(key(cell.x(), cell.y(), cell.z()), i);
            sections.computeIfAbsent(key(cell.x() >> 4, cell.y() >> 4, cell.z() >> 4), k -> new IntArrayList()).add(i);
            materialByState[cell.state()].total += units[cell.state()];
        }
        materials = materialsById.values().stream().filter(m -> m.total > 0)
                .sorted(Comparator.comparingInt((Material m) -> m.total).reversed().thenComparing(m -> m.name)).toList();
        status = new byte[blueprint.cells().size()];
        updateRotation();
    }

    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> key, String value) {
        return key.getValue(value).map(v -> state.setValue(key, v)).orElse(null);
    }

    /** Item, mit dem der Block gesetzt wird; Flüssigkeiten per Eimer. */
    public static Item materialItem(BlockState state) {
        if (state.is(Blocks.WATER)) return Items.WATER_BUCKET;
        if (state.is(Blocks.LAVA)) return Items.LAVA_BUCKET;
        return state.getBlock().asItem();
    }

    public static int materialUnits(BlockState state) {
        // Nur Quellblöcke brauchen einen Eimer; fließendes Wasser entsteht von selbst.
        if (state.is(Blocks.WATER) || state.is(Blocks.LAVA))
            return state.getValue(BlockStateProperties.LEVEL) == 0 ? 1 : 0;
        // Kolbenkopf, Portale, Feuer usw. entstehen im Spiel und werden nicht eingekauft.
        if (state.is(Blocks.PISTON_HEAD) || state.is(Blocks.MOVING_PISTON) || state.is(Blocks.NETHER_PORTAL)
                || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.BUBBLE_COLUMN)) return 0;
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) return 0;
        if (state.hasProperty(BlockStateProperties.BED_PART)
                && state.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) return 0;
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE)
                && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) return 2;
        for (String name : List.of("candles", "pickles", "eggs", "flower_amount", "layers")) {
            Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
            if (property != null && state.getValue(property) instanceof Integer count) return count;
        }
        return 1;
    }

    public BlockPos origin() { return origin; }
    public int turns() { return turns; }
    public boolean mirrored() { return mirrored; }
    public BlueprintTransform transform() { return new BlueprintTransform(blueprint.sizeX(), blueprint.sizeZ(), turns, mirrored); }
    public void move(BlockPos pos) { origin = pos.immutable(); resetComparison(); }
    public void rotate() { turns = (turns + 1) % 4; updateRotation(); }
    public void mirror() { mirrored = !mirrored; updateRotation(); }
    public void setTransform(int turns, boolean mirrored) {
        this.turns = Math.floorMod(turns, 4);
        this.mirrored = mirrored;
        updateRotation();
    }

    private void updateRotation() {
        Rotation rotation = Rotation.values()[turns];
        for (int i = 0; i < original.length; i++) {
            BlockState state = original[i];
            rotated[i] = state == null ? null : (mirrored ? state.mirror(Mirror.FRONT_BACK) : state).rotate(rotation);
        }
        resetComparison();
    }

    private void resetComparison() {
        Arrays.fill(status, (byte) Status.UNCHECKED.ordinal());
        Arrays.fill(counts, 0);
        counts[Status.UNCHECKED.ordinal()] = status.length;
        for (Material material : materials) material.correct = 0;
        cursor = 0;
    }

    public BlockPos position(Blueprint.Cell cell) {
        BlueprintTransform.XZ xz = transform().apply(cell.x(), cell.z());
        return origin.offset(xz.x(), cell.y(), xz.z());
    }
    public BlockState state(Blueprint.Cell cell) { return rotated[cell.state()]; }
    public Status status(int i) { return Status.values()[status[i]]; }
    public int count(Status value) { return counts[value.ordinal()]; }
    public boolean inDimension(ClientLevel level) { return level != null && level.dimension().location().toString().equals(dimension); }
    public boolean visibleLayer(int y) { return layers == Layers.ALL || (layers == Layers.SINGLE ? y == layer : y <= layer); }

    /** Neighbor lookup uses the whole rotated plan, so stairs/fences retain their stored shapes. */
    public BlockState stateAt(BlockPos worldPos) {
        int y = worldPos.getY() - origin.getY();
        BlueprintTransform.XZ xz = transform().inverse(worldPos.getX() - origin.getX(), worldPos.getZ() - origin.getZ());
        if (xz.x() < 0 || xz.x() >= blueprint.sizeX() || y < 0 || y >= blueprint.sizeY()
                || xz.z() < 0 || xz.z() >= blueprint.sizeZ() || !visibleLayer(y)) return null;
        int cell = index.get(key(xz.x(), y, xz.z()));
        return cell < 0 ? null : state(blueprint.cells().get(cell));
    }
    /** Index der Bauplan-Zelle an dieser Weltposition (nur sichtbare Ebenen), sonst -1. */
    public int cellAt(BlockPos worldPos) {
        int y = worldPos.getY() - origin.getY();
        BlueprintTransform.XZ xz = transform().inverse(worldPos.getX() - origin.getX(), worldPos.getZ() - origin.getZ());
        if (xz.x() < 0 || xz.x() >= blueprint.sizeX() || y < 0 || y >= blueprint.sizeY()
                || xz.z() < 0 || xz.z() >= blueprint.sizeZ() || !visibleLayer(y)) return -1;
        return index.get(key(xz.x(), y, xz.z()));
    }

    public Material material(Blueprint.Cell cell) { return materialByState[cell.state()]; }
    public boolean unsupported(Blueprint.Cell cell) { return unsupported[cell.state()]; }

    private static long key(int x, int y, int z) { return ((long) x << 32) | ((long) y << 16) | z; }

    public IntArrayList nearby(BlockPos worldPos, int radius) {
        BlueprintTransform.XZ xz = transform().inverse(worldPos.getX() - origin.getX(), worldPos.getZ() - origin.getZ());
        int y = worldPos.getY() - origin.getY();
        IntArrayList result = new IntArrayList();
        for (int sx = Math.max(0, (xz.x() - radius) >> 4); sx <= Math.min((blueprint.sizeX() - 1) >> 4, (xz.x() + radius) >> 4); sx++)
            for (int sy = Math.max(0, (y - radius) >> 4); sy <= Math.min((blueprint.sizeY() - 1) >> 4, (y + radius) >> 4); sy++)
                for (int sz = Math.max(0, (xz.z() - radius) >> 4); sz <= Math.min((blueprint.sizeZ() - 1) >> 4, (xz.z() + radius) >> 4); sz++) {
                    IntArrayList section = sections.get(key(sx, sy, sz));
                    if (section != null) result.addAll(section);
                }
        return result;
    }

    public Status compare(ClientLevel level, Blueprint.Cell cell, BlockPos pos) {
        if (unsupported[cell.state()]) return Status.UNSUPPORTED;
        if (pos.getY() < level.getMinY() || pos.getY() > level.getMaxY() || !level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4))
            return Status.UNCHECKED;
        BlockState actual = level.getBlockState(pos), expected = state(cell);
        if (actual == expected) return Status.CORRECT;
        if (actual.getBlock() == expected.getBlock()) return matches(actual, expected) ? Status.CORRECT : Status.WRONG_STATE;
        // Luft, Gras, Schnee oder Wasser im Bauraum: der Block fehlt noch, es steht nichts Falsches im Weg.
        if (actual.isAir() || actual.canBeReplaced()) return Status.MISSING;
        return Status.WRONG_BLOCK;
    }

    /**
     * Gleicher Block: nur bauliche Eigenschaften vergleichen. Zustände, die sich im Spiel von selbst
     * oder durch Benutzung ändern (Laub-Abstand, Strom, offen/zu, Wachstum), zählen nicht als Fehler.
     */
    private static boolean matches(BlockState actual, BlockState expected) {
        for (Property<?> property : expected.getProperties()) {
            if (TRANSIENT.contains(property.getName())) continue;
            if (!actual.getValue(property).equals(expected.getValue(property))) return false;
        }
        return true;
    }

    private static final java.util.Set<String> TRANSIENT = java.util.Set.of(
            "distance", "persistent", "powered", "power", "triggered", "snowy", "enabled", "locked", "open", "lit",
            "has_book", "has_record", "occupied", "instrument", "age", "stage", "moisture", "honey_level",
            "berries", "bloom", "shrieking", "can_summon", "hatch", "dusted", "cracked", "tip", "thickness");

    /** Maximum 2000 lookups per tick; unloaded chunks are explicitly not counted as missing. */
    public void scan(ClientLevel level) {
        if (!inDimension(level)) return;
        if (lastLevel != level) { lastLevel = level; resetComparison(); }
        int budget = Math.min(2000, status.length);
        for (int n = 0; n < budget; n++) {
            int i = cursor++;
            if (cursor == status.length) cursor = 0;
            Blueprint.Cell cell = blueprint.cells().get(i);
            int next = compare(level, cell, position(cell)).ordinal(), previous = status[i];
            if (next == previous) continue;
            counts[previous]--; counts[next]++;
            Material material = materialByState[cell.state()];
            if (previous == Status.CORRECT.ordinal()) material.correct -= units[cell.state()];
            if (next == Status.CORRECT.ordinal()) material.correct += units[cell.state()];
            status[i] = (byte) next;
        }
    }
}
