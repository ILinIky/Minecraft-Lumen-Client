package com.example.examplemod.farm;

import java.util.*;

/** Enclosing areas are derived from persisted planting positions, separately for each height. */
public final class FarmAreas {
    private FarmAreas() {}
    public record Bounds(int minX, int maxX, int minZ, int maxZ) {
        boolean near(Bounds b, int gap) {
            int distance = gap + 1;
            return minX <= b.maxX + distance && b.minX <= maxX + distance
                    && minZ <= b.maxZ + distance && b.minZ <= maxZ + distance;
        }
    }
    public static Map<Integer, Bounds> bounds(List<FarmRecord.Plot> plots) {
        Map<Integer, Bounds> result = new HashMap<>();
        for (var p : plots) result.compute(p.y(), (y, b) -> b == null
                ? new Bounds(p.x(), p.x(), p.z(), p.z())
                : new Bounds(Math.min(b.minX, p.x()), Math.max(b.maxX, p.x()),
                        Math.min(b.minZ, p.z()), Math.max(b.maxZ, p.z())));
        return result;
    }
    public static boolean near(Map<Integer, Bounds> a, Map<Integer, Bounds> b, int gap) {
        for (var entry : a.entrySet()) {
            Bounds other = b.get(entry.getKey());
            if (other != null && entry.getValue().near(other, gap)) return true;
        }
        return false;
    }
    /** Fixed point: an expanded rectangle can contain another previously separate bed. */
    public static List<FarmRecord> merge(List<FarmRecord> records, int gap) {
        List<FarmRecord> result = new ArrayList<>(records);
        List<Map<Integer, Bounds>> regions = new ArrayList<>();
        for (var farm : result) regions.add(bounds(farm.plots()));
        boolean changed;
        do {
            changed = false;
            outer: for (int i = 0; i < result.size(); i++) for (int j = i + 1; j < result.size(); j++) {
                var first = result.get(i); var second = result.get(j);
                if (first.area() != null || second.area() != null) continue;
                if (!first.dimension().equals(second.dimension()) || !near(regions.get(i), regions.get(j), gap)) continue;
                Set<FarmRecord.Plot> union = new LinkedHashSet<>(first.plots()); union.addAll(second.plots());
                if (union.size() > FarmRecord.MAX_PLOTS) throw new IllegalArgumentException("Farmbereich überschreitet 4096 Pflanzplätze");
                var merged = new FarmRecord(first.id(), first.name(), first.dimension(), List.copyOf(union), 0, 0, 0, first.waypointId()).withHarvestNotified(first.harvestNotified() || second.harvestNotified());
                result.set(i, merged); regions.set(i, bounds(merged.plots())); result.remove(j); regions.remove(j);
                changed = true; break outer;
            }
        } while (changed);
        return List.copyOf(result);
    }
}
