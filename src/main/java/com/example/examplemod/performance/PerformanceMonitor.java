package com.example.examplemod.performance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongSupplier;

/** Client-thread-only, bounded diagnostics. No allocations or sorting in a measured call. */
public final class PerformanceMonitor {
    public enum Phase { TICK, WORLD, HUD, OTHER, FRAME }
    public record Row(String name, Phase phase, long calls, double averageMs, double maxMs, double msPerSecond) {}
    public record Snapshot(List<Row> rows, Row frames, List<Double> graph, double seconds, long slowFrames,
                           double measuredMsPerSecond) {}
    public static final PerformanceMonitor INSTANCE = new PerformanceMonitor(System::nanoTime);
    private static final long SECOND = 1_000_000_000L;
    private final LongSupplier clock;
    private final List<Metric> metrics = new ArrayList<>();
    private final Metric frames;
    private final double[] graph = new double[120];
    private int graphIndex, graphSize;
    private boolean enabled;
    private long started, lastFrame, published;
    private boolean haveFrame;
    private Snapshot snapshot = empty();
    private PerformanceRecording recording, completed;

    public PerformanceMonitor(LongSupplier clock) {
        this.clock = clock;
        frames = metric("Frame-Abstand", Phase.FRAME);
    }

    public Metric metric(String name, Phase phase) {
        Metric metric = new Metric(metrics.size(), name, phase);
        metrics.add(metric);
        return metric;
    }

    public boolean enabled() { return enabled; }
    public void setEnabled(boolean value) {
        if (enabled == value) return;
        if (!value) {
            stopRecording(PerformanceRecording.Reason.DISABLED);
            snapshot = collect(clock.getAsLong());
        }
        enabled = value;
        if (value) reset();
        haveFrame = false;
    }

    public boolean startRecording(String startedAtUtc, java.util.Map<String, String> environment) {
        if (!enabled || recording != null || completed != null) return false;
        reset(); // The visible live window and the recording start at the same point.
        recording = new PerformanceRecording(clock.getAsLong(), startedAtUtc,
                metrics.stream().map(metric -> new PerformanceRecording.Section(metric.id, metric.name, metric.phase)).toList(), environment);
        haveFrame = false; // Never attribute a pre-recording interval to this session.
        return true;
    }

    public boolean recording() { return recording != null; }
    public long recordingElapsedMillis() { return recording == null ? 0 : recording.elapsedMillis(clock.getAsLong()); }
    public void recordingContext(PerformanceRecording.Context context) {
        if (recording != null) recording.context(clock.getAsLong(), context);
    }
    public void stopRecording(PerformanceRecording.Reason reason) {
        if (recording == null) return;
        recording.finish(clock.getAsLong(), reason);
        completed = recording;
        recording = null;
    }
    public PerformanceRecording takeCompletedRecording() {
        PerformanceRecording result = completed;
        completed = null;
        return result;
    }

    public void reset() {
        stopRecording(PerformanceRecording.Reason.RESET);
        for (Metric metric : metrics) metric.clear();
        graphIndex = graphSize = 0;
        haveFrame = false;
        started = clock.getAsLong();
        published = started;
        snapshot = empty();
    }

    /** Once at each render-tick start, also when F1 hides the HUD. Includes frame limiting/VSync. */
    public void frame(boolean inWorld) {
        if (!enabled) return;
        if (!inWorld) {
            stopRecording(PerformanceRecording.Reason.WORLD_LEFT);
            haveFrame = false;
            return;
        }
        long now = clock.getAsLong();
        if (haveFrame) {
            long duration = Math.max(0, now - lastFrame);
            frames.record(now, duration);
            graph[graphIndex] = duration / 1_000_000.0;
            graphIndex = (graphIndex + 1) % graph.length;
            graphSize = Math.min(graph.length, graphSize + 1);
        }
        lastFrame = now;
        haveFrame = true;
        if (recording != null && recording.expired(now)) stopRecording(PerformanceRecording.Reason.TIME_LIMIT);
    }

    /** Shared cached view: at most two summaries per second, independent of FPS/HUD consumers. */
    public Snapshot snapshot() {
        long now = clock.getAsLong();
        if (enabled && now - published >= SECOND / 2) {
            snapshot = collect(now);
            published = now;
        }
        return snapshot;
    }

    private Snapshot collect(long now) {
        long second = Math.floorDiv(now, SECOND);
        double seconds = Math.max(0.001, (now - Math.max(started, (second - 9) * SECOND)) / (double) SECOND);
        List<Row> rows = new ArrayList<>();
        Row frameRow = new Row(frames.name, Phase.FRAME, 0, 0, 0, 0);
        long slow = 0;
        double total = 0;
        for (Metric metric : metrics) {
            long count = 0, sum = 0, max = 0, over = 0;
            for (int i = 0; i < 10; i++) {
                if (metric.seconds[i] < second - 9 || metric.seconds[i] > second) continue;
                count += metric.count[i]; sum += metric.sum[i];
                max = Math.max(max, metric.max[i]); over += metric.slow[i];
            }
            if (count == 0) continue;
            Row row = new Row(metric.name, metric.phase, count, sum / (double) count / 1_000_000,
                    max / 1_000_000.0, sum / 1_000_000.0 / seconds);
            if (metric == frames) { frameRow = row; slow = over; }
            else { rows.add(row); total += row.msPerSecond; }
        }
        rows.sort(Comparator.comparingDouble(Row::msPerSecond).reversed());
        List<Double> history = new ArrayList<>(graphSize);
        for (int i = 0; i < graphSize; i++) history.add(graph[(graphIndex - graphSize + i + graph.length) % graph.length]);
        return new Snapshot(List.copyOf(rows), frameRow, List.copyOf(history), seconds, slow, total);
    }

    private static Snapshot empty() {
        return new Snapshot(List.of(), new Row("Frame-Abstand", Phase.FRAME, 0, 0, 0, 0), List.of(), 0, 0, 0);
    }

    public final class Metric {
        private final int id;
        private final String name;
        private final Phase phase;
        private final long[] seconds = new long[10], count = new long[10], sum = new long[10],
                max = new long[10], slow = new long[10];
        private Metric(int id, String name, Phase phase) { this.id = id; this.name = name; this.phase = phase; clear(); }

        public long begin() { return enabled ? clock.getAsLong() : Long.MIN_VALUE; }
        public void end(long start) {
            if (!enabled || start == Long.MIN_VALUE || start < started) return;
            long now = clock.getAsLong();
            record(now, Math.max(0, now - start));
        }
        private void record(long now, long duration) {
            if (recording != null) recording.record(id, now, duration, this == frames);
            long second = Math.floorDiv(now, SECOND);
            int slot = Math.floorMod(second, 10);
            if (seconds[slot] != second) {
                seconds[slot] = second;
                count[slot] = sum[slot] = max[slot] = slow[slot] = 0;
            }
            count[slot]++; sum[slot] += duration;
            max[slot] = Math.max(max[slot], duration);
            if (duration > 50_000_000L) slow[slot]++;
        }
        private void clear() {
            Arrays.fill(seconds, Long.MIN_VALUE);
            Arrays.fill(count, 0); Arrays.fill(sum, 0); Arrays.fill(max, 0); Arrays.fill(slow, 0);
        }
    }
}
