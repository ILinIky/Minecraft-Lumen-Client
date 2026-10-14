package com.example.examplemod.gui;

import com.example.examplemod.module.ModuleManager;
import com.example.examplemod.performance.PerformanceMonitor;
import com.example.examplemod.performance.PerformanceCapture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** Live diagnostics use the same cached snapshot as the compact HUD. */
public final class PerformanceScreen extends ClientScreen {
    private final Scroll scroll = new Scroll();
    private PerformanceMonitor.Snapshot last;
    private List<String> numbers = List.of();
    private String frameText = "", workload = "", window = "";

    public PerformanceScreen(Screen parent) { super(Component.literal("Leistungsanzeige"), parent); }
    @Override protected int preferredWidth() { return 570; }
    @Override protected int preferredHeight() { return 440; }

    @Override protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        var monitor = PerformanceMonitor.INSTANCE;
        var snapshot = monitor.snapshot();
        if (snapshot != last) {
            last = snapshot;
            numbers = snapshot.rows().stream().map(row -> String.format(Locale.GERMAN,
                    "%.2f / %.2f / %.1f", row.averageMs(), row.maxMs(), row.msPerSecond())).toList();
            frameText = snapshot.frames().calls() == 0 ? "Noch keine Frames erfasst" : String.format(Locale.GERMAN,
                    "Frame Ø %.1f ms · Spitze %.1f ms · %d über 50 ms",
                    snapshot.frames().averageMs(), snapshot.frames().maxMs(), snapshot.slowFrames());
            workload = String.format(Locale.GERMAN, "Lumen: %.1f ms/s gemessen · Fenster %.1f s",
                    snapshot.measuredMsPerSecond(), snapshot.seconds());
            window = monitor.enabled() ? "Live · bis 10 s" : "Messung gestoppt";
        }
        drawTitle(g, "Leistung", window);
        int x = winX + 14, w = winW - 28, y = winY + 40;
        boolean compact = winH < 340;
        line(g, frameText, x, y, w, Theme.TEXT);
        line(g, workload, x, y + 14, w, Theme.TEXT_DIM);
        if (!compact) {
            graph(g, snapshot.graph(), x, y + 30, w, 34);
            line(g, "Letzte 120 Frames · Linie 16,7 ms (60 FPS) · Höhe max. 100 ms", x, y + 68, w, Theme.TEXT_MUTED);
        }
        int recordingY = winY + winH - (compact ? 100 : 134);
        int tableY = y + (compact ? 32 : 88);
        int tableH = Math.max(0, recordingY - 9 - (tableY + 15));
        line(g, "Bereich / Aufrufart", x, tableY, w / 2, Theme.TEXT_DIM);
        String heading = "Ø / Spitze (ms) / ms pro s";
        g.drawString(font, heading, x + w - font.width(heading), tableY, Theme.TEXT_DIM, false);
        int contentY = tableY + 15;
        scroll.setMax(snapshot.rows().size() * 32 - tableH);
        int offset = scroll.update(frameDelta);
        pushClip(g, x, contentY, w, tableH);
        double highest = snapshot.rows().isEmpty() ? 1 : Math.max(.001, snapshot.rows().getFirst().msPerSecond());
        for (int i = 0; i < snapshot.rows().size(); i++) {
            int rowY = contentY + i * 32 - offset;
            if (rowY + 30 < contentY || rowY >= contentY + tableH) continue;
            var row = snapshot.rows().get(i);
            UiRender.roundRect(g, x, rowY, w - 6, 29, 3, Theme.CARD);
            String value = numbers.get(i);
            int valueX = x + w - 12 - font.width(value);
            line(g, row.name(), x + 7, rowY + 4, Math.max(0, valueX - x - 14), Theme.TEXT);
            g.drawString(font, value, valueX, rowY + 4, Theme.TEXT, false);
            line(g, phase(row.phase()) + " · " + row.calls() + " Aufrufe", x + 7, rowY + 16, w - 20, Theme.TEXT_MUTED);
            int bar = (int) ((w - 20) * row.msPerSecond() / highest);
            g.fill(x + 7, rowY + 27, x + 7 + bar, rowY + 28, Theme.ACCENT);
        }
        if (snapshot.rows().isEmpty()) line(g, "Messung starten und kurz in der Welt spielen.", x + 4, contentY + 10, w - 8, Theme.TEXT_MUTED);
        popClip(g);
        scrollbar(g, scroll, x + w - 3, contentY, tableH, snapshot.rows().size() * 32);
        line(g, PerformanceCapture.status(), x, recordingY, w, monitor.recording() ? Theme.WARNING : Theme.TEXT_DIM);
        int recordW = (w - 6) * 2 / 3;
        String recordLabel = PerformanceCapture.writing() ? "Wird gespeichert …" : monitor.recording()
                ? "Stoppen & exportieren" : PerformanceCapture.failed() ? "Export wiederholen" : "Aufnahme starten";
        button(g, mouseX, mouseY, x, recordingY + 14, recordW, 22, recordLabel, Style.PRIMARY, () -> {
            if (PerformanceCapture.writing()) return;
            if (monitor.recording()) PerformanceCapture.stopAndExport(); else PerformanceCapture.start();
        });
        button(g, mouseX, mouseY, x + recordW + 6, recordingY + 14, w - recordW - 6, 22,
                "Exportordner", Style.SECONDARY, PerformanceCapture::openDirectory);
        if (hovered(mouseX, mouseY, x, recordingY, w, 12)) {
            String hint = PerformanceCapture.lastFile() != null ? PerformanceCapture.lastFile().getFileName().toString()
                    : "JSON mit Messverlauf und technischen Daten; keine Namen, Adressen oder Koordinaten.";
            g.setTooltipForNextFrame(font, Component.literal(hint), mouseX, mouseY);
        }
        int footer = winY + winH - (compact ? 57 : 87);
        if (compact) {
            line(g, "Aufrufzeiten · keine GPU/Server-Messung · Details hier", x, footer, w, Theme.TEXT_MUTED);
            if (hovered(mouseX, mouseY, x, footer, w, 12)) g.setTooltipForNextFrame(font,
                    Component.literal("Sortiert nach ms/s. Aufrufzeiten enthalten Wartezeiten; Frames auch FPS-Limit und VSync. Menüs beeinflussen die Werte. Keine GPU-, Server- oder Hintergrundmessung."), mouseX, mouseY);
        } else {
            line(g, "Sortiert nach Zeit pro Sekunde; Tick, Welt und HUD getrennt.", x, footer, w, Theme.TEXT_DIM);
            line(g, "CPU-Aufrufdauer inkl. Wartezeiten; keine GPU-, Server- oder Hintergrundmessung.", x, footer + 13, w, Theme.TEXT_MUTED);
            line(g, "Frame-Abstand enthält FPS-Limit / VSync. Menüs beeinflussen die Messung.", x, footer + 26, w, Theme.TEXT_MUTED);
        }
        int bw = (w - 12) / 3, by = winY + winH - 34;
        button(g, mouseX, mouseY, x, by, bw, 22, "Zurück", Style.SECONDARY, this::onClose);
        button(g, mouseX, mouseY, x + bw + 6, by, bw, 22,
                monitor.enabled() ? "Messung stoppen" : "Messung starten", Style.PRIMARY, () -> {
                    ModuleManager.PERFORMANCE.toggle(); last = null;
                });
        button(g, mouseX, mouseY, x + 2 * (bw + 6), by, bw, 22, "Zurücksetzen", Style.SECONDARY, () -> {
            monitor.reset(); scroll.reset(); last = null;
        });
    }

    private void line(GuiGraphics g, String text, int x, int y, int width, int color) {
        g.drawString(font, UiRender.ellipsize(font, text, width), x, y, color, false);
    }
    public static String phase(PerformanceMonitor.Phase phase) {
        return switch (phase) { case TICK -> "Tick"; case WORLD -> "Welt-Vorschau";
            case HUD -> "HUD"; case OTHER -> "Client-Tick"; case FRAME -> "Frame"; };
    }
    public static void graph(GuiGraphics g, List<Double> values, int x, int y, int w, int h) {
        UiRender.roundRect(g, x, y, w, h, 3, Theme.CARD);
        int target = y + h - 1 - Math.round((h - 2) * 16.667F / 100);
        g.fill(x + 1, target, x + w - 1, target + 1, Theme.TEXT_MUTED);
        for (int i = 0; i < values.size(); i++) {
            double ms = values.get(i);
            int left = x + 1 + (w - 2) * i / 120;
            int right = x + 1 + (w - 2) * (i + 1) / 120;
            int height = Math.max(1, (int) ((h - 2) * Math.min(100, ms) / 100));
            g.fill(left, y + h - 1 - height, Math.max(left + 1, right), y + h - 1,
                    ms > 50 ? Theme.WARNING : Theme.ACCENT);
        }
    }
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        scroll.scroll(dy); return true;
    }
}
