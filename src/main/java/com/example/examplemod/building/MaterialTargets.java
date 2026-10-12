package com.example.examplemod.building;

import java.util.LinkedHashMap;
import java.util.Map;

/** Manual targets and blueprint requirements share inventory; they must not count it twice. */
public final class MaterialTargets {
    public static final int MAX_AMOUNT = 2_000_000, MAX_ENTRIES = 256;
    public static Map<String, Integer> combine(Map<String, Integer> manual, Map<String, Integer> blueprint) {
        Map<String, Integer> result = new LinkedHashMap<>(manual);
        blueprint.forEach((id, amount) -> result.merge(id, amount, Math::max));
        result.replaceAll((id, amount) -> Math.max(0, Math.min(MAX_AMOUNT, amount)));
        return result;
    }
    public static int missing(int target, int inventory) { return Math.max(0, target - inventory); }
    private MaterialTargets() {}
}
