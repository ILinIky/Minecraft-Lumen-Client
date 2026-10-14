package com.example.examplemod.farm;

import java.util.List;

/** Immutable snapshot, safe to hand to the background writer. Area is the set of planted positions. */
public record FarmRecord(String id, String name, String dimension, List<Plot> plots,
                         int ripe, int planted, long seen, int waypointId,
                         Area area, List<String> crops, List<Bed> beds, boolean harvestNotified) {
    public static final int MAX_PLOTS = 4096, MAX_FARMS = 128;
    public record Plot(int x, int y, int z) {}
    public record Bed(String crop, int total, int ripe) {
        public int percent() { return total == 0 ? 0 : (int) (100L * ripe / total); }
    }
    public record Area(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public long volume() { return ((long) maxX - minX + 1) * ((long) maxY - minY + 1) * ((long) maxZ - minZ + 1); }
        public boolean valid() { return minX <= maxX && minY <= maxY && minZ <= maxZ
                && minX >= -30_000_000 && maxX <= 30_000_000 && minZ >= -30_000_000 && maxZ <= 30_000_000
                && minY >= -2048 && maxY <= 2048 && (long) maxX - minX < 65536 && (long) maxZ - minZ < 65536
                && volume() > 0 && volume() <= 65536; }
        public boolean contains(Plot p) { return p.x >= minX && p.x <= maxX && p.y >= minY && p.y <= maxY && p.z >= minZ && p.z <= maxZ; }
        public Plot at(int index) {
            int width = maxX - minX + 1, depth = maxZ - minZ + 1;
            return new Plot(minX + index % width, minY + index / (width * depth), minZ + (index / width) % depth);
        }
    }
    public FarmRecord(String id, String name, String dimension, List<Plot> plots, int ripe, int planted, long seen, int waypointId) {
        this(id, name, dimension, plots, ripe, planted, seen, waypointId, null, List.of(), List.of());
    }
    public FarmRecord(String id, String name, String dimension, List<Plot> plots, int ripe, int planted, long seen,
                      int waypointId, Area area, List<String> crops, List<Bed> beds) {
        this(id, name, dimension, plots, ripe, planted, seen, waypointId, area, crops, beds, false);
    }
    public FarmRecord withHarvestNotified(boolean value) {
        return new FarmRecord(id, name, dimension, plots, ripe, planted, seen, waypointId, area, crops, beds, value);
    }
    public FarmRecord {
        plots = List.copyOf(plots);
        crops = crops == null || crops.isEmpty() ? java.util.Collections.nCopies(plots.size(), "") : List.copyOf(crops);
        beds = beds == null ? List.of() : List.copyOf(beds);
    }
    public int percent() { return plots.isEmpty() ? 0 : (int) (100L * ripe / plots.size()); }
    public Plot position() { return plots.get(0); }
    public FarmRecord renamed(String value) { return new FarmRecord(id, cleanName(value), dimension, plots, ripe, planted, seen, waypointId, area, crops, beds, harvestNotified); }
    public FarmRecord observed(int ready, int growing, long time) { return new FarmRecord(id, name, dimension, plots, ready, growing, time, waypointId, area, crops, List.of(), harvestNotified); }
    public FarmRecord withWaypoint(int value) { return new FarmRecord(id, name, dimension, plots, ripe, planted, seen, value, area, crops, beds, harvestNotified); }
    public static String cleanName(String value) {
        String name = value == null ? "" : value.replaceAll("[\\p{Cntrl}§]", "").trim();
        return name.isEmpty() ? "Meine Farm" : name.substring(0, Math.min(48, name.length()));
    }
    public boolean valid() {
        return id != null && !id.isBlank() && name != null && dimension != null && !dimension.isBlank()
                && !plots.isEmpty() && plots.size() <= MAX_PLOTS && plots.stream().allMatch(p -> p != null
                && Math.abs((long) p.x()) <= 30_000_000 && Math.abs((long) p.z()) <= 30_000_000
                && p.y() >= -2048 && p.y() <= 2048)
                && plots.stream().distinct().count() == plots.size()
                && ripe >= 0 && planted >= ripe && planted <= plots.size() && seen >= 0
                && (area == null || area.valid() && plots.stream().allMatch(area::contains))
                && crops.size() == plots.size() && crops.stream().allMatch(c -> c != null && c.length() <= 128)
                && beds.size() <= 128 && beds.stream().allMatch(b -> b != null && b.crop != null && b.total > 0 && b.ripe >= 0 && b.ripe <= b.total)
                && beds.stream().mapToInt(Bed::total).sum() <= plots.size() && beds.stream().mapToInt(Bed::ripe).sum() <= ripe;
    }
}
