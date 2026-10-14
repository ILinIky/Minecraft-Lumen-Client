package com.example.examplemod.feature;

import com.example.examplemod.gui.PerformanceScreen;
import com.example.examplemod.gui.hud.HudEditorScreen;
import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.Setting;
import com.example.examplemod.performance.PerformanceMonitor;

public final class Performance extends Module {
    public final Setting.Bool overlay;

    public Performance() {
        super("performance", "Leistungsanzeige", "Frame-Verlauf und gemessene Lumen-Zeiten pro Bereich", Category.CLIENT);
        overlay = add(new Setting.Bool("Kompaktes HUD", "Messwerte beim Spielen anzeigen; im HUD-Editor verschiebbar", true));
        add(new Setting.Action("Messwerte öffnen", "Details, Spitzen und teuerste Bereiche vergleichen", () ->
                mc.setScreen(new PerformanceScreen(mc.screen))));
        add(new Setting.Action("Messung zurücksetzen", "Neue Vergleichsmessung beginnen", PerformanceMonitor.INSTANCE::reset));
        add(new Setting.Action("Position ändern", "Leistungsanzeige im HUD-Editor verschieben", () ->
                mc.setScreen(new HudEditorScreen(mc.screen))));
    }

    @Override protected void onEnable() { PerformanceMonitor.INSTANCE.setEnabled(true); }
    @Override protected void onDisable() { PerformanceMonitor.INSTANCE.setEnabled(false); }
    @Override public void onLeaveWorld() {
        com.example.examplemod.performance.PerformanceCapture.onLeaveWorld();
        PerformanceMonitor.INSTANCE.reset();
    }
    @Override public boolean showInModuleList() { return false; }
}
