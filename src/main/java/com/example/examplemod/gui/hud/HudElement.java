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
    /** Größe im HUD-Editor (Ecke ziehen), 50–250 %. */
    public static final float MIN_SCALE = 0.5F, MAX_SCALE = 2.5F;
    public float scale = 1F;
    /**
     * Zuletzt berechnete Lage, für den Editor. Nach {@link #place} in Bildschirm-Koordinaten (skaliert);
     * während {@link #render} lokal: x/y = 0 und w/h ungeskaliert.
     */
    public int x, y, w, h;
    /** Ungeskalierte Größe aus {@link #measure}. */
    public int baseW, baseH;
    /** Gerade ohne Inhalt (im Editor nur als Platzhalter). */
    public boolean empty;

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
        scale = 1F;
    }

    public void setScale(float value) {
        scale = Float.isFinite(value) ? Math.max(MIN_SCALE, Math.min(MAX_SCALE, value)) : 1F;
    }

    /** Messen, auf {@link #scale} umrechnen und anordnen; danach gelten x/y/w/h auf dem Bildschirm. */
    public void place(Minecraft mc, int screenW, int screenH, boolean editing) {
        measure(mc, editing);
        baseW = w; baseH = h;
        empty = w <= 0 || h <= 0;
        if (!empty) { w = Math.round(baseW * scale); h = Math.round(baseH * scale); }
        layout(mc, screenW, screenH, editing);
    }

    /** Zeichnet nach {@link #place} vergrößert/verkleinert; das Element selbst rechnet ungeskaliert ab 0/0. */
    public void draw(GuiGraphics g, Minecraft mc, boolean editing, float delta) {
        int sx = x, sy = y, sw = w, sh = h;
        g.pose().pushMatrix();
        g.pose().translate(sx, sy);
        if (scale != 1F) g.pose().scale(scale, scale);
        x = 0; y = 0; w = baseW; h = baseH;
        try { render(g, mc, editing, delta); }
        finally { g.pose().popMatrix(); x = sx; y = sy; w = sw; h = sh; }
    }

    /** Mausposition in ungeskalierte Element-Koordinaten (0/0 = linke obere Ecke). */
    public double localX(double mouseX) { return (mouseX - x) / scale; }
    public double localY(double mouseY) { return (mouseY - y) / scale; }

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
