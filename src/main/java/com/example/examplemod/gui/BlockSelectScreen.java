package com.example.examplemod.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Raster aller Blöcke mit Suche und Mehrfachauswahl (für X-Ray und Block-ESP). */
public class BlockSelectScreen extends ClientScreen {
    private static final int SLOT = 22;

    private final String heading;
    private final Consumer<Set<Block>> onSave;
    private final Set<Block> selected;
    private final List<Block> allBlocks = new ArrayList<>();
    private final Map<Block, ItemStack> icons = new HashMap<>();
    private final Map<Block, String> names = new HashMap<>();
    private final Scroll scroll = new Scroll();
    private List<Block> filtered = new ArrayList<>();
    private EditBox search;
    private String query = "";
    private boolean onlySelected;

    public BlockSelectScreen(Screen parent, String heading, Set<Block> current, Consumer<Set<Block>> onSave) {
        super(Component.literal(heading), parent);
        this.heading = heading;
        this.onSave = onSave;
        this.selected = new LinkedHashSet<>(current);
        for (Block block : BuiltInRegistries.BLOCK) {
            ItemStack icon = iconFor(block);
            if (icon.isEmpty()) continue;
            allBlocks.add(block);
            icons.put(block, icon);
            names.put(block, block.getName().getString());
        }
        refilter();
    }

    private static ItemStack iconFor(Block block) {
        if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) return ItemStack.EMPTY;
        if (block == Blocks.WATER) return new ItemStack(Items.WATER_BUCKET);
        if (block == Blocks.LAVA) return new ItemStack(Items.LAVA_BUCKET);
        if (block.asItem() == Items.AIR) return ItemStack.EMPTY;
        return new ItemStack(block.asItem());
    }

    @Override protected int preferredWidth() { return 420; }
    @Override protected int preferredHeight() { return 300; }

    @Override
    protected void init() {
        super.init();
        int fieldW = Math.min(150, winW / 3);
        search = borderlessField(winX + winW - fieldW - 2, winY + 11, fieldW - 18, "Block suchen...", 40);
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            refilter();
        });
        addRenderableWidget(search);
        setInitialFocus(search);
    }

    private void refilter() {
        String q = query.toLowerCase(Locale.ROOT).trim();
        filtered = new ArrayList<>();
        for (Block block : allBlocks) {
            if (onlySelected && !selected.contains(block)) continue;
            if (!q.isEmpty() && !names.get(block).toLowerCase(Locale.ROOT).contains(q)
                    && !BuiltInRegistries.BLOCK.getKey(block).getPath().contains(q)) continue;
            filtered.add(block);
        }
        scroll.reset();
    }

    private void selectWhere(java.util.function.Predicate<String> pathMatches) {
        for (Block block : allBlocks) {
            if (pathMatches.test(BuiltInRegistries.BLOCK.getKey(block).getPath())) selected.add(block);
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
        button(g, mouseX, mouseY, bx, by, 52, 15, "+ Erze", Style.SECONDARY,
                () -> selectWhere(path -> path.endsWith("_ore") || path.equals("ancient_debris")));
        button(g, mouseX, mouseY, bx + 56, by, 58, 15, "+ Lager", Style.SECONDARY,
                () -> selectWhere(path -> path.contains("chest") || path.equals("barrel") || path.endsWith("shulker_box")));
        button(g, mouseX, mouseY, bx + 118, by, 76, 15, onlySelected ? "✔ Nur Auswahl" : "Nur Auswahl",
                onlySelected ? Style.PRIMARY : Style.SECONDARY, () -> {
                    onlySelected = !onlySelected;
                    refilter();
                });
        button(g, mouseX, mouseY, bx + 198, by, 50, 15, "Leeren", Style.DANGER, () -> {
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

        Block hovered = null;
        pushClip(g, gridX, gridY, columns * SLOT, gridH);
        int firstRow = Math.max(0, offset / SLOT);
        int lastRow = Math.min(rows - 1, (offset + gridH) / SLOT + 1);
        for (int row = firstRow; row <= lastRow; row++) {
            for (int col = 0; col < columns; col++) {
                int index = row * columns + col;
                if (index >= filtered.size()) break;
                Block block = filtered.get(index);
                int x = gridX + col * SLOT, y = gridY + row * SLOT - offset;
                boolean isSelected = selected.contains(block);
                boolean hover = hovered(mouseX, mouseY, x, y, SLOT - 2, SLOT - 2);
                if (isSelected) {
                    UiRender.panel(g, x, y, SLOT - 2, SLOT - 2, 3, Theme.ACCENT_SOFT, Theme.ACCENT);
                } else {
                    UiRender.roundRect(g, x, y, SLOT - 2, SLOT - 2, 3, hover ? Theme.CARD_HOVER : Theme.CARD);
                }
                g.renderItem(icons.get(block), x + 2, y + 2);
                if (hover) hovered = block;
                addHit(x, y, SLOT - 2, SLOT - 2, (mx, my, button) -> {
                    if (button == 1) selected.clear();
                    if (!selected.remove(block) || button == 1) selected.add(block);
                    if (onlySelected) refilter();
                });
            }
        }
        popClip(g);
        scrollbar(g, scroll, winX + winW - 14, gridY, gridH, contentHeight);

        if (filtered.isEmpty()) {
            String empty = onlySelected ? "Noch nichts ausgewählt" : "Kein Block gefunden";
            g.drawString(font, empty, winX + (winW - font.width(empty)) / 2, gridY + 30, Theme.TEXT_MUTED, false);
        }
        if (hovered != null) {
            String tip = names.get(hovered) + (selected.contains(hovered) ? "  ✔" : "");
            g.setTooltipForNextFrame(font, Component.literal(tip), mouseX, mouseY);
        }

        int footerY = winY + winH - 26;
        g.fill(winX + 1, footerY - 4, winX + winW - 1, footerY - 3, Theme.DIVIDER);
        UiRender.scaledText(g, font, "Klick: an/aus · Rechtsklick: nur diesen Block", winX + 14, footerY + 6, Theme.TEXT_MUTED, 0.75F);
        button(g, mouseX, mouseY, winX + winW - 176, footerY, 78, 18, "Abbrechen", Style.SECONDARY, this::onClose);
        button(g, mouseX, mouseY, winX + winW - 92, footerY, 80, 18, "Übernehmen", Style.PRIMARY, () -> {
            onSave.accept(Set.copyOf(selected));
            Notifications.success(heading, selected.size() + " Blöcke ausgewählt");
            onClose();
        });
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll.scroll(scrollY);
        return true;
    }
}
