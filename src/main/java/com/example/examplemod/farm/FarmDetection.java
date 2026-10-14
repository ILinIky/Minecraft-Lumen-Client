package com.example.examplemod.farm;

import java.util.*;

/** Pure connected-component calculation on observed crops, never reads or guesses world data. */
public final class FarmDetection {
    private FarmDetection() {}
    /** Same eight-neighbor rule as component detection; never join different vertical layers. */
    public static boolean touchesKnown(List<FarmRecord.Plot> plots, Set<FarmRecord.Plot> known) {
        if (known.isEmpty()) return false;
        for (var p : plots) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (known.contains(new FarmRecord.Plot(p.x() + dx, p.y(), p.z() + dz))) return true;
            }
        }
        return false;
    }

    /** Repair exact/overlapping duplicates only; separately named neighboring farms stay separate. */
    public static List<FarmRecord> deduplicate(List<FarmRecord> records) {
        List<FarmRecord> result = new ArrayList<>();
        Map<String, Map<FarmRecord.Plot, FarmRecord>> owners = new HashMap<>();
        for (FarmRecord record : records) {
            var dimension = owners.computeIfAbsent(record.dimension(), key -> new HashMap<>());
            Set<FarmRecord> duplicates = Collections.newSetFromMap(new IdentityHashMap<>());
            for (var p : record.plots()) {
                FarmRecord owner = dimension.get(p);
                if (owner != null) duplicates.add(owner);
            }
            if (duplicates.isEmpty()) {
                result.add(record);
                for (var p : record.plots()) dimension.put(p, record);
                continue;
            }
            FarmRecord keep = result.stream().filter(duplicates::contains).findFirst().orElseThrow();
            if (record.area() != null || duplicates.stream().anyMatch(f -> f.area() != null))
                throw new IllegalArgumentException("Überlappende manuelle Farmbereiche; Datei unverändert lassen");
            Set<FarmRecord.Plot> union = new LinkedHashSet<>(keep.plots());
            for (var duplicate : duplicates) union.addAll(duplicate.plots());
            union.addAll(record.plots());
            if (union.size() > FarmRecord.MAX_PLOTS) throw new IllegalArgumentException("Doppelte Farmfläche zu groß zum Zusammenführen");
            // Preserve the first saved name, ID, anchor and waypoint. Old counters cannot describe a union.
            FarmRecord merged = new FarmRecord(keep.id(), keep.name(), keep.dimension(), List.copyOf(union), 0, 0, 0, keep.waypointId()).withHarvestNotified(record.harvestNotified() || duplicates.stream().anyMatch(FarmRecord::harvestNotified));
            int at = result.indexOf(keep);
            result.removeIf(duplicates::contains);
            result.add(at, merged);
            for (var p : union) dimension.put(p, merged);
        }
        return List.copyOf(result);
    }
    public static List<List<FarmRecord.Plot>> components(Set<FarmRecord.Plot> crops, Set<FarmRecord.Plot> incomplete) {
        return components(crops, incomplete, 0);
    }
    public static List<List<FarmRecord.Plot>> components(Set<FarmRecord.Plot> crops, Set<FarmRecord.Plot> incomplete, int gap) {
        int distance = Math.max(1, Math.min(17, gap + 1));
        Set<FarmRecord.Plot> remaining = new HashSet<>(crops);
        // Query observed plants in nine spatial buckets instead of allocating every possible neighbor.
        Map<Bucket, ArrayList<FarmRecord.Plot>> buckets = new HashMap<>();
        if (distance > 1) for (var p : crops) buckets.computeIfAbsent(bucket(p, distance), k -> new ArrayList<>()).add(p);
        List<List<FarmRecord.Plot>> result = new ArrayList<>();
        ArrayDeque<FarmRecord.Plot> queue = new ArrayDeque<>();
        for (FarmRecord.Plot seed : crops) {
            if (!remaining.remove(seed)) continue;
            if (distance > 1) {
                var seedBucket = bucket(seed, distance);
                var seeds = buckets.get(seedBucket);
                seeds.remove(seed);
                if (seeds.isEmpty()) buckets.remove(seedBucket);
            }
            queue.add(seed);
            List<FarmRecord.Plot> area = new ArrayList<>();
            boolean complete = true;
            while (!queue.isEmpty()) {
                var p = queue.removeFirst(); area.add(p);
                if (incomplete.contains(p)) complete = false;
                if (distance == 1) {
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        var neighbor = new FarmRecord.Plot(p.x() + dx, p.y(), p.z() + dz);
                        if (remaining.remove(neighbor)) queue.addLast(neighbor);
                    }
                    continue;
                }
                var center = bucket(p, distance);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    var key = new Bucket(center.x + dx, p.y(), center.z + dz);
                    var nearby = buckets.get(key);
                    if (nearby == null) continue;
                    for (int i = nearby.size() - 1; i >= 0; i--) {
                        var neighbor = nearby.get(i);
                        if (Math.abs((long) neighbor.x() - p.x()) > distance || Math.abs((long) neighbor.z() - p.z()) > distance) continue;
                        nearby.set(i, nearby.getLast()); nearby.removeLast();
                        remaining.remove(neighbor); queue.addLast(neighbor);
                    }
                    if (nearby.isEmpty()) buckets.remove(key);
                }
            }
            if (complete && area.size() <= FarmRecord.MAX_PLOTS) {
                area.sort(Comparator.comparingInt(FarmRecord.Plot::x).thenComparingInt(FarmRecord.Plot::z));
                result.add(List.copyOf(area));
            }
        }
        result.sort(Comparator.<List<FarmRecord.Plot>>comparingInt(List::size).reversed());
        return List.copyOf(result);
    }
    private record Bucket(int x, int y, int z) {}
    private static Bucket bucket(FarmRecord.Plot p, int distance) {
        return new Bucket(Math.floorDiv(p.x(), distance), p.y(), Math.floorDiv(p.z(), distance));
    }
}
