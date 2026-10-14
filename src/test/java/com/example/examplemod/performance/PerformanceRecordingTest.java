package com.example.examplemod.performance;

import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public final class PerformanceRecordingTest {
    private static final long MS = 1_000_000L;
    private static int checks;
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static void near(double expected, double actual, String message) {
        check(Math.abs(expected - actual) < .00001, message + ": " + actual);
    }
    private static void sample(PerformanceMonitor.Metric metric, AtomicLong clock, int millis) {
        long start = metric.begin(); clock.addAndGet(millis * MS); metric.end(start);
    }
    private static PerformanceRecording.Measurement total(PerformanceRecording.Report report, int id) {
        return report.totals().stream().filter(row -> row.sectionId() == id).findFirst().orElseThrow();
    }
    public static void main(String[] args) throws Exception {
        AtomicLong clock = new AtomicLong();
        PerformanceMonitor monitor = new PerformanceMonitor(clock::get);
        var farm = monitor.metric("Farm Tracker", PerformanceMonitor.Phase.TICK);
        var hud = monitor.metric("Minimap", PerformanceMonitor.Phase.HUD);
        check(!monitor.startRecording("2026-10-14T00:00:00Z", Map.of()), "Cannot record while off");
        monitor.setEnabled(true);
        sample(farm, clock, 5); // Live history must not leak into recording.
        check(monitor.startRecording("2026-10-14T00:00:00Z", Map.of("minecraft", "1.21.7")), "Start");
        check(!monitor.startRecording("duplicate", Map.of()), "Double start refused");
        monitor.recordingContext(new PerformanceRecording.Context(60, 400, false, false, List.of("farm_tracker")));
        monitor.frame(true);
        clock.addAndGet(16 * MS); monitor.frame(true);
        sample(farm, clock, 2);
        sample(farm, clock, 4);
        sample(hud, clock, 1);
        clock.set(1005 * MS); // Exact 1-second boundary relative to capture start.
        sample(hud, clock, 3);
        monitor.recordingContext(new PerformanceRecording.Context(59, 410, true, false, List.of("minimap")));
        clock.set(1205 * MS); monitor.frame(true); // Long frame preserved uncapped.
        monitor.stopRecording(PerformanceRecording.Reason.MANUAL);
        check(!monitor.recording(), "Stopped");
        check(!monitor.startRecording("cannot overwrite", Map.of()), "Undelivered recording protected");
        var recording = monitor.takeCompletedRecording();
        check(monitor.takeCompletedRecording() == null, "Completed capture drained only once");
        var report = recording.report();
        near(1200, report.durationMs(), "Relative duration");
        check(report.stopReason() == PerformanceRecording.Reason.MANUAL, "Manual reason");
        check(report.intervals().size() == 2, "Partial last interval retained");
        near(1000, report.intervals().getFirst().toMs(), "First interval ends at one second");
        near(1000, report.intervals().getLast().fromMs(), "Second interval starts without overlap");
        near(1200, report.intervals().getLast().toMs(), "Partial tail end");
        near(6, total(report, 1).totalMs(), "No pre-capture samples or rolling-window duplication");
        near(3, total(report, 1).averageMs(), "Mean retained");
        near(4, total(report, 1).maxMs(), "Maximum retained");
        check(total(report, 1).calls() == 2, "Call count");
        check(report.intervals().getFirst().context().fps() == 60, "First context");
        check(report.intervals().getLast().context().menuOpen(), "Menu indicator");
        near(1200, report.worstFrames().getFirst().endedAtMs(), "Spike timestamp");
        near(1184, report.worstFrames().getFirst().durationMs(), "Uncapped spike duration");
        check(report.framesOver50Ms() == 1 && !report.worstFramesTruncated(), "Spike completeness");
        near(total(report, 2).totalMs(), report.intervals().stream().flatMap(i -> i.measurements().stream())
                .filter(m -> m.sectionId() == 2).mapToDouble(PerformanceRecording.Measurement::totalMs).sum(), "Totals agree with timeline");
        sample(farm, clock, 20);
        recording.context(clock.get(), new PerformanceRecording.Context(1, 1, false, false, List.of()));
        check(report.equals(recording.report()), "Frozen data cannot change during export");

        for (var reason : List.of(PerformanceRecording.Reason.DISABLED, PerformanceRecording.Reason.RESET,
                PerformanceRecording.Reason.WORLD_LEFT)) {
            monitor.setEnabled(true);
            check(monitor.startRecording("lifecycle", Map.of()), "Restart after drain");
            sample(farm, clock, 5);
            switch (reason) {
                case DISABLED -> monitor.setEnabled(false);
                case RESET -> monitor.reset();
                case WORLD_LEFT -> monitor.frame(false);
                default -> throw new AssertionError();
            }
            var stopped = monitor.takeCompletedRecording().report();
            check(stopped.stopReason() == reason, "Lifecycle stop reason " + reason);
            near(5, total(stopped, 1).totalMs(), "Lifecycle preserves unsaved data");
        }

        monitor.setEnabled(true);
        check(monitor.startRecording("boundary", Map.of()), "Boundary test start");
        sample(farm, clock, 1000);
        monitor.stopRecording(PerformanceRecording.Reason.MANUAL);
        var boundary = monitor.takeCompletedRecording().report();
        check(boundary.intervals().size() == 1, "No zero-length tail at exact boundary");
        near(1000, boundary.intervals().getFirst().measurements().getFirst().totalMs(), "Boundary call belongs to ending interval");

        check(monitor.startRecording("bounded", Map.of()), "Limit test start");
        long start = clock.get();
        monitor.frame(true);
        for (int i = 0; i < 200; i++) {
            clock.addAndGet((51L + i) * MS); monitor.frame(true);
        }
        clock.set(start + 121_000 * MS); monitor.frame(true);
        check(!monitor.recording(), "Automatically finished without any snapshot or HUD reads");
        var bounded = monitor.takeCompletedRecording().report();
        check(bounded.stopReason() == PerformanceRecording.Reason.TIME_LIMIT, "Limit reason");
        check(bounded.intervals().size() == 120, "Bounded interval count despite long stall");
        near(121000, bounded.durationMs(), "Actual extended stall duration disclosed");
        check(bounded.framesOver50Ms() == 201, "All slow frames counted");
        check(bounded.worstFramesTruncated() && bounded.worstFrames().size() == 64, "Only 64 strongest spikes retained");
        check(bounded.worstFrames().getLast().durationMs() > 50_000, "Late worst spike retained");
        check(bounded.worstFrames().stream().allMatch(s -> s.durationMs() >= 188), "Smaller early spikes replaced");
        check(bounded.intervals().get(80).context() == null, "Missing context explicitly unknown");

        // Relative offsets work with negative System.nanoTime values.
        AtomicLong negativeClock = new AtomicLong(-5000 * MS);
        var negative = new PerformanceMonitor(negativeClock::get);
        var negativeMetric = negative.metric("Tick", PerformanceMonitor.Phase.TICK);
        negative.setEnabled(true); negative.startRecording("negative", Map.of());
        sample(negativeMetric, negativeClock, 5);
        negative.stopRecording(PerformanceRecording.Reason.MANUAL);
        near(5, negative.takeCompletedRecording().report().durationMs(), "Negative monotonic clock");

        var folder = Files.createTempDirectory("lumen-performance-test-");
        try {
            var first = PerformanceExport.write(folder, recording);
            var second = PerformanceExport.write(folder, recording);
            check(!first.equals(second), "Repeated export never overwrites existing report");
            var json = JsonParser.parseString(Files.readString(first)).getAsJsonObject();
            check(json.get("format").getAsString().equals("lumen-performance"), "Versioned format");
            check(json.get("version").getAsInt() == 1, "Schema version");
            var payload = json.getAsJsonObject("recording");
            near(1200, payload.get("durationMs").getAsDouble(), "JSON duration");
            check(payload.getAsJsonArray("intervals").size() == 2, "JSON timeline");
            check(payload.getAsJsonObject("environment").size() == 1, "Only supplied technical metadata");
            try (var paths = Files.list(folder)) { check(paths.noneMatch(p -> p.toString().endsWith(".tmp")), "No leftover temporary file"); }
            boolean failed = false;
            try { PerformanceExport.write(first, recording); } catch (java.io.IOException expected) { failed = true; }
            check(failed, "Write error propagates instead of false success");
            check(Files.exists(PerformanceExport.write(folder, recording)), "Same frozen recording retryable after failure");
        } finally {
            try (var paths = Files.list(folder)) { for (var file : paths.toList()) Files.delete(file); }
            Files.delete(folder);
        }
        System.out.println("PerformanceRecordingTest: " + checks + " checks passed");
    }
}
