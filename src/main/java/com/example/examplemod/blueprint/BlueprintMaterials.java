package com.example.examplemod.blueprint;

import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.BiFunction;

/** Replacement changes palette names only, preserving every stored state property and cell. */
public final class BlueprintMaterials {
    public static Blueprint replace(Blueprint source, Map<String, String> replacements,
                                    BiPredicate<Blueprint.State, String> compatible) {
        return remap(source, replacements, (state, target) -> {
            if (!compatible.test(state, target)) throw new IllegalArgumentException("Unpassender Ersatz: " + state.name() + " → " + target);
            return new Blueprint.State(target, state.properties());
        });
    }

    /** Keep only properties that exist on the selected block and accept the original value. */
    public static Blueprint.State transfer(Blueprint.State source, String target, BiPredicate<String, String> acceptsProperty) {
        Map<String, String> properties = new java.util.LinkedHashMap<>();
        source.properties().forEach((key, value) -> { if (acceptsProperty.test(key, value)) properties.put(key, value); });
        return new Blueprint.State(target, properties);
    }

    public static Blueprint remap(Blueprint source, Map<String, String> replacements,
                                  BiFunction<Blueprint.State, String, Blueprint.State> resolve) {
        var palette = source.palette().stream().map(state -> {
            String target = replacements.get(state.name());
            if (target == null || target.equals(state.name())) return state;
            return resolve.apply(state, target);
        }).toList();
        return new Blueprint(source.name(), source.source(), source.sizeX(), source.sizeY(), source.sizeZ(),
                palette, source.cells(), source.warnings(), null);
    }
    private BlueprintMaterials() {}
}
