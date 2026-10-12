package com.example.examplemod.gui;

import com.example.examplemod.feature.PlayerRadar;
import com.example.examplemod.module.ModuleManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Online players plus previously selected offline players; selection by UUID survives name changes. */
public final class PlayerRadarScreen extends ClientScreen {
    private record Entry(UUID id, String name, PlayerInfo info) {}
    private final PlayerRadar feature = ModuleManager.PLAYER_RADAR;
    private final Scroll scroll = new Scroll();
    private List<Entry> entries = List.of();
    private EditBox search;
    private String query = "";
    private boolean onlySelected;
    private int ticks;
    public PlayerRadarScreen(Screen parent) { super(Component.literal("Spieler-Radar"), parent); }
    @Override protected int preferredWidth() { return 460; }
    @Override protected int preferredHeight() { return 310; }
    @Override protected void init() {
        super.init();
        search = borderlessField(winX + 18, winY + 44, winW - 36, "Spieler suchen …", 64);
        search.setValue(query); search.setResponder(value -> { query = value; refresh(); scroll.reset(); });
        addRenderableWidget(search); refresh();
    }
    @Override public void tick() { if (++ticks % 20 == 0) refresh(); }
    private void refresh() {
        Map<UUID, Entry> all = new LinkedHashMap<>();
        feature.selected().forEach((id, name) -> all.put(id, new Entry(id, name, null)));
        if (minecraft != null && minecraft.getConnection() != null) for (var info : minecraft.getConnection().getOnlinePlayers()) {
            var profile = info.getProfile();
            if (minecraft.player != null && profile.getId().equals(minecraft.player.getUUID())) continue;
            all.put(profile.getId(), new Entry(profile.getId(), profile.getName(), info));
        }
        String needle = query.toLowerCase(Locale.ROOT).trim();
        entries = all.values().stream().filter(e -> (!onlySelected || feature.selected().containsKey(e.id))
                && e.name.toLowerCase(Locale.ROOT).contains(needle)).sorted(Comparator.comparing(e -> e.name.toLowerCase(Locale.ROOT))).toList();
    }
    @Override protected void renderContent(GuiGraphics g, int mx, int my, float dt) {
        drawTitle(g, "Spieler-Radar", feature.selected().size() + " ausgewählt");
        fieldBackground(g, search, 6, 5);
        int x = winX + 12, w = winW - 28, third = (w - 8) / 3;
        button(g, mx, my, x, winY + 65, third, 20, onlySelected ? "Nur Auswahl" : "Alle Spieler", Style.SECONDARY,
                () -> { onlySelected = !onlySelected; refresh(); scroll.reset(); });
        button(g, mx, my, x + third + 4, winY + 65, third, 20, "Radar: " + (feature.isEnabled() ? "An" : "Aus"), Style.SECONDARY, feature::toggle);
        button(g, mx, my, x + 2 * (third + 4), winY + 65, third, 20, "Auswahl leeren", Style.SECONDARY, () -> { feature.clearPlayers(); refresh(); });
        String hint = "Radius " + feature.radius.getInt() + " Bl. · Nur bekannte Positionen in deiner Dimension";
        g.drawString(font, UiRender.ellipsize(font, hint, w), x, winY + 92, Theme.TEXT_DIM, false);
        int top = winY + 108, h = Math.max(1, winH - 142);
        scroll.setMax(entries.size() * 26 - h); int offset = scroll.update(frameDelta);
        pushClip(g, x, top, w, h);
        for (int i = Math.max(0, offset / 26); i < Math.min(entries.size(), (offset + h) / 26 + 1); i++) {
            var entry = entries.get(i); int y = top + i * 26 - offset;
            boolean checked = feature.selected().containsKey(entry.id);
            button(g, mx, my, x, y, w, 23, "", Style.SECONDARY, () -> { feature.togglePlayer(entry.id, entry.name); refresh(); });
            if (entry.info != null) PlayerFaceRenderer.draw(g, entry.info.getSkin(), x + 4, y + 4, 15);
            else UiRender.roundRect(g, x + 4, y + 4, 15, 15, 2, Theme.SWITCH_OFF);
            String label = entry.name + (entry.info == null ? " (offline)" : "");
            g.drawString(font, UiRender.ellipsize(font, label, w - 48), x + 25, y + 8, checked ? Theme.TEXT : Theme.TEXT_DIM, false);
            UiRender.checkbox(g, font, x + w - 17, y + 7, checked, hovered(mx, my, x, y, w, 23));
        }
        if (entries.isEmpty()) {
            String empty = onlySelected ? "Noch keine passenden Spieler ausgewählt." : "Keine passenden Mitspieler in der Online-Liste.";
            g.drawString(font, UiRender.ellipsize(font, empty, w - 12), x + 6, top + 12, Theme.TEXT_MUTED, false);
        }
        popClip(g); scrollbar(g, scroll, winX + winW - 12, top, h, entries.size() * 26);
        button(g, mx, my, winX + winW - 100, winY + winH - 26, 88, 18, "Fertig", Style.PRIMARY, this::onClose);
    }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) { scroll.scroll(sy); return true; }
    @Override public void onClose() { ModuleManager.saveNow(); super.onClose(); }
}
