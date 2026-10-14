package com.example.examplemod.gui.hud;

import com.example.examplemod.feature.Performance;
import com.example.examplemod.gui.PerformanceScreen;
import com.example.examplemod.gui.Theme;
import com.example.examplemod.gui.UiRender;
import com.example.examplemod.performance.PerformanceMonitor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;

public final class PerformanceElement extends HudElement {
    private final Performance feature;
    private PerformanceMonitor.Snapshot last;
    private String frame = "", cost = "", top = "";

    PerformanceElement(Performance feature) {
        super("performance", "Leistungsanzeige", feature.overlay, 0, 1, 4, 58);
        this.feature = feature;
    }
    @Override public boolean isVisible() { return feature.isEnabled() && feature.overlay.get(); }
    @Override public void toggleVisible() {
        if (!feature.isEnabled()) { feature.setEnabled(true); if (!feature.overlay.get()) feature.overlay.toggle(); }
        else feature.overlay.toggle();
    }
    @Override public void measure(Minecraft mc, boolean editing) { w = 214; h = 82; }
    @Override public void render(GuiGraphics g, Minecraft mc, boolean editing, float delta) {
        var snapshot = PerformanceMonitor.INSTANCE.snapshot();
        if (snapshot != last) {
            last = snapshot;
            frame = snapshot.frames().calls() == 0 ? "Messung läuft an …" : String.format(Locale.GERMAN,
                    "Frame Ø %.1f · Max %.1f ms", snapshot.frames().averageMs(), snapshot.frames().maxMs());
            cost = String.format(Locale.GERMAN, "Lumen gemessen: %.1f ms/s", snapshot.measuredMsPerSecond());
            top = snapshot.rows().isEmpty() ? "Details: /leistung" : "Top: " + snapshot.rows().getFirst().name()
                    + " · " + PerformanceScreen.phase(snapshot.rows().getFirst().phase());
        }
        UiRender.roundRect(g, x, y, w, h, 4, Theme.HUD_BG);
        g.drawString(mc.font, mc.getFps() + (PerformanceMonitor.INSTANCE.recording() ? " FPS · REC " + PerformanceMonitor.INSTANCE.recordingElapsedMillis() / 1000 + "/120 s" : " FPS · Leistung"), x + 7, y + 6, Theme.ACCENT_2, false);
        g.drawString(mc.font, frame, x + 7, y + 19, Theme.TEXT, false);
        g.drawString(mc.font, cost, x + 7, y + 31, Theme.TEXT_DIM, false);
        PerformanceScreen.graph(g, snapshot.graph(), x + 7, y + 44, w - 14, 19);
        g.drawString(mc.font, UiRender.ellipsize(mc.font, top, w - 14), x + 7, y + 68, Theme.TEXT_DIM, false);
    }
}
