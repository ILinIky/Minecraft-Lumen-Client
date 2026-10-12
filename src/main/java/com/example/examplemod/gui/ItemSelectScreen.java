package com.example.examplemod.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Raster aller Items mit Suche und Mehrfachauswahl (für den Auto-Abwurf). Aufbau wie {@link BlockSelectScreen}.
 * Optional mit Menge je ausgewähltem Item (Mausrad oder Rechtsklick), z.B. "64 behalten".
 */
public class ItemSelectScreen extends ClientScreen {
    private static final int SLOT = 22;
    /** Typischer Abbau-Müll; per Knopf auf einmal auswählbar. */
    private static final Set<String> STONE_PRESET = Set.of("granite", "diorite", "andesite", "tuff", "calcite",
            "cobbled_deepslate", "netherrack", "dirt", "gravel");

    private final String heading;
    private final BiConsumer<Set<Item>, Map<Item, Integer>> onSave;
    private final Set<Item> selected;
    /** {@code null}: ohne Mengen. Sonst: Menge je ausgewähltem Item (0 = keine). */
    private final Map<Item, Integer> amounts;
    private static final int[] AMOUNT_PRESETS = {0, 16, 32, 64, 128, 256};
    private static final int MAX_AMOUNT = 36 * 64;
    private Item hoveredItem;
    private final List<Item> allItems = new ArrayList<>();
    private final Map<Item, ItemStack> icons = new HashMap<>();
    private final Map<Item, String> names = new HashMap<>();
    private final Scroll scroll = new Scroll();
    private List<Item> filtered = new ArrayList<>();
    private EditBox search;
    private String query = "";
    private boolean onlySelected, onlyInventory;

    public ItemSelectScreen(Screen parent, String heading, Set<Item> current, Consumer<Set<Item>> onSave) {
        this(parent, heading, current, null, (items, amounts) -> onSave.accept(items));
    }

