package com.example.examplemod.gui.hud;

import com.example.examplemod.feature.MaterialList;
import com.example.examplemod.gui.Theme;
import com.example.examplemod.gui.UiRender;
import com.example.examplemod.module.ModuleManager;
import com.example.examplemod.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.List;

public final class MaterialListElement extends HudElement {
    private List<MaterialList.Row> rows = List.of();
    private int shown;
    private final ListViewport viewport = new ListViewport();
    public MaterialListElement(Setting.Bool visible) { super("material_list", "Sammelliste", visible, 0, .5F, 4, 0); }
    @Override public void measure(Minecraft mc, boolean editing) {
        var feature = ModuleManager.MATERIAL_LIST;
        rows = feature.isEnabled() ? feature.hudRows() : List.of();
        shown = Math.min(rows.size(), Math.min(feature.hudRows.getInt(), Math.max(1, (mc.getWindow().getGuiScaledHeight() - 52) / 25)));
        viewport.resize(rows.size(), shown);
        w = Math.min(240, mc.getWindow().getGuiScaledWidth() - 8);
        h = rows.isEmpty() ? (editing ? 38 : 0) : 22 + shown * 25 + (rows.size() > shown ? 14 : 0);
    }
    public boolean scroll(double delta) { return viewport.scroll(delta); }

    /** Zeile unter der Maus (Lage vom letzten Bild), sonst {@code null}. */
    public MaterialList.Row rowAt(double mouseX, double mouseY) {
        if (!UiRender.inside(mouseX, mouseY, x, y + 22, w, shown * 25)) return null;
        int index = viewport.offset() + (int) ((mouseY - y - 22) / 25);
        return index >= 0 && index < rows.size() ? rows.get(index) : null;
    }
    @Override public void render(GuiGraphics g, Minecraft mc, boolean editing, float dt) {
        UiRender.roundRect(g, x, y, w, h, 4, Theme.HUD_BG);
        g.drawString(mc.font, "Sammelliste", x + 6, y + 6, Theme.ACCENT, false);
        if (ModuleManager.MATERIAL_LIST.solo() != null) {
            // Nur ein Eintrag gezeigt: kleiner Hinweis, Klick im Chat zeigt wieder alle.
            String all = "Alle (" + ModuleManager.MATERIAL_LIST.rows().size() + ") ✕";
            g.drawString(mc.font, all, x + w - mc.font.width(all) - 6, y + 6, Theme.TEXT_DIM, false);
        }
        if (rows.isEmpty()) g.drawString(mc.font, "Stein: 128/320", x + 6, y + 20, Theme.TEXT, false);
        for (int i = 0; i < shown; i++) {
            var row = rows.get(viewport.offset() + i); int ry = y + 22 + i * 25;
            g.renderItem(row.icon(), x + 5, ry + 1);
            String count = row.have() + "/" + row.target();
            int cw = mc.font.width(count);
            g.drawString(mc.font, UiRender.ellipsize(mc.font, row.name(), w - cw - 40), x + 26, ry + 2, Theme.TEXT, false);
            g.drawString(mc.font, count, x + w - cw - 10, ry + 2, row.missing() == 0 ? Theme.ACCENT_2 : Theme.TEXT_DIM, false);
            g.fill(x + 26, ry + 15, x + w - 10, ry + 17, Theme.CARD);
            int progress = Math.round((w - 36) * Math.min(1F, row.have() / (float)Math.max(1, row.target())));
            g.fill(x + 26, ry + 15, x + 26 + progress, ry + 17, Theme.ACCENT);
        }
        if (rows.size() > shown) {
            String footer = (viewport.offset() + 1) + "–" + (viewport.offset() + shown) + "/" + rows.size() + " · Shift + Mausrad";
            g.drawString(mc.font, UiRender.ellipsize(mc.font, footer, w - 12), x + 6, y + h - 11, Theme.TEXT_DIM, false);
            int track = shown * 25 - 4, thumb = Math.max(8, track * shown / rows.size());
            int ty = y + 22 + Math.round((track - thumb) * viewport.offset() / (float)viewport.maximum());
            g.fill(x + w - 4, y + 22, x + w - 2, y + 22 + track, Theme.SCROLL_TRACK);
            g.fill(x + w - 4, ty, x + w - 2, ty + thumb, Theme.ACCENT);
        }
    }
}
