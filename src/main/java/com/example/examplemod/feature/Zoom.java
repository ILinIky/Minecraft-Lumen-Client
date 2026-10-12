package com.example.examplemod.feature;

import com.example.examplemod.ClientKeybinds;
import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.ModuleManager;
import com.example.examplemod.module.Setting;
import net.minecraft.Util;

/** Zoom wie bei OptiFine: Taste C gedrückt halten, Mausrad ändert die Stärke. */
public class Zoom extends Module {
    private final Setting.Slider strength;
    private final Setting.Bool scroll;
    private final Setting.Bool smooth;
    private final Setting.Bool slowMouse;

    private double scrollFactor = 1.0;
    private double current = 1.0;
    private long lastFrame;

    public Zoom() {
        super("zoom", "Zoom", "Taste C halten zum Heranzoomen (Optionen → Steuerung)", Category.RENDER);
        enabledByDefault();
        strength = add(new Setting.Slider("Stärke", "Vergrößerung beim Drücken der Zoom-Taste", 4, 2, 15, 0.5, "x"));
        scroll = add(new Setting.Bool("Mausrad", "Während des Zooms mit dem Mausrad nachregeln", true));
        smooth = add(new Setting.Bool("Sanft", "Weicher Übergang beim Zoomen", true));
        slowMouse = add(new Setting.Bool("Maus verlangsamen", "Feineres Zielen beim Zoomen", true));
    }

    public static Zoom get() { return ModuleManager.ZOOM; }

    @Override
    public boolean showInModuleList() { return false; }

    /** Ob die Zoom-Taste gerade gehalten wird (und kein Menü offen ist). */
    public boolean isZooming() {
        return isEnabled() && mc.screen == null && ClientKeybinds.ZOOM != null && ClientKeybinds.ZOOM.isDown();
    }

    /** Wird einmal pro Bild aus dem FOV-Ereignis aufgerufen und liefert den aktuellen Faktor. */
    public double updateFactor() {
        long now = Util.getMillis();
        float delta = lastFrame == 0 ? 0 : Math.min(0.1F, (now - lastFrame) / 1000F);
        lastFrame = now;
        boolean zooming = isZooming();
        if (!zooming) scrollFactor = 1.0;
        double target = zooming ? strength.get() * scrollFactor : 1.0;
        target = Math.max(1.0, Math.min(50.0, target));
        if (!smooth.get()) {
            current = target;
        } else {
            current += (target - current) * Math.min(1.0, delta * 14.0);
            if (Math.abs(target - current) < 0.01) current = target;
        }
        return current;
    }

    /** Mausrad während des Zooms: true = Scrollen verbraucht (keine Hotbar-Änderung). */
    public boolean onScroll(double delta) {
        if (!isZooming() || !scroll.get()) return false;
        scrollFactor = Math.max(0.25, Math.min(5.0, scrollFactor * (delta > 0 ? 1.15 : 1 / 1.15)));
        return true;
    }

    /** Faktor für die Mausbewegung, damit man beim Zoomen genauer zielt. */
    public double mouseFactor() {
        return slowMouse.get() && current > 1.01 ? 1.0 / current : 1.0;
    }
}
