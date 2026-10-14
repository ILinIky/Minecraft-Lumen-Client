package com.example.examplemod.gui.hud;

import com.example.examplemod.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Ein verschiebbares HUD-Element. Die Position wird relativ zur nächsten Bildschirmkante
 * gespeichert (Anker 0 = links/oben, 0.5 = Mitte, 1 = rechts/unten), damit sie bei jeder
 * Fenstergröße und GUI-Skalierung passt.
 */
public abstract class HudElement {
    public final com.example.examplemod.performance.PerformanceMonitor.Metric metric;
    public final String id;
    public final String name;
    private final Setting.Bool visible;
    private final float defaultAnchorX, defaultAnchorY;
    private final int defaultOffsetX, defaultOffsetY;

    public float anchorX, anchorY;
    public int offsetX, offsetY;
    /** Zuletzt berechnete Lage, für den Editor. */
    public int x, y, w, h;

    protected HudElement(String id, String name, Setting.Bool visible, float anchorX, float anchorY, int offsetX, int offsetY) {
        metric = com.example.examplemod.performance.PerformanceMonitor.INSTANCE.metric(name,
                com.example.examplemod.performance.PerformanceMonitor.Phase.HUD);
        this.id = id;
        this.name = name;
        this.visible = visible;
        this.defaultAnchorX = anchorX;
        this.defaultAnchorY = anchorY;
        this.defaultOffsetX = offsetX;
        this.defaultOffsetY = offsetY;
        reset();
    }

    public void reset() {
        anchorX = defaultAnchorX;
        anchorY = defaultAnchorY;
        offsetX = defaultOffsetX;
        offsetY = defaultOffsetY;
    }

    public boolean isVisible() { return visible.get(); }
    public void toggleVisible() { visible.toggle(); }

    public boolean alignRight() { return anchorX >= 1; }

    /** Setzt {@link #w} und {@link #h}; 0 bedeutet, dass gerade nichts angezeigt wird. */
    public abstract void measure(Minecraft mc, boolean editing);

    /** Zeichnet an {@link #x}/{@link #y}. */
    public abstract void render(GuiGraphics g, Minecraft mc, boolean editing, float delta);

    /** Zusätzlicher Abstand nach unten, z.B. unter den Vanilla-Effektsymbolen. */
    protected int extraOffsetY(Minecraft mc, boolean editing) { return 0; }

    public void layout(Minecraft mc, int screenW, int screenH, boolean editing) {
        x = anchorX <= 0 ? offsetX : anchorX >= 1 ? screenW - w - offsetX : screenW / 2 + offsetX - w / 2;
        int oy = offsetY + extraOffsetY(mc, editing);
        y = anchorY <= 0 ? oy : anchorY >= 1 ? screenH - h - oy : screenH / 2 + oy - h / 2;
        x = Math.max(0, Math.min(screenW - w, x));
        y = Math.max(0, Math.min(screenH - h, y));
    }

    /** Übernimmt eine neue Lage aus dem Editor und wählt dafür den passenden Anker. */
    public void moveTo(int newX, int newY, int screenW, int screenH) {
        newX = Math.max(0, Math.min(screenW - w, newX));
        newY = Math.max(0, Math.min(screenH - h, newY));
        int centerX = newX + w / 2, centerY = newY + h / 2;
        if (centerX < screenW / 3) {
            anchorX = 0; offsetX = newX;
        } else if (centerX > screenW * 2 / 3) {
            anchorX = 1; offsetX = screenW - newX - w;
        } else {
            anchorX = 0.5F; offsetX = centerX - screenW / 2;
        }
        if (centerY < screenH / 3) {
            anchorY = 0; offsetY = newY;
        } else if (centerY > screenH * 2 / 3) {
            anchorY = 1; offsetY = screenH - newY - h;
        } else {
            anchorY = 0.5F; offsetY = centerY - screenH / 2;
        }
        x = newX;
        y = newY;
    }
}
