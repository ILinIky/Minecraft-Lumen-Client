package com.example.examplemod.blueprint;

import java.util.Arrays;
import java.util.function.IntPredicate;
import it.unimi.dsi.fastutil.ints.IntArrayList;

/** Immutable placement dependencies. Reuses the plan's position index; no world scans. */
record BlueprintParts(int[] partners, IntArrayList[] layers, int[] buildLayers, int[] deferredCounts) {
    @FunctionalInterface interface Lookup { int at(int x, int y, int z); }

    static BlueprintParts create(Blueprint blueprint, IntPredicate supportedDoubleHalf, Lookup lookup) {
        return create(blueprint, supportedDoubleHalf, p -> false, lookup);
    }

    static BlueprintParts create(Blueprint blueprint, IntPredicate supportedDoubleHalf,
                                 IntPredicate needsCeiling, Lookup lookup) {
        int[] partners = new int[blueprint.cells().size()];
        Arrays.fill(partners, -1);
        for (int i = 0; i < partners.length; i++) {
            var cell = blueprint.cells().get(i);
            var state = blueprint.palette().get(cell.state());
            if (!supportedDoubleHalf.test(cell.state()) || !"lower".equals(state.properties().get("half"))) continue;
            int other = lookup.at(cell.x(), cell.y() + 1, cell.z());
            if (other < 0) continue;
            var upper = blueprint.cells().get(other);
            var upperState = blueprint.palette().get(upper.state());
            if (supportedDoubleHalf.test(upper.state()) && state.name().equals(upperState.name())
                    && "upper".equals(upperState.properties().get("half"))) {
                partners[i] = other;
                partners[other] = i;
            }
        }
        IntArrayList[] layers = new IntArrayList[blueprint.sizeY()];
        for (int y = 0; y < layers.length; y++) layers[y] = new IntArrayList();
        int[] buildLayers = new int[partners.length];
        for (int i = 0; i < partners.length; i++) {
            buildLayers[i] = blueprint.cells().get(i).y();
            layers[buildLayers[i]].add(i);
        }
        // Top to bottom resolves arbitrarily long hanging chains in one bounded pass.
        // The temporary lists contain physical positions, independent of source cell order.
        for (int y = layers.length - 1; y >= 0; y--) {
            for (int i : layers[y]) {
                var cell = blueprint.cells().get(i);
                if (!needsCeiling.test(cell.state())) continue;
                int support = lookup.at(cell.x(), cell.y() + 1, cell.z());
                if (support >= 0) buildLayers[i] = buildLayers[support];
            }
        }
        for (var layer : layers) layer.clear();
        int[] deferred = new int[layers.length];
        for (int i = 0; i < partners.length; i++) {
            layers[buildLayers[i]].add(i);
            if (buildLayers[i] != blueprint.cells().get(i).y()) deferred[blueprint.cells().get(i).y()]++;
            if (partners[i] >= 0 && buildLayers[partners[i]] != buildLayers[i]) layers[buildLayers[partners[i]]].add(i);
        }
        return new BlueprintParts(partners, layers, buildLayers, deferred);
    }
}
