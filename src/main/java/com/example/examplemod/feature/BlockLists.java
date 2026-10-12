package com.example.examplemod.feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Speichern von Block- und Entity-Listen als Registry-IDs sowie Farben für bekannte Erze. */
public final class BlockLists {
    private static final Map<Block, Integer> ORE_COLORS = Map.ofEntries(
            Map.entry(Blocks.DIAMOND_ORE, 0x4AEDD9), Map.entry(Blocks.DEEPSLATE_DIAMOND_ORE, 0x4AEDD9),
            Map.entry(Blocks.EMERALD_ORE, 0x17DD62), Map.entry(Blocks.DEEPSLATE_EMERALD_ORE, 0x17DD62),
            Map.entry(Blocks.GOLD_ORE, 0xFCEE4B), Map.entry(Blocks.DEEPSLATE_GOLD_ORE, 0xFCEE4B),
            Map.entry(Blocks.NETHER_GOLD_ORE, 0xFCEE4B), Map.entry(Blocks.IRON_ORE, 0xE2B48F),
            Map.entry(Blocks.DEEPSLATE_IRON_ORE, 0xE2B48F), Map.entry(Blocks.REDSTONE_ORE, 0xFF3B3B),
            Map.entry(Blocks.DEEPSLATE_REDSTONE_ORE, 0xFF3B3B), Map.entry(Blocks.LAPIS_ORE, 0x4A7BFF),
            Map.entry(Blocks.DEEPSLATE_LAPIS_ORE, 0x4A7BFF), Map.entry(Blocks.COAL_ORE, 0x9A9A9A),
            Map.entry(Blocks.DEEPSLATE_COAL_ORE, 0x9A9A9A), Map.entry(Blocks.COPPER_ORE, 0xF08A5D),
            Map.entry(Blocks.DEEPSLATE_COPPER_ORE, 0xF08A5D), Map.entry(Blocks.NETHER_QUARTZ_ORE, 0xF2EDE6),
            Map.entry(Blocks.ANCIENT_DEBRIS, 0xC27A5B), Map.entry(Blocks.SPAWNER, 0xC084FC),
            Map.entry(Blocks.CHEST, 0xFFB84D), Map.entry(Blocks.TRAPPED_CHEST, 0xFF8C4D),
            Map.entry(Blocks.ENDER_CHEST, 0x9F6BFF), Map.entry(Blocks.BARREL, 0xC8A165));

    private BlockLists() {}

    public static JsonArray toJson(Collection<Block> blocks) {
        JsonArray array = new JsonArray();
        for (Block block : blocks) array.add(BuiltInRegistries.BLOCK.getKey(block).toString());
        return array;
    }

    public static Set<Block> fromJson(JsonArray array) {
        Set<Block> blocks = new LinkedHashSet<>();
        for (JsonElement element : array) {
            ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
            if (id == null) continue;
            BuiltInRegistries.BLOCK.getOptional(id).filter(block -> block != Blocks.AIR).ifPresent(blocks::add);
        }
        return blocks;
    }

    public static JsonArray entitiesToJson(Collection<EntityType<?>> types) {
        JsonArray array = new JsonArray();
        for (EntityType<?> type : types) array.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        return array;
    }

    public static Set<EntityType<?>> entitiesFromJson(JsonArray array) {
        Set<EntityType<?>> types = new LinkedHashSet<>();
        for (JsonElement element : array) {
            ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
            if (id == null) continue;
            BuiltInRegistries.ENTITY_TYPE.getOptional(id).ifPresent(types::add);
        }
        return types;
    }

    /** Gut sichtbare Farbe für einen Block (ARGB). */
    public static int color(Block block) {
        Integer known = ORE_COLORS.get(block);
        int rgb = known != null ? known : block.defaultMapColor().col;
        if (rgb == 0) rgb = 0x22D3EE;
        // Sehr dunkle Kartenfarben aufhellen, damit die Box sichtbar bleibt.
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        if (max < 140) {
            float factor = 140F / Math.max(1, max);
            r = Math.min(255, (int) (r * factor));
            g = Math.min(255, (int) (g * factor));
            b = Math.min(255, (int) (b * factor));
        }
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
