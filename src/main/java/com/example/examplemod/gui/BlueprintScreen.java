package com.example.examplemod.gui;

import com.example.examplemod.ClientKeybinds;
import com.example.examplemod.blueprint.BlueprintPlan;
import com.example.examplemod.blueprint.BlueprintReader;
import com.example.examplemod.blueprint.BlueprintWriter;
import com.example.examplemod.compat.KeyBindings;
import com.example.examplemod.feature.Blueprints;
import com.example.examplemod.module.ModuleManager;
import net.minecraft.Util;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Three focused tabs reuse Lumen's cards, theme, clipping and scrolling at every GUI scale. */
public final class BlueprintScreen extends ClientScreen {
    private enum Tab { FILES("Dateien"), PLACEMENT("Platzierung"), MATERIALS("Material"), CAPTURE("Aufnehmen");
        final String label;
        Tab(String label) { this.label = label; }
    }
    private final Blueprints feature = ModuleManager.BLUEPRINTS;
    private final Scroll scroll = new Scroll();
    private static final int FILE_ROW = 36;
    private int clipTop, clipBottom;
    /** Vorschaubild unter der Maus, wird nach dem Inhalt groß eingeblendet. */
    private Path hoverPreview;
    /** Wartet auf die neue Taste für die Block-Info. */
    /** Taste, die gerade neu belegt wird, sonst {@code null}. */
    private KeyMapping binding;
    /** Rechtsklick-Menü der Materialliste: Material und Position, sonst {@code null}. */
    private BlueprintPlan.Material menuMaterial;
    private int menuX, menuY;
    private static final int MENU_W = 118, MENU_ROW = 13;
    private List<Path> files = List.of();
    private Tab tab;
    private BlueprintPlan seenPlan;
    private BlueprintPlan resetPlan;
    private long resetUntil;
    private EditBox search;
    private String query = "", fileError;
    /** Reiter "Aufnehmen": Name und Format des neuen Bauplans (bleiben beim Wechseln erhalten). */
    private EditBox nameField;
    private static String captureName = "";
    private static BlueprintWriter.Format captureFormat = BlueprintWriter.Format.LITEMATIC;

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
        search.setVisible(searchTab());
        addRenderableWidget(search);
        nameField = borderlessField(winX + winW - fieldW - 18, winY + 11, fieldW, "Name des Bauplans …", 64);
        nameField.setValue(captureName);
        nameField.setResponder(value -> captureName = value);
        nameField.setVisible(tab == Tab.CAPTURE);
        addRenderableWidget(nameField);
    }

    private boolean searchTab() { return tab == Tab.FILES || tab == Tab.MATERIALS; }

    private void select(Tab value) {
        tab = value;
        menuMaterial = null;
        scroll.reset();
        query = "";
        if (search != null) { search.setValue(""); search.setVisible(searchTab()); setFocused(null); }
        if (nameField != null) nameField.setVisible(tab == Tab.CAPTURE);
        if (value == Tab.FILES) refresh();
    }

    @Override protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        BlueprintPlan plan = feature.plan();
        if (plan != seenPlan) {
            boolean keepMaterials = tab == Tab.MATERIALS && seenPlan != null && plan != null
                    && seenPlan.blueprint.source().equals(plan.blueprint.source());
            seenPlan = plan;
            select(keepMaterials ? Tab.MATERIALS : plan == null ? Tab.FILES : Tab.PLACEMENT);
        }
        drawTitle(g, "Baupläne", null);
        if (searchTab()) fieldBackground(g, search, 6, 4);
        else if (tab == Tab.CAPTURE) fieldBackground(g, nameField, 6, 4);
        else {
            String label = plan == null ? "Kein Plan" : plan.blueprint.name();
            int left = winX + 24 + font.width("Baupläne");
            g.drawString(font, UiRender.ellipsize(font, label, winX + winW - 14 - left), left, winY + 11, Theme.TEXT_MUTED, false);
        }
        int tabW = (winW - 36) / 4;
        for (Tab value : Tab.values()) button(g, mx, my, winX + 12 + value.ordinal() * (tabW + 4), winY + 38,
                tabW, 20, value.label, tab == value ? Style.PRIMARY : Style.SECONDARY, () -> select(value));

        int x = winX + 12, top = winY + 66, w = winW - 32, h = Math.max(0, winH - 102);
        clipTop = top;
        clipBottom = top + h;
        int y = top - scroll.update(frameDelta), start = y;
        pushClip(g, x, top, w, h);
        if (tab == Tab.FILES) y = renderFiles(g, mx, my, x, y, w);
        else if (tab == Tab.CAPTURE) y = renderCapture(g, mx, my, x, y, w);
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
            // Nur sichtbare Zeilen zeichnen; so werden auch nur dafür Vorschaubilder berechnet.
            if (y + FILE_ROW >= clipTop && y <= clipBottom) fileRow(g, mx, my, x, y, w, file, name);
            y += FILE_ROW + 4;
        }
        if (shown == 0) y += paragraph(g, files.isEmpty() ? "Noch keine Baupläne im schematics-Ordner." : "Keine Treffer.", x, y, w, Theme.TEXT_MUTED) + 8;
        if (!feature.loading()) y += paragraph(g, feature.message(), x, y, w, Theme.TEXT_MUTED) + 8;
        return y;
    }

    /**
     * Taste direkt hier belegen: Knopf klicken, dann Taste (oder Maus-Seitentaste) drücken.
     * Esc bricht ab, Entf/Rücktaste stellt den Standard wieder her. Doppelte Belegungen werden angezeigt.
     */
    private int renderKey(GuiGraphics g, int mx, int my, int x, int y, int w, KeyMapping key, String title, String defaultName) {
        if (key == null) return y;
        boolean listening = binding == key;
        UiRender.roundRect(g, x, y, w, 24, 4, Theme.CARD);
        g.drawString(font, title, x + 8, y + 8, Theme.TEXT_DIM, false);
        String label = listening ? "Taste drücken …" : KeyBindings.keyName(key);
        int chipW = Math.max(90, font.width(label) + 16);
        int chipX = x + w - chipW - 30;
        boolean hover = hovered(mx, my, chipX, y + 3, chipW, 18);
        int border = listening ? Theme.accentWave(0) : hover ? Theme.SWITCH_OFF_HOVER : Theme.FIELD_BORDER;
        UiRender.panel(g, chipX, y + 3, chipW, 18, 4, listening ? Theme.ACCENT_SOFT : Theme.FIELD, border);
        g.drawString(font, label, chipX + (chipW - font.width(label)) / 2, y + 8, Theme.TEXT, false);
        addHit(chipX, y + 3, chipW, 18, (a, b, c) -> {
            binding = listening ? null : key;
            setFocused(null);
        });
        tooltip(g, mx, my, chipX, y + 3, chipW, 18, listening
                ? "Gewünschte Taste drücken · Esc: abbrechen · Entf: Standard (" + defaultName + ")"
                : "Klicken und neue Taste drücken (auch Maus-Seitentasten)");
        // Auf Standard zurücksetzen
        boolean resetHover = hovered(mx, my, x + w - 24, y + 3, 18, 18);
        g.drawString(font, "↺", x + w - 19, y + 8, KeyBindings.isDefault(key) ? Theme.TEXT_MUTED
                : resetHover ? Theme.ACCENT_2 : Theme.TEXT_DIM, false);
        addHit(x + w - 24, y + 3, 18, 18, (a, b, c) -> {
            KeyBindings.reset(key);
            binding = null;
            announceKey(key);
        });
        if (resetHover) g.setTooltipForNextFrame(font, Component.literal("Standard: " + defaultName), mx, my);
        y += 28;
        List<String> conflicts = KeyBindings.conflicts(key);
        if (!conflicts.isEmpty()) {
            y += paragraph(g, "⚠ Diese Taste ist auch belegt für: " + String.join(", ", conflicts)
                    + ". Beides passiert dann gleichzeitig – besser eine freie Taste wählen.", x, y, w, Theme.WARNING) + 2;
        }
        return y;
    }

    private void announceKey(KeyMapping key) {
        String title = Component.translatable(key.getName()).getString();
        List<String> conflicts = KeyBindings.conflicts(key);
        if (conflicts.isEmpty()) Notifications.success(title, KeyBindings.keyName(key));
        else Notifications.warning(title + ": " + KeyBindings.keyName(key), "Auch belegt für " + String.join(", ", conflicts));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (menuMaterial != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            menuMaterial = null;
            return true;
        }
        if (binding != null) {
            KeyMapping key = binding;
            binding = null;
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) return true;
            if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) KeyBindings.reset(key);
            else KeyBindings.bindKeyboard(key, keyCode, scanCode);
            announceKey(key);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Offenes Menü: Klick daneben schließt es nur.
        if (menuMaterial != null) {
            int h = menuEntries(menuMaterial).size() * MENU_ROW + 4;
            if (!UiRender.inside(mouseX, mouseY, menuX, menuY, MENU_W, h)) {
                menuMaterial = null;
                return true;
            }
        }
        // Maus-Seitentasten und Mausrad-Klick lassen sich belegen; Links-/Rechtsklick bricht ab.
        if (binding != null && button >= GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
            KeyMapping key = binding;
            binding = null;
            KeyBindings.bindMouse(key, button);
            announceKey(key);
            return true;
        }
        KeyMapping before = binding;
        boolean handled = super.mouseClicked(mouseX, mouseY, button);
        // Klick daneben beendet das Warten auf eine Taste.
        if (before != null && binding == before && !handled) binding = null;
        return handled;
    }

    /** Reiter "Aufnehmen": zwei Ecken setzen, Format wählen, als eigenen Bauplan speichern. */
    private int renderCapture(GuiGraphics g, int mx, int my, int x, int y, int w) {
        y += paragraph(g, "Einen Bereich der Welt als eigenen Bauplan speichern – z. B. in der Kreativwelt bauen "
                + "und später im Survival nachbauen. Den Namen oben rechts eingeben.", x, y, w, Theme.TEXT_DIM) + 6;
        for (int which = 1; which <= 2; which++) {
            final int corner = which;
            BlockPos pos = feature.corner(corner);
            UiRender.roundRect(g, x, y, w, 24, 4, Theme.CARD);
            g.drawString(font, "Ecke " + corner, x + 8, y + 8, corner == 1 ? Theme.ACCENT : Theme.ACCENT_2, false);
            String text = pos == null ? "nicht gesetzt" : pos.getX() + " / " + pos.getY() + " / " + pos.getZ();
            int bw = Math.min(92, (w - 130) / 2);
            g.drawString(font, UiRender.ellipsize(font, text, w - 2 * bw - 70), x + 52, y + 8, pos == null ? Theme.TEXT_MUTED : Theme.TEXT, false);
            button(g, mx, my, x + w - 2 * bw - 8, y + 3, bw, 18, "Anvisiert", Style.SECONDARY, () -> {
                BlockPos aimed = feature.aimedBlock();
                if (aimed == null) Notifications.info("Bauplan-Aufnahme", "Zuerst im Spiel einen Block anvisieren");
                else feature.setCorner(corner, aimed);
            });
            button(g, mx, my, x + w - bw - 4, y + 3, bw, 18, "Meine Position", Style.SECONDARY, () -> {
                if (minecraft.player != null) feature.setCorner(corner, minecraft.player.blockPosition());
            });
            y += 28;
        }
        if (feature.hasSelection()) {
            y += paragraph(g, "Größe: " + feature.selectionSize(), x, y, w, feature.selectionTooLarge() ? Theme.DANGER : Theme.TEXT) + 2;
            if (feature.selectionTooLarge()) y += paragraph(g, "Zu groß: höchstens 2 Millionen Positionen. Bitte in Teile aufteilen.", x, y, w, Theme.DANGER) + 2;
        }
        y += 4;
        int half = (w - 4) / 2;
        button(g, mx, my, x, y, half, 20, ".litematic (Litematica)",
                captureFormat == BlueprintWriter.Format.LITEMATIC ? Style.PRIMARY : Style.SECONDARY,
                () -> captureFormat = BlueprintWriter.Format.LITEMATIC);
        button(g, mx, my, x + half + 4, y, w - half - 4, 20, ".nbt (Strukturblock)",
                captureFormat == BlueprintWriter.Format.STRUCTURE ? Style.PRIMARY : Style.SECONDARY,
                () -> captureFormat = BlueprintWriter.Format.STRUCTURE);
        y += 26;
        boolean ready = feature.hasSelection() && !feature.selectionTooLarge() && !feature.saving();
        button(g, mx, my, x, y, w - 112, 22, feature.saving() ? "Speichere …" : "Als Bauplan speichern",
                ready ? Style.PRIMARY : Style.SECONDARY, () -> {
                    if (ready) feature.saveSelection(captureName, captureFormat);
                    else if (!feature.hasSelection()) Notifications.info("Bauplan-Aufnahme", "Zuerst beide Ecken setzen");
                });
        button(g, mx, my, x + w - 108, y, 108, 22, "Auswahl löschen", Style.SECONDARY, feature::clearSelection);
        y += 30;
        y += paragraph(g, "Im Spiel geht es auch per Chat: /bauplan ecke1, /bauplan ecke2, /bauplan speichern <Name>. "
                + "Ecken werden in der Welt farbig angezeigt.", x, y, w, Theme.TEXT_MUTED) + 4;
        y += paragraph(g, "Gespeichert wird in .minecraft/schematics. Kisteninhalte, Schildtexte und Tiere werden nicht "
                + "übernommen. Bereits vorhandene Dateien werden nie überschrieben.", x, y, w, Theme.TEXT_MUTED) + 4;
        return y;
    }

    private static int nextOpacity(int current, boolean backwards) {
        int[] steps = {30, 50, 70, 85, 100};
        if (backwards) {
            for (int i = steps.length - 1; i >= 0; i--) if (steps[i] < current) return steps[i];
            return steps[steps.length - 1];
        }
        for (int step : steps) if (step > current) return step;
        return steps[0];
    }

    /** Eine Datei mit Vorschaubild, Namen aus der Datei und Dateinamen darunter. */
    private void fileRow(GuiGraphics g, int mx, int my, int x, int y, int w, Path file, String fileName) {
        boolean hover = hovered(mx, my, x, y, w, FILE_ROW);
        boolean loaded = feature.file() != null
                && feature.file().toAbsolutePath().normalize().equals(file.toAbsolutePath().normalize());
        UiRender.panel(g, x, y, w, FILE_ROW, 4, hover ? Theme.CARD_HOVER : Theme.CARD,
                loaded ? Theme.ACCENT : hover ? Theme.SWITCH_OFF_HOVER : Theme.FIELD_BORDER);
        int thumb = FILE_ROW - 6;
        BlueprintPreviews.draw(g, file, x + 3, y + 3, thumb);
        if (hovered(mx, my, x + 3, y + 3, thumb, thumb)) hoverPreview = file;

        BlueprintPreviews.Entry preview = BlueprintPreviews.get(file);
        int tx = x + thumb + 10, tw = w - thumb - 16;
        String chip = loaded ? "Geladen" : null;
        int chipW = chip == null ? 0 : font.width(chip) + 10;
        String title = preview.done() && !preview.failed() && preview.name() != null ? preview.name() : withoutExtension(fileName);
        g.drawString(font, UiRender.ellipsize(font, title, tw - chipW - 4), tx, y + 8, Theme.TEXT, false);
        if (chip != null) {
            UiRender.roundRect(g, x + w - chipW - 6, y + 6, chipW, 11, 5, Theme.ACCENT);
            g.drawString(font, chip, x + w - chipW - 1, y + 8, Theme.ON_ACCENT, false);
        }
        String details = !preview.done() ? "Vorschau wird erstellt …" : preview.details();
        g.drawString(font, UiRender.ellipsize(font, fileName + "  ·  " + details, tw), tx, y + 21,
                preview.failed() ? Theme.DANGER : Theme.TEXT_MUTED, false);
        addHit(x, y, w, FILE_ROW, (a, b, c) -> feature.load(file));
        tooltip(g, mx, my, x + thumb + 6, y, w - thumb - 6, FILE_ROW, fileName + " · Klicken zum Laden");
    }

    private static String withoutExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    // ---- Rechtsklick-Menü der Materialliste ----

    private void openMenu(BlueprintPlan.Material material, int x, int y) {
        menuMaterial = material;
        menuX = Math.min(x, width - MENU_W - 4);
        menuY = Math.min(y, height - menuEntries(material).size() * MENU_ROW - 8);
    }

    private record MenuEntry(String label, Runnable action) {}

    private List<MenuEntry> menuEntries(BlueprintPlan.Material material) {
        List<MenuEntry> entries = new java.util.ArrayList<>();
        BlueprintPlan plan = feature.plan();
        boolean focused = plan != null && plan.focus == material;
        entries.add(new MenuEntry(focused ? "Alle anzeigen" : "Nur dieses anzeigen", () -> feature.focusMaterial(material)));
        if (material.item != Items.AIR) {
            entries.add(new MenuEntry("Ersetzen …", () -> feature.openReplace(this, material.item)));
            entries.add(new MenuEntry("In die Hand nehmen", () -> feature.takeIntoHand(material.item, material)));
            if (ModuleManager.CHEST_MEMORY != null && ModuleManager.CHEST_MEMORY.isEnabled()) {
                entries.add(new MenuEntry("In Kisten suchen", () -> minecraft.setScreen(new ChestSearchScreen(this, material.name))));
            }
        }
        return entries;
    }

    private void renderMenu(GuiGraphics g, int mx, int my) {
        if (menuMaterial == null) return;
        List<MenuEntry> entries = menuEntries(menuMaterial);
        int h = entries.size() * MENU_ROW + 4;
        g.nextStratum();
        UiRender.shadow(g, menuX, menuY, MENU_W, h, 4);
        UiRender.panel(g, menuX, menuY, MENU_W, h, 4, Theme.WINDOW, Theme.WINDOW_BORDER);
        int ry = menuY + 2;
        for (MenuEntry entry : entries) {
            boolean hover = UiRender.inside(mx, my, menuX + 2, ry, MENU_W - 4, MENU_ROW);
            if (hover) UiRender.roundRect(g, menuX + 2, ry, MENU_W - 4, MENU_ROW, 3, Theme.CARD_HOVER);
            g.drawString(font, UiRender.ellipsize(font, entry.label, MENU_W - 12), menuX + 6, ry + 3, hover ? Theme.TEXT : Theme.TEXT_DIM, false);
            addHit(menuX + 2, ry, MENU_W - 4, MENU_ROW, (a, b, c) -> {
                menuMaterial = null;
                entry.action.run();
            });
            ry += MENU_ROW;
        }
    }

    /** Große Vorschau, solange die Maus über einem Vorschaubild steht. */
    @Override protected void renderOverlay(GuiGraphics g, int mx, int my, float partialTick) {
        renderMenu(g, mx, my);
        Path file = hoverPreview;
        hoverPreview = null;
        if (file == null) return;
        int size = Math.min(144, Math.min(width, height) - 24);
        int px = mx + 14, py = my - size / 2;
        if (px + size + 6 > width) px = mx - size - 14;
        py = Math.max(6, Math.min(height - size - 6, py));
        g.nextStratum();
        UiRender.shadow(g, px - 4, py - 4, size + 8, size + 8, 6);
        UiRender.panel(g, px - 4, py - 4, size + 8, size + 8, 6, Theme.WINDOW, Theme.WINDOW_BORDER);
        BlueprintPreviews.draw(g, file, px, py, size);
    }

    private int renderPlacement(GuiGraphics g, int mx, int my, int x, int y, int w, BlueprintPlan plan) {
        var data = plan.blueprint;
        var transform = plan.transform();
        // Karte mit Vorschaubild, damit ähnlich benannte Baupläne nicht verwechselt werden.
        int image = Math.max(56, Math.min(96, w / 4));
        UiRender.panel(g, x, y, w, image + 8, 5, Theme.CARD, Theme.FIELD_BORDER);
        BlueprintPreviews.draw(g, feature.file(), x + 4, y + 4, image);
        if (feature.file() != null && hovered(mx, my, x + 4, y + 4, image, image)) hoverPreview = feature.file();
        int tx = x + image + 14, tw = w - image - 22, ty = y + 8;
        g.drawString(font, UiRender.ellipsize(font, data.name(), tw), tx, ty, Theme.TEXT, false);
        ty += 13;
        g.drawString(font, UiRender.ellipsize(font, data.source(), tw), tx, ty, Theme.TEXT_MUTED, false);
        ty += 13;
        g.drawString(font, UiRender.ellipsize(font, transform.width() + " × " + data.sizeY() + " × " + transform.depth()
                + " Blöcke · " + data.cells().size() + " Positionen", tw), tx, ty, Theme.TEXT_DIM, false);
        ty += 13;
        if (feature.file() != null) {
            BlueprintPreviews.Entry preview = BlueprintPreviews.get(feature.file());
            String source = !preview.done() ? "Vorschau wird erstellt …"
                    : preview.fromFile() ? "Bild aus der Datei" : "Berechnete Ansicht (schräg von oben)";
            UiRender.scaledText(g, font, UiRender.ellipsize(font, source, (int) (tw / 0.85F)), tx, ty + 1, Theme.TEXT_MUTED, 0.85F);
        }
        y += image + 16;
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
        button(g, mx, my, x, y, w, 20, "Automatischer Ebenenwechsel: " + (feature.autoLayer.get() ? "An" : "Aus"),
                feature.autoLayer.get() ? Style.PRIMARY : Style.SECONDARY, () -> {
                    feature.autoLayer.toggle();
                    plan.resetLayerProgress();
                    if (feature.autoLayer.get()) { plan.layers = BlueprintPlan.Layers.SINGLE; feature.focusItem(null); }
                });
        y += 26;
        if (plan.layers != BlueprintPlan.Layers.ALL) {
            var progress = plan.layerProgress();
            String progressText = "Ebene " + (plan.layer + 1) + "/" + data.sizeY() + " – " + progress.percent() + " %"
                    + (!progress.checked() ? " · wird geprüft" : progress.unknown() > 0 ? " · ungeprüfte Blöcke" : "");
            g.drawString(font, UiRender.ellipsize(font, progressText, w), x, y, Theme.TEXT_DIM, false);
            UiRender.roundRect(g, x, y + 13, w, 4, 2, Theme.CARD);
            if (progress.percent() > 0) UiRender.gradientH(g, x, y + 13, w * progress.percent() / 100, 4, Theme.ACCENT, Theme.ACCENT_2);
            y += 24;
        }
        y += paragraph(g, "Automatik gilt für die gewählte Ebene. Bei Alle Ebenen oder Materialfokus pausiert sie.", x, y, w, Theme.TEXT_MUTED) + 4;
        button(g, mx, my, x, y, w, 22, "Lazy-Bauen: " + (feature.lazyBuild.get() ? "An" : "Aus"),
                feature.lazyBuild.get() ? Style.PRIMARY : Style.SECONDARY, () -> {
                    feature.lazyBuild.toggle(); feature.lazyBuilder.cancel();
                    if (feature.lazyBuild.get()) { plan.layers = BlueprintPlan.Layers.SINGLE; feature.focusItem(null); }
                });
        y += 27;
        if (feature.lazyBuild.get()) {
            button(g, mx, my, x, y, half, 20, feature.lazyMode.get(), Style.SECONDARY, () -> feature.lazyMode.cycle(1));
            button(g, mx, my, x + half + 4, y, w - half - 4, 20, "Tempo: " + feature.lazyTempo.get(), Style.SECONDARY, () -> feature.lazyTempo.cycle(1));
            y += 25;
            y = renderKey(g, mx, my, x, y, w, ClientKeybinds.BLUEPRINT_BUILD, "Lazy-Bauen umschalten", "G");
            y += paragraph(g, "Einmal drücken: Bauen an. Erneut drücken: aus. Manuelles Bauen hat Vorrang; danach geht es automatisch weiter. "
                    + "Volle Baublöcke, Stämme, einfache Stufen und Treppen; Ausrichtung muss passen. Nach einem Ebenenwechsel erneut drücken.", x, y, w, Theme.TEXT_DIM) + 4;
            y += paragraph(g, "Normale Minecraft-Reichweite. Der Block am Fadenkreuz wird bevorzugt; auch erreichbare Kanten und Stufenflächen werden geprüft.", x, y, w, Theme.TEXT_DIM) + 4;
            y += paragraph(g, "Kein Abbau. Keine Platzierung durch Wände. Bei fehlendem Material, Ablehnung oder Zeitüberschreitung: "
                    + "stoppen und Taste erneut drücken. Ein freier Hotbar-Platz ermöglicht das Nachladen aus dem Inventar.", x, y, w, Theme.TEXT_MUTED) + 6;
        }
        button(g, mx, my, x, y, half, 20, plan.onlyMissing ? "Nur offene Blöcke" : "Gesamter Bauplan", Style.SECONDARY,
                () -> plan.onlyMissing = !plan.onlyMissing);
        button(g, mx, my, x + half + 4, y, w - half - 4, 20, "Deckkraft: " + feature.opacity.getInt() + "%", Style.SECONDARY,
                () -> feature.opacity.set(nextOpacity(feature.opacity.getInt(), hasShiftDown())));
        y += 24;
        boolean infoOn = !feature.blockInfo.is(Blueprints.INFO_OFF);
        button(g, mx, my, x, y, half, 20, UiRender.ellipsize(font, "Block-Info: " + feature.infoLabel(), half - 8),
                infoOn ? Style.PRIMARY : Style.SECONDARY, () -> feature.blockInfo.cycle(hasShiftDown() ? -1 : 1));
        tooltip(g, mx, my, x, y, half, 20, "Block-Info: " + feature.infoLabel()
                + " · Klicken zum Wechseln (Taste halten → Taste umschalten → Immer → Aus)");
        button(g, mx, my, x + half + 4, y, w - half - 4, 20, "Position: " + feature.infoPosition.get(), Style.SECONDARY,
                () -> feature.infoPosition.cycle(hasShiftDown() ? -1 : 1));
        tooltip(g, mx, my, x + half + 4, y, w - half - 4, 20, "Wo der Tooltip der Block-Info erscheint: kompakt oben mittig oder neben dem Fadenkreuz");
        y += 24;
        boolean marking = !feature.markers.is(Blueprints.MARKERS_OFF);
        button(g, mx, my, x, y, half, 20, "Markierungen: " + feature.markers.get(),
                marking ? Style.PRIMARY : Style.SECONDARY, () -> feature.markers.cycle(hasShiftDown() ? -1 : 1));
        tooltip(g, mx, my, x, y, half, 20,
                "Aus → Nur Fehler (rot/gelb/violett) → Alles (zusätzlich blau = fehlt) · Shift: rückwärts");
        boolean picking = feature.pickBlock.get();
        button(g, mx, my, x + half + 4, y, w - half - 4, 20, UiRender.ellipsize(font, "Mittelklick wählt Block: " + (picking ? "An" : "Aus"), w - half - 12),
                picking ? Style.PRIMARY : Style.SECONDARY, feature.pickBlock::toggle);
        tooltip(g, mx, my, x + half + 4, y, w - half - 4, 20, "Mausrad-Klick auf einen noch nicht gebauten Block nimmt das Item aus deinem Inventar "
                + "in die Hand. Es wird nichts erzeugt. Ist ein echter Block näher, gilt der normale Mittelklick.");
        y += 24;
        y = renderKey(g, mx, my, x, y, w, ClientKeybinds.BLUEPRINT_INFO, "Block-Info-Taste", "linkes Alt");
        y = renderKey(g, mx, my, x, y, w, ClientKeybinds.BLUEPRINT_ACCEPT, "Abweichung übernehmen", "N");
        y = renderKey(g, mx, my, x, y, w, ClientKeybinds.BLUEPRINT_HIDE, "Block als erledigt ausblenden", "H");
        y += paragraph(g, "Block anvisieren und Taste drücken: aus der Vorschau entfernen und als erledigt zählen, auch ohne echten Block. "
                + "Die Taste kann auch auf eine Maus-Seitentaste gelegt werden. Änderungen bleiben je Bauplandatei und Welt gespeichert.", x, y, w, Theme.TEXT_DIM) + 4;
        if (feature.canUndoHidden()) {
            button(g, mx, my, x, y, w, 20, "Letztes Ausblenden rückgängig", Style.SECONDARY, feature::undoHidden);
            y += 24;
        }
        boolean resetConfirm = resetPlan == plan && System.currentTimeMillis() < resetUntil;
        button(g, mx, my, x, y, w, 20, resetConfirm ? "Original wirklich wiederherstellen?" : "Originalblöcke wiederherstellen (" + plan.ignoredCount() + " ausgeblendet)", Style.SECONDARY, () -> {
            if (resetPlan == plan && System.currentTimeMillis() < resetUntil) { feature.restoreOriginal(); resetPlan = null; }
            else { resetPlan = plan; resetUntil = System.currentTimeMillis() + 4000; }
        });
        y += 24;
        y += paragraph(g, "Original wiederherstellen setzt Ausblendungen, Abweichungen und Materialtausch zurück. Position, Drehung und Ebene bleiben erhalten.", x, y, w, Theme.TEXT_MUTED) + 4;
        y += paragraph(g, "Abweichung übernehmen: auf einen falsch gesetzten Block (rot/gelb) zielen und die Taste drücken – "
                + "die Stelle zählt dann als richtig, z.B. für gewollte Varianten. Nochmal drücken nimmt es zurück.", x, y, w, Theme.TEXT_MUTED) + 4;
        int acceptedCount = plan.acceptedCount();
        if (acceptedCount > 0) {
            button(g, mx, my, x, y, w, 20, "Übernommene Abweichungen zurücksetzen (" + acceptedCount + ")", Style.SECONDARY, feature::clearAccepted);
            y += 24;
        }
        y += 6;
        y += paragraph(g, "Markierungen: Rot = falscher Block · Gelb = Zustand/Ausrichtung · Violett = unbekannt"
                + " · Blau = fehlt (nur bei „Alles“)", x, y, w, Theme.TEXT_DIM) + 6;
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
        button(g, mx, my, x, y, (w - 4) / 2, 20, "Gesamte Liste anheften", Style.PRIMARY, () -> {
            ModuleManager.MATERIAL_LIST.pinPlan();
            Notifications.success("Sammelliste angeheftet", "Gesamter offener Bedarf · aktualisiert sich auch beim Materialtausch");
        });
        button(g, mx, my, x + (w - 4) / 2 + 4, y, (w - 4) / 2, 20, "Ersetzungen verwalten", Style.SECONDARY,
                () -> minecraft.setScreen(new BlueprintMaterialsScreen(this)));
        y += 27;
        y += paragraph(g, "Gesamter Plan, unabhängig vom Ebenenfilter. Offen enthält auch ungeprüfte Positionen. Inventar zählt lose Items, keine Kisteninhalte.", x, y, w, Theme.TEXT_MUTED) + 8;
        if (!feature.isEnabled()) y += paragraph(g, "Abgleich pausiert: Vorschau unter Platzierung einschalten.", x, y, w, Theme.WARNING) + 8;
        if (!plan.inDimension(minecraft.level)) y += paragraph(g, "Andere Dimension: Die Zahlen zeigen den letzten Abgleich.", x, y, w, Theme.WARNING) + 8;
        if (plan.focus != null) {
            // Kompakter Hinweis, solange nur ein Material in der Vorschau gezeigt wird.
            UiRender.panel(g, x, y, w, 18, 4, Theme.ACCENT_SOFT, Theme.ACCENT);
            g.drawString(font, UiRender.ellipsize(font, "Nur angezeigt: " + plan.focus.name, w - 96), x + 6, y + 5, Theme.TEXT, false);
            button(g, mx, my, x + w - 84, y + 2, 82, 14, "Alle anzeigen", Style.SECONDARY, () -> feature.focusMaterial(null));
            y += 22;
        }
        String needle = query.toLowerCase(Locale.ROOT);
        for (BlueprintPlan.Material material : plan.materials) {
            if (!material.name.toLowerCase(Locale.ROOT).contains(needle)) continue;
            int inventory = 0;
            if (minecraft.player != null && material.item != Items.AIR) {
                var slots = minecraft.player.getInventory();
                for (int i = 0; i < slots.getContainerSize(); i++) if (slots.getItem(i).is(material.item)) inventory += slots.getItem(i).getCount();
            }
            if (plan.focus == material) UiRender.panel(g, x, y, w, 43, 4, Theme.CARD, Theme.ACCENT);
            else UiRender.roundRect(g, x, y, w, 43, 4, menuMaterial == material ? Theme.CARD_HOVER : Theme.CARD);
            final int cardY = y;
            // Rechts- oder Linksklick öffnet das kleine Menü.
            addHit(x, y, w, 43, (a, b, button) -> openMenu(material, (int) a, (int) b));
            var icon = material.icon();
            if (!icon.isEmpty()) g.renderItem(icon, x + 5, y + 5);
            else g.drawString(font, "?", x + 9, y + 9, Theme.WARNING, false);
            g.drawString(font, UiRender.ellipsize(font, material.name, w - 34), x + 28, y + 6, Theme.TEXT, false);
            String totals = "Gesamt " + material.total + " · Offen " + material.remaining() + " · Inventar " + inventory;
            UiRender.scaledText(g, font, UiRender.ellipsize(font, totals, (int) ((w - 12) / 0.85F)), x + 6, y + 21, Theme.TEXT_DIM, 0.85F);
            String missing = material.item == Items.AIR ? "Sonderblock / Flüssigkeit: manuell planen"
                    : "Noch besorgen: " + Math.max(0, material.remaining() - inventory);
            UiRender.scaledText(g, font, UiRender.ellipsize(font, missing, (int) ((w - 12) / 0.8F)), x + 6, y + 32, Theme.TEXT_MUTED, 0.8F);
            if (menuMaterial == null) tooltip(g, mx, my, x, cardY, w, 43, material.name + " · " + totals + " · " + missing + " · Rechtsklick: Optionen");
            y += 47;
            if (material.item != Items.AIR) {
                button(g, mx, my, x, y, w, 18, "Material ersetzen", Style.SECONDARY, () -> feature.openReplace(this, material.item));
                y += 24;
            }
        }
        y += paragraph(g, "Orientierung und Blockzustände zählen mit. Bedarf ist eine Bauhilfe: Wasser, Redstone-Zustände und Spezialmechaniken können Nacharbeit benötigen.", x, y, w, Theme.TEXT_MUTED) + 6;
        return y;
    }

    private String summary(BlueprintPlan plan) {
        return "Richtig " + plan.count(BlueprintPlan.Status.CORRECT) + "/" + plan.blueprint.cells().size()
                + " · Fehlt " + plan.count(BlueprintPlan.Status.MISSING)
                + " · Abweichend " + (plan.count(BlueprintPlan.Status.WRONG_BLOCK) + plan.count(BlueprintPlan.Status.WRONG_STATE))
                + " · Ungeprüft " + plan.count(BlueprintPlan.Status.UNCHECKED)
                + " · Unbekannt " + plan.count(BlueprintPlan.Status.UNSUPPORTED)
                + (plan.acceptedCount() > 0 ? " · Übernommen " + plan.acceptedCount() : "");
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
        BlueprintPreviews.invalidateChanged();
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
    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) { menuMaterial = null; scroll.scroll(sy); return true; }
    @Override public void onClose() { feature.saveState(); ModuleManager.saveNow(); super.onClose(); }
}
