package com.example.examplemod.farm;

/** Evaluated only after a complete, loaded observation; the latch is persisted with the farm. */
public final class FarmHarvestNotice {
    private FarmHarvestNotice() {}
    public record Decision(boolean notified, boolean announce) {}
    public static Decision evaluate(FarmRecord previous, FarmRecord current, int threshold, boolean enabled) {
        if (threshold < 1 || threshold > 100) throw new IllegalArgumentException("Invalid harvest threshold");
        boolean notified = previous.harvestNotified();
        boolean above = current.percent() >= threshold;
        // A settings change or an enlarged denominator is not evidence of harvesting.
        boolean harvested = previous.seen() > 0 && previous.plots().equals(current.plots()) && current.ripe() < previous.ripe();
        if (notified && harvested && !above) notified = false;
        boolean announce = enabled && !notified && above && current.seen() > 0 && !current.plots().isEmpty();
        return new Decision(notified || announce, announce);
    }
}
