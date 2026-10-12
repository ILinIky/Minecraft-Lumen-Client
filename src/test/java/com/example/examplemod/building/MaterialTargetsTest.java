package com.example.examplemod.building;

import com.example.examplemod.blueprint.Blueprint;
import com.example.examplemod.blueprint.BlueprintMaterials;
import java.util.*;

public final class MaterialTargetsTest {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
    private static void rejects(Runnable action, String message) {
        try { action.run(); } catch (IllegalArgumentException expected) { check(true, message); return; }
        throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var targets = MaterialTargets.combine(Map.of("stone", 320, "glass", 10), Map.of("stone", 200, "oak", 64));
        check(targets.get("stone") == 320 && targets.get("oak") == 64, "Same inventory not required twice");
        check(MaterialTargets.combine(Map.of("stone", 20), Map.of("stone", 320)).get("stone") == 320, "Larger blueprint target wins");
        check(MaterialTargets.missing(320,128) == 192 && MaterialTargets.missing(320,400) == 0, "Progress and surplus");
        var stairs = new Blueprint.State("minecraft:oak_stairs", Map.of("facing", "east", "half", "top", "shape", "inner_left", "waterlogged", "false"));
        var slab = new Blueprint.State("minecraft:oak_slab", Map.of("type", "double", "waterlogged", "false"));
        var source = new Blueprint("House", "house.litematic", 2,1,1, List.of(stairs, slab),
                List.of(new Blueprint.Cell(0,0,0,0), new Blueprint.Cell(1,0,0,1)), List.of(), new int[]{1});
        var replaced = BlueprintMaterials.replace(source, Map.of("minecraft:oak_stairs", "minecraft:spruce_stairs", "minecraft:oak_slab", "minecraft:spruce_slab"), (state, name) -> true);
        check(replaced.palette().get(0).properties().equals(stairs.properties()), "Stair facing/half/shape survive");
        check(replaced.palette().get(1).properties().equals(slab.properties()), "Double slab survives");
        check(replaced.cells().equals(source.cells()), "Cell positions unchanged");
        check(source.palette().get(0).name().equals("minecraft:oak_stairs"), "Source not modified");
        check(replaced.previewImage() == null, "Stale embedded preview invalidated");
        rejects(() -> BlueprintMaterials.replace(source, Map.of("minecraft:oak_stairs", "minecraft:stone"), (state, name) -> false), "Incompatible replacement rejected atomically");
        System.out.println("MaterialTargetsTest: " + assertions + " assertions passed");
    }
}

