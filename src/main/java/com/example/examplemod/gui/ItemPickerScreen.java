package com.example.examplemod.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;
import java.util.function.*;

/** Single selection picker shared by the shopping list and blueprint replacements. */
public final class ItemPickerScreen extends ClientScreen {
    private record Entry(Item item, ItemStack icon, String name, String search) {}
    private final List<Entry> entries = new ArrayList<>();
    private List<Entry> filtered = List.of();
    private final Consumer<Item> select;
    private final String heading;
    private final Scroll scroll = new Scroll();
    private EditBox search;
    private String query = "";
    public ItemPickerScreen(Screen parent, String heading, Predicate<Item> filter, Consumer<Item> select) {
        super(Component.literal(heading), parent); this.heading = heading; this.select = select;
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR || !filter.test(item)) continue;
            ItemStack stack = new ItemStack(item);
            String name = stack.getHoverName().getString();
            entries.add(new Entry(item, stack, name, (name + " " + BuiltInRegistries.ITEM.getKey(item)).toLowerCase(Locale.ROOT)));
        }
        entries.sort(Comparator.comparing(Entry::name)); filtered = List.copyOf(entries);
    }
    @Override protected int preferredWidth() { return 450; }
    @Override protected int preferredHeight() { return 310; }
    @Override protected void init() {
        super.init();
        search = borderlessField(winX + 18, winY + 43, winW - 36, "Item suchen …", 80);
        search.setValue(query);
        search.setResponder(value -> { query = value; filtered = entries.stream().filter(e -> e.search.contains(value.toLowerCase(Locale.ROOT))).toList(); scroll.reset(); });
        addRenderableWidget(search); setInitialFocus(search);
    }
    @Override protected void renderContent(GuiGraphics g, int mx, int my, float dt) {
        drawTitle(g, heading, null); fieldBackground(g, search, 6, 4);
        int top = winY + 66, h = Math.max(1, winH - 100), w = winW - 30;
        scroll.setMax(filtered.size() * 24 - h); int offset = scroll.update(frameDelta);
        pushClip(g, winX + 12, top, w, h);
        int first = Math.max(0, offset / 24), last = Math.min(filtered.size(), (offset + h) / 24 + 1);
        for (int i = first; i < last; i++) {
            Entry e = filtered.get(i); int y = top + i * 24 - offset;
            button(g, mx, my, winX + 12, y, w, 22, "", Style.SECONDARY, () -> { minecraft.setScreen(parent); select.accept(e.item); });
            g.renderItem(e.icon, winX + 16, y + 3);
            g.drawString(font, UiRender.ellipsize(font, e.name, w - 30), winX + 38, y + 7, Theme.TEXT, false);
        }
        if (filtered.isEmpty()) g.drawString(font, "Keine passenden Items", winX + 18, top + 12, Theme.TEXT_MUTED, false);
        popClip(g); scrollbar(g, scroll, winX + winW - 12, top, h, filtered.size() * 24);
        button(g, mx, my, winX + winW - 100, winY + winH - 26, 88, 18, "Zurück", Style.SECONDARY, this::onClose);
    }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) { scroll.scroll(sy); return true; }
}
