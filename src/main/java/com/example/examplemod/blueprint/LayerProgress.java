package com.example.examplemod.blueprint;

/** Progress from a complete comparison pass; unknown positions never count as finished. */
public record LayerProgress(int total, int correct, int unknown, boolean checked) {
    public int percent() { return total == 0 ? (checked ? 100 : 0) : (int) (100L * correct / total); }
    public boolean complete() { return checked && unknown == 0 && correct == total; }

    /** Pause between complete passes, never between slices of a large pass. */
    public static final class Refresh {
        private final int interval;
        private long next;
        public Refresh(int interval) { this.interval = interval; }
        public boolean ready(long tick) { return tick >= next; }
        public void completed(long tick) { next = tick + interval; }
        public void reset() { next = 0; }
    }

    /**
     * Calm display value: keeps the last complete pass visible while new passes run. Publishes at once for a new
     * layer, a change of completion or an explicit request, otherwise at most every {@code interval} ticks.
     */
    public static final class Display {
        private final int interval;
        private LayerProgress shown;
        private int layer = -1;
        private Object mode;
        private long next;
        private boolean urgent;
        public Display(int interval) { this.interval = interval; }
        /** Next complete pass is shown immediately (after edits by the player). */
        public void request() { urgent = true; }
        /** Placement changed: the old value no longer belongs to these positions. */
        public void clear() { shown = null; layer = -1; mode = null; }
        public boolean offer(LayerProgress progress, int layer, Object mode, long tick) {
            boolean publish = urgent || shown == null || this.layer != layer || this.mode != mode
                    || shown.complete() != progress.complete() || tick >= next;
            if (!publish) return false;
            shown = progress; this.layer = layer; this.mode = mode;
            next = tick + interval; urgent = false;
            return true;
        }
        public LayerProgress get(int layer, Object mode) {
            return shown != null && this.layer == layer && this.mode == mode ? shown : null;
        }
    }

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
