package com.example.examplemod.farm;

import java.util.*;

/** One bounded observation pass. Remember crop identity at harvested planting positions. */
public final class FarmObservation {
    private final LinkedHashMap<FarmRecord.Plot, String> crops = new LinkedHashMap<>();
    private final Set<FarmRecord.Plot> ready = new HashSet<>();
    private int planted;
    private boolean overflow;
    public FarmObservation(FarmRecord farm) {
        for (int i = 0; i < farm.plots().size(); i++) crops.put(farm.plots().get(i), farm.crops().get(i));
    }
    public void sample(FarmRecord.Plot pos, String crop, boolean ripe) {
        if (crop.isEmpty()) return;
        if (!crops.containsKey(pos) && crops.size() >= FarmRecord.MAX_PLOTS) { overflow = true; return; }
        crops.put(pos, crop); planted++;
        if (ripe) ready.add(pos);
    }
    public boolean overflow() { return overflow; }
    public FarmRecord finish(FarmRecord farm, long time) {
        if (overflow) throw new IllegalStateException("Zu viele Pflanzplätze");
        Map<String, int[]> counts = new TreeMap<>();
        crops.forEach((pos, crop) -> {
            if (crop.isEmpty()) return;
            int[] count = counts.computeIfAbsent(crop, k -> new int[2]); count[0]++;
            if (ready.contains(pos)) count[1]++;
        });
        var beds = counts.entrySet().stream().map(e -> new FarmRecord.Bed(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
        List<FarmRecord.Plot> positions = List.copyOf(crops.keySet());
        // Preserve identity when only counters change, allowing stale-scan detection and the store fast path.
        if (positions.equals(farm.plots())) positions = farm.plots();
        return new FarmRecord(farm.id(), farm.name(), farm.dimension(), positions, ready.size(), planted, time,
                farm.waypointId(), farm.area(), List.copyOf(crops.values()), beds, farm.harvestNotified());
    }
}
