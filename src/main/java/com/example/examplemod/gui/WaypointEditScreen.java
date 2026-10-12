package com.example.examplemod.gui;

import com.example.examplemod.waypoint.TeleportService;
import com.example.examplemod.waypoint.Waypoint;
import com.example.examplemod.waypoint.WaypointGroups;
import com.example.examplemod.waypoint.WaypointStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/** Kleines Fenster zum Speichern oder Bearbeiten eines Wegpunkts (ersetzt den alten /tpname-Ablauf). */
public class WaypointEditScreen extends ClientScreen {
    private static final int MAX_NAME = 32;

    private final Waypoint editing;
    private final String dimension;
    private final float yaw, pitch;
    private String nameValue, xValue, yValue, zValue;
    private EditBox nameBox, xBox, yBox, zBox;
    private String error;
    private int colorValue;
    private String iconValue;
    private String groupValue = "";
    private boolean duplicateConfirmed;

    private WaypointEditScreen(Screen parent, Waypoint editing, String dimension, String name,
                               double x, double y, double z, float yaw, float pitch) {
        super(Component.literal(editing == null ? "Neuer Wegpunkt" : "Wegpunkt bearbeiten"), parent);
        this.editing = editing;
        this.dimension = dimension;
        this.yaw = yaw;
        this.pitch = pitch;
        this.nameValue = name;
        this.xValue = format(x);
        this.yValue = format(y);
        this.zValue = format(z);
        if (editing != null) {
            this.colorValue = editing.color;
            this.iconValue = editing.icon;
            this.groupValue = editing.groupName();
        }
    }

