package com.example.examplemod.gui.hud;

import com.example.examplemod.feature.Hud;
import com.example.examplemod.gui.Theme;
import com.example.examplemod.gui.UiRender;
import com.example.examplemod.module.ModuleManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * HUD-Elemente per Maus verschieben und in der Größe ändern (Ecke unten rechts ziehen oder Mausrad).
 * Rechtsklick blendet ein Element ein oder aus, Mittelklick setzt die Größe auf 100 %.
 */
public class HudEditorScreen extends Screen {
    private static final int SNAP = 5, HANDLE = 7;
    private static final int BAR_W = 270, BAR_H = 60;

    private final Screen parent;
    private final Hud hud;
    private HudElement dragging, resizing;
    private int grabX, grabY;
    private int startX, startY, startW, startH;
    private float startScale;
    private boolean snapX, snapY;

    public HudEditorScreen(Screen parent) {
        super(Component.literal("HUD bearbeiten"));
        this.parent = parent;
        this.hud = ModuleManager.HUD;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0x50000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (dragging != null && snapX) g.fill(width / 2, 0, width / 2 + 1, height, Theme.alpha(Theme.ACCENT_2, 0.6F));
        if (dragging != null && snapY) g.fill(0, height / 2, width, height / 2 + 1, Theme.alpha(Theme.ACCENT_2, 0.6F));

        HudRenderer.renderEditor(g, hud);
        HudElement hovered = elementAt(mouseX, mouseY);
        for (HudElement element : hud.elements()) {
            boolean active = element == dragging || element == hovered;
            int color = !element.isVisible() ? Theme.alpha(Theme.TEXT_MUTED, 0.8F)
                    : active ? Theme.ACCENT_2 : Theme.alpha(Theme.ACCENT, 0.8F);
            outline(g, element.x - 1, element.y - 1, element.w + 2, element.h + 2, color);
            if (!element.isVisible()) {
                g.drawString(font, element.name, element.x + 6, element.y + 3, Theme.TEXT_MUTED, false);
            }
            if (resizable(element) && (active || element == resizing)) {
                // Griff zum Größerziehen, unten rechts.
                int hx = element.x + element.w - HANDLE + 2, hy = element.y + element.h - HANDLE + 2;
                g.fill(hx, hy, hx + HANDLE, hy + HANDLE, element == resizing ? Theme.ACCENT_2 : Theme.ACCENT);
                g.fill(hx + 2, hy + 2, hx + HANDLE - 2, hy + HANDLE - 2, Theme.ON_ACCENT);
            }
            if (active || element == resizing) {
                int percent = Math.round(element.scale * 100);
                String label = element.name + (element.isVisible() ? "" : " (ausgeblendet)")
                        + (percent != 100 || element == resizing ? " · " + percent + " %" : "");
                int ly = element.y > 14 ? element.y - 13 : element.y + element.h + 3;
                label(g, label, element.x, ly);
            }
        }

        // Werkzeugleiste in der Mitte; ohne Schatten, damit die Schrift scharf bleibt.
        int bx = (width - BAR_W) / 2, by = height / 2 - BAR_H / 2;
        if (dragging == null && resizing == null) {
            UiRender.panel(g, bx, by, BAR_W, BAR_H, 6, Theme.WINDOW, Theme.WINDOW_BORDER);
            centered(g, "Ziehen: verschieben · Rechtsklick: ein/aus", by + 8, Theme.TEXT);
            centered(g, "Ecke ziehen oder Mausrad: Größe · Mittelklick: 100 %", by + 20, Theme.TEXT_DIM);
            button(g, mouseX, mouseY, bx + 10, by + BAR_H - 24, 110, 16, "Zurücksetzen", false);
            button(g, mouseX, mouseY, bx + BAR_W - 120, by + BAR_H - 24, 110, 16, "Fertig", true);
        }
    }

