package com.example.examplemod.gui;

import com.example.examplemod.farm.FarmRecord;
import com.example.examplemod.farm.FarmStore;
import com.example.examplemod.feature.FarmTracker;
import com.example.examplemod.module.ModuleManager;
import com.example.examplemod.waypoint.Waypoint;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Predicate;

/** Farm cards and confirmation flow using the shared Lumen window/widgets. */
public final class FarmScreen extends ClientScreen {
    private final FarmTracker tracker = ModuleManager.FARM_TRACKER;
    private final Scroll scroll = new Scroll();
    private boolean proposals;
    private String deleteId;
    private long deleteUntil;
    public FarmScreen(Screen parent) { super(Component.literal("Farm Tracker"), parent); }
    @Override protected int preferredWidth() { return 540; }
    @Override protected int preferredHeight() { return 370; }

    @Override protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        drawTitle(g, "Farm Tracker", null);
        var farms = FarmStore.all();
        var suggestions = tracker.suggestions();
        int x = winX + 14, w = winW - 34;
        int ready = (int) farms.stream().filter(f -> tracker.current(f) && f.percent() == 100).count();
        String summary = farms.size() + " Farmen · " + ready + " bereit zur Ernte";
        g.drawString(font, UiRender.ellipsize(font, summary, w), x, winY + 34, Theme.ACCENT_2, false);
        int tabW = (w - 6) / 2;
        button(g, mx, my, x, winY + 50, tabW, 21, "Meine Farmen", !proposals ? Style.PRIMARY : Style.SECONDARY,
                () -> { proposals = false; scroll.reset(); });
        button(g, mx, my, x + tabW + 6, winY + 50, tabW, 21, "Vorschläge · " + suggestions.size(), proposals ? Style.PRIMARY : Style.SECONDARY,
                () -> { proposals = true; scroll.reset(); });
        int top = winY + 80, h = Math.max(0, winH - 125);
        int columns = w >= 470 ? 2 : 1, gap = 8, cardW = (w - gap * (columns - 1)) / columns;
        int count = proposals ? suggestions.size() : farms.size();
        int cardH = proposals ? 124 : 196, content = ((count + columns - 1) / columns) * (cardH + gap);
        scroll.setMax(content - h);
        int offset = scroll.update(frameDelta);
        pushClip(g, x, top, w, h);
        if (count == 0) {
            String empty = !FarmStore.ready() ? "Farmdaten werden geladen oder sind nicht verfügbar."
                    : !tracker.isEnabled() ? "Aktiviere den Tracker, um Farmen in deiner Nähe zu entdecken."
                    : proposals ? "Noch keine neue Farm gefunden. Geh in die Nähe bepflanzter Felder."
                    : "Deine Farmen erscheinen hier, sobald du einen Vorschlag bestätigst und benennst.";
            int textY = top + 12;
            for (var line : font.split(Component.literal(empty), Math.max(40, w - 24))) {
                g.drawString(font, line, x + 12, textY, Theme.TEXT_DIM, false); textY += 12;
            }
        }
        for (int i = 0; i < count; i++) {
            int cx = x + (i % columns) * (cardW + gap), cy = top + (i / columns) * (cardH + gap) - offset;
            if (cy + cardH < top || cy > top + h) continue;
            if (proposals) suggestion(g, mx, my, cx, cy, cardW, suggestions.get(i));
            else card(g, mx, my, cx, cy, cardW, farms.get(i));
        }
        popClip(g);
        scrollbar(g, scroll, winX + winW - 12, top, h, content);
        int footer = winY + winH - 26;
        g.drawString(font, UiRender.ellipsize(font, tracker.isEnabled() ? tracker.scanMessage() : "Tracker pausiert", w), x, footer - 13, Theme.TEXT_MUTED, false);
        button(g, mx, my, x, footer, 68, 18, "Zurück", Style.SECONDARY, this::onClose);
        button(g, mx, my, x + 74, footer, 96, 18, "Fläche wählen", Style.SECONDARY, () -> minecraft.setScreen(new AreaScreen(this)));
        button(g, mx, my, winX + winW - 140, footer, 124, 18, "Tracker: " + (tracker.isEnabled() ? "An" : "Aus"),
                tracker.isEnabled() ? Style.PRIMARY : Style.SECONDARY, tracker::toggle);
    }

    private void card(GuiGraphics g, int mx, int my, int x, int y, int w, FarmRecord farm) {
        boolean live = tracker.current(farm);
        int color = live ? Theme.ACCENT_2 : Theme.TEXT_MUTED;
        UiRender.panel(g, x, y, w, 196, 5, Theme.CARD, Theme.FIELD_BORDER);
        g.drawString(font, UiRender.ellipsize(font, farm.name(), w - 60), x + 9, y + 9, Theme.TEXT, false);
        String percent = farm.seen() == 0 ? "–" : farm.percent() + "%";
        g.drawString(font, percent, x + w - 9 - font.width(percent), y + 9, color, false);
        location(g, x + 9, y + 24, w - 18, farm.dimension(), farm.position());
        g.drawString(font, UiRender.ellipsize(font, tracker.status(farm), w - 18), x + 9, y + 39, color, false);
        UiRender.roundRect(g, x + 9, y + 54, w - 18, 6, 3, Theme.FIELD);
        int fill = (w - 18) * farm.percent() / 100;
        if (fill > 0) UiRender.gradientH(g, x + 9, y + 54, fill, 6, live ? Theme.ACCENT : Theme.TEXT_MUTED, color);
        String details = farm.ripe() + " / " + farm.plots().size() + " erntereif · " + farm.plots().size() + " m²";
        g.drawString(font, UiRender.ellipsize(font, details, w - 18), x + 9, y + 66, Theme.TEXT_DIM, false);
        g.drawString(font, UiRender.ellipsize(font, age(farm.seen()), w - 18), x + 9, y + 152, Theme.TEXT_MUTED, false);
        int row = 0;
        for (var bed : farm.beds()) {
            if (row >= 5) break;
            int by = y + 82 + row++ * 13;
            String text = cropName(bed.crop()) + " " + bed.percent() + " %";
            g.drawString(font, UiRender.ellipsize(font, text, w - 83), x + 9, by, color, false);
            UiRender.roundRect(g, x + w - 67, by + 2, 58, 4, 2, Theme.FIELD);
            int progress = 58 * bed.percent() / 100;
            if (progress > 0) UiRender.gradientH(g, x + w - 67, by + 2, progress, 4, color, color);
        }
        if (farm.beds().isEmpty()) g.drawString(font, "Pflanzenarten werden geprüft …", x + 9, y + 83, Theme.TEXT_MUTED, false);
        tooltip(g, mx, my, x + 8, y + 80, w - 16, 66, farm.beds().stream()
                .map(b -> cropName(b.crop()) + ": " + b.ripe() + "/" + b.total() + " (" + b.percent() + " %)")
                .collect(java.util.stream.Collectors.joining(" · ")));
        int bw = (w - 26) / 3;
        button(g, mx, my, x + 7, y + 171, bw, 18, "Ansteuern", Style.PRIMARY, () -> tracker.navigate(farm));
        button(g, mx, my, x + 10 + bw, y + 171, bw, 18, "Name", Style.SECONDARY,
                () -> minecraft.setScreen(new NameScreen(this, "Farm umbenennen", farm.name(), name -> { tracker.rename(farm, name); return true; })));
        boolean confirm = farm.id().equals(deleteId) && System.currentTimeMillis() < deleteUntil;
        button(g, mx, my, x + 13 + 2 * bw, y + 171, bw, 18, confirm ? "Bestätigen" : "Löschen", Style.DANGER, () -> {
            if (farm.id().equals(deleteId) && System.currentTimeMillis() < deleteUntil) { tracker.delete(farm); deleteId = null; }
            else { deleteId = farm.id(); deleteUntil = System.currentTimeMillis() + 4000; }
        });
    }
    private void suggestion(GuiGraphics g, int mx, int my, int x, int y, int w, FarmTracker.Suggestion s) {
        UiRender.panel(g, x, y, w, 124, 5, Theme.CARD, Theme.FIELD_BORDER);
        boolean extension = FarmStore.nearby(s.dimension(), s.bounds(), tracker.areaGap.getInt());
        g.drawString(font, extension ? "Farmbereich erweitern" : "Neuer Farmbereich", x + 9, y + 9, Theme.ACCENT_2, false);
        location(g, x + 9, y + 26, w - 18, s.dimension(), s.plots().get(0));
        g.drawString(font, s.plots().size() + " bepflanzte Blöcke · " + s.plots().size() + " m²", x + 9, y + 43, Theme.TEXT, false);
        g.drawString(font, extension ? "Vorhandener Name bleibt erhalten" : "Beete gemeinsam verfolgen", x + 9, y + 65, Theme.TEXT_DIM, false);
        int half = (w - 22) / 2;
        button(g, mx, my, x + 8, y + 99, half, 18, "Übernehmen", Style.PRIMARY,
                () -> {
                    if (extension) tracker.confirm(s, "Meine Farm");
                    else minecraft.setScreen(new NameScreen(this, "Farm speichern", "Meine Farm", name -> tracker.confirm(s, name)));
                });
        button(g, mx, my, x + 14 + half, y + 99, half, 18, "Verwerfen", Style.SECONDARY, () -> tracker.dismiss(s));
    }
    private static String cropName(String id) {
        return switch (id) {
            case "minecraft:wheat" -> "Weizen";
            case "minecraft:carrots" -> "Karotten";
            case "minecraft:potatoes" -> "Kartoffeln";
            case "minecraft:beetroots" -> "Rote Bete";
            case "minecraft:nether_wart" -> "Netherwarzen";
            default -> id;
        };
    }
    private void tooltip(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String text) {
        if (!text.isEmpty() && hovered(mx, my, x, y, w, h)) g.setTooltipForNextFrame(font, Component.literal(text), mx, my);
    }
    private static final class AreaScreen extends ClientScreen {
        private final FarmTracker tracker = ModuleManager.FARM_TRACKER;
        private EditBox name;
        private String value = "Meine Farm";
        AreaScreen(Screen parent) { super(Component.literal("Eigene Farmfläche"), parent); }
        @Override protected int preferredWidth() { return 400; }
        @Override protected int preferredHeight() { return 245; }
        @Override protected void init() {
            super.init(); name = borderlessField(winX + 22, winY + 172, winW - 44, "Farmname", 48);
            name.setValue(value); name.setResponder(v -> value = v); addRenderableWidget(name);
        }
        @Override protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
            drawTitle(g, "Eigene Farmfläche · optional", null);
            int y = winY + 37;
            String help = "An zwei gegenüberliegende Ecken gehen und jeweils /farm ecke1 bzw. /farm ecke2 eingeben. "
                    + "Alternativ hier die aktuelle Spielerposition übernehmen. Die Höhe zählt mit; alle Beete innerhalb werden gemeinsam verfolgt.";
            for (var line : font.split(Component.literal(help), winW - 32)) { g.drawString(font, line, winX + 16, y, Theme.TEXT_DIM, false); y += 11; }
            button(g, mx, my, winX + 16, winY + 112, (winW - 38) / 2, 20, "Hier: Ecke 1", Style.SECONDARY, () -> tracker.corner(1));
            button(g, mx, my, winX + winW / 2 + 3, winY + 112, (winW - 38) / 2, 20, "Hier: Ecke 2", Style.SECONDARY, () -> tracker.corner(2));
            g.drawString(font, UiRender.ellipsize(font, tracker.selectionText(), winW - 32), winX + 16, winY + 142, Theme.TEXT_MUTED, false);
            fieldBackground(g, name, 6, 5);
            button(g, mx, my, winX + 16, winY + winH - 30, 80, 20, "Zurück", Style.SECONDARY, this::onClose);
            button(g, mx, my, winX + 102, winY + winH - 30, 85, 20, "Auswahl aus", Style.SECONDARY, tracker::clearSelection);
            button(g, mx, my, winX + winW - 126, winY + winH - 30, 110, 20, "Fläche speichern", Style.PRIMARY,
                    () -> { if (tracker.saveSelection(value)) onClose(); });
        }
    }
    private void location(GuiGraphics g, int x, int y, int w, String dimension, FarmRecord.Plot p) {
        g.drawString(font, UiRender.ellipsize(font, Waypoint.dimensionName(dimension) + " · " + p.x() + " / " + p.y() + " / " + p.z(), w), x, y, Theme.TEXT_MUTED, false);
    }
    private static String age(long seen) {
        if (seen == 0) return "Noch keine Messung";
        long seconds = Math.max(0, (System.currentTimeMillis() - seen) / 1000);
        return seconds < 60 ? "Gemessen vor " + seconds + " Sek." : seconds < 3600 ? "Gemessen vor " + seconds / 60 + " Min."
                : "Gemessen vor " + seconds / 3600 + " Std.";
    }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) { scroll.scroll(sy); return true; }

    private static final class NameScreen extends ClientScreen {
        private final String heading;
        private final Predicate<String> save;
        private String value;
        private EditBox name;
        NameScreen(Screen parent, String title, String initial, Predicate<String> save) {
            super(Component.literal(title), parent); heading = title; value = initial; this.save = save;
        }
        @Override protected int preferredWidth() { return 300; }
        @Override protected int preferredHeight() { return 120; }
        @Override protected boolean isModal() { return true; }
        @Override protected void init() {
            super.init(); name = borderlessField(winX + 22, winY + 47, winW - 44, "Name der Farm", 48);
            name.setValue(value); name.setResponder(text -> value = text); addRenderableWidget(name); setInitialFocus(name);
        }
        private void submit() { if (save.test(value)) onClose(); }
        @Override protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
            drawTitle(g, heading, null); fieldBackground(g, name, 6, 5);
            button(g, mx, my, winX + 16, winY + winH - 30, 90, 20, "Abbrechen", Style.SECONDARY, this::onClose);
            button(g, mx, my, winX + winW - 106, winY + winH - 30, 90, 20, "Speichern", Style.PRIMARY, this::submit);
        }
        @Override public boolean keyPressed(int key, int scan, int mods) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) { submit(); return true; }
            return super.keyPressed(key, scan, mods);
        }
    }
}
