package com.example.examplemod.performance;

import java.util.concurrent.atomic.AtomicLong;

public final class PerformanceMonitorTest {
    private static int checks;
    private static final long MS = 1_000_000L;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void near(double expected, double actual, String message) {
        check(Math.abs(expected - actual) < .00001, message + ": " + actual);
    }
    private static void sample(PerformanceMonitor.Metric metric, AtomicLong clock, int ms) {
        long start = metric.begin();
        clock.addAndGet(ms * MS);
        metric.end(start);
    }

    public static void main(String[] args) {
        AtomicLong clock = new AtomicLong();
        PerformanceMonitor monitor = new PerformanceMonitor(clock::get);
        var tick = monitor.metric("Farm", PerformanceMonitor.Phase.TICK);
        var world = monitor.metric("Plan", PerformanceMonitor.Phase.WORLD);
        check(!monitor.enabled(), "Default off");
        sample(tick, clock, 5);
        clock.set(1000 * MS);
        check(monitor.snapshot().rows().isEmpty(), "Disabled calls ignored");

        monitor.setEnabled(true);
        sample(tick, clock, 2);
        sample(tick, clock, 4);
        sample(world, clock, 10);
        clock.set(1500 * MS);
        var first = monitor.snapshot();
        check(first.rows().size() == 2, "Two measured sections");
        check(first.rows().getFirst().name().equals("Plan"), "Sorted by total time");
        near(3, first.rows().get(1).averageMs(), "Mean per call");
        near(4, first.rows().get(1).maxMs(), "Maximum per call");
        near(12, first.rows().get(1).msPerSecond(), "Rate includes idle time");
        near(32, first.measuredMsPerSecond(), "Non-overlapping total");
        check(first.rows().get(1).calls() == 2, "Call count");
        clock.set(1600 * MS);
        check(first == monitor.snapshot(), "Snapshot reused within 500ms");

        monitor.frame(true);
        clock.addAndGet(16 * MS); monitor.frame(true);
        clock.addAndGet(50 * MS); monitor.frame(true);
        clock.addAndGet(80 * MS); monitor.frame(true);
        clock.set(2100 * MS);
        var frames = monitor.snapshot();
        check(frames.frames().calls() == 3, "First frame does not create artificial interval");
        near(146 / 3.0, frames.frames().averageMs(), "Frame mean");
        near(80, frames.frames().maxMs(), "Frame peak");
        check(frames.slowFrames() == 1, "Only frames strictly above 50ms");
        check(frames.graph().equals(java.util.List.of(16.0, 50.0, 80.0)), "History in order");
        near(16 / 1.1, frames.measuredMsPerSecond(), "Frames excluded from Lumen total");
        monitor.frame(false);
        clock.addAndGet(300 * MS); monitor.frame(true);
        clock.addAndGet(16 * MS); monitor.frame(true);
        clock.addAndGet(500 * MS);
        check(monitor.snapshot().frames().calls() == 4, "No artificial frame across missing world");

        monitor.setEnabled(false);
        var stopped = monitor.snapshot();
        sample(tick, clock, 100);
        clock.addAndGet(20_000 * MS);
        monitor.frame(true);
        check(stopped == monitor.snapshot(), "Stopped snapshot stays frozen");
        monitor.setEnabled(true);
        check(monitor.snapshot().rows().isEmpty(), "Resume starts fresh comparison");
        monitor.frame(true);
        check(monitor.snapshot().graph().isEmpty(), "Resume excludes stopped interval");

        monitor.reset();
        long abandoned = tick.begin();
        clock.addAndGet(MS);
        monitor.reset();
        clock.addAndGet(MS);
        tick.end(abandoned);
        clock.addAndGet(500 * MS);
        check(monitor.snapshot().rows().isEmpty(), "In-flight pre-reset sample discarded");
        sample(tick, clock, 100);
        clock.addAndGet(500 * MS);
        near(100, monitor.snapshot().rows().getFirst().maxMs(), "Spike visible");
        clock.addAndGet(11_000 * MS);
        check(monitor.snapshot().rows().isEmpty(), "Old spikes expire even without new calls");
        sample(tick, clock, 1);
        clock.addAndGet(500 * MS);
        near(1, monitor.snapshot().rows().getFirst().maxMs(), "Reused bucket clears old peak");

        monitor.reset();
        for (int i = 0; i < 1000; i++) {
            clock.addAndGet(i == 999 ? 31 * MS : 16 * MS);
            monitor.frame(true);
        }
        var bounded = monitor.snapshot();
        check(bounded.graph().size() == 120, "Frame history bounded");
        near(31, bounded.graph().getLast(), "Newest frame retained after ring wrap");
        check(bounded.frames().calls() < 650, "Stats expire while continuously recording");
        check(bounded.seconds() >= 9 && bounded.seconds() <= 10, "Actual rolling bucket duration");
        monitor.reset();
        check(monitor.snapshot().frames().calls() == 0, "World reset clears frames");
        check(monitor.snapshot().graph().isEmpty(), "World reset clears graph");

        // System.nanoTime is allowed to be negative; bucket indexing must still work.
        AtomicLong negativeClock = new AtomicLong(-12_000 * MS);
        var negative = new PerformanceMonitor(negativeClock::get);
        var negativeMetric = negative.metric("Negative clock", PerformanceMonitor.Phase.HUD);
        negative.setEnabled(true);
        sample(negativeMetric, negativeClock, 3);
        negativeClock.addAndGet(500 * MS);
        near(3, negative.snapshot().rows().getFirst().averageMs(), "Negative monotonic timestamps");
        var clockReads = new java.util.concurrent.atomic.AtomicInteger();
        var idle = new PerformanceMonitor(() -> { clockReads.incrementAndGet(); return 0; });
        var idleMetric = idle.metric("Off", PerformanceMonitor.Phase.TICK);
        for (int i = 0; i < 10_000; i++) idleMetric.end(idleMetric.begin());
        idle.frame(true);
        check(clockReads.get() == 0, "Disabled hooks do not query clock");
        System.out.println("PerformanceMonitorTest: " + checks + " checks passed");
    }
}
