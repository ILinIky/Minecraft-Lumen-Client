package com.example.examplemod.gui.hud;

import com.example.examplemod.feature.Hud;
import com.example.examplemod.gui.BlueprintTooltip;
import com.example.examplemod.gui.Notifications;
import com.example.examplemod.module.ModuleManager;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;

/** Zeichnet das HUD über dem Spielbild (aufgerufen aus GuiMixin) und im HUD-Editor. */
public final class HudRenderer {
    private static long lastFrame;
    private static float frameDelta;

    private HudRenderer() {}

    static float frameDelta() { return frameDelta; }

    private static void tickFrame() {
        long now = Util.getMillis();
        frameDelta = lastFrame == 0 ? 0 : Math.min(0.1F, (now - lastFrame) / 1000F);
        lastFrame = now;
    }

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof HudEditorScreen) return;
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
        element.measure(mc, editing);
        if (element.w <= 0 || element.h <= 0) {
            if (!editing) return;
            element.w = mc.font.width(element.name) + 12;
            element.h = 14;
            element.layout(mc, g.guiWidth(), g.guiHeight(), true);
            return;
        }
        element.layout(mc, g.guiWidth(), g.guiHeight(), editing);
        if (editing && !element.isVisible()) return;
        element.render(g, mc, editing, frameDelta);
    }
}
