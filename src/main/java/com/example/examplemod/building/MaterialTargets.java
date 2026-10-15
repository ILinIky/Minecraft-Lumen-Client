package com.example.examplemod.building;

import java.util.LinkedHashMap;
import java.util.Map;

/** A list has exactly one source: the active blueprint or independent manual goals. */
public final class MaterialTargets {
    public static final int MAX_AMOUNT = 2_000_000, MAX_ENTRIES = 256;
    public static Map<String, Integer> select(boolean followPlan, Map<String, Integer> manual, Map<String, Integer> blueprint) {
        Map<String, Integer> result = new LinkedHashMap<>(followPlan ? blueprint : manual);
        result.replaceAll((id, amount) -> Math.max(0, Math.min(MAX_AMOUNT, amount)));
        return result;
    }
    public static int missing(int target, int inventory) { return Math.max(0, target - inventory); }
    /** Blueprint targets already mean unbuilt demand; owning the item does not complete the build. */
    public static boolean outstanding(boolean followPlan, int target, int inventory) {
        return followPlan ? target > 0 : missing(target, inventory) > 0;
    }
    private MaterialTargets() {}
}
