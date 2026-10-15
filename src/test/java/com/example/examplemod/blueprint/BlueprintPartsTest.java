package com.example.examplemod.blueprint;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.function.IntPredicate;

/** Layer dependency regressions without the Minecraft bootstrap. */
public final class BlueprintPartsTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static BlueprintParts parts(Blueprint blueprint, boolean supported) {
        return parts(blueprint, supported, p -> false);
    }
    private static BlueprintParts parts(Blueprint blueprint, boolean supported, IntPredicate ceiling) {
        Map<String, Integer> positions = new HashMap<>();
        for (int i = 0; i < blueprint.cells().size(); i++) {
            var c = blueprint.cells().get(i);
            positions.put(c.x() + "/" + c.y() + "/" + c.z(), i);
        }
        return BlueprintParts.create(blueprint, p -> supported, ceiling,
                (x, y, z) -> positions.getOrDefault(x + "/" + y + "/" + z, -1));
    }
    private static Blueprint.State state(String name, String half) {
        return new Blueprint.State(name, Map.of("half", half));
    }
    public static void main(String[] args) {
        for (String block : List.of("minecraft:oak_door", "minecraft:lilac", "minecraft:tall_grass")) {
            var plan = new Blueprint("Parts", "test.nbt", 2, 3, 1,
                    List.of(state(block, "lower"), state(block, "upper"),
                            new Blueprint.State("minecraft:stone", Map.of())),
                    List.of(new Blueprint.Cell(0, 0, 0, 0), new Blueprint.Cell(0, 1, 0, 1),
                            new Blueprint.Cell(1, 1, 0, 2), new Blueprint.Cell(1, 2, 0, 2)), List.of(), null);
            var paired = parts(plan, true);
            check(paired.partners()[0] == 1 && paired.partners()[1] == 0, "Both halves linked: " + block);
            check(paired.layers()[0].size() == 2 && paired.layers()[0].contains(1), "Lower layer checks upper half");
            check(paired.layers()[1].size() == 3 && paired.layers()[1].contains(0), "Upper layer checks lower half");
            check(paired.layers()[2].size() == 1, "Unrelated layer unchanged");
            check(paired.partners()[2] == -1 && paired.partners()[3] == -1, "Stacked normal blocks not linked");
            check(parts(plan, false).layers()[0].size() == 1, "Unsupported states are not paired");
            var replaced = new Blueprint(plan.name(), plan.source(), 2, 3, 1,
                    List.of(new Blueprint.State("minecraft:stone", Map.of()),
                            new Blueprint.State("minecraft:stone", Map.of()), plan.palette().get(2)),
                    plan.cells(), List.of(), null);
            check(parts(replaced, true).partners()[0] == -1, "Palette replacement removes old dependencies");
        }
        var broken = new Blueprint("Broken", "test.nbt", 3, 2, 1,
                List.of(state("minecraft:oak_door", "lower"), state("minecraft:birch_door", "upper"),
                        state("minecraft:oak_stairs", "bottom"), state("minecraft:oak_stairs", "top")),
                List.of(new Blueprint.Cell(0, 0, 0, 0), new Blueprint.Cell(0, 1, 0, 1),
                        new Blueprint.Cell(1, 0, 0, 2), new Blueprint.Cell(1, 1, 0, 3),
                        new Blueprint.Cell(2, 0, 0, 0)), List.of(), null);
        var isolated = parts(broken, true);
        for (int partner : isolated.partners()) check(partner == -1, "Wrong type, stairs and cropped halves stay independent");
        check(!new LayerProgress(2, 1, 1, true).complete(), "Unknown partner blocks completion");
        check(!new LayerProgress(2, 1, 0, true).complete(), "Missing partner blocks completion");
        check(new LayerProgress(2, 2, 0, true).complete(), "Both confirmed halves complete the layer");
        ceilingDependencies();
        System.out.println("BlueprintPartsTest: " + checks + " checks passed");
    }

    private static void ceilingDependencies() {
        var palette = List.of(new Blueprint.State("minecraft:lantern", Map.of("hanging", "true")),
                new Blueprint.State("minecraft:stone", Map.of()),
                new Blueprint.State("minecraft:weeping_vines_plant", Map.of()),
                new Blueprint.State("minecraft:lantern", Map.of("hanging", "false")));
        var cells = List.of(new Blueprint.Cell(0, 0, 0, 0), new Blueprint.Cell(0, 1, 0, 1),
                new Blueprint.Cell(1, 0, 0, 2), new Blueprint.Cell(1, 1, 0, 2),
                new Blueprint.Cell(1, 2, 0, 2), new Blueprint.Cell(1, 3, 0, 1),
                new Blueprint.Cell(2, 0, 0, 0), // No support in the plan: stays here.
                new Blueprint.Cell(3, 0, 0, 3), new Blueprint.Cell(3, 1, 0, 1));
        for (boolean reverse : new boolean[]{false, true}) {
            var ordered = new ArrayList<>(cells);
            if (reverse) Collections.reverse(ordered);
            var plan = new Blueprint("Attachments", "test.nbt", 4, 4, 1, palette, ordered, List.of(), null);
            var grouped = parts(plan, false, p -> p == 0 || p == 2);
            for (int i = 0; i < plan.cells().size(); i++) {
                var cell = plan.cells().get(i);
                int expected = cell.x() == 0 ? 1 : cell.x() == 1 ? 3 : cell.y();
                check(grouped.buildLayers()[i] == expected, "Support layer independent of cell order");
                check(grouped.layers()[expected].contains(i), "Deferred cell is checked with its support");
                if (expected != cell.y()) check(!grouped.layers()[cell.y()].contains(i), "No deadlock on earlier layer");
            }
            check(grouped.layers()[0].size() == 2, "Only unsupported and standing lantern remain on layer zero");
            check(grouped.layers()[1].size() == 3, "Ceiling and hanging lantern share layer one");
            check(grouped.layers()[2].isEmpty(), "Intermediate hanging-chain layer can advance");
            check(grouped.layers()[3].size() == 4, "Full hanging chain stays required on support layer");
            check(grouped.deferredCounts()[0] == 2 && grouped.deferredCounts()[1] == 1
                    && grouped.deferredCounts()[2] == 1, "UI explains postponed work on its original layer");
            check(java.util.Arrays.stream(grouped.layers()).mapToInt(List::size).sum() == cells.size(),
                    "Attachments counted exactly once across layers");
            var replaced = parts(plan, false, p -> false);
            for (int i = 0; i < plan.cells().size(); i++)
                check(replaced.buildLayers()[i] == plan.cells().get(i).y(), "New palette removes old ceiling dependency");
        }
    }
}
