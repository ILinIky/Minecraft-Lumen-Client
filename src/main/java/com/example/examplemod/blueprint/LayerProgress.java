package com.example.examplemod.blueprint;

/** Progress from a complete comparison pass; unknown positions never count as finished. */
public record LayerProgress(int total, int correct, int unknown, boolean checked) {
    public int percent() { return total == 0 ? (checked ? 100 : 0) : (int) (100L * correct / total); }
    public boolean complete() { return checked && unknown == 0 && correct == total; }

    /** Requires repeated complete passes over at least 30 client ticks. */
    public static final class Delay {
        private long since = -1;
        public void reset() { since = -1; }
        public boolean ready(LayerProgress progress, long tick) {
            if (!progress.complete()) { reset(); return false; }
            if (since < 0) since = tick;
            return tick - since >= 30;
        }
    }
}
