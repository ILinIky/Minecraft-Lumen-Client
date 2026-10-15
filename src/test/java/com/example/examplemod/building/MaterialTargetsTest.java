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
        var manual = new LinkedHashMap<>(Map.of("stone", 320, "glass", 10));
        var planA = Map.of("stone", 200, "oak", 64);
        var planB = Map.of("stone", 5, "sand", 12);
        var targets = MaterialTargets.select(true, manual, planA);
        check(targets.equals(planA), "Blueprint mode excludes independent goals and their larger quantities");
        check(MaterialTargets.select(true, manual, Map.of()).isEmpty(), "Unloading/loading a plan hides all plan rows, even with saved own goals");
        check(MaterialTargets.select(true, manual, planB).equals(planB), "A to B contains only B, including exact smaller quantities");
        check(MaterialTargets.select(true, manual, planA).equals(planA), "Returning to A restores only A requirements");
        check(MaterialTargets.select(false, manual, planB).equals(manual), "Own mode ignores loaded blueprint");
        check(MaterialTargets.select(false, manual, Map.of()).equals(manual), "Own goals survive blueprint unloading");
        targets.clear();
        check(planA.size() == 2 && manual.size() == 2, "Changing a displayed snapshot does not mutate either source");
        var replacedTargets = MaterialTargets.select(true, manual, Map.of("spruce", 64));
        check(!replacedTargets.containsKey("oak") && !replacedTargets.containsKey("glass"), "Replacing a blueprint material does not retain old or unrelated goals");
        check(manual.get("stone") == 320, "Blueprint operations preserve stored own goals");
        check(MaterialTargets.missing(320,128) == 192 && MaterialTargets.missing(320,400) == 0, "Progress and surplus");
        check(MaterialTargets.outstanding(true, 1, 1), "Last unbuilt block remains visible with its item in inventory");
        check(MaterialTargets.outstanding(true, 1, 64), "Surplus inventory never hides unbuilt demand");
        check(MaterialTargets.outstanding(true, 1, 0), "Missing material also remains visible");
        check(!MaterialTargets.outstanding(true, 0, 64), "Completed blueprint row disappears");
        check(!MaterialTargets.outstanding(false, 1, 1), "Completed manual collection goal still hides");
        check(MaterialTargets.outstanding(false, 2, 1), "Incomplete manual goal remains visible");
        check(MaterialTargets.outstanding(true, 1, 64) && !MaterialTargets.outstanding(false, 1, 64),
                "Same quantity follows the selected source, never inventory-only filtering in blueprint mode");
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

