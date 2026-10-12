package com.example.examplemod.gui.hud;

import com.example.examplemod.feature.Hud;
import com.example.examplemod.gui.Theme;
import com.example.examplemod.gui.UiRender;
import com.example.examplemod.module.ModuleManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** HUD-Elemente per Maus verschieben. Rechtsklick blendet ein Element ein oder aus. */
public class HudEditorScreen extends Screen {
    private static final int SNAP = 5;

    private final Screen parent;
    private final Hud hud;
    private HudElement dragging;
    private int grabX, grabY;
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
                g.drawString(font, element.name, element.x + 6, element.y + 3, Theme.TEXT_MUTED, true);
            }
            if (active) {
                String label = element.name + (element.isVisible() ? "" : " (ausgeblendet)");
                int ly = element.y > 12 ? element.y - 11 : element.y + element.h + 3;
                g.drawString(font, label, element.x, ly, Theme.TEXT, true);
            }
        }

        // Werkzeugleiste in der Mitte
        int bw = 220, bh = 46, bx = (width - bw) / 2, by = height / 2 - bh / 2;
        if (dragging == null) {
            UiRender.panel(g, bx, by, bw, bh, 6, Theme.WINDOW, Theme.WINDOW_BORDER);
            g.drawCenteredString(font, "Ziehen: verschieben · Rechtsklick: ein/aus", width / 2, by + 7, Theme.TEXT_DIM);
            button(g, mouseX, mouseY, bx + 10, by + 22, 96, 16, "Zurücksetzen", false);
            button(g, mouseX, mouseY, bx + bw - 106, by + 22, 96, 16, "Fertig", true);
        }
    }

    private void button(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h, String label, boolean primary) {
        boolean hover = UiRender.inside(mouseX, mouseY, x, y, w, h);
        int fill = primary ? (hover ? Theme.hover(Theme.ACCENT) : Theme.ACCENT) : (hover ? Theme.CARD_HOVER : Theme.CARD);
        UiRender.roundRect(g, x, y, w, h, 4, fill);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 4, primary ? Theme.ON_ACCENT : Theme.TEXT, false);
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
        int bw = 220, bh = 46, bx = (width - bw) / 2, by = height / 2 - bh / 2;
        if (UiRender.inside(mouseX, mouseY, bx + 10, by + 22, 96, 16)) {
            hud.elements().forEach(HudElement::reset);
            ModuleManager.markDirty();
            return true;
        }
        if (UiRender.inside(mouseX, mouseY, bx + bw - 106, by + 22, 96, 16)) {
            onClose();
            return true;
        }
        HudElement element = elementAt(mouseX, mouseY);
        if (element == null) return super.mouseClicked(mouseX, mouseY, button);
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
        if (dragging != null) {
            dragging = null;
            snapX = snapY = false;
            ModuleManager.markDirty();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        ModuleManager.saveNow();
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
