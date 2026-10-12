package com.example.examplemod.gui;

import com.example.examplemod.feature.Waypoints;
import com.example.examplemod.waypoint.TeleportService;
import com.example.examplemod.waypoint.Waypoint;
import com.example.examplemod.waypoint.WaypointGroups;
import com.example.examplemod.waypoint.WaypointStore;
import com.example.examplemod.ExampleMod;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;

/** Wegpunkt-Liste (Taste F6): teleportieren, bearbeiten, löschen, neue Position speichern. */
public class WaypointScreen extends ClientScreen {
    private enum Sort {
        DISTANCE("Entfernung"), NAME("Name"), GROUP("Gruppe"), NEWEST("Neueste");
        final String label;
        Sort(String label) { this.label = label; }
    }

    private static final int ROW_H = 42;
    private static Sort sort = Sort.DISTANCE;
    private static boolean currentDimensionOnly;

    private final Scroll scroll = new Scroll();
    private EditBox search;
    private String query = "";
    private String groupFilter;
    private int confirmDeleteId = -1;
    private long confirmDeleteUntil;
    private long confirmClearUntil;
    private int lastClickedId = -1;
    private long lastClickTime;

    public WaypointScreen(Screen parent) {
        super(Component.literal("Wegpunkte"), parent);
    }

    @Override protected int preferredWidth() { return 560; }
    @Override protected int preferredHeight() { return 310; }

