package com.example.examplemod.gui;

import com.example.examplemod.blueprint.BlueprintPlan;
import com.example.examplemod.blueprint.BlueprintReader;
import com.example.examplemod.feature.Blueprints;
import com.example.examplemod.module.ModuleManager;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Three focused tabs reuse Lumen's cards, theme, clipping and scrolling at every GUI scale. */
public final class BlueprintScreen extends ClientScreen {
    private enum Tab { FILES("Dateien"), PLACEMENT("Platzierung"), MATERIALS("Material");
        final String label;
        Tab(String label) { this.label = label; }
    }
    private final Blueprints feature = ModuleManager.BLUEPRINTS;
    private final Scroll scroll = new Scroll();
    private List<Path> files = List.of();
    private Tab tab;
    private BlueprintPlan seenPlan;
    private EditBox search;
    private String query = "", fileError;

    public BlueprintScreen(Screen parent) {
        super(Component.literal("Baupläne"), parent);
        seenPlan = feature.plan();
        tab = seenPlan == null ? Tab.FILES : Tab.PLACEMENT;
        refresh();
    }
    @Override protected int preferredWidth() { return 520; }
    @Override protected int preferredHeight() { return 330; }

    @Override protected void init() {
        super.init();
        int fieldW = Math.min(160, winW / 2 - 20);
        search = borderlessField(winX + winW - fieldW - 18, winY + 11, fieldW, "Suchen …", 80);
        search.setValue(query);
        search.setResponder(value -> { query = value; scroll.reset(); });
        search.setVisible(tab != Tab.PLACEMENT);
        addRenderableWidget(search);
    }

    private void select(Tab value) {
        tab = value;
        scroll.reset();
        query = "";
        if (search != null) { search.setValue(""); search.setVisible(tab != Tab.PLACEMENT); setFocused(null); }
    }

    @Override protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        BlueprintPlan plan = feature.plan();
        if (plan != seenPlan) { seenPlan = plan; select(plan == null ? Tab.FILES : Tab.PLACEMENT); }
        drawTitle(g, "Baupläne", null);
        if (tab != Tab.PLACEMENT) fieldBackground(g, search, 6, 4);
        else {
            String label = plan == null ? "Kein Plan" : plan.blueprint.name();
            int left = winX + 24 + font.width("Baupläne");
            g.drawString(font, UiRender.ellipsize(font, label, winX + winW - 14 - left), left, winY + 11, Theme.TEXT_MUTED, false);
        }
        int tabW = (winW - 32) / 3;
        for (Tab value : Tab.values()) button(g, mx, my, winX + 12 + value.ordinal() * (tabW + 4), winY + 38,
                tabW, 20, value.label, tab == value ? Style.PRIMARY : Style.SECONDARY, () -> select(value));

        int x = winX + 12, top = winY + 66, w = winW - 32, h = Math.max(0, winH - 102);
        int y = top - scroll.update(frameDelta), start = y;
        pushClip(g, x, top, w, h);
        if (tab == Tab.FILES) y = renderFiles(g, mx, my, x, y, w);
        else if (plan == null) y += paragraph(g, "Zuerst unter Dateien einen Bauplan laden.", x, y, w, Theme.TEXT_DIM);
        else if (tab == Tab.PLACEMENT) y = renderPlacement(g, mx, my, x, y, w, plan);
        else y = renderMaterials(g, mx, my, x, y, w, plan);
        popClip(g);
        scroll.setMax(y - start - h);
        scrollbar(g, scroll, winX + winW - 14, top, h, y - start);

