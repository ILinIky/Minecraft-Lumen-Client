package com.example.examplemod.gui;

import com.example.examplemod.module.ModuleManager;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Profile speichern und laden, z.B. "Bauen" oder "Mining" mit jeweils passenden Modulen. */
public class ProfileScreen extends ClientScreen {
    private static final int ROW_H = 26;

    private final Scroll scroll = new Scroll();
    private EditBox nameBox;
    private String nameValue = "";
    private String confirmDelete;
    private long confirmUntil;
    private String error;

    public ProfileScreen(Screen parent) {
        super(Component.literal("Profile"), parent);
    }

    @Override protected int preferredWidth() { return 340; }
    @Override protected int preferredHeight() { return 260; }

    @Override
    protected void init() {
        super.init();
        nameBox = borderlessField(winX + 20, winY + winH - 21, winW - 150, "Name, z.B. Mining", 24);
        nameBox.setValue(nameValue);
        nameBox.setResponder(value -> {
            nameValue = value;
            error = null;
        });
        addRenderableWidget(nameBox);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawTitle(g, "Profile", "Module und Einstellungen als Set");
        List<String> names = ModuleManager.profileNames();

        int listX = winX + 12, listY = winY + 40, listW = winW - 30, listH = winH - 40 - 40;
        int contentHeight = names.size() * (ROW_H + 4);
        scroll.setMax(contentHeight - listH);
        int offset = scroll.update(frameDelta);

        if (names.isEmpty()) {
            String title = "Noch keine Profile";
            String hint = "Stell die Module ein und speichere sie unten als Profil.";
            g.drawString(font, title, winX + (winW - font.width(title)) / 2, listY + listH / 2 - 12, Theme.TEXT, false);
            g.drawString(font, hint, winX + (winW - font.width(hint)) / 2, listY + listH / 2, Theme.TEXT_MUTED, false);
        }

        long now = Util.getMillis();
        pushClip(g, listX, listY, listW, listH);
        int y = listY - offset;
        for (String name : names) {
            if (y + ROW_H >= listY && y <= listY + listH) {
                boolean hover = hovered(mouseX, mouseY, listX, y, listW, ROW_H);
                UiRender.roundRect(g, listX, y, listW, ROW_H, 5, hover ? Theme.CARD_HOVER : Theme.CARD);
                g.drawString(font, UiRender.ellipsize(font, name, listW - 190), listX + 10, y + 5, Theme.TEXT, false);
                int active = ModuleManager.profileActiveCount(name);
                UiRender.scaledText(g, font, active + (active == 1 ? " Modul aktiv" : " Module aktiv"), listX + 10, y + 16,
                        Theme.TEXT_MUTED, 0.75F);

                int bx = listX + listW - 170;
                button(g, mouseX, mouseY, bx, y + 5, 54, 16, "Laden", Style.PRIMARY, () -> ModuleManager.applyProfile(name));
                button(g, mouseX, mouseY, bx + 58, y + 5, 74, 16, "Überschreiben", Style.SECONDARY, () -> {
                    ModuleManager.saveProfile(name);
                    Notifications.success("Profil gespeichert", name);
                });
                boolean confirming = name.equals(confirmDelete) && now < confirmUntil;
                button(g, mouseX, mouseY, bx + 136, y + 5, 30, 16, confirming ? "OK?" : "✕", Style.DANGER, () -> {
                    if (name.equals(confirmDelete) && Util.getMillis() < confirmUntil) {
                        ModuleManager.deleteProfile(name);
                        confirmDelete = null;
                        Notifications.info("Profil gelöscht", name);
                    } else {
                        confirmDelete = name;
                        confirmUntil = Util.getMillis() + 3000;
                    }
                });
            }
            y += ROW_H + 4;
        }
        popClip(g);
        scrollbar(g, scroll, winX + winW - 14, listY, listH, contentHeight);

        int footerY = winY + winH - 26;
        g.fill(winX + 1, footerY - 6, winX + winW - 1, footerY - 5, Theme.DIVIDER);
        fieldBackground(g, nameBox, 6, 4);
        button(g, mouseX, mouseY, winX + winW - 120, footerY, 108, 18, "+ Aktuelles speichern", Style.PRIMARY, this::saveNew);
        if (error != null) {
            g.drawString(font, error, winX + 14, footerY - 18, Theme.DANGER, false);
        }
    }

    private void saveNew() {
        String name = nameValue.trim();
        if (name.isEmpty()) {
            error = "Bitte einen Namen eingeben";
            return;
        }
        boolean existed = ModuleManager.profileNames().contains(name);
        ModuleManager.saveProfile(name);
        Notifications.success(existed ? "Profil überschrieben" : "Profil gespeichert", name);
        nameBox.setValue("");
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (nameBox.isFocused() && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) {
            saveNew();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll.scroll(scrollY);
        return true;
    }
}
