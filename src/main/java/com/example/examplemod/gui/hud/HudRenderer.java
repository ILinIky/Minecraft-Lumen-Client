package com.example.examplemod.gui.hud;

import com.example.examplemod.feature.Hud;
import com.example.examplemod.gui.BlueprintTooltip;
import com.example.examplemod.gui.Notifications;
import com.example.examplemod.module.ModuleManager;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import com.example.examplemod.gui.ChestSearchScreen;

import java.util.ArrayList;
import java.util.List;

/** Zeichnet das HUD über dem Spielbild (aufgerufen aus GuiMixin) und im HUD-Editor. */
public final class HudRenderer {
    private static long lastFrame;
    private static float frameDelta;

    private HudRenderer() {}

    static float frameDelta() { return frameDelta; }

    /** Null mouse coordinates: gameplay shortcut. Otherwise only scroll under the pointer. */
    public static boolean scrollMaterials(double delta, Double mouseX, Double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        Hud hud = ModuleManager.HUD;
        if (mc.player == null || hud == null || !hud.isEnabled() || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return false;
        for (HudElement element : hud.elements()) {
            if (!(element instanceof MaterialListElement list) || !element.isVisible()) continue;
            element.place(mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), false);
            if (element.h <= 0 || mouseX != null && !com.example.examplemod.gui.UiRender.inside(mouseX, mouseY, element.x, element.y, element.w, element.h)) return false;
            return list.scroll(delta);
        }
        return false;
    }

    /**
     * Klick bei offenem Chat auf die Sammelliste: Rechtsklick auf eine Zeile öffnet das Menü,
     * Klick auf die Kopfzeile zeigt nach "Nur diesen" wieder alle. {@code true} = Klick verbraucht.
     */
    public static boolean clickMaterials(double mouseX, double mouseY, int button) {
        Minecraft mc = Minecraft.getInstance();
        Hud hud = ModuleManager.HUD;
        var feature = ModuleManager.MATERIAL_LIST;
        if (mc.player == null || hud == null || feature == null || !hud.isEnabled() || mc.options.hideGui) return false;
        for (HudElement element : hud.elements()) {
            if (!(element instanceof MaterialListElement list) || !element.isVisible()) continue;
            if (element.h <= 0 || !com.example.examplemod.gui.UiRender.inside(mouseX, mouseY, element.x, element.y, element.w, element.h)) return false;
            com.example.examplemod.feature.MaterialList.Row row = list.rowAt(mouseX, mouseY);
            if (row == null) {
                if (feature.solo() == null) return false;
                ModuleManager.BLUEPRINTS.focusItem(null);
                return true;
            }
            if (button != 1) return false;
            List<HudMenu.Entry> entries = new ArrayList<>();
            boolean solo = feature.solo() == row.item();
            entries.add(new HudMenu.Entry(solo ? "Alle anzeigen" : "Nur diesen anzeigen", () -> ModuleManager.BLUEPRINTS.focusItem(solo ? null : row.item())));
            // Materialien des geladenen Bauplans lassen sich direkt im ganzen Plan ersetzen.
            if (ModuleManager.BLUEPRINTS.usesMaterial(row.item())) {
                entries.add(new HudMenu.Entry("Ersetzen …", () -> ModuleManager.BLUEPRINTS.openReplace(null, row.item())));
            }
            entries.add(new HudMenu.Entry("In die Hand nehmen", () -> ModuleManager.BLUEPRINTS.takeIntoHand(row.item(), null)));
            if (ModuleManager.CHEST_MEMORY != null && ModuleManager.CHEST_MEMORY.isEnabled()) {
                entries.add(new HudMenu.Entry("In Kisten suchen", () -> mc.setScreen(new ChestSearchScreen(null, row.name()))));
            }
            if (feature.isManual(row.item())) entries.add(new HudMenu.Entry("Aus Liste entfernen", () -> feature.set(row.item(), 0)));
            HudMenu.open(entries, mouseX, mouseY);
            return true;
        }
        return false;
    }

    private static void tickFrame() {
        long now = Util.getMillis();
        frameDelta = lastFrame == 0 ? 0 : Math.min(0.1F, (now - lastFrame) / 1000F);
        lastFrame = now;
    }

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof HudEditorScreen) return;
        // Das Rechtsklick-Menü gehört zum offenen Chat.
        if (!(mc.screen instanceof ChatScreen)) HudMenu.close();
        tickFrame();
        Hud hud = ModuleManager.HUD;
        boolean showHud = mc.player != null && hud != null && hud.isEnabled() && !mc.options.hideGui
                && !mc.getDebugOverlay().showDebugScreen();
        if (showHud) {
            for (HudElement element : hud.elements()) {
                if (!element.isVisible()) continue;
                drawElement(g, mc, element, false);
            }
        }
        if (mc.player != null && !mc.getDebugOverlay().showDebugScreen()) BlueprintTooltip.render(g);
        if (mc.screen == null || mc.screen instanceof ChatScreen) Notifications.render(g);
    }

    /** Im Editor: alle Elemente, ausgeblendete nur als Umriss. */
    static void renderEditor(GuiGraphics g, Hud hud) {
        tickFrame();
        Minecraft mc = Minecraft.getInstance();
        for (HudElement element : hud.elements()) {
            drawElement(g, mc, element, true);
        }
    }

    private static void drawElement(GuiGraphics g, Minecraft mc, HudElement element, boolean editing) {
        long start = element.metric.begin();
        try { drawMeasuredElement(g, mc, element, editing); } finally { element.metric.end(start); }
    }

    private static void drawMeasuredElement(GuiGraphics g, Minecraft mc, HudElement element, boolean editing) {
        element.place(mc, g.guiWidth(), g.guiHeight(), editing);
        if (element.w <= 0 || element.h <= 0) {
            if (!editing) return;
            // Gerade leer: im Editor als Platzhalter mit Namen, ohne Größenänderung.
            element.w = element.baseW = mc.font.width(element.name) + 12;
            element.h = element.baseH = 14;
            element.layout(mc, g.guiWidth(), g.guiHeight(), true);
            return;
        }
        if (editing && !element.isVisible()) return;
        element.draw(g, mc, editing, frameDelta);
    }
}
