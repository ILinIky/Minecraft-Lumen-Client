package com.example.examplemod.gui.hud;

import com.example.examplemod.gui.Theme;
import com.example.examplemod.gui.UiRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** Kleines Rechtsklick-Menü für HUD-Elemente, solange der Chat offen ist (Maus sichtbar). */
public final class HudMenu {
    public record Entry(String label, Runnable action) {}

    private static final int ROW = 12;
    private static List<Entry> entries = List.of();
    private static int x, y, w;

    private HudMenu() {}

    public static boolean isOpen() { return !entries.isEmpty(); }
    public static void close() { entries = List.of(); }

    public static void open(List<Entry> list, double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        entries = List.copyOf(list);
        w = 0;
        for (Entry entry : entries) w = Math.max(w, font.width(entry.label()));
        w += 12;
        int screenW = mc.getWindow().getGuiScaledWidth(), screenH = mc.getWindow().getGuiScaledHeight();
        x = Math.max(2, Math.min((int) mouseX + 2, screenW - w - 2));
        y = Math.max(2, Math.min((int) mouseY, screenH - height() - 2));
    }

    private static int height() { return entries.size() * ROW + 4; }

    public static void render(GuiGraphics g, int mouseX, int mouseY) {
        if (entries.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        g.nextStratum();
        UiRender.shadow(g, x, y, w, height(), 4);
        UiRender.panel(g, x, y, w, height(), 4, Theme.WINDOW, Theme.WINDOW_BORDER);
        int ry = y + 2;
        for (Entry entry : entries) {
            boolean hover = UiRender.inside(mouseX, mouseY, x + 2, ry, w - 4, ROW);
            if (hover) UiRender.roundRect(g, x + 2, ry, w - 4, ROW, 3, Theme.CARD_HOVER);
            g.drawString(font, entry.label(), x + 6, ry + 2, hover ? Theme.TEXT : Theme.TEXT_DIM, false);
            ry += ROW;
        }
    }

    /** {@code true}, wenn das Menü offen war: Klick auf einen Eintrag führt ihn aus, daneben schließt es nur. */
    public static boolean click(double mouseX, double mouseY) {
        if (entries.isEmpty()) return false;
        List<Entry> current = entries;
        close();
        if (UiRender.inside(mouseX, mouseY, x + 2, y + 2, w - 4, current.size() * ROW)) {
            int index = (int) ((mouseY - y - 2) / ROW);
            if (index >= 0 && index < current.size()) current.get(index).action().run();
        }
        return true;
    }
}