    @Override
    protected void init() {
        super.init();
        int fieldW = Math.min(140, winW / 3);
        search = borderlessField(winX + winW - fieldW - 2, winY + 11, fieldW - 18, "Wegpunkt suchen...", 40);
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            scroll.reset();
        });
        addRenderableWidget(search);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        String world = WaypointStore.worldKey() == null ? "" : WaypointStore.worldKey().replaceFirst("^singleplayer_", "Welt: ");
        int fx = search.getX() - 18, fy = winY + 7;
        int subtitleX = winX + 18 + font.width("Wegpunkte");
        int subtitleW = fx - 10 - subtitleX;
        drawTitle(g, "Wegpunkte", subtitleW >= font.width("…") ? UiRender.ellipsize(font, world, subtitleW) : null);
        UiRender.panel(g, fx, fy, search.getWidth() + 24, 16, 4, Theme.FIELD, search.isFocused() ? Theme.ACCENT : Theme.FIELD_BORDER);
        g.drawString(font, "⌕", fx + 6, fy + 4, Theme.TEXT_MUTED, false);
        if (!world.isEmpty() && hovered(mouseX, mouseY, subtitleX, winY + 7, Math.max(0, subtitleW), 16)) {
            g.setTooltipForNextFrame(font, Component.literal(world), mouseX, mouseY);
        }

        renderToolbar(g, mouseX, mouseY);
        renderList(g, mouseX, mouseY);
        renderFooter(g, mouseX, mouseY);
    }

    private int sortWidth() {
        int widest = 0;
        for (Sort value : Sort.values()) widest = Math.max(widest, font.width("Sortierung: " + value.label));
        return widest + 12;
    }

    private int dimensionWidth() {
        return Math.max(font.width("✔ Diese Dimension"), font.width("Alle Dimensionen")) + 12;
    }

    /** Unter schmaler GUI-Skalierung erhalten Filter und Export getrennte Zeilen. */
    private boolean separateExportRow() {
        int exportWidth = font.width("Exportieren (" + WaypointStore.all().size() + ")") + 12;
        return sortWidth() + dimensionWidth() + exportWidth + 24 + 12 > winW - 24;
    }

    private void renderToolbar(GuiGraphics g, int mouseX, int mouseY) {
        int x = winX + 12, y = winY + 38;
        button(g, mouseX, mouseY, x, y, 112, 16, "+ Position speichern", Style.PRIMARY,
                () -> minecraft.setScreen(WaypointEditScreen.forCurrentPosition(this, groupFilter)));
        x += 116;
        if (TeleportService.hasBack()) {
            button(g, mouseX, mouseY, x, y, 60, 16, "↶ Zurück", Style.SECONDARY, () -> {
                if (TeleportService.teleportBack()) closeAll();
            });
            x += 64;
        }
        String groupLabel = groupFilter == null ? "Alle Gruppen" : groupFilter.isEmpty() ? "Ohne Gruppe" : "„" + groupFilter + "“";
        button(g, mouseX, mouseY, x, y, winX + winW - 12 - x, 16, "Gruppe: " + groupLabel, Style.SECONDARY,
                () -> minecraft.setScreen(new WaypointGroupScreen(this, true, groupFilter, value -> {
                    groupFilter = value;
                    scroll.reset();
                    lastClickedId = -1;
                })));
        if (hovered(mouseX, mouseY, x, y, winX + winW - 12 - x, 16)) {
            g.setTooltipForNextFrame(font, Component.literal("Gruppe: " + groupLabel), mouseX, mouseY);
        }

        x = winX + 12;
        y += 22;
        boolean separateExport = separateExportRow();
        int availableW = winW - 24;
        String sortLabel = "Sortierung: " + sort.label;
        int sortW = separateExport ? Math.min(sortWidth(), (availableW - 4) / 2) : sortWidth();
        button(g, mouseX, mouseY, x, y, sortW, 16, sortLabel, Style.GHOST,
                () -> {
                    sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
                    scroll.reset();
                });
        if (hovered(mouseX, mouseY, x, y, sortW, 16)) {
            g.setTooltipForNextFrame(font, Component.literal(sortLabel + " · Klicken zum Wechseln"), mouseX, mouseY);
        }
        x += sortW + 4;
        String dimLabel = currentDimensionOnly ? "✔ Diese Dimension" : "Alle Dimensionen";
        int dimW = separateExport ? availableW - sortW - 4 : dimensionWidth();
        button(g, mouseX, mouseY, x, y, dimW, 16, dimLabel,
                currentDimensionOnly ? Style.PRIMARY : Style.GHOST, () -> {
                    currentDimensionOnly = !currentDimensionOnly;
                    scroll.reset();
                });
        x += dimW + 4;
        if (separateExport) {
            x = winX + 12;
            y += 22;
        }
        int exportW = winX + winW - 40 - x;
        int count = visibleWaypoints().size();
        button(g, mouseX, mouseY, x, y, exportW, 16, "Exportieren (" + count + ")", Style.SECONDARY, this::exportVisible);
        if (hovered(mouseX, mouseY, x, y, exportW, 16)) {
            g.setTooltipForNextFrame(font, Component.literal("Nur die sichtbaren Wegpunkte als JSON exportieren (Gruppe, Suche und Dimension)."), mouseX, mouseY);
        }
        button(g, mouseX, mouseY, winX + winW - 36, y, 24, 16, "…", Style.SECONDARY, this::openExportDirectory);
        if (hovered(mouseX, mouseY, winX + winW - 36, y, 24, 16)) {
            g.setTooltipForNextFrame(font, Component.literal("Exportordner öffnen"), mouseX, mouseY);
        }
    }

    private List<Waypoint> visibleWaypoints() {
        String dimension = TeleportService.currentDimension();
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<Waypoint> list = new ArrayList<>();
        for (Waypoint waypoint : WaypointStore.all()) {
            if (!WaypointGroups.matches(waypoint, groupFilter)) continue;
            if (currentDimensionOnly && !dimension.equals(waypoint.dimension)) continue;
            if (!q.isEmpty() && !waypoint.displayName().toLowerCase(Locale.ROOT).contains(q)
                    && !waypoint.dimensionName().toLowerCase(Locale.ROOT).contains(q)
                    && !waypoint.groupLabel().toLowerCase(Locale.ROOT).contains(q)) continue;
            list.add(waypoint);
        }
        Vec3 here = minecraft.player != null ? minecraft.player.position() : Vec3.ZERO;
        switch (sort) {
            case NAME -> list.sort(Comparator.comparing(w -> w.displayName().toLowerCase(Locale.ROOT)));
            case GROUP -> list.sort(Comparator.comparing((Waypoint w) -> w.groupName().isEmpty())
                    .thenComparing(w -> w.groupName().toLowerCase(Locale.ROOT))
                    .thenComparing(w -> w.displayName().toLowerCase(Locale.ROOT)));
            case NEWEST -> list.sort(Comparator.comparingLong((Waypoint w) -> w.created).thenComparingInt(w -> w.id).reversed());
            default -> list.sort(Comparator.comparing((Waypoint w) -> !dimension.equals(w.dimension))
                    .thenComparingDouble(w -> w.position().distanceToSqr(here)));
        }
        return list;
    }

    private void renderList(GuiGraphics g, int mouseX, int mouseY) {
        int listX = winX + 12, listY = winY + 84 + (separateExportRow() ? 22 : 0);
        int listW = winW - 30, listH = winY + winH - 34 - listY;
        List<Waypoint> waypoints = visibleWaypoints();
        int contentHeight = waypoints.size() * (ROW_H + 4);
        scroll.setMax(contentHeight - listH);
        int offset = scroll.update(frameDelta);

        if (waypoints.isEmpty()) {
            boolean none = WaypointStore.all().isEmpty();
            int cy = listY + listH / 2 - 16;
            String icon = "✦";
            g.drawString(font, icon, winX + (winW - font.width(icon)) / 2, cy, Theme.ACCENT, false);
            String title = none ? "Noch keine Wegpunkte" : "Keine Treffer";
            g.drawString(font, title, winX + (winW - font.width(title)) / 2, cy + 14, Theme.TEXT, false);
            String hint = none ? "Drücke F7 im Spiel oder klicke auf \"+ Position speichern\"" : "Suche oder Filter anpassen";
            g.drawString(font, hint, winX + (winW - font.width(hint)) / 2, cy + 26, Theme.TEXT_MUTED, false);
            return;
        }

        String dimension = TeleportService.currentDimension();
        Vec3 here = minecraft.player != null ? minecraft.player.position() : Vec3.ZERO;
        long now = Util.getMillis();
        pushClip(g, listX, listY, listW, listH);
        int y = listY - offset;
        for (Waypoint waypoint : waypoints) {
            if (y + ROW_H >= listY && y <= listY + listH) {
                renderRow(g, waypoint, listX, y, listW, dimension, here, now, mouseX, mouseY);
            }
            y += ROW_H + 4;
        }
        popClip(g);
        scrollbar(g, scroll, winX + winW - 14, listY, listH, contentHeight);
    }

    private void renderRow(GuiGraphics g, Waypoint waypoint, int x, int y, int w, String dimension, Vec3 here,
                           long now, int mouseX, int mouseY) {
        boolean sameDimension = dimension.equals(waypoint.dimension);
        boolean hover = hovered(mouseX, mouseY, x, y, w, ROW_H);
        UiRender.roundRect(g, x, y, w, ROW_H, 5, hover ? Theme.CARD_HOVER : Theme.CARD);
        g.drawString(font, waypoint.icon(), x + 7, y + 6, waypoint.color(), false);
        if (WaypointStore.isTarget(waypoint)) UiRender.roundRect(g, x, y + 6, 2, ROW_H - 12, 1, Theme.ACCENT);

        int textX = x + 20;
        int buttonsW = 178;
        String name = UiRender.ellipsize(font, waypoint.displayName(), w - buttonsW - 110);
        g.drawString(font, name, textX, y + 6, Theme.TEXT, false);
        UiRender.scaledText(g, font, "#" + waypoint.id, textX + font.width(name) + 4, y + 7.5F, Theme.TEXT_MUTED, 0.75F);
        String coords = String.format(Locale.ROOT, "X %d · Y %d · Z %d",
                (long) Math.floor(waypoint.x), (long) Math.floor(waypoint.y), (long) Math.floor(waypoint.z));
        g.drawString(font, coords, textX, y + 18, Theme.TEXT_MUTED, false);

        String dimName = waypoint.dimensionName();
        int badgeX = textX + font.width(coords) + 8;
        int dimColor = Waypoint.dimensionColor(waypoint.dimension);
        UiRender.roundRect(g, badgeX, y + 16, font.width(dimName) + 8, 11, 3, Theme.alpha(dimColor, 0.18F));
        g.drawString(font, dimName, badgeX + 4, y + 18, dimColor, false);
        String groupText = "Gruppe: " + waypoint.groupLabel();
        UiRender.scaledText(g, font, UiRender.ellipsize(font, groupText, (int) ((w - 30) / 0.8F)),
                textX, y + 31, Theme.TEXT_DIM, 0.8F);

        String distance = sameDimension ? Waypoints.formatDistance(waypoint.position().distanceTo(here)) : "andere Dim.";
        int bx = x + w - buttonsW;
        g.drawString(font, distance, bx - 8 - font.width(distance), y + 11, sameDimension ? Theme.TEXT_DIM : Theme.TEXT_MUTED, false);

        // Ganze Zeile: Doppelklick teleportiert.
        addHit(x, y, w, ROW_H, (mx, my, b) -> {
            if (b == 1) {
                minecraft.setScreen(WaypointEditScreen.forWaypoint(this, waypoint));
                return;
            }
            if (lastClickedId == waypoint.id && Util.getMillis() - lastClickTime < 350) {
                if (TeleportService.teleport(waypoint)) closeAll();
            }
            lastClickedId = waypoint.id;
            lastClickTime = Util.getMillis();
        });

        boolean isTarget = WaypointStore.isTarget(waypoint);
        button(g, mouseX, mouseY, bx, y + 7, 22, 16, "◎", isTarget ? Style.PRIMARY : Style.SECONDARY, () -> {
            WaypointStore.setTarget(isTarget ? null : waypoint);
            Notifications.info("Kompass", isTarget ? "Ziel entfernt" : "Ziel: " + waypoint.displayName());
        });
        button(g, mouseX, mouseY, bx + 26, y + 7, 22, 16, "✉", Style.SECONDARY, () -> minecraft.setScreen(new ChatScreen(shareText(waypoint))));
        if (hovered(mouseX, mouseY, bx, y + 7, 22, 16)) {
            g.setTooltipForNextFrame(font, Component.literal(isTarget ? "Kompass-Ziel entfernen" : "Als Kompass-Ziel setzen"), mouseX, mouseY);
        } else if (hovered(mouseX, mouseY, bx + 26, y + 7, 22, 16)) {
            g.setTooltipForNextFrame(font, Component.literal("Koordinaten in den Chat schreiben (Enter sendet)"), mouseX, mouseY);
        }
        bx += 50;
        boolean canTeleport = TeleportService.methodFor(waypoint.dimension) != TeleportService.Method.UNAVAILABLE;
        button(g, mouseX, mouseY, bx, y + 7, 62, 16, "Teleport", canTeleport ? Style.PRIMARY : Style.SECONDARY, () -> {
            if (TeleportService.teleport(waypoint)) closeAll();
        });
        button(g, mouseX, mouseY, bx + 66, y + 7, 26, 16, "✎", Style.SECONDARY,
                () -> minecraft.setScreen(WaypointEditScreen.forWaypoint(this, waypoint)));
        boolean confirming = confirmDeleteId == waypoint.id && now < confirmDeleteUntil;
        if (confirming) {
            button(g, mouseX, mouseY, bx + 96, y + 7, 30, 16, "OK?", Style.DANGER, () -> {
                WaypointStore.remove(waypoint.id);
                confirmDeleteId = -1;
                Notifications.info("Wegpunkt gelöscht", waypoint.displayName());
            });
        } else {
            button(g, mouseX, mouseY, bx + 96, y + 7, 30, 16, "✕", Style.DANGER, () -> {
                confirmDeleteId = waypoint.id;
                confirmDeleteUntil = Util.getMillis() + 3000;
            });
        }
    }

    private static String shareText(Waypoint waypoint) {
        return String.format(Locale.ROOT, "Wegpunkt %s: X %d Y %d Z %d (%s)", waypoint.displayName(),
                (long) Math.floor(waypoint.x), (long) Math.floor(waypoint.y), (long) Math.floor(waypoint.z), waypoint.dimensionName());
    }

    private void exportVisible() {
        List<Waypoint> visible = visibleWaypoints();
        if (visible.isEmpty()) {
            Notifications.info("Export", "Keine sichtbaren Wegpunkte zum Exportieren");
            return;
        }
        try {
            Path file = WaypointStore.export(visible, groupFilter);
            Notifications.success("Export gespeichert", visible.size() + " Wegpunkte · Ordner über … öffnen");
            ExampleMod.LOGGER.info("Wegpunkt-Export gespeichert: {}", file.toAbsolutePath());
        } catch (IOException | RuntimeException failure) {
            Notifications.error("Export fehlgeschlagen", "Datei konnte nicht gespeichert werden");
            ExampleMod.LOGGER.error("Wegpunkt-Export fehlgeschlagen", failure);
        }
    }

    private void openExportDirectory() {
        try {
            Path directory = WaypointStore.exportDirectory();
            Files.createDirectories(directory);
            Util.getPlatform().openPath(directory.toAbsolutePath());
        } catch (IOException | RuntimeException failure) {
            Notifications.error("Exportordner", "Ordner konnte nicht geöffnet werden");
            ExampleMod.LOGGER.error("Exportordner konnte nicht geöffnet werden", failure);
        }
    }

    private void renderFooter(GuiGraphics g, int mouseX, int mouseY) {
        int footerY = winY + winH - 26;
        g.fill(winX + 1, footerY - 4, winX + winW - 1, footerY - 3, Theme.DIVIDER);
        int count = WaypointStore.all().size();
        String countText = visibleWaypoints().size() + " / " + count + " Orte";
        String shownCount = UiRender.ellipsize(font, countText, (winW - 28) / 3);
        int nextX = winX + 14 + font.width(shownCount) + 10;
        g.drawString(font, shownCount, winX + 14, footerY + 5, Theme.TEXT_DIM, false);
        if (hovered(mouseX, mouseY, winX + 14, footerY, font.width(shownCount), 18)) {
            g.setTooltipForNextFrame(font, Component.literal(countText), mouseX, mouseY);
        }
        if (count > 0) {
            boolean confirming = Util.getMillis() < confirmClearUntil;
            int deleteW = confirming ? 104 : 74;
            button(g, mouseX, mouseY, nextX, footerY, deleteW, 18,
                    confirming ? "Wirklich alle?" : "Alle löschen", Style.DANGER, () -> {
                        if (Util.getMillis() < confirmClearUntil) {
                            int removed = WaypointStore.clear();
                            confirmClearUntil = 0;
                            Notifications.info("Wegpunkte", removed + " gelöscht");
                        } else {
                            confirmClearUntil = Util.getMillis() + 3000;
                        }
                    });
            nextX += deleteW + 10;
        }
        String hint = "Teleport: " + TeleportService.methodFor(TeleportService.currentDimension()).label;
        int hintW = winX + winW - 14 - nextX;
        if (hintW >= 40) {
            String shownHint = UiRender.ellipsize(font, hint, (int) (hintW / 0.75F));
            UiRender.scaledText(g, font, shownHint, winX + winW - 14 - font.width(shownHint) * 0.75F,
                    footerY + 6, Theme.TEXT_MUTED, 0.75F);
            if (hovered(mouseX, mouseY, nextX, footerY, hintW, 18)) {
                g.setTooltipForNextFrame(font, Component.literal(hint), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll.scroll(scrollY);
        return true;
    }
}
