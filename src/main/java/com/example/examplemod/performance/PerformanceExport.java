package com.example.examplemod.performance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Versioned local-only diagnostic format; write failures propagate so a retry remains possible. */
public final class PerformanceExport {
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private record Document(String format, int version, String units, List<String> interpretation,
                            PerformanceRecording.Report recording) {}
    private PerformanceExport() {}

    public static Path write(Path directory, PerformanceRecording recording) throws IOException {
        var report = recording.report();
        var document = new Document("lumen-performance", 1, "Durations and offsets in milliseconds; memory in MiB",
                List.of("Intervals are non-overlapping, indexed by call completion time; not rolling HUD snapshots.",
                        "CPU wall-clock call duration includes waits and GC; no GPU, server, worker-thread or complete game profiling.",
                        "FRAME is full frame spacing including FPS limit/VSync; never add FRAME to Lumen section totals.",
                        "Calls crossing interval boundaries are assigned whole to the ending interval. Context is sampled once per second; null means unobserved.",
                        "Menus influence timings. Enabled module IDs describe observed state, not module settings.",
                        "Worst frames are the strongest 64 over 50ms, with end offsets; these do not prove which module caused a stall.",
                        "Automatic stop occurs on the next render callback; a stall can extend the last interval beyond 120 seconds."), report);
        Files.createDirectories(directory);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path temp = Files.createTempFile(directory, "lumen-leistung-" + stamp + "-", ".tmp");
        Path target = temp.resolveSibling(temp.getFileName().toString().replaceFirst("\\.tmp$", ".json"));
        try {
            try (var writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) { GSON.toJson(document, writer); }
            try { Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, target); }
            return target;
        } finally { Files.deleteIfExists(temp); }
    }
}
