package com.example.examplemod.blueprint;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Palette/schema regressions runnable without Forge's game bootstrap. */
public final class BlueprintMaterialTest {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++; if (!condition) throw new AssertionError(message);
    }
    private static boolean compatible(Blueprint.State state, String target) {
        Map<String, Set<String>> schema = switch (target) {
            case "minecraft:spruce_stairs" -> Map.of("facing", Set.of("north", "east", "south", "west"),
                    "half", Set.of("top", "bottom"), "shape", Set.of("straight", "inner_left", "inner_right", "outer_left", "outer_right"),
                    "waterlogged", Set.of("true", "false"));
            case "minecraft:spruce_slab" -> Map.of("type", Set.of("top", "bottom", "double"), "waterlogged", Set.of("true", "false"));
            default -> Map.of();
        };
        return state.properties().entrySet().stream().allMatch(e -> schema.getOrDefault(e.getKey(), Set.of()).contains(e.getValue()));
    }
    public static void main(String[] args) {
        var stairs = new Blueprint.State("minecraft:oak_stairs", Map.of("facing", "east", "half", "top", "shape", "inner_left", "waterlogged", "false"));
        var slab = new Blueprint.State("minecraft:oak_slab", Map.of("type", "double", "waterlogged", "false"));
        var source = new Blueprint("Test", "test.nbt", 2,1,1, List.of(stairs, slab),
                List.of(new Blueprint.Cell(0,0,0,0), new Blueprint.Cell(1,0,0,1)), List.of(), null);
        Map<String,String> swaps = Map.of("minecraft:oak_stairs", "minecraft:spruce_stairs", "minecraft:oak_slab", "minecraft:spruce_slab");
        var replaced = BlueprintMaterials.replace(source, swaps, BlueprintMaterialTest::compatible);
        check(replaced.palette().get(0).name().equals("minecraft:spruce_stairs"), "Stair block replaced");
        check(replaced.palette().get(1).name().equals("minecraft:spruce_slab"), "Slab block replaced");
        check(replaced.palette().get(0).properties().equals(stairs.properties()), "Stair orientation/shape/half preserved");
        check(replaced.palette().get(1).properties().get("type").equals("double"), "Double slab preserved");
        check(replaced.cells() == source.cells(), "Immutable cells shared for reuse of spatial indices");
        for (int turns = 0; turns < 4; turns++) for (boolean mirror : new boolean[]{false,true}) {
            var before = new BlueprintTransform(source.sizeX(), source.sizeZ(), turns, mirror);
            var after = new BlueprintTransform(replaced.sizeX(), replaced.sizeZ(), turns, mirror);
            for (var cell : source.cells()) check(before.apply(cell.x(),cell.z()).equals(after.apply(cell.x(),cell.z())), "Same placement after rotation/mirroring");
        }
        var restored = BlueprintMaterials.replace(source, Map.of(), BlueprintMaterialTest::compatible);
        check(restored.palette().equals(source.palette()), "Reset restores original palette");
        var replayed = BlueprintMaterials.replace(source, Map.copyOf(swaps), BlueprintMaterialTest::compatible);
        check(replayed.palette().equals(replaced.palette()), "Replay saved mapping produces same palette");
        var partial = BlueprintMaterials.replace(source, Map.of("minecraft:oak_stairs", "minecraft:spruce_stairs"), BlueprintMaterialTest::compatible);
        check(partial.palette().get(1).equals(slab), "Unchanged materials stay original");
        var solid = BlueprintMaterials.remap(source, Map.of("minecraft:oak_stairs", "minecraft:stone"),
                (state, target) -> BlueprintMaterials.transfer(state, target, (key, value) -> false));
        check(solid.palette().get(0).name().equals("minecraft:stone") && solid.palette().get(0).properties().isEmpty(), "Arbitrary full block drops stair-only properties");
        check(solid.palette().get(1).equals(slab), "Only selected material changes");
        var facingOnly = BlueprintMaterials.transfer(stairs, "minecraft:chest", (key, value) -> key.equals("facing") || key.equals("waterlogged"));
        check(facingOnly.properties().get("facing").equals("east") && !facingOnly.properties().containsKey("half"), "Common orientation preserved, incompatible half discarded");
        var badValue = BlueprintMaterials.transfer(new Blueprint.State("example:block", Map.of("axis", "invalid")), "minecraft:oak_log", (key, value) -> key.equals("axis") && Set.of("x", "y", "z").contains(value));
        check(badValue.properties().isEmpty(), "Unsupported values use new block defaults");
        var replaySolid = BlueprintMaterials.remap(source, Map.of("minecraft:oak_stairs", "minecraft:stone"),
                (state, target) -> BlueprintMaterials.transfer(state, target, (key, value) -> false));
        check(replaySolid.palette().equals(solid.palette()), "Any-block replacement replays after reload");
        try {
            BlueprintMaterials.replace(source, Map.of("minecraft:oak_stairs", "minecraft:stone"), BlueprintMaterialTest::compatible);
            throw new AssertionError("Incompatible replacement must fail");
        } catch (IllegalArgumentException expected) { check(source.palette().get(0).equals(stairs), "Failure leaves source untouched"); }
        System.out.println("BlueprintMaterialTest: " + assertions + " assertions passed");
    }
}
