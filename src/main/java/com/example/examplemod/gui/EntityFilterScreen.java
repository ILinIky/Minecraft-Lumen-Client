package com.example.examplemod.gui;

import com.example.examplemod.feature.EntityEsp;
import com.example.examplemod.module.Setting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Auswahl einzelner Monster, Tiere und Spieler für das Entity-ESP. Änderungen wirken sofort. */
public class EntityFilterScreen extends ClientScreen {
    private enum Tab {
        MONSTERS("Monster"), ANIMALS("Tiere & NPCs"), PLAYERS("Spieler");
        final String title;
        Tab(String title) { this.title = title; }
    }

    private static final int ROW_H = 20;
    private static Tab tab = Tab.MONSTERS;

    private final EntityEsp esp;
    private final List<EntityType<?>> monsters = new ArrayList<>();
    private final List<EntityType<?>> animals = new ArrayList<>();
    private final Map<EntityType<?>, ItemStack> eggs = new HashMap<>();
    private final Scroll scroll = new Scroll();
    private EditBox search;
    private String query = "";

    public EntityFilterScreen(Screen parent, EntityEsp esp) {
        super(Component.literal("Entity-Filter"), parent);
        this.esp = esp;
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            MobCategory category = type.getCategory();
            SpawnEggItem egg = SpawnEggItem.byId(type);
            if (egg != null) eggs.put(type, new ItemStack(egg));
            if (category == MobCategory.MONSTER) {
                monsters.add(type);
            } else if (category != MobCategory.MISC || egg != null) {
                if (type != EntityType.PLAYER) animals.add(type);
            }
        }
        Comparator<EntityType<?>> byName = Comparator.comparing(type -> type.getDescription().getString());
        monsters.sort(byName);
        animals.sort(byName);
    }

    @Override protected int preferredWidth() { return 420; }
    @Override protected int preferredHeight() { return 300; }

    @Override
    protected void init() {
        super.init();
        int fieldW = Math.min(140, winW / 3);
        search = borderlessField(winX + winW - fieldW - 2, winY + 11, fieldW - 18, "Suchen...", 40);
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            scroll.reset();
        });
        addRenderableWidget(search);
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawTitle(g, "Entity-ESP Filter", null);
        int fx = search.getX() - 18, fy = winY + 7;
        UiRender.panel(g, fx, fy, search.getWidth() + 24, 16, 4, Theme.FIELD, search.isFocused() ? Theme.ACCENT : Theme.FIELD_BORDER);
        g.drawString(font, "⌕", fx + 6, fy + 4, Theme.TEXT_MUTED, false);

        // Tabs
        int tx = winX + 12, ty = winY + 38;
        for (Tab t : Tab.values()) {
            int w = font.width(t.title) + 18;
            button(g, mouseX, mouseY, tx, ty, w, 16, t.title, t == tab ? Style.PRIMARY : Style.GHOST, () -> {
                tab = t;
                scroll.reset();
            });
            tx += w + 4;
        }

        int listX = winX + 12, listY = winY + 80, listW = winW - 30, listH = winH - 80 - 34;
        renderModeHint(g, mouseX, mouseY, winX + 12, winY + 60, winW - 24);

        List<Row> rows = rows();
        int columns = 2;
        int colW = (listW - 6) / columns;
        int lineCount = (rows.size() + columns - 1) / columns;
        int contentHeight = lineCount * (ROW_H + 3);
        scroll.setMax(contentHeight - listH);
        int offset = scroll.update(frameDelta);

        pushClip(g, listX, listY, listW, listH);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int x = listX + (i % columns) * (colW + 6);
            int y = listY + (i / columns) * (ROW_H + 3) - offset;
            if (y + ROW_H < listY || y > listY + listH) continue;
            boolean hover = hovered(mouseX, mouseY, x, y, colW, ROW_H);
            UiRender.roundRect(g, x, y, colW, ROW_H, 4, hover ? Theme.CARD_HOVER : Theme.CARD);
            row.drawIcon(g, x + 3, y + 2);
            g.drawString(font, UiRender.ellipsize(font, row.label, colW - 46), x + 24, y + 6, row.selected ? Theme.TEXT : Theme.TEXT_DIM, false);
            UiRender.checkbox(g, font, x + colW - 15, y + 5, row.selected, hover);
            addHit(x, y, colW, ROW_H, (mx, my, b) -> {
                row.toggle.run();
                esp.filterChanged();
            });
        }
        popClip(g);
        scrollbar(g, scroll, winX + winW - 14, listY, listH, contentHeight);

        if (rows.isEmpty()) {
            String empty = tab == Tab.PLAYERS ? "Keine Spieler online" : "Nichts gefunden";
            g.drawString(font, empty, winX + (winW - font.width(empty)) / 2, listY + 30, Theme.TEXT_MUTED, false);
        }

        int footerY = winY + winH - 26;
        g.fill(winX + 1, footerY - 4, winX + winW - 1, footerY - 3, Theme.DIVIDER);
        button(g, mouseX, mouseY, winX + 12, footerY, 50, 18, "Alle", Style.SECONDARY, () -> setAll(rows, true));
        button(g, mouseX, mouseY, winX + 66, footerY, 50, 18, "Keine", Style.SECONDARY, () -> setAll(rows, false));
        button(g, mouseX, mouseY, winX + winW - 92, footerY, 80, 18, "Fertig", Style.PRIMARY, this::onClose);
    }

    private void renderModeHint(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w) {
        String text;
        Runnable fix = null;
        if (tab == Tab.PLAYERS) {
            text = esp.selectedPlayers.isEmpty() ? "Keine Auswahl = alle Spieler werden angezeigt" : esp.selectedPlayers.size() + " Spieler ausgewählt";
            if (!esp.playersEnabled().get()) {
                text = "Spieler-ESP ist ausgeschaltet";
                fix = () -> esp.playersEnabled().set(true);
            }
        } else {
            Setting.Mode mode = tab == Tab.MONSTERS ? esp.monsterMode() : esp.animalMode();
            if (mode.is("Auswahl")) {
                text = "Nur ausgewählte " + tab.title + " werden angezeigt";
            } else {
                text = "Modus steht auf \"" + mode.get() + "\" - die Auswahl wirkt nur im Modus \"Auswahl\"";
                fix = () -> {
                    while (!mode.is("Auswahl")) mode.cycle(1);
                };
            }
        }
        UiRender.roundRect(g, x, y, w, 15, 3, fix != null ? 0x30FBBF24 : 0x2034D399);
        g.drawString(font, UiRender.ellipsize(font, text, w - (fix != null ? 90 : 12)), x + 6, y + 4,
                fix != null ? Theme.WARNING : Theme.SUCCESS, false);
        if (fix != null) {
            Runnable action = fix;
            button(g, mouseX, mouseY, x + w - 80, y + 1, 78, 13, "Aktivieren", Style.SECONDARY, action);
        }
    }

    private void setAll(List<Row> rows, boolean value) {
        for (Row row : rows) {
            if (row.selected != value) row.toggle.run();
        }
        esp.filterChanged();
    }

    private List<Row> rows() {
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<Row> rows = new ArrayList<>();
        if (tab == Tab.PLAYERS) {
            Map<String, PlayerInfo> online = new LinkedHashMap<>();
            ClientPacketListener connection = minecraft.getConnection();
            if (connection != null) {
                List<PlayerInfo> infos = new ArrayList<>(connection.getOnlinePlayers());
                infos.sort(Comparator.comparing(info -> info.getProfile().getName().toLowerCase(Locale.ROOT)));
                for (PlayerInfo info : infos) {
                    if (minecraft.player != null && info.getProfile().getId().equals(minecraft.player.getUUID())) continue;
                    online.put(info.getProfile().getName(), info);
                }
            }
            for (String name : esp.selectedPlayers) online.putIfAbsent(name, null);
            for (Map.Entry<String, PlayerInfo> entry : online.entrySet()) {
                String name = entry.getKey();
                if (!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q)) continue;
                PlayerInfo info = entry.getValue();
                rows.add(new Row(info == null ? name + " (offline)" : name, esp.selectedPlayers.contains(name),
                        () -> { if (!esp.selectedPlayers.remove(name)) esp.selectedPlayers.add(name); },
                        (g, x, y) -> {
                            if (info != null) PlayerFaceRenderer.draw(g, info.getSkin(), x + 1, y + 1, 14);
                            else UiRender.roundRect(g, x + 1, y + 1, 14, 14, 3, Theme.SWITCH_OFF);
                        }));
            }
            return rows;
        }
        List<EntityType<?>> source = tab == Tab.MONSTERS ? monsters : animals;
        Set<EntityType<?>> selection = tab == Tab.MONSTERS ? esp.selectedMonsters : esp.selectedAnimals;
        for (EntityType<?> type : source) {
            String name = type.getDescription().getString();
            if (!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q)) continue;
            ItemStack egg = eggs.get(type);
            rows.add(new Row(name, selection.contains(type),
                    () -> { if (!selection.remove(type)) selection.add(type); },
                    (g, x, y) -> {
                        if (egg != null) g.renderItem(egg, x, y);
                        else g.drawString(font, name.substring(0, Math.min(2, name.length())), x + 2, y + 4, Theme.TEXT_DIM, false);
                    }));
        }
        return rows;
    }

    @FunctionalInterface
    private interface IconDrawer {
        void draw(GuiGraphics g, int x, int y);
    }

    private record Row(String label, boolean selected, Runnable toggle, IconDrawer icon) {
        void drawIcon(GuiGraphics g, int x, int y) { icon.draw(g, x, y); }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll.scroll(scrollY);
        return true;
    }
}
