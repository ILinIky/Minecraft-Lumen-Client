package com.example.examplemod.gui;

import com.example.examplemod.module.Module;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Kleine Einblendungen unten rechts statt Chat-Nachrichten. */
public final class Notifications {
    private static final long DURATION = 2600L;
    private static final long FADE = 220L;
    private static final int MAX = 5;
    private static final int WIDTH = 150;
    private static final int HEIGHT = 30;
    private static final List<Entry> ENTRIES = new ArrayList<>();

    private record Entry(String title, String message, int color, long start) {}

    private Notifications() {}

    public static void info(String title, String message) { push(title, message, Theme.INFO); }
    public static void success(String title, String message) { push(title, message, Theme.SUCCESS); }
    public static void warning(String title, String message) { push(title, message, Theme.WARNING); }
    public static void error(String title, String message) { push(title, message, Theme.DANGER); }

    public static void moduleToggled(Module module) {
        if (Minecraft.getInstance().player == null) return;
        boolean on = module.isEnabled();
        push(module.getName(), on ? "Aktiviert" : "Deaktiviert", on ? Theme.SUCCESS : Theme.DANGER);
    }

    public static synchronized void push(String title, String message, int color) {
        // Gleiche Meldung nicht stapeln, sondern neu starten.
        ENTRIES.removeIf(entry -> entry.title.equals(title));
        ENTRIES.add(new Entry(title, message, color, System.currentTimeMillis()));
        while (ENTRIES.size() > MAX) ENTRIES.remove(0);
    }

    public static synchronized void render(GuiGraphics g) {
        if (ENTRIES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        long now = System.currentTimeMillis();
        int screenW = g.guiWidth();
        int y = g.guiHeight() - 8;

        Iterator<Entry> iterator = ENTRIES.iterator();
        while (iterator.hasNext()) {
            if (now - iterator.next().start > DURATION) iterator.remove();
        }

        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            Entry entry = ENTRIES.get(i);
            long age = now - entry.start;
            float in = UiRender.ease(age / (float) FADE);
            float out = UiRender.ease((DURATION - age) / (float) FADE);
            float visibility = Math.min(in, out);
            int width = Math.max(WIDTH, Math.max(font.width(entry.title), font.width(entry.message)) + 24);
            int x = screenW - 8 - Math.round(width * visibility);
            y -= Math.round((HEIGHT + 4) * visibility);

            UiRender.roundRect(g, x, y, width, HEIGHT, 4, Theme.alpha(Theme.TOAST_BG, visibility));
            UiRender.roundRect(g, x, y, 3, HEIGHT, 1, Theme.alpha(entry.color, visibility));
            g.drawString(font, entry.title, x + 10, y + 6, Theme.alpha(Theme.TEXT, Math.max(0.05F, visibility)), false);
            g.drawString(font, entry.message, x + 10, y + 17, Theme.alpha(entry.color, Math.max(0.05F, visibility)), false);
            float remaining = 1 - Math.min(1, age / (float) DURATION);
            g.fill(x + 3, y + HEIGHT - 1, x + 3 + Math.round((width - 5) * remaining), y + HEIGHT,
                    Theme.alpha(entry.color, 0.6F * visibility));
        }
    }
}
