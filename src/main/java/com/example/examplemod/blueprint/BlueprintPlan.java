package com.example.examplemod.blueprint;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.SporeBlossomBlock;
import net.minecraft.world.level.block.HangingRootsBlock;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.WeepingVinesBlock;
import net.minecraft.world.level.block.WeepingVinesPlantBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BellAttachType;
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
    private final Long2IntOpenHashMap index;
    private final Long2ObjectOpenHashMap<IntArrayList> sections;
    private final IntArrayList[] layerCells;
    private final int[] partners;
    private final int[] buildLayers, deferredCounts;
    private final int[] correctByLayer;
    private final LayerProgress.Delay layerDelay = new LayerProgress.Delay();
    private LayerProgress layerProgress = new LayerProgress(0, 0, 0, false);
    /** Shown percentage changes at most every 10 s; immediately for a new layer or when it becomes complete. */
    private final LayerProgress.Display layerDisplay = new LayerProgress.Display(200);
    /** Layer pass every 2 s: cheap (one layer only) and fast enough for the 30-tick auto-advance delay. */
    private final LayerProgress.Refresh layerRefresh = new LayerProgress.Refresh(40);
    private final LayerProgress.Refresh planRefresh = new LayerProgress.Refresh(200);
    private int scannedLayer = -1, layerCursor, layerCorrect, layerUnknown;
    private Layers scannedMode;
    private boolean scannedAuto;
    private long scanTick;
    private final java.util.Set<Long> layerChunks = new java.util.HashSet<>();
    private final int[] paletteCounts;
    private final byte[] status;
    /** Vom Spieler übernommene Abweichungen (Zellindex): jeder echte Block zählt dort als richtig. */
    private final java.util.BitSet accepted = new java.util.BitSet();
    /** Explicitly removed from this working copy, including empty positions. */
    private final java.util.BitSet ignored = new java.util.BitSet();
    private int acceptedVersion;
    private final BlueprintAudit audit = new BlueprintAudit(Status.values().length);
    public BlueprintAudit audit() { return audit; }
    public void startAudit() { audit.start(status.length); }

    private final int[] counts = new int[Status.values().length];
    private BlockPos origin;
    private int turns;
    private boolean mirrored;
    private int cursor;
    private ClientLevel lastLevel;
    public Layers layers = Layers.ALL;
    public int layer;
    public boolean onlyMissing = true;
    /** Nur dieses Material in der Vorschau zeigen, über alle Ebenen; {@code null} = alle. */
    public Material focus;

    public BlueprintPlan(Blueprint blueprint, BlockPos origin, String dimension) {
        this(blueprint, origin, dimension, null);
    }

    /** Palette-only edits reuse immutable spatial indices, avoiding another multi-million-cell index build. */
    public BlueprintPlan(Blueprint blueprint, BlueprintPlan previous) {
        this(blueprint, previous.origin(), previous.dimension, previous);
    }

    private BlueprintPlan(Blueprint blueprint, BlockPos origin, String dimension, BlueprintPlan previous) {
        if (previous != null && (blueprint.cells() != previous.blueprint.cells()
                || blueprint.palette().size() != previous.blueprint.palette().size()))
            throw new IllegalArgumentException("Materialtausch darf keine Positionen ändern");
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
        if (previous != null) {
            index = previous.index; sections = previous.sections; paletteCounts = previous.paletteCounts;
        } else {
            index = new Long2IntOpenHashMap(); sections = new Long2ObjectOpenHashMap<>();
            paletteCounts = new int[paletteSize];
            index.defaultReturnValue(-1);
            for (int i = 0; i < blueprint.cells().size(); i++) {
                Blueprint.Cell cell = blueprint.cells().get(i);
                index.put(key(cell.x(), cell.y(), cell.z()), i);
                sections.computeIfAbsent(key(cell.x() >> 4, cell.y() >> 4, cell.z() >> 4), k -> new IntArrayList()).add(i);
                paletteCounts[cell.state()]++;
            }
        }
        BlueprintParts parts = BlueprintParts.create(blueprint,
                palette -> !unsupported[palette] && original[palette] != null
                        && original[palette].hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF),
                palette -> !unsupported[palette] && needsCeiling(original[palette]),
                (x, y, z) -> index.get(key(x, y, z)));
        partners = parts.partners();
        layerCells = parts.layers();
        buildLayers = parts.buildLayers();
        deferredCounts = parts.deferredCounts();
        for (int i = 0; i < paletteSize; i++) materialByState[i].total += paletteCounts[i] * units[i];
        correctByLayer = new int[blueprint.sizeY()];
        materials = materialsById.values().stream().filter(m -> m.total > 0)
                .sorted(Comparator.comparingInt((Material m) -> m.total).reversed().thenComparing(m -> m.name)).toList();
        status = new byte[blueprint.cells().size()];
        updateRotation();
    }

    /** Only known upward attachments; never infer a dependency merely from a block's visual shape. */
    public static boolean needsCeiling(BlockState state) {
        if (state == null) return false;
        var block = state.getBlock();
        if (state.hasProperty(BlockStateProperties.ATTACH_FACE))
            return state.getValue(BlockStateProperties.ATTACH_FACE) == AttachFace.CEILING;
        if (block instanceof LanternBlock) return state.getValue(BlockStateProperties.HANGING);
        if (block instanceof BellBlock) return state.getValue(BellBlock.ATTACHMENT) == BellAttachType.CEILING;
        if (block instanceof PointedDripstoneBlock)
            return state.getValue(BlockStateProperties.VERTICAL_DIRECTION) == Direction.DOWN;
        if (block instanceof AmethystClusterBlock)
            return state.getValue(BlockStateProperties.FACING) == Direction.DOWN;
        return block instanceof CeilingHangingSignBlock || block instanceof SporeBlossomBlock
                || block instanceof HangingRootsBlock || block instanceof CaveVinesBlock
                || block instanceof CaveVinesPlantBlock || block instanceof WeepingVinesBlock
                || block instanceof WeepingVinesPlantBlock;
    }

    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> key, String value) {
        return key.getValue(value).map(v -> state.setValue(key, v)).orElse(null);
    }

    /** Every placeable target is allowed. Unsupported source properties fall back to target defaults. */
    public static boolean replacementCompatible(Blueprint.State source, String target) {
        ResourceLocation id = ResourceLocation.tryParse(target);
        if (source.air() || id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return false;
        BlockState state = BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
        if (state.isAir() || state.getBlock().asItem() == Items.AIR) return false;
        return true;
    }

    public static Blueprint.State replacementState(Blueprint.State source, String target) {
        if (!replacementCompatible(source, target)) throw new IllegalArgumentException("Ungültiger Ersatzblock: " + target);
        BlockState state = BuiltInRegistries.BLOCK.getValue(ResourceLocation.parse(target)).defaultBlockState();
        return BlueprintMaterials.transfer(source, target, (name, value) -> {
            Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
            return property != null && applyProperty(state, property, value) != null;
        });
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
    public boolean paletteUsed(int index) { return paletteCounts[index] > 0; }
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
        audit.invalidate();
        Arrays.fill(status, (byte) Status.UNCHECKED.ordinal());
        Arrays.fill(counts, 0);
        Arrays.fill(correctByLayer, 0);
        counts[Status.UNCHECKED.ordinal()] = status.length;
        for (Material material : materials) material.correct = 0;
        ignored.stream().forEach(i -> updateStatus(i, Status.CORRECT.ordinal()));
        cursor = 0;
        planRefresh.reset();
        layerDisplay.clear();
        resetLayerProgress();
    }

    /** Restarts the measurement; the last shown value stays until the new pass is complete. */
    public void resetLayerProgress() {
        scannedLayer = -1;
        layerDelay.reset();
        layerProgress = new LayerProgress(0, 0, 0, false);
        layerDisplay.request();
        layerRefresh.reset();
    }

    /** Invalidate only if the unloaded chunk overlaps this plan, not for every distant chunk. */
    public void onChunkUnload(int x, int z) {
        var transform = transform();
        if (x < (origin.getX() >> 4) || x > ((origin.getX() + transform.width() - 1) >> 4)
                || z < (origin.getZ() >> 4) || z > ((origin.getZ() + transform.depth() - 1) >> 4)) return;
        audit.invalidate();
        resetLayerProgress();
        planRefresh.reset();
    }

    public LayerProgress layerProgress() {
        return scannedLayer == layer && scannedMode == layers ? layerProgress
                : new LayerProgress(layerCells[Math.max(0, Math.min(layerCells.length - 1, layer))].size(), 0, 0, false);
    }

    /** Last complete pass for presentation only; never used to authorize building or advancement. */
    public LayerProgress displayedLayerProgress() {
        LayerProgress shown = layerDisplay.get(layer, layers);
        return shown != null ? shown : layerProgress();
    }

    /** Priority comparison of the selected layer, sharing the existing status/material accounting. */
    private void scanLayer(ClientLevel level, boolean auto) {
        scanTick++;
        if (layers == Layers.ALL || focus != null) { resetLayerProgress(); return; }
        // A pending placement temporarily blocks automatic advancement, not progress measurement.
        if (scannedAuto != auto) { scannedAuto = auto; layerDelay.reset(); }
        if (scannedLayer != layer || scannedMode != layers) {
            scannedLayer = layer; scannedMode = layers;
            layerCursor = layerCorrect = layerUnknown = 0;
            layerChunks.clear(); layerDelay.reset();
            layerProgress = new LayerProgress(layerCells[layer].size(), 0, 0, false);
            layerRefresh.reset();
        }
        if (layerCursor == 0 && !layerRefresh.ready(scanTick)) return;
        IntArrayList cells = layerCells[layer];
        int end = Math.min(cells.size(), layerCursor + 512);
        while (layerCursor < end) {
            int i = cells.getInt(layerCursor++);
            BlockPos pos = position(blueprint.cells().get(i));
            layerChunks.add(net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
            update(level, i);
            if (status(i) == Status.CORRECT) layerCorrect++;
            if (status(i) == Status.UNCHECKED || status(i) == Status.UNSUPPORTED) layerUnknown++;
            if (status(i) != Status.CORRECT) layerDelay.reset();
        }
        if (layerCursor < cells.size()) return;
        // A chunk may have unloaded since the beginning of a multi-tick pass.
        boolean loaded = layerChunks.stream().allMatch(key -> level.hasChunk(
                net.minecraft.world.level.ChunkPos.getX(key), net.minecraft.world.level.ChunkPos.getZ(key)));
        layerProgress = loaded ? new LayerProgress(cells.size(), Math.min(layerCorrect, correctByLayer[layer]), layerUnknown, true)
                : new LayerProgress(cells.size(), 0, cells.size(), true);
        layerDisplay.offer(layerProgress, layer, layers, scanTick);
        layerRefresh.completed(scanTick);
        if (auto && layerDelay.ready(layerProgress, scanTick) && layer + 1 < layerCells.length) {
            layer++;
            resetLayerProgress();
        } else if (!auto) layerDelay.reset();
        layerCursor = layerCorrect = layerUnknown = 0;
        layerChunks.clear();
    }

    public BlockPos position(Blueprint.Cell cell) {
        BlueprintTransform.XZ xz = transform().apply(cell.x(), cell.z());
        return origin.offset(xz.x(), cell.y(), xz.z());
    }
    public BlockState state(Blueprint.Cell cell) { return rotated[cell.state()]; }
    public Status status(int i) { return Status.values()[status[i]]; }
    public int count(Status value) { return counts[value.ordinal()]; }
    /** Known missing material on the selected layer; reuses the existing comparison cache. */
    public int missingOnLayer(Item item) {
        if (layer < 0 || layer >= layerCells.length) return 0;
        int amount = 0;
        for (int i : layerCells[layer]) {
            var cell = blueprint.cells().get(i);
            if (!ignored(i) && (status(i) == Status.MISSING
                    || partners[i] >= 0 && status(partners[i]) == Status.MISSING)
                    && materialByState[cell.state()].item == item)
                amount += units[cell.state()];
        }
        return amount;
    }
    /** Offener Bedarf der gewählten Ebene je Item: alles, was dort noch nicht richtig steht (ungeprüft zählt mit). */
    public Map<Item, Integer> remainingOnLayer() {
        Map<Item, Integer> result = new LinkedHashMap<>();
        if (layer < 0 || layer >= layerCells.length) return result;
        for (int i : layerCells[layer]) {
            if (materialComplete(i)) continue;
            int state = blueprint.cells().get(i).state();
            Material material = materialByState[state];
            if (material != null && material.item != Items.AIR && units[state] > 0)
                result.merge(material.item, units[state], Integer::sum);
        }
        return result;
    }
    public boolean inDimension(ClientLevel level) { return level != null && level.dimension().location().toString().equals(dimension); }
    /** Mit gewähltem Material gelten alle Ebenen, damit jede Stelle dieses Materials zu sehen ist. */
    public boolean visibleLayer(int y) {
        return focus != null || layers == Layers.ALL || (layers == Layers.SINGLE ? y == layer : y <= layer);
    }

    /** A two-block placement remains accessible from either of its occupied layers. */
    public boolean visibleCell(int i) {
        return visibleLayer(buildLayers[i])
                || partners[i] >= 0 && visibleLayer(buildLayers[partners[i]]);
    }

    public int buildLayer(int i) { return buildLayers[i]; }
    public boolean onLayer(int i, int selected) {
        return buildLayers[i] == selected || partners[i] >= 0 && buildLayers[partners[i]] == selected;
    }
    public int deferredOnLayer() { return deferredCounts[layer]; }

    private boolean materialComplete(int i) {
        return status(i) == Status.CORRECT
                && (partners[i] < 0 || status(partners[i]) == Status.CORRECT);
    }

    /** Neighbor lookup uses the whole rotated plan, so stairs/fences retain their stored shapes. */
    public BlockState stateAt(BlockPos worldPos) {
        int y = worldPos.getY() - origin.getY();
        BlueprintTransform.XZ xz = transform().inverse(worldPos.getX() - origin.getX(), worldPos.getZ() - origin.getZ());
        if (xz.x() < 0 || xz.x() >= blueprint.sizeX() || y < 0 || y >= blueprint.sizeY()
                || xz.z() < 0 || xz.z() >= blueprint.sizeZ()) return null;
        int cell = index.get(key(xz.x(), y, xz.z()));
        return cell < 0 || !visibleCell(cell) || ignored.get(cell) ? null : state(blueprint.cells().get(cell));
    }
    /** Index der Bauplan-Zelle an dieser Weltposition (nur sichtbare Ebenen), sonst -1. */
    public int cellAt(BlockPos worldPos) {
        int y = worldPos.getY() - origin.getY();
        BlueprintTransform.XZ xz = transform().inverse(worldPos.getX() - origin.getX(), worldPos.getZ() - origin.getZ());
        if (xz.x() < 0 || xz.x() >= blueprint.sizeX() || y < 0 || y >= blueprint.sizeY()
                || xz.z() < 0 || xz.z() >= blueprint.sizeZ()) return -1;
        int cell = index.get(key(xz.x(), y, xz.z()));
        return cell >= 0 && visibleCell(cell) ? cell : -1;
    }

    public Material material(Blueprint.Cell cell) { return materialByState[cell.state()]; }
    /** Ob die Zelle zum gewählten Material gehört (ohne Auswahl immer). */
    public boolean focused(Blueprint.Cell cell) { return focus == null || materialByState[cell.state()] == focus; }
    public Material materialForPalette(int index) { return materialByState[index]; }
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

    public Status compare(ClientLevel level, int cellIndex, BlockPos pos) {
        if (ignored.get(cellIndex)) return Status.CORRECT;
        Blueprint.Cell cell = blueprint.cells().get(cellIndex);
        boolean accept = accepted.get(cellIndex);
        if (unsupported[cell.state()] && !accept) return Status.UNSUPPORTED;
        if (pos.getY() < level.getMinY() || pos.getY() > level.getMaxY() || !level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4))
            return Status.UNCHECKED;
        BlockState actual = level.getBlockState(pos);
        // Luft, Gras, Schnee oder Wasser im Bauraum: der Block fehlt noch, es steht nichts Falsches im Weg.
        boolean empty = actual.isAir() || actual.canBeReplaced();
        // Übernommene Abweichung: jeder echte Block ist hier richtig; wird er abgebaut, fehlt er wieder.
        if (accept) return empty ? Status.MISSING : Status.CORRECT;
        BlockState expected = state(cell);
        if (actual == expected) return Status.CORRECT;
        if (actual.getBlock() == expected.getBlock()) return matches(actual, expected) ? Status.CORRECT : Status.WRONG_STATE;
        if (empty) return Status.MISSING;
        return Status.WRONG_BLOCK;
    }

    // ---- Übernommene Abweichungen (gewollte Varianten) ----

    public boolean accepted(int cellIndex) { return accepted.get(cellIndex); }
    public int acceptedCount() { return accepted.cardinality(); }
    /** Steigt bei jeder Änderung; für das Speichern pro Welt. */
    public int acceptedVersion() { return acceptedVersion; }
    public int[] acceptedCells() { return accepted.stream().toArray(); }
    public boolean ignored(int index) { return ignored.get(index); }
    public int ignoredCount() { return ignored.cardinality(); }
    public int[] ignoredCells() { return ignored.stream().toArray(); }
    public void setIgnored(ClientLevel level, int index, boolean value) {
        if (index < 0 || index >= status.length || ignored.get(index) == value) return;
        audit.invalidate();
        ignored.set(index, value); acceptedVersion++; resetLayerProgress();
        if (value) updateStatus(index, Status.CORRECT.ordinal());
        else if (level != null && inDimension(level)) update(level, index);
        else updateStatus(index, Status.UNCHECKED.ordinal());
    }
    public void restoreIgnored(int[] cells) {
        ignored.clear();
        for (int cell : cells) if (cell >= 0 && cell < status.length) ignored.set(cell);
        acceptedVersion++; resetComparison();
    }

    /** Abweichung übernehmen oder zurücknehmen; der Status der Zelle wird sofort neu berechnet. */
    public void setAccepted(ClientLevel level, int cellIndex, boolean value) {
        if (cellIndex < 0 || cellIndex >= status.length || accepted.get(cellIndex) == value) return;
        audit.invalidate();
        accepted.set(cellIndex, value);
        acceptedVersion++;
        resetLayerProgress();
        if (level != null && inDimension(level)) update(level, cellIndex);
    }

    /** Beim Wiederherstellen; ungültige Einträge (z.B. geänderte Datei) werden ignoriert. */
    public void restoreAccepted(int[] cells) {
        for (int cell : cells) if (cell >= 0 && cell < status.length) accepted.set(cell);
        acceptedVersion++;
        resetComparison();
    }

    public void clearAccepted() {
        if (accepted.isEmpty()) return;
        accepted.clear();
        acceptedVersion++;
        resetComparison();
    }

    /**
     * Gleicher Block: nur bauliche Eigenschaften vergleichen. Zustände, die sich im Spiel von selbst
     * oder durch Benutzung ändern (Laub-Abstand, Strom, offen/zu, Wachstum), zählen nicht als Fehler.
     */
    public static boolean comparedProperty(String name) { return !TRANSIENT.contains(name); }
    public static boolean matches(BlockState actual, BlockState expected) {
        if (actual == null || expected == null || actual.getBlock() != expected.getBlock()) return false;
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
        scan(level, false);
    }

    public void scan(ClientLevel level, boolean autoLayer) {
        if (!inDimension(level)) { resetLayerProgress(); return; }
        if (lastLevel != level) { lastLevel = level; resetComparison(); }
        scanLayer(level, autoLayer);
        if (audit.running()) {
            audit.step(1488, i -> { update(level, i); return status[i]; }, Status.CORRECT.ordinal());
            return;
        }
        if (cursor == 0 && !planRefresh.ready(scanTick)) return;
        int budget = Math.min(1488, status.length - cursor);
        for (int n = 0; n < budget; n++) {
            int i = cursor++;
            update(level, i);
        }
        if (cursor == status.length) { cursor = 0; planRefresh.completed(scanTick); }
    }

    private void update(ClientLevel level, int i) {
        updateStatus(i, compare(level, i, position(blueprint.cells().get(i))).ordinal());
    }
    private void updateStatus(int i, int next) {
        Blueprint.Cell cell = blueprint.cells().get(i);
        int previous = status[i];
        if (next == previous) return;
        audit.observedChange(i);
        int partner = partners[i];
        int workLayer = buildLayers[i];
        int partnerLayer = partner < 0 ? -1 : buildLayers[partner];
        int delta = (next == Status.CORRECT.ordinal() ? 1 : 0) - (previous == Status.CORRECT.ordinal() ? 1 : 0);
        correctByLayer[workLayer] += delta;
        if (partnerLayer >= 0 && partnerLayer != workLayer) correctByLayer[partnerLayer] += delta;
        if ((workLayer == layer || partnerLayer == layer) && next != Status.CORRECT.ordinal()) {
            layerDelay.reset();
            layerProgress = new LayerProgress(layerCells[layer].size(), correctByLayer[layer], 0, false);
        }
        // Both halves consume one item together; a missing upper half must keep that item open.
        int materialCell = partner >= 0 && blueprint.cells().get(partner).y() < cell.y() ? partner : i;
        int state = blueprint.cells().get(materialCell).state();
        boolean wasComplete = materialComplete(materialCell);
        counts[previous]--; counts[next]++;
        status[i] = (byte) next;
        boolean nowComplete = materialComplete(materialCell);
        if (wasComplete != nowComplete) materialByState[state].correct += nowComplete ? units[state] : -units[state];
    }
}