    /** Mit Mengen: {@code currentAmounts} wird kopiert, {@code onSave} erhält Auswahl und Mengen. */
    public ItemSelectScreen(Screen parent, String heading, Set<Item> current, Map<Item, Integer> currentAmounts,
                            BiConsumer<Set<Item>, Map<Item, Integer>> onSave) {
        super(Component.literal(heading), parent);
        this.heading = heading;
        this.onSave = onSave;
        this.selected = new LinkedHashSet<>(current);
        this.amounts = currentAmounts == null ? null : new HashMap<>(currentAmounts);
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            ItemStack icon = new ItemStack(item);
            allItems.add(item);
            icons.put(item, icon);
            names.put(item, icon.getHoverName().getString());
        }
        refilter();
    }

    @Override protected int preferredWidth() { return 420; }
    @Override protected int preferredHeight() { return 300; }

    @Override
    protected void init() {
        super.init();
        int fieldW = Math.min(150, winW / 3);
        search = borderlessField(winX + winW - fieldW - 2, winY + 11, fieldW - 18, "Item suchen...", 40);
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            refilter();
        });
        addRenderableWidget(search);
        setInitialFocus(search);
    }

    /** Items, die gerade im Inventar liegen (für "Nur Inventar"). */
    private Set<Item> inventoryItems() {
        Set<Item> result = new HashSet<>();
        if (minecraft == null || minecraft.player == null) return result;
        Inventory inventory = minecraft.player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) result.add(stack.getItem());
        }
        return result;
    }

    private void refilter() {
        String q = query.toLowerCase(Locale.ROOT).trim();
        Set<Item> inInventory = onlyInventory ? inventoryItems() : Set.of();
        filtered = new ArrayList<>();
        for (Item item : allItems) {
            if (onlySelected && !selected.contains(item)) continue;
            if (onlyInventory && !inInventory.contains(item)) continue;
            if (!q.isEmpty() && !names.get(item).toLowerCase(Locale.ROOT).contains(q)
                    && !BuiltInRegistries.ITEM.getKey(item).getPath().contains(q)) continue;
            filtered.add(item);
        }
        scroll.reset();
    }

    private void selectPreset(Set<String> paths) {
        for (Item item : allItems) {
            if (paths.contains(BuiltInRegistries.ITEM.getKey(item).getPath())) selected.add(item);
        }
        refilter();
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        drawTitle(g, heading, selected.size() + " ausgewählt");
        int fx = search.getX() - 18, fy = winY + 7;
        UiRender.panel(g, fx, fy, search.getWidth() + 24, 16, 4, Theme.FIELD, search.isFocused() ? Theme.ACCENT : Theme.FIELD_BORDER);
        g.drawString(font, "⌕", fx + 6, fy + 4, Theme.TEXT_MUTED, false);

        int bx = winX + 12, by = winY + 38;
        button(g, mouseX, mouseY, bx, by, 64, 15, "+ Gestein", Style.SECONDARY, () -> selectPreset(STONE_PRESET));
        button(g, mouseX, mouseY, bx + 68, by, 82, 15, onlyInventory ? "✔ Nur Inventar" : "Nur Inventar",
                onlyInventory ? Style.PRIMARY : Style.SECONDARY, () -> {
                    onlyInventory = !onlyInventory;
                    refilter();
                });
        button(g, mouseX, mouseY, bx + 154, by, 76, 15, onlySelected ? "✔ Nur Auswahl" : "Nur Auswahl",
                onlySelected ? Style.PRIMARY : Style.SECONDARY, () -> {
                    onlySelected = !onlySelected;
                    refilter();
                });
        button(g, mouseX, mouseY, bx + 234, by, 50, 15, "Leeren", Style.DANGER, () -> {
            selected.clear();
            refilter();
        });

        int gridX = winX + 12, gridY = winY + 60;
        int gridW = winW - 30, gridH = winH - 60 - 34;
        int columns = Math.max(1, gridW / SLOT);
        int rows = (filtered.size() + columns - 1) / columns;
        int contentHeight = rows * SLOT;
        scroll.setMax(contentHeight - gridH);
        int offset = scroll.update(frameDelta);

        Item hovered = null;
        hoveredItem = null;
        pushClip(g, gridX, gridY, columns * SLOT, gridH);
        int firstRow = Math.max(0, offset / SLOT);
        int lastRow = Math.min(rows - 1, (offset + gridH) / SLOT + 1);
        for (int row = firstRow; row <= lastRow; row++) {
            for (int col = 0; col < columns; col++) {
                int index = row * columns + col;
                if (index >= filtered.size()) break;
                Item item = filtered.get(index);
                int x = gridX + col * SLOT, y = gridY + row * SLOT - offset;
                boolean isSelected = selected.contains(item);
                boolean hover = hovered(mouseX, mouseY, x, y, SLOT - 2, SLOT - 2);
                if (isSelected) {
                    UiRender.panel(g, x, y, SLOT - 2, SLOT - 2, 3, Theme.ACCENT_SOFT, Theme.ACCENT);
                } else {
                    UiRender.roundRect(g, x, y, SLOT - 2, SLOT - 2, 3, hover ? Theme.CARD_HOVER : Theme.CARD);
                }
                g.renderItem(icons.get(item), x + 2, y + 2);
                int amount = amount(item);
                if (isSelected && amount > 0) g.renderItemDecorations(font, icons.get(item), x + 2, y + 2, compact(amount));
                if (hover) {
                    hovered = item;
                    hoveredItem = item;
                }
                addHit(x, y, SLOT - 2, SLOT - 2, (mx, my, button) -> {
                    if (button == 1 && amounts != null) {
                        // Rechtsklick: auswählen bzw. Menge durch die Vorgaben schalten.
                        selected.add(item);
                        setAmount(item, nextPreset(amount(item)));
                        return;
                    }
                    if (!selected.remove(item)) selected.add(item);
                    if (onlySelected) refilter();
                });
            }
        }
        popClip(g);
        scrollbar(g, scroll, winX + winW - 14, gridY, gridH, contentHeight);

        if (filtered.isEmpty()) {
            String empty = onlySelected ? "Noch nichts ausgewählt" : onlyInventory ? "Nichts davon im Inventar" : "Kein Item gefunden";
            g.drawString(font, empty, winX + (winW - font.width(empty)) / 2, gridY + 30, Theme.TEXT_MUTED, false);
        }
        if (hovered != null) {
            String tip = names.get(hovered);
            if (selected.contains(hovered)) {
                int amount = amount(hovered);
                tip += amounts == null || amount == 0 ? "  ✔ wird abgeworfen" : "  ✔ " + amount + " behalten, Rest abwerfen";
            }
            g.setTooltipForNextFrame(font, Component.literal(tip), mouseX, mouseY);
        }

        int footerY = winY + winH - 26;
        g.fill(winX + 1, footerY - 4, winX + winW - 1, footerY - 3, Theme.DIVIDER);
        String hint = amounts == null ? "Klick: an/aus · Wertvolles und das Item in der Hand bleiben geschützt"
                : "Klick: an/aus · Rechtsklick oder Mausrad: Menge behalten (Shift ×16, Strg ×64)";
        UiRender.scaledText(g, font, UiRender.ellipsize(font, hint, (int) ((winW - 200) / 0.75F)),
                winX + 14, footerY + 6, Theme.TEXT_MUTED, 0.75F);
        button(g, mouseX, mouseY, winX + winW - 176, footerY, 78, 18, "Abbrechen", Style.SECONDARY, this::onClose);
        button(g, mouseX, mouseY, winX + winW - 92, footerY, 80, 18, "Übernehmen", Style.PRIMARY, () -> {
            Map<Item, Integer> result = new HashMap<>();
            if (amounts != null) {
                for (Item item : selected) if (amount(item) > 0) result.put(item, amount(item));
            }
            onSave.accept(Set.copyOf(selected), result);
            Notifications.success(heading, selected.size() + " Items ausgewählt");
            onClose();
        });
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // Mausrad über einem ausgewählten Item ändert die Menge statt zu scrollen.
        if (amounts != null && hoveredItem != null && selected.contains(hoveredItem) && scrollY != 0) {
            int step = hasControlDown() ? 64 : hasShiftDown() ? 16 : 1;
            setAmount(hoveredItem, amount(hoveredItem) + (scrollY > 0 ? step : -step));
            return true;
        }
        scroll.scroll(scrollY);
        return true;
    }

    private int amount(Item item) {
        return amounts == null ? 0 : amounts.getOrDefault(item, 0);
    }

    private void setAmount(Item item, int value) {
        if (amounts == null) return;
        int clamped = Math.max(0, Math.min(MAX_AMOUNT, value));
        if (clamped == 0) amounts.remove(item); else amounts.put(item, clamped);
    }

    private static int nextPreset(int current) {
        for (int preset : AMOUNT_PRESETS) if (preset > current) return preset;
        return 0;
    }

    /** Kurz für die Anzeige auf dem Item, z.B. 1.2k. */
    private static String compact(int amount) {
        return amount >= 1000 ? String.format(Locale.ROOT, "%.1fk", amount / 1000.0) : String.valueOf(amount);
    }
}