        int footer = winY + winH - 27;
        g.fill(winX + 1, footer - 5, winX + winW - 1, footer - 4, Theme.DIVIDER);
        button(g, mx, my, winX + 12, footer, 62, 18, "Zurück", Style.SECONDARY, this::onClose);
        String status = feature.loading() ? feature.message() : plan != null ? summary(plan) : feature.message();
        g.drawString(font, UiRender.ellipsize(font, status, winW - 96), winX + 84, footer + 5, Theme.TEXT_MUTED, false);
        tooltip(g, mx, my, winX + 84, footer, winW - 96, 18, status);
    }

    private int renderFiles(GuiGraphics g, int mx, int my, int x, int y, int w) {
        int half = (w - 4) / 2;
        button(g, mx, my, x, y, half, 20, "Ordner öffnen", Style.SECONDARY, this::openFolder);
        button(g, mx, my, x + half + 4, y, w - half - 4, 20, "Neu einlesen", Style.SECONDARY, this::refresh);
        y += 28;
        y += paragraph(g, ".litematic, .schem (WorldEdit) oder Struktur-.nbt in den Ordner legen – oder die Datei auf dieses Fenster ziehen, dann wird sie dorthin kopiert.", x, y, w, Theme.TEXT_DIM) + 8;
        if (fileError != null) y += paragraph(g, fileError, x, y, w, Theme.DANGER) + 8;
        if (feature.loading()) y += paragraph(g, feature.message(), x, y, w, Theme.ACCENT) + 8;
        String needle = query.toLowerCase(Locale.ROOT);
        int shown = 0;
        for (Path file : files) {
            String name = file.getFileName().toString();
            if (!name.toLowerCase(Locale.ROOT).contains(needle)) continue;
            shown++;
            button(g, mx, my, x, y, w, 26, name, Style.SECONDARY, () -> feature.load(file));
            tooltip(g, mx, my, x, y, w, 26, name + " · Klicken zum Laden");
            y += 30;
        }
        if (shown == 0) y += paragraph(g, files.isEmpty() ? "Noch keine Baupläne im schematics-Ordner." : "Keine Treffer.", x, y, w, Theme.TEXT_MUTED) + 8;
        if (!feature.loading()) y += paragraph(g, feature.message(), x, y, w, Theme.TEXT_MUTED) + 8;
        return y;
    }

    private int renderPlacement(GuiGraphics g, int mx, int my, int x, int y, int w, BlueprintPlan plan) {
        var data = plan.blueprint;
        y += paragraph(g, data.name(), x, y, w, Theme.TEXT) + 3;
        var transform = plan.transform();
        y += paragraph(g, transform.width() + " × " + data.sizeY() + " × " + transform.depth() + " Blöcke · "
                + data.cells().size() + " Positionen", x, y, w, Theme.TEXT_MUTED) + 7;
        int half = (w - 4) / 2;
        button(g, mx, my, x, y, half, 20, feature.isEnabled() ? "Vorschau: An" : "Vorschau: Aus",
                feature.isEnabled() ? Style.PRIMARY : Style.SECONDARY, feature::toggle);
        button(g, mx, my, x + half + 4, y, w - half - 4, 20, "Im Spiel ansehen", Style.SECONDARY, this::closeAll);
        y += 28;
        BlockPos origin = plan.origin();
        y += paragraph(g, "Ursprung: X " + origin.getX() + " · Y " + origin.getY() + " · Z " + origin.getZ(), x, y, w, Theme.TEXT_DIM) + 4;
        if (!plan.inDimension(minecraft.level)) y += paragraph(g, "Vorschau gehört zu " + plan.dimension + ". In dieser Dimension ausgeblendet.", x, y, w, Theme.WARNING) + 6;
        int third = (w - 8) / 3;
        button(g, mx, my, x, y, third, 20, "Vor mir", Style.SECONDARY, () -> {
            if (minecraft.player != null) move(plan, minecraft.player.blockPosition().relative(minecraft.player.getDirection(), 3));
        });
        button(g, mx, my, x + third + 4, y, third, 20, "Anvisiert", Style.SECONDARY, () -> {
            if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK)
                move(plan, hit.getBlockPos().relative(hit.getDirection()));
            else Notifications.info("Bauplan", "Zuerst im Spiel eine Blockfläche anvisieren.");
        });
        button(g, mx, my, x + 2 * (third + 4), y, w - 2 * (third + 4), 20, "Koordinaten", Style.SECONDARY,
                () -> minecraft.setScreen(new BlueprintPositionScreen(this, plan, pos -> move(plan, pos))));
        y += 24;
        String[] labels = {"X −", "X +", "Y −", "Y +", "Z −", "Z +"};
        int stepW = (w - 20) / 6;
        for (int i = 0; i < 6; i++) {
            int axis = i / 2, sign = i % 2 == 0 ? -1 : 1;
            button(g, mx, my, x + i * (stepW + 4), y, stepW, 18, labels[i], Style.SECONDARY, () -> {
                int amount = sign * (hasShiftDown() ? 10 : 1);
                move(plan, plan.origin().offset(axis == 0 ? amount : 0, axis == 1 ? amount : 0, axis == 2 ? amount : 0));
            });
        }
        y += 23;
        y += paragraph(g, "Verschieben: 1 Block · mit Shift: 10 Blöcke", x, y, w, Theme.TEXT_MUTED) + 6;
        button(g, mx, my, x, y, half, 20, "Drehung: " + plan.turns() * 90 + "° ↻", Style.SECONDARY, plan::rotate);
        button(g, mx, my, x + half + 4, y, w - half - 4, 20, "Spiegeln X: " + (plan.mirrored() ? "An" : "Aus"), Style.SECONDARY, plan::mirror);
        y += 28;
        String[] layers = {"Alle Ebenen", "Nur Ebene", "Bis Ebene"};
        for (int i = 0; i < 3; i++) {
            BlueprintPlan.Layers value = BlueprintPlan.Layers.values()[i];
            button(g, mx, my, x + i * (third + 4), y, third, 20, layers[i], plan.layers == value ? Style.PRIMARY : Style.SECONDARY,
                    () -> plan.layers = value);
        }
        y += 24;
        if (plan.layers != BlueprintPlan.Layers.ALL) {
            button(g, mx, my, x, y, 26, 18, "−", Style.SECONDARY, () -> plan.layer = Math.max(0, plan.layer - (hasShiftDown() ? 10 : 1)));
            button(g, mx, my, x + w - 26, y, 26, 18, "+", Style.SECONDARY,
                    () -> plan.layer = Math.min(data.sizeY() - 1, plan.layer + (hasShiftDown() ? 10 : 1)));
            String layer = "Ebene " + (plan.layer + 1) + " / " + data.sizeY() + " · Y " + (origin.getY() + plan.layer);
            g.drawString(font, UiRender.ellipsize(font, layer, w - 68), x + 34, y + 5, Theme.TEXT_DIM, false);
            y += 24;
        }
        y += paragraph(g, "Im Spiel: Bild auf / Bild ab wechselt die Ebene (startet auf deiner Höhe).", x, y, w, Theme.TEXT_MUTED) + 6;
        button(g, mx, my, x, y, half, 20, plan.onlyMissing ? "Nur offene Blöcke" : "Gesamter Bauplan", Style.SECONDARY,
                () -> plan.onlyMissing = !plan.onlyMissing);
        button(g, mx, my, x + half + 4, y, w - half - 4, 20, "Deckkraft: " + feature.opacity.getInt() + "%", Style.SECONDARY,
                () -> feature.opacity.setProgress(feature.opacity.getInt() >= 85 ? 0 : feature.opacity.getProgress() + 1.0 / 7));
        y += 24;
        button(g, mx, my, x, y, half, 20, "Block-Info: " + (feature.blockInfo.get() ? "An" : "Aus"),
                feature.blockInfo.get() ? Style.PRIMARY : Style.SECONDARY, feature.blockInfo::toggle);
        tooltip(g, mx, my, x, y, half, 20, feature.blockInfo.getDescription());
        button(g, mx, my, x + half + 4, y, w - half - 4, 20, "Fehler markieren: " + (feature.outlines.get() ? "An" : "Aus"),
                feature.outlines.get() ? Style.PRIMARY : Style.SECONDARY, feature.outlines::toggle);
        y += 28;
        y += paragraph(g, "Blau: fehlt · Rot: falscher Block · Gelb: Zustand/Ausrichtung · Violett: unbekannt", x, y, w, Theme.TEXT_DIM) + 6;
        y += paragraph(g, "Blockmodelle mit echten Texturen. Truhen, Betten, Schilder, Köpfe und Flüssigkeiten erscheinen als farbiger Körper. Luftbereiche werden nicht geprüft.", x, y, w, Theme.TEXT_MUTED) + 6;
        if (feature.renderer().limited()) y += paragraph(g, "Vorschau auf die nächsten 4096 Blöcke begrenzt. Für Details eine einzelne Ebene wählen.", x, y, w, Theme.WARNING) + 6;
        for (String warning : plan.warnings) y += paragraph(g, warning, x, y, w, Theme.WARNING) + 4;
        button(g, mx, my, x, y + 4, w, 20, "Bauplan entladen", Style.SECONDARY, feature::clear);
        return y + 30;
    }

    private void move(BlueprintPlan plan, BlockPos pos) {
        if (!plan.inDimension(minecraft.level)) { Notifications.warning("Bauplan", "Bitte zur ursprünglichen Dimension zurückkehren."); return; }
        var size = plan.transform();
        if (Math.abs((long) pos.getX()) + size.width() > 29_999_984 || Math.abs((long) pos.getZ()) + size.depth() > 29_999_984
                || pos.getY() < minecraft.level.getMinY() || (long) pos.getY() + plan.blueprint.sizeY() - 1 > minecraft.level.getMaxY()) {
            Notifications.error("Bauplan", "Die Platzierung liegt außerhalb der Baugrenzen.");
            return;
        }
        plan.move(pos);
    }

    private int renderMaterials(GuiGraphics g, int mx, int my, int x, int y, int w, BlueprintPlan plan) {
        y += paragraph(g, summary(plan), x, y, w, Theme.TEXT) + 6;
        y += paragraph(g, "Gesamter Plan, unabhängig vom Ebenenfilter. Offen enthält auch ungeprüfte Positionen. Inventar zählt lose Items, keine Kisteninhalte.", x, y, w, Theme.TEXT_MUTED) + 8;
        if (!feature.isEnabled()) y += paragraph(g, "Abgleich pausiert: Vorschau unter Platzierung einschalten.", x, y, w, Theme.WARNING) + 8;
        if (!plan.inDimension(minecraft.level)) y += paragraph(g, "Andere Dimension: Die Zahlen zeigen den letzten Abgleich.", x, y, w, Theme.WARNING) + 8;
        String needle = query.toLowerCase(Locale.ROOT);
        for (BlueprintPlan.Material material : plan.materials) {
            if (!material.name.toLowerCase(Locale.ROOT).contains(needle)) continue;
            int inventory = 0;
            if (minecraft.player != null && material.item != Items.AIR) {
                var slots = minecraft.player.getInventory();
                for (int i = 0; i < slots.getContainerSize(); i++) if (slots.getItem(i).is(material.item)) inventory += slots.getItem(i).getCount();
            }
            UiRender.roundRect(g, x, y, w, 43, 4, Theme.CARD);
            var icon = material.icon();
            if (!icon.isEmpty()) g.renderItem(icon, x + 5, y + 5);
            else g.drawString(font, "?", x + 9, y + 9, Theme.WARNING, false);
            g.drawString(font, UiRender.ellipsize(font, material.name, w - 34), x + 28, y + 6, Theme.TEXT, false);
            String totals = "Gesamt " + material.total + " · Offen " + material.remaining() + " · Inventar " + inventory;
            UiRender.scaledText(g, font, UiRender.ellipsize(font, totals, (int) ((w - 12) / 0.85F)), x + 6, y + 21, Theme.TEXT_DIM, 0.85F);
            String missing = material.item == Items.AIR ? "Sonderblock / Flüssigkeit: manuell planen"
                    : "Noch besorgen: " + Math.max(0, material.remaining() - inventory);
            UiRender.scaledText(g, font, UiRender.ellipsize(font, missing, (int) ((w - 12) / 0.8F)), x + 6, y + 32, Theme.TEXT_MUTED, 0.8F);
            tooltip(g, mx, my, x, y, w, 43, material.name + " · " + totals + " · " + missing);
            y += 47;
        }
        y += paragraph(g, "Orientierung und Blockzustände zählen mit. Bedarf ist eine Bauhilfe: Wasser, Redstone-Zustände und Spezialmechaniken können Nacharbeit benötigen.", x, y, w, Theme.TEXT_MUTED) + 6;
        return y;
    }

    private String summary(BlueprintPlan plan) {
        return "Richtig " + plan.count(BlueprintPlan.Status.CORRECT) + "/" + plan.blueprint.cells().size()
                + " · Fehlt " + plan.count(BlueprintPlan.Status.MISSING)
                + " · Abweichend " + (plan.count(BlueprintPlan.Status.WRONG_BLOCK) + plan.count(BlueprintPlan.Status.WRONG_STATE))
                + " · Ungeprüft " + plan.count(BlueprintPlan.Status.UNCHECKED)
                + " · Unbekannt " + plan.count(BlueprintPlan.Status.UNSUPPORTED);
    }

    private int paragraph(GuiGraphics g, String text, int x, int y, int width, int color) {
        var lines = font.split(Component.literal(text), width);
        for (var line : lines) { g.drawString(font, line, x, y, color, false); y += 11; }
        return lines.size() * 11;
    }
    private void tooltip(GuiGraphics g, int mx, int my, int x, int y, int w, int h, String text) {
        if (hovered(mx, my, x, y, w, h)) g.setTooltipForNextFrame(font, Component.literal(text), mx, my);
    }
    private void refresh() {
        try {
            Files.createDirectories(feature.directory());
            try (var stream = Files.list(feature.directory())) {
                files = stream.filter(Files::isRegularFile).filter(BlueprintReader::supported)
                        .sorted(java.util.Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT))).limit(2000).toList();
            }
            fileError = null;
        } catch (IOException | RuntimeException failure) { fileError = "Bauplan-Ordner konnte nicht gelesen werden."; }
    }
    private void openFolder() {
        try { Files.createDirectories(feature.directory()); Util.getPlatform().openPath(feature.directory().toAbsolutePath()); }
        catch (IOException | RuntimeException failure) { fileError = "Bauplan-Ordner konnte nicht geöffnet werden."; }
    }
    @Override public void onFilesDrop(List<Path> paths) {
        for (Path path : paths) {
            if (!BlueprintReader.supported(path)) continue;
            Path copy = feature.importFile(path);
            refresh();
            feature.load(copy);
            return;
        }
        Notifications.warning("Bauplan", "Bitte eine .litematic-, .schem- oder Struktur-.nbt-Datei hineinziehen.");
    }
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) { scroll.scroll(sy); return true; }
    @Override public void onClose() { feature.saveState(); ModuleManager.saveNow(); super.onClose(); }
}
