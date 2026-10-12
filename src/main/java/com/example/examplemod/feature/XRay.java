package com.example.examplemod.feature;

import com.example.examplemod.gui.BlockSelectScreen;
import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.ModuleManager;
import com.example.examplemod.module.Setting;
import com.google.gson.JsonObject;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.Set;

/**
 * Blendet alle Blöcke ausser der Auswahl aus. Die Mixins in BlockRenderDispatcher und
 * ModelBlockRenderer lesen den Zustand auf den Chunk-Threads, daher volatile und unveränderliche Mengen.
 */
public class XRay extends Module {
    public static final Set<Block> DEFAULT_BLOCKS = Set.of(
            Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE, Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
            Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
            Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE, Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
            Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE, Blocks.ANCIENT_DEBRIS,
            Blocks.SPAWNER, Blocks.TRIAL_SPAWNER, Blocks.VAULT, Blocks.CHEST, Blocks.TRAPPED_CHEST,
            Blocks.ENDER_CHEST, Blocks.BARREL, Blocks.END_PORTAL_FRAME);

    private static volatile boolean active;
    private static volatile boolean showLava = true;
    private static volatile boolean showWater;
    private static volatile Set<Block> visibleBlocks = DEFAULT_BLOCKS;

    private final Setting.Bool lava;
    private final Setting.Bool water;

    public XRay() {
        super("xray", "X-Ray", "Zeigt nur Erze und ausgewählte Blöcke", Category.RENDER);
        lava = add(new Setting.Bool("Lava anzeigen", "Lava bleibt sichtbar", true).onChange(this::applyFluids));
        water = add(new Setting.Bool("Wasser anzeigen", "Wasser bleibt sichtbar", false).onChange(this::applyFluids));
        add(new Setting.Action("Blöcke auswählen", "Welche Blöcke sichtbar bleiben", () ->
                mc.setScreen(new BlockSelectScreen(mc.screen, "X-Ray Blöcke", visibleBlocks, blocks -> {
                    visibleBlocks = Set.copyOf(blocks);
                    reloadChunks();
                }))));
        add(new Setting.Action("Standard wiederherstellen", "Erze, Truhen und Spawner", () -> {
            visibleBlocks = DEFAULT_BLOCKS;
            reloadChunks();
        }));
    }

    public static boolean isActive() { return active; }

    public static boolean hides(BlockState state) {
        return active && !visibleBlocks.contains(state.getBlock());
    }

    public static boolean hidesFluid(FluidState fluid) {
        if (!active) return false;
        if (fluid.is(FluidTags.LAVA)) return !showLava;
        if (fluid.is(FluidTags.WATER)) return !showWater;
        return true;
    }

    private void applyFluids() {
        showLava = lava.get();
        showWater = water.get();
        if (isEnabled()) reloadChunks();
    }

    @Override
    protected void onEnable() {
        applyFluids();
        active = true;
        reloadChunks();
    }

    @Override
    protected void onDisable() {
        active = false;
        reloadChunks();
    }

    private void reloadChunks() {
        ModuleManager.markDirty();
        if (mc.levelRenderer != null && mc.level != null) mc.levelRenderer.allChanged();
    }

    @Override
    public String getHudInfo() { return String.valueOf(visibleBlocks.size()); }

    @Override
    public void saveExtra(JsonObject json) {
        json.add("blocks", BlockLists.toJson(visibleBlocks));
    }

    @Override
    public void loadExtra(JsonObject json) {
        if (json.has("blocks")) visibleBlocks = Set.copyOf(BlockLists.fromJson(json.getAsJsonArray("blocks")));
        showLava = lava.get();
        showWater = water.get();
        // Beim Laden eines Profils mit anderer Auswahl die Chunks neu bauen.
        if (active && mc.levelRenderer != null && mc.level != null) mc.levelRenderer.allChanged();
    }
}