    public static WaypointEditScreen forCurrentPosition(Screen parent) {
        LocalPlayer player = Minecraft.getInstance().player;
        String name = "Wegpunkt " + (WaypointStore.all().size() + 1);
        if (player == null) {
            return new WaypointEditScreen(parent, null, TeleportService.currentDimension(), name, 0, 64, 0, 0, 0);
        }
        return new WaypointEditScreen(parent, null, TeleportService.currentDimension(), name,
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
    }

    public static WaypointEditScreen forWaypoint(Screen parent, Waypoint waypoint) {
        return new WaypointEditScreen(parent, waypoint, waypoint.dimension, waypoint.name == null ? "" : waypoint.name,
                waypoint.x, waypoint.y, waypoint.z, waypoint.yaw, waypoint.pitch);
    }

    public static WaypointEditScreen forCurrentPosition(Screen parent, String group) {
        WaypointEditScreen screen = forCurrentPosition(parent);
        screen.groupValue = WaypointGroups.normalize(group);
        return screen;
    }

    private static String format(double value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        return text.endsWith(".00") ? text.substring(0, text.length() - 3) : text;
    }

    @Override protected int preferredWidth() { return 320; }
    @Override protected int preferredHeight() { return 236; }
    @Override protected boolean isModal() { return parent instanceof ClientScreen; }

    @Override
    protected void init() {
        super.init();
        int fieldX = winX + 20, fieldW = winW - 40;
        nameBox = borderlessField(fieldX, winY + 58, fieldW, "Name des Wegpunkts", MAX_NAME);
        nameBox.setValue(nameValue);
        nameBox.setResponder(value -> { nameValue = value; error = null; });
        addRenderableWidget(nameBox);

        int third = (fieldW - 16) / 3;
        xBox = coordinateField(fieldX, third, xValue, value -> xValue = value);
        yBox = coordinateField(fieldX + third + 8, third, yValue, value -> yValue = value);
        zBox = coordinateField(fieldX + (third + 8) * 2, third, zValue, value -> zValue = value);
        setInitialFocus(nameBox);
    }

    private EditBox coordinateField(int x, int w, String value, java.util.function.Consumer<String> responder) {
        EditBox box = borderlessField(x, winY + 98, w, "0", 16);
        box.setFilter(text -> text.matches("-?\\d*(\\.\\d*)?"));
        box.setValue(value);
        box.setResponder(text -> {
            responder.accept(text);
            error = null;
            duplicateConfirmed = false;
        });
        addRenderableWidget(box);
        return box;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        String dimName = Waypoint.dimensionName(dimension);
        drawTitle(g, editing == null ? "Neuer Wegpunkt" : "Wegpunkt bearbeiten", null);
        int dimColor = Waypoint.dimensionColor(dimension);
        int badgeW = font.width(dimName) + 10;
        UiRender.roundRect(g, winX + winW - 14 - badgeW, winY + 9, badgeW, 12, 3, Theme.alpha(dimColor, 0.18F));
        g.drawString(font, dimName, winX + winW - 14 - badgeW + 5, winY + 11, dimColor, false);

        UiRender.scaledText(g, font, "NAME", winX + 16, winY + 42, Theme.TEXT_MUTED, 0.75F);
        fieldBackground(g, nameBox, 6, 4);
        UiRender.scaledText(g, font, "X", xBox.getX() - 4, winY + 82, Theme.TEXT_MUTED, 0.75F);
        UiRender.scaledText(g, font, "Y", yBox.getX() - 4, winY + 82, Theme.TEXT_MUTED, 0.75F);
        UiRender.scaledText(g, font, "Z", zBox.getX() - 4, winY + 82, Theme.TEXT_MUTED, 0.75F);
        fieldBackground(g, xBox, 6, 4);
        fieldBackground(g, yBox, 6, 4);
        fieldBackground(g, zBox, 6, 4);

        if (minecraft.player != null && dimension.equals(TeleportService.currentDimension())) {
            button(g, mouseX, mouseY, winX + 14, winY + 116, 120, 14, "↻ Aktuelle Position", Style.GHOST, () -> {
                LocalPlayer player = minecraft.player;
                xBox.setValue(format(player.getX()));
                yBox.setValue(format(player.getY()));
                zBox.setValue(format(player.getZ()));
            });
        }

        renderPickers(g, mouseX, mouseY);

        String groupLabel = "Gruppe: " + (groupValue.isEmpty() ? "Ohne Gruppe" : groupValue);
        button(g, mouseX, mouseY, winX + 140, winY + 116, winW - 154, 14, groupLabel, Style.SECONDARY,
                () -> minecraft.setScreen(new WaypointGroupScreen(this, false, groupValue, value -> groupValue = value)));
        if (hovered(mouseX, mouseY, winX + 140, winY + 116, winW - 154, 14)) {
            g.setTooltipForNextFrame(font, Component.literal(groupLabel + " · Klicken zum Ändern"), mouseX, mouseY);
        }

        if (error != null) {
            g.drawString(font, UiRender.ellipsize(font, error, winW - 28), winX + 16, winY + 194,
                    duplicateConfirmed ? Theme.WARNING : Theme.DANGER, false);
        }

        int footerY = winY + winH - 26;
        button(g, mouseX, mouseY, winX + winW - 172, footerY, 76, 18, "Abbrechen", Style.SECONDARY, this::onClose);
        button(g, mouseX, mouseY, winX + winW - 92, footerY, 80, 18, "Speichern", Style.PRIMARY, this::save);
        UiRender.scaledText(g, font, "Enter = Speichern", winX + 16, footerY + 6, Theme.TEXT_MUTED, 0.75F);
    }

    /** Farbe (Auto + Palette) und Symbol zur Auswahl. */
    private void renderPickers(GuiGraphics g, int mouseX, int mouseY) {
        UiRender.scaledText(g, font, "FARBE", winX + 16, winY + 138, Theme.TEXT_MUTED, 0.75F);
        int x = winX + 16, y = winY + 147;
        boolean autoSelected = colorValue == 0;
        UiRender.panel(g, x, y, 30, 14, 3, autoSelected ? Theme.ACCENT_SOFT : Theme.FIELD, autoSelected ? Theme.ACCENT : Theme.FIELD_BORDER);
        g.drawString(font, "Auto", x + 4, y + 3, Theme.TEXT_DIM, false);
        addHit(x, y, 30, 14, (mx, my, b) -> colorValue = 0);
        x += 36;
        for (int color : Waypoint.PALETTE) {
            boolean selected = colorValue == color;
            if (selected) UiRender.roundRect(g, x - 2, y - 2, 18, 18, 9, Theme.TEXT);
            UiRender.roundRect(g, x, y, 14, 14, 7, color);
            int chosen = color;
            addHit(x - 2, y - 2, 18, 18, (mx, my, b) -> colorValue = chosen);
            x += 20;
        }

        UiRender.scaledText(g, font, "SYMBOL", winX + 16, winY + 166, Theme.TEXT_MUTED, 0.75F);
        x = winX + 16;
        y = winY + 175;
        int previewColor = colorValue != 0 ? colorValue : Theme.ACCENT;
        for (String icon : Waypoint.ICONS) {
            boolean selected = icon.equals(iconValue) || (iconValue == null && icon.equals("◆"));
            boolean hover = hovered(mouseX, mouseY, x, y, 16, 14);
            UiRender.panel(g, x, y, 16, 14, 3, selected ? Theme.ACCENT_SOFT : hover ? Theme.CARD_HOVER : Theme.FIELD,
                    selected ? Theme.ACCENT : Theme.FIELD_BORDER);
            g.drawString(font, icon, x + (16 - font.width(icon)) / 2, y + 3, previewColor, false);
            addHit(x, y, 16, 14, (mx, my, b) -> iconValue = icon.equals("◆") ? null : icon);
            x += 20;
        }
    }

    private void save() {
        double x, y, z;
        try {
            x = Double.parseDouble(xValue);
            y = Double.parseDouble(yValue);
            z = Double.parseDouble(zValue);
        } catch (NumberFormatException e) {
            error = "Bitte gültige Koordinaten eingeben";
            return;
        }
        String name = nameValue.trim().isEmpty() ? null : nameValue.trim();

        if (editing == null) {
            Waypoint near = WaypointStore.near(x, y, z, dimension, 2.0);
            if (near != null && !duplicateConfirmed) {
                duplicateConfirmed = true;
                error = "Fast gleiche Stelle wie \"" + near.displayName() + "\" - nochmal speichern?";
                return;
            }
            Waypoint created = WaypointStore.add(name, x, y, z, yaw, pitch, dimension);
            created.color = colorValue;
            created.icon = iconValue;
            WaypointStore.assignGroup(created, groupValue);
            WaypointStore.save();
            Notifications.success("Wegpunkt gespeichert", created.displayName());
        } else {
            editing.name = name;
            editing.x = x;
            editing.y = y;
            editing.z = z;
            editing.color = colorValue;
            editing.icon = iconValue;
            WaypointStore.assignGroup(editing, groupValue);
            WaypointStore.save();
            Notifications.success("Wegpunkt aktualisiert", editing.displayName());
        }
        onClose();
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }
}
