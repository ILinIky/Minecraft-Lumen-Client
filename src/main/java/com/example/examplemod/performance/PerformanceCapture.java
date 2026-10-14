package com.example.examplemod.performance;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.gui.Notifications;
import com.example.examplemod.module.ClientConfig;
import com.example.examplemod.module.ModuleManager;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.ModList;
import org.lwjgl.opengl.GL11;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Main-thread controller. Only immutable, finished recordings go to the bounded writer. */
public final class PerformanceCapture {
    private static final ThreadPoolExecutor WRITER = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1), task -> {
                Thread thread = new Thread(task, "Lumen-Leistungsbericht");
                thread.setDaemon(false);
                return thread;
            });
    static { WRITER.allowCoreThreadTimeOut(true); }
    private static PerformanceRecording lastRecording;
    private static Path lastFile;
    private static boolean writing, failed;
    private static int lastContextSecond = -1;

    private PerformanceCapture() {}
    public static Path directory() { return ClientConfig.directory().resolve("performance").resolve("exports"); }
    public static Path lastFile() { return lastFile; }
    public static boolean writing() { return writing; }
    public static boolean failed() { return failed; }

    public static String status() {
        var monitor = PerformanceMonitor.INSTANCE;
        if (monitor.recording()) return "Aufnahme " + monitor.recordingElapsedMillis() / 1000 + " / 120 s";
        if (writing) return "Diagnose wird im Hintergrund gespeichert …";
        if (failed) return "Export fehlgeschlagen · Aufnahme für Wiederholung behalten";
        if (lastFile != null) return "Diagnose gespeichert · JSON im Exportordner";
        return "Bis 120 s aufnehmen · danach JSON zum Weitergeben";
    }

    public static void start() {
        onFrame(); // Finish any pending reset/disable before starting another session.
        if (writing || PerformanceMonitor.INSTANCE.recording()) return;
        if (failed) { retry(); return; }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            Notifications.info("Leistungsaufnahme", "Bitte zuerst eine Welt betreten");
            return;
        }
        ModuleManager.PERFORMANCE.setEnabled(true, false);
        if (PerformanceMonitor.INSTANCE.startRecording(Instant.now().toString(), environment(mc))) {
            lastContextSecond = -1;
            lastFile = null;
            lastRecording = null;
            onFrame();
            Notifications.info("Aufnahme gestartet", "Normal spielen · /leistung → Stoppen & exportieren · maximal 120 s");
        }
    }

    public static void stopAndExport() {
        PerformanceMonitor.INSTANCE.stopRecording(PerformanceRecording.Reason.MANUAL);
        onFrame();
        if (failed && !writing) retry();
    }

    /** Called even without visible HUD. No sampling work when recording is off. */
    public static void onFrame() {
        var monitor = PerformanceMonitor.INSTANCE;
        PerformanceRecording completed = monitor.takeCompletedRecording();
        if (completed != null) {
            lastRecording = completed;
            save(completed);
        }
        if (!monitor.recording()) return;
        int second = (int) (monitor.recordingElapsedMillis() / 1000);
        if (second == lastContextSecond) return;
        lastContextSecond = second;
        Minecraft mc = Minecraft.getInstance();
        Runtime runtime = Runtime.getRuntime();
        monitor.recordingContext(new PerformanceRecording.Context(mc.getFps(),
                (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024), mc.screen != null, mc.isPaused(),
                ModuleManager.all().stream().filter(module -> module.isEnabled()).map(module -> module.getId()).toList()));
    }

    public static void onLeaveWorld() {
        PerformanceMonitor.INSTANCE.stopRecording(PerformanceRecording.Reason.WORLD_LEFT);
        onFrame();
    }

    public static void retry() { if (failed && !writing && lastRecording != null) save(lastRecording); }

    private static void save(PerformanceRecording recording) {
        writing = true;
        failed = false;
        Path folder = directory();
        Minecraft mc = Minecraft.getInstance();
        WRITER.execute(() -> {
            try {
                Path file = PerformanceExport.write(folder, recording);
                mc.execute(() -> {
                    writing = false;
                    lastFile = file;
                    lastRecording = null;
                    Notifications.success("Leistungsaufnahme gespeichert", "JSON bereit · /leistung → Exportordner");
                });
            } catch (Exception error) {
                ExampleMod.LOGGER.error("Leistungsaufnahme konnte nicht exportiert werden", error);
                mc.execute(() -> {
                    writing = false;
                    failed = true;
                    Notifications.error("Diagnose-Export fehlgeschlagen", "Aufnahme bleibt erhalten · unter /leistung erneut exportieren");
                });
            }
        });
    }

    public static void openDirectory() {
        // Opening is a user action; creating this small directory follows the existing export UI.
        try {
            Path folder = directory();
            Files.createDirectories(folder);
            Util.getPlatform().openPath(folder.toAbsolutePath());
        } catch (Exception error) {
            ExampleMod.LOGGER.error("Leistungs-Exportordner konnte nicht geöffnet werden", error);
            Notifications.error("Exportordner", "Ordner konnte nicht geöffnet werden");
        }
    }

    /** Explicit allowlist: no config dump, world IDs, addresses, paths or user-generated names. */
    private static Map<String, String> environment(Minecraft mc) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("minecraft", SharedConstants.getCurrentVersion().name());
        for (String id : new String[]{ExampleMod.MODID, "forge"}) ModList.get().getModContainerById(id)
                .ifPresent(mod -> data.put(id, mod.getModInfo().getVersion().toString()));
        data.put("java", System.getProperty("java.version", "unknown"));
        data.put("os", System.getProperty("os.name", "unknown"));
        data.put("arch", System.getProperty("os.arch", "unknown"));
        data.put("logicalProcessors", String.valueOf(Runtime.getRuntime().availableProcessors()));
        data.put("maxHeapMiB", String.valueOf(Runtime.getRuntime().maxMemory() / (1024 * 1024)));
        String renderer = GL11.glGetString(GL11.GL_RENDERER);
        if (renderer != null) data.put("graphicsRenderer", renderer);
        data.put("renderDistance", String.valueOf(mc.options.renderDistance().get()));
        data.put("simulationDistance", String.valueOf(mc.options.simulationDistance().get()));
        data.put("fpsLimit", String.valueOf(mc.options.framerateLimit().get()));
        data.put("vsync", String.valueOf(mc.options.enableVsync().get()));
        data.put("graphicsMode", mc.options.graphicsMode().get().name());
        data.put("worldType", mc.getSingleplayerServer() == null ? "multiplayer" : "integrated-server");
        return data;
    }
}