    private void button(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, String label, boolean primary) {
        boolean hover = UiRender.inside(mouseX, mouseY, x, y, w, h);
        int fill = primary ? (hover ? Theme.hover(Theme.ACCENT) : Theme.ACCENT) : (hover ? Theme.CARD_HOVER : Theme.CARD);
        UiRender.roundRect(g, x, y, w, h, 4, fill);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 4, primary ? Theme.ON_ACCENT : Theme.TEXT, false);
    }

    private void centered(GuiGraphics g, String text, int y, int color) {
        g.drawString(font, text, (width - font.width(text)) / 2, y, color, false);
    }

    /** Beschriftung über dem Spielbild: eigener Hintergrund statt Schatten, damit sie lesbar und scharf bleibt. */
    private void label(GuiGraphics g, String text, int x, int y) {
        int w = font.width(text) + 8;
        x = Math.max(0, Math.min(width - w, x));
        UiRender.roundRect(g, x, y - 2, w, 12, 3, Theme.WINDOW);
        g.drawString(font, text, x + 4, y, Theme.TEXT, false);
    }

    /** Leere Platzhalter haben keine echte Größe; nur sichtbare Elemente mit Inhalt lassen sich skalieren. */
    private static boolean resizable(HudElement element) {
        return element.isVisible() && !element.empty;
    }

    private HudElement handleAt(double mouseX, double mouseY) {
        var elements = hud.elements();
        for (int i = elements.size() - 1; i >= 0; i--) {
            HudElement element = elements.get(i);
            if (resizable(element) && UiRender.inside(mouseX, mouseY,
                    element.x + element.w - HANDLE + 2, element.y + element.h - HANDLE + 2, HANDLE, HANDLE)) return element;
        }
        return null;
    }

    /** Neue Größe bei gleicher linker oberer Ecke (5-%-Schritte, rastet bei 100 % ein); der Anker wird neu gewählt. */
    private void applyScale(HudElement element, float value, int x, int y) {
        float fit = Math.min((width - x) / (float) Math.max(1, element.baseW), (height - y) / (float) Math.max(1, element.baseH));
        value = Math.round(value * 20) / 20F;
        if (Math.abs(value - 1F) < 0.04F) value = 1F;
        element.setScale(Math.min(value, Math.max(HudElement.MIN_SCALE, fit)));
        element.w = Math.round(element.baseW * element.scale);
        element.h = Math.round(element.baseH * element.scale);
        element.moveTo(x, y, width, height);
        ModuleManager.markDirty();
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private HudElement elementAt(double mouseX, double mouseY) {
        var elements = hud.elements();
        for (int i = elements.size() - 1; i >= 0; i--) {
            HudElement element = elements.get(i);
            if (UiRender.inside(mouseX, mouseY, element.x, element.y, element.w, element.h)) return element;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int bx = (width - BAR_W) / 2, by = height / 2 - BAR_H / 2;
        if (UiRender.inside(mouseX, mouseY, bx + 10, by + BAR_H - 24, 110, 16)) {
            hud.elements().forEach(HudElement::reset);
            ModuleManager.markDirty();
            return true;
        }
        if (UiRender.inside(mouseX, mouseY, bx + BAR_W - 120, by + BAR_H - 24, 110, 16)) {
            onClose();
            return true;
        }
        HudElement handle = button == 0 ? handleAt(mouseX, mouseY) : null;
        if (handle != null) {
            resizing = handle;
            startX = handle.x; startY = handle.y; startW = handle.w; startH = handle.h;
            startScale = handle.scale;
            return true;
        }
        HudElement element = elementAt(mouseX, mouseY);
        if (element == null) return super.mouseClicked(mouseX, mouseY, button);
        if (button == 2) {
            if (resizable(element)) applyScale(element, 1F, element.x, element.y);
            return true;
        }
        if (button == 1) {
            element.toggleVisible();
            return true;
        }
        dragging = element;
        grabX = (int) mouseX - element.x;
        grabY = (int) mouseY - element.y;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (resizing != null) {
            // Größe folgt der Maus; maßgeblich ist die Richtung, in die weiter gezogen wurde.
            float ratio = Math.max((float) (mouseX - startX) / Math.max(1, startW), (float) (mouseY - startY) / Math.max(1, startH));
            applyScale(resizing, startScale * ratio, startX, startY);
            return true;
        }
        if (dragging == null) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        int nx = (int) mouseX - grabX, ny = (int) mouseY - grabY;
        int w = dragging.w, h = dragging.h;
        // An Rändern und an der Bildschirmmitte einrasten.
        if (Math.abs(nx) < SNAP + 4) nx = 4;
        if (Math.abs(width - nx - w) < SNAP + 4) nx = width - w - 4;
        if (Math.abs(ny) < SNAP + 4) ny = 4;
        if (Math.abs(height - ny - h) < SNAP + 4) ny = height - h - 4;
        snapX = Math.abs(nx + w / 2 - width / 2) < SNAP;
        snapY = Math.abs(ny + h / 2 - height / 2) < SNAP;
        if (snapX) nx = width / 2 - w / 2;
        if (snapY) ny = height / 2 - h / 2;
        dragging.moveTo(nx, ny, width, height);
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != null || resizing != null) {
            dragging = null;
            resizing = null;
            snapX = snapY = false;
            ModuleManager.markDirty();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        HudElement element = elementAt(mouseX, mouseY);
        if (element == null || !resizable(element) || scrollY == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        applyScale(element, element.scale + (scrollY > 0 ? 0.1F : -0.1F), element.x, element.y);
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        ModuleManager.saveNow();
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
