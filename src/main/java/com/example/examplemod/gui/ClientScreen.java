package com.example.examplemod.gui;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * Grundlage aller Client-Menüs: zentriertes Fenster mit Schatten, sanftes Einblenden und
 * Klickbereiche, die beim Zeichnen registriert werden (Zeichnen und Klicken nutzen dieselben Masse).
 */
public abstract class ClientScreen extends Screen {
    public enum Style { PRIMARY, SECONDARY, DANGER, GHOST }

    @FunctionalInterface
    protected interface Click {
        void click(double mouseX, double mouseY, int button);
    }

    private record Hit(int x, int y, int w, int h, Click handler) {}

    protected final Screen parent;
    protected int winX, winY, winW, winH;
    protected float frameDelta;

    private final List<Hit> hits = new ArrayList<>();
    private final List<int[]> clipStack = new ArrayList<>();
    private long openedAt;
    private long lastFrame;

    protected ClientScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    protected abstract int preferredWidth();
    protected abstract int preferredHeight();
    protected abstract void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick);

    /** Modale Fenster zeigen das darunterliegende Menü abgedunkelt im Hintergrund. */
    protected boolean isModal() { return false; }

    @Override
    protected void init() {
        winW = Math.min(preferredWidth(), width - 16);
        winH = Math.min(preferredHeight(), height - 16);
        winX = (width - winW) / 2;
        winY = (height - winH) / 2;
        if (openedAt == 0) openedAt = Util.getMillis();
        if (isModal() && parent != null && minecraft != null) parent.init(minecraft, width, height);
    }

    protected float openProgress() {
        return UiRender.ease((Util.getMillis() - openedAt) / 180F);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (isModal() && parent != null) {
            parent.renderBackground(g, -1000, -1000, partialTick);
            parent.render(g, -1000, -1000, partialTick);
            g.fill(0, 0, width, height, Theme.alpha(Theme.OVERLAY, openProgress()));
            return;
        }
        g.fill(0, 0, width, height, Theme.alpha(Theme.OVERLAY, openProgress()));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long now = Util.getMillis();
        frameDelta = lastFrame == 0 ? 0 : Math.min(0.1F, (now - lastFrame) / 1000F);
        lastFrame = now;
        hits.clear();
        clipStack.clear();

        UiRender.shadow(g, winX, winY, winW, winH, 6);
        UiRender.panel(g, winX, winY, winW, winH, 6, Theme.WINDOW, Theme.WINDOW_BORDER);
        // Akzentlinie oben, wächst beim Öffnen von der Mitte nach außen.
        int lineW = Math.round((winW - 12) * openProgress());
        UiRender.gradientH(g, winX + (winW - lineW) / 2, winY + 1, lineW, 1, Theme.ACCENT, Theme.ACCENT_2);

        renderContent(g, mouseX, mouseY, partialTick);
        super.render(g, mouseX, mouseY, partialTick);
        renderOverlay(g, mouseX, mouseY, partialTick);
        // Ein modales Fenster zeichnet dieses Menü im Hintergrund mit; Meldungen nur einmal zeichnen.
        if (minecraft != null && minecraft.screen == this) Notifications.render(g);
    }

    /** Wird nach den Vanilla-Widgets gezeichnet, z.B. für Tooltips. */
    protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    // ---- Klickbereiche ----

    protected void addHit(int x, int y, int w, int h, Click handler) {
        if (!clipStack.isEmpty()) {
            int[] clip = clipStack.get(clipStack.size() - 1);
            int x1 = Math.max(x, clip[0]), y1 = Math.max(y, clip[1]);
            int x2 = Math.min(x + w, clip[2]), y2 = Math.min(y + h, clip[3]);
            if (x2 <= x1 || y2 <= y1) return;
            x = x1; y = y1; w = x2 - x1; h = y2 - y1;
        }
        hits.add(new Hit(x, y, w, h, handler));
    }

    protected void pushClip(GuiGraphics g, int x, int y, int w, int h) {
        int[] rect = {x, y, x + w, y + h};
        if (!clipStack.isEmpty()) {
            int[] outer = clipStack.get(clipStack.size() - 1);
            rect = new int[]{Math.max(rect[0], outer[0]), Math.max(rect[1], outer[1]),
                    Math.min(rect[2], outer[2]), Math.min(rect[3], outer[3])};
        }
        clipStack.add(rect);
        g.enableScissor(x, y, x + w, y + h);
    }

    protected void popClip(GuiGraphics g) {
        clipStack.remove(clipStack.size() - 1);
        g.disableScissor();
    }

    protected boolean isVisibleInClip(double mouseX, double mouseY) {
        if (clipStack.isEmpty()) return true;
        int[] clip = clipStack.get(clipStack.size() - 1);
        return mouseX >= clip[0] && mouseX < clip[2] && mouseY >= clip[1] && mouseY < clip[3];
    }

    protected boolean hovered(int mouseX, int mouseY, int x, int y, int w, int h) {
        return UiRender.inside(mouseX, mouseY, x, y, w, h) && isVisibleInClip(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (UiRender.inside(mouseX, mouseY, hit.x, hit.y, hit.w, hit.h)) {
                setFocused(null);
                playClick();
                hit.handler.click(mouseX, mouseY, button);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    protected void playClick() {
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.35F));
        }
    }

    // ---- Bausteine ----

    protected void button(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int h,
                          String label, Style style, Runnable action) {
        boolean hover = hovered(mouseX, mouseY, x, y, w, h);
        int fill, text = Theme.TEXT;
        switch (style) {
            case PRIMARY -> {
                fill = hover ? Theme.lerp(Theme.ACCENT, 0xFFFFFFFF, 0.12F) : Theme.ACCENT;
                text = Theme.ON_ACCENT;
            }
            case DANGER -> {
                fill = hover ? 0xFFDC4B4B : Theme.DANGER_BG;
                if (hover) text = Theme.ON_ACCENT;
            }
            case GHOST -> {
                fill = hover ? Theme.CARD_HOVER : 0x00000000;
                text = hover ? Theme.TEXT : Theme.TEXT_DIM;
            }
            default -> fill = hover ? Theme.CARD_HOVER : Theme.CARD;
        }
        if (style == Style.DANGER && !hover) text = Theme.DANGER;
        if (style == Style.SECONDARY) UiRender.panel(g, x, y, w, h, 4, fill, hover ? Theme.SWITCH_OFF_HOVER : Theme.FIELD_BORDER);
        else if ((fill >>> 24) != 0) UiRender.roundRect(g, x, y, w, h, 4, fill);
        String shown = UiRender.ellipsize(font, label, w - 6);
        g.drawString(font, shown, x + (w - font.width(shown)) / 2, y + (h - 8) / 2, text, false);
        addHit(x, y, w, h, (mx, my, b) -> action.run());
    }

    /** Hintergrund für ein randloses Vanilla-Textfeld. */
    protected void fieldBackground(GuiGraphics g, EditBox box, int padX, int padY) {
        boolean focused = box.isFocused();
        UiRender.panel(g, box.getX() - padX, box.getY() - padY, box.getWidth() + padX * 2, box.getHeight() + padY * 2, 4,
                Theme.FIELD, focused ? Theme.ACCENT : Theme.FIELD_BORDER);
    }

    protected EditBox borderlessField(int x, int y, int w, String hint, int maxLength) {
        EditBox box = new EditBox(font, x, y, w, 10, Component.literal(hint)) {
            @Override public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
                setTextColor(Theme.TEXT);
                setTextColorUneditable(Theme.TEXT_DIM);
                setHint(Component.literal(hint).withColor(Theme.TEXT_DIM));
                super.renderWidget(graphics, mouseX, mouseY, partialTick);
            }
        };
        box.setBordered(false);
        box.setTextShadow(false);
        box.setMaxLength(maxLength);
        box.setTextColor(Theme.TEXT);
        box.setHint(Component.literal(hint).withColor(Theme.TEXT_DIM));
        return box;
    }

    protected void drawTitle(GuiGraphics g, String title, String subtitle) {
        g.drawString(font, title, winX + 14, winY + 11, Theme.TEXT, false);
        if (subtitle != null) {
            g.drawString(font, subtitle, winX + 18 + font.width(title), winY + 11, Theme.TEXT_MUTED, false);
        }
        g.fill(winX + 1, winY + 30, winX + winW - 1, winY + 31, Theme.DIVIDER);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    /** Schließt auch alle darunterliegenden Menüs, z.B. nach einem Teleport. */
    protected void closeAll() {
        if (minecraft != null) minecraft.setScreen(null);
    }

    /** Weiches Scrollen für Listen. */
    protected static final class Scroll {
        private float current;
        private float target;
        private int max;

        public void setMax(int max) {
            this.max = Math.max(0, max);
            target = Math.max(0, Math.min(this.max, target));
        }

        public void scroll(double amount) {
            target = Math.max(0, Math.min(max, target - (float) amount * 28));
        }

        public void reset() { current = target = 0; }

        public int update(float delta) {
            current += (target - current) * Math.min(1, delta * 16);
            if (Math.abs(target - current) < 0.5F) current = target;
            return Math.round(current);
        }

        public int max() { return max; }
        public float progress() { return max == 0 ? 0 : current / max; }
    }

    protected void scrollbar(GuiGraphics g, Scroll scroll, int x, int y, int h, int contentHeight) {
        if (scroll.max() <= 0) return;
        int thumb = Math.max(18, h * h / Math.max(h, contentHeight));
        int thumbY = y + Math.round((h - thumb) * scroll.progress());
        UiRender.roundRect(g, x, y, 3, h, 1, Theme.SCROLL_TRACK);
        UiRender.roundRect(g, x, thumbY, 3, thumb, 1, Theme.alpha(Theme.ACCENT, 0.9F));
    }
}
