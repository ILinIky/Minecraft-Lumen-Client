package com.example.examplemod.gui.hud;

import com.example.examplemod.feature.PlayerRadarModel;
import com.example.examplemod.gui.Theme;
import com.example.examplemod.gui.UiRender;
import com.example.examplemod.module.ModuleManager;
import com.example.examplemod.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import java.util.List;
import java.util.Locale;

public final class PlayerRadarElement extends HudElement {
    private List<PlayerRadarModel.Hit> hits = List.of();
    private int shown;
    public PlayerRadarElement(Setting.Bool visible) { super("player_radar", "Spieler-Radar", visible, 1, .5F, 4, 0); }
    @Override public void measure(Minecraft mc, boolean editing) {
        hits = ModuleManager.PLAYER_RADAR.nearby();
        shown = Math.min(hits.size(), Math.min(ModuleManager.PLAYER_RADAR.maxRows.getInt(), Math.max(1, (mc.getWindow().getGuiScaledHeight() - 45) / 14)));
        w = Math.min(205, mc.getWindow().getGuiScaledWidth() - 8);
        h = hits.isEmpty() ? editing ? 38 : 0 : 24 + shown * 14 + (hits.size() > shown ? 12 : 0);
    }
    @Override public void render(GuiGraphics g, Minecraft mc, boolean editing, float dt) {
        UiRender.roundRect(g, x, y, w, h, 4, Theme.HUD_BG);
        g.drawString(mc.font, "Spieler im Radius · " + hits.size(), x + 6, y + 6, Theme.ACCENT_2, false);
        if (hits.isEmpty()) g.drawString(mc.font, "Spielername                 24,0 Bl.", x + 6, y + 23, Theme.TEXT_DIM, false);
        for (int i = 0; i < shown; i++) {
            var hit = hits.get(i); String distance = String.format(Locale.GERMANY, "%.1f Bl.", hit.distance());
            int dw = mc.font.width(distance), ry = y + 23 + i * 14;
            g.drawString(mc.font, UiRender.ellipsize(mc.font, hit.name(), w - dw - 20), x + 6, ry, Theme.TEXT, false);
            g.drawString(mc.font, distance, x + w - dw - 6, ry, Theme.TEXT_DIM, false);
        }
        if (hits.size() > shown) g.drawString(mc.font, "+ " + (hits.size() - shown) + " weitere", x + 6, y + h - 11, Theme.TEXT_MUTED, false);
    }
}
