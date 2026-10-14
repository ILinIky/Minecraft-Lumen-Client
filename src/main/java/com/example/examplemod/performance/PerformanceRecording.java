package com.example.examplemod.performance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Client-thread accumulator, handed to the exporter only after finish(). Fixed memory bounds. */
public final class PerformanceRecording {
    public static final int LIMIT_SECONDS = 120;
    public static final int MAX_SPIKES = 64;
    public enum Reason { MANUAL, TIME_LIMIT, DISABLED, RESET, WORLD_LEFT }
    public record Section(int id, String name, PerformanceMonitor.Phase phase) {}
    public record Context(int fps, long usedHeapMiB, boolean menuOpen, boolean paused, List<String> enabledModules) {
        public Context { enabledModules = List.copyOf(enabledModules); }
    }
    public record Measurement(int sectionId, long calls, double totalMs, double averageMs, double maxMs, long over50Ms) {}
    public record Interval(double fromMs, double toMs, Context context, List<Measurement> measurements) {}
    public record Spike(double endedAtMs, double durationMs) {}
    public record Report(String startedAtUtc, double durationMs, Reason stopReason, Map<String, String> environment,
                         List<Section> sections, List<Measurement> totals, List<Interval> intervals,
                         List<Spike> worstFrames, long framesOver50Ms, boolean worstFramesTruncated) {}

    private static final long SECOND = 1_000_000_000L;
    private final long start;
    private final String startedAtUtc;
    private final List<Section> sections;
    private final Map<String, String> environment;
    private final long[] counts, sums, peaks, slow;
    private final Context[] contexts = new Context[LIMIT_SECONDS];
    private final long[] spikeTimes = new long[MAX_SPIKES], spikeDurations = new long[MAX_SPIKES];
    private long totalSlowFrames, end;
    private int spikeCount;
    private Reason reason;

    PerformanceRecording(long start, String startedAtUtc, List<Section> sections, Map<String, String> environment) {
        if (sections.isEmpty() || sections.size() > 256) throw new IllegalArgumentException("Invalid section count");
        this.start = start;
        this.startedAtUtc = startedAtUtc;
        this.sections = List.copyOf(sections);
        this.environment = Map.copyOf(environment);
        int length = sections.size() * LIMIT_SECONDS;
        counts = new long[length]; sums = new long[length]; peaks = new long[length]; slow = new long[length];
    }

    public long elapsedMillis(long now) { return Math.max(0, (now - start) / 1_000_000L); }
    boolean expired(long now) { return now - start >= LIMIT_SECONDS * SECOND; }
    private int second(long now) { return (int) Math.min(LIMIT_SECONDS - 1, Math.max(0, (now - start) / SECOND)); }

    void record(int sectionId, long now, long duration, boolean frame) {
        if (reason != null || sectionId >= sections.size() || now - duration < start) return;
        int slot = second(now > start ? now - 1 : now) * sections.size() + sectionId;
        counts[slot]++; sums[slot] += duration; peaks[slot] = Math.max(peaks[slot], duration);
        if (duration > 50_000_000L) {
            slow[slot]++;
            if (frame) recordSpike(now, duration);
        }
    }

    /** Keep the strongest 64 frames, rather than losing later stutters after the first 64. */
    private void recordSpike(long now, long duration) {
        totalSlowFrames++;
        int slot = spikeCount;
        if (spikeCount == MAX_SPIKES) {
            slot = 0;
            for (int i = 1; i < spikeCount; i++) if (spikeDurations[i] < spikeDurations[slot]) slot = i;
            if (duration <= spikeDurations[slot]) return;
        } else spikeCount++;
        spikeTimes[slot] = now - start;
        spikeDurations[slot] = duration;
    }

    public void context(long now, Context value) {
        if (reason == null) contexts[second(now)] = value;
    }

    void finish(long now, Reason reason) {
        if (this.reason != null) return;
        this.end = Math.max(start, now);
        this.reason = reason;
    }

    /** Called on the export worker after finish; no client/world access here. */
    public Report report() {
        if (reason == null) throw new IllegalStateException("Recording still active");
        double elapsed = (end - start) / 1_000_000.0;
        int buckets = (int) Math.min(LIMIT_SECONDS, Math.max(1, (end - start + SECOND - 1) / SECOND));
        List<Interval> intervals = new ArrayList<>(buckets);
        long[] totalCounts = new long[sections.size()], totalSums = new long[sections.size()],
                totalPeaks = new long[sections.size()], totalSlow = new long[sections.size()];
        for (int second = 0; second < buckets; second++) {
            List<Measurement> rows = new ArrayList<>();
            for (int id = 0; id < sections.size(); id++) {
                int slot = second * sections.size() + id;
                if (counts[slot] == 0) continue;
                rows.add(measurement(id, counts[slot], sums[slot], peaks[slot], slow[slot]));
                totalCounts[id] += counts[slot]; totalSums[id] += sums[slot];
                totalPeaks[id] = Math.max(totalPeaks[id], peaks[slot]); totalSlow[id] += slow[slot];
            }
            // A final interval can exceed one second if the client stalled across the time limit.
            double to = second == buckets - 1 ? elapsed : Math.min(elapsed, (second + 1) * 1000.0);
            intervals.add(new Interval(second * 1000.0, to, contexts[second], List.copyOf(rows)));
        }
        List<Measurement> totals = new ArrayList<>();
        for (int id = 0; id < sections.size(); id++) if (totalCounts[id] > 0)
            totals.add(measurement(id, totalCounts[id], totalSums[id], totalPeaks[id], totalSlow[id]));
        List<Spike> spikes = new ArrayList<>(spikeCount);
        for (int i = 0; i < spikeCount; i++) spikes.add(new Spike(spikeTimes[i] / 1_000_000.0, spikeDurations[i] / 1_000_000.0));
        spikes.sort(Comparator.comparingDouble(Spike::endedAtMs));
        return new Report(startedAtUtc, elapsed, reason, environment, sections, List.copyOf(totals),
                List.copyOf(intervals), List.copyOf(spikes), totalSlowFrames, totalSlowFrames > MAX_SPIKES);
    }

    private static Measurement measurement(int id, long count, long sum, long peak, long slow) {
        return new Measurement(id, count, sum / 1_000_000.0, sum / (double) count / 1_000_000,
                peak / 1_000_000.0, slow);
    }
}
