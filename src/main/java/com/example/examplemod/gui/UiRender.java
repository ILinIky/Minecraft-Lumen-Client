package com.example.examplemod.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/** Zeichenhilfen für das moderne Menü-Design (abgerundete Flächen, Schalter, Verläufe). */
public final class UiRender {
    private UiRender() {}

    public static void roundRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.min(radius, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
    }

    /** Abgerundete Fläche mit 1px Rahmen. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int radius, int fill, int border) {
        roundRect(g, x, y, w, h, radius, border);
        roundRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), fill);
    }

    /** Weicher Schatten unter Fenstern. */
    public static void shadow(GuiGraphics g, int x, int y, int w, int h, int radius) {
        for (int i = 6; i >= 1; i--) {
            roundRect(g, x - i, y - i + 2, w + i * 2, h + i * 2, radius + i, Theme.alpha(Theme.SHADOW, 0.35F / i));
        }
    }

    public static void gradientH(GuiGraphics g, int x, int y, int w, int h, int left, int right) {
        int step = Math.max(1, w / 64);
        for (int i = 0; i < w; i += step) {
            int stripe = Math.min(step, w - i);
            g.fill(x + i, y, x + i + stripe, y + h, Theme.lerp(left, right, (float) i / Math.max(1, w - 1)));
        }
    }

    /** Animierter An/Aus-Schalter, {@code progress} von 0 (aus) bis 1 (an). */
    public static void toggle(GuiGraphics g, int x, int y, float progress, boolean hovered) {
        int w = 20, h = 10;
        int track = Theme.lerp(hovered ? Theme.SWITCH_OFF_HOVER : Theme.SWITCH_OFF, Theme.accent(progress * 0.4F), progress);
        roundRect(g, x, y, w, h, 5, track);
        int knobX = x + 1 + Math.round((w - h) * ease(progress));
        roundRect(g, knobX, y + 1, h - 2, h - 2, 4, Theme.KNOB);
    }

    public static void checkbox(GuiGraphics g, Font font, int x, int y, boolean checked, boolean hovered) {
        panel(g, x, y, 10, 10, 2, checked ? Theme.ACCENT : Theme.FIELD, checked ? Theme.ACCENT : (hovered ? Theme.TEXT_MUTED : Theme.FIELD_BORDER));
        if (checked) g.drawString(font, "✔", x + 2, y + 1, Theme.ON_ACCENT, false);
    }

    /** Flache Schaltfläche mit Hover-Zustand. */
    public static void button(GuiGraphics g, Font font, int x, int y, int w, int h, String label,
                              boolean hovered, int baseColor, int textColor) {
        int fill = hovered ? Theme.hover(baseColor) : baseColor;
        roundRect(g, x, y, w, h, 3, fill);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 8) / 2, textColor, false);
    }

    public static void text(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    public static void scaledText(GuiGraphics g, Font font, String text, float x, float y, int color, float scale) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(scale, scale);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    public static String ellipsize(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String dots = "…";
        return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(dots))) + dots;
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public static float ease(float t) {
        t = Math.max(0, Math.min(1, t));
        return 1 - (float) Math.pow(1 - t, 3);
    }

    /** Bewegt einen Animationswert zeitbasiert Richtung Ziel. */
    public static float approach(float current, float target, float deltaSeconds, float speed) {
        float diff = target - current;
        float step = deltaSeconds * speed;
        if (Math.abs(diff) <= step) return target;
        return current + Math.signum(diff) * step;
    }
}
