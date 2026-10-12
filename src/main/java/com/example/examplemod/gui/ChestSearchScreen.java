package com.example.examplemod.gui;

import com.example.examplemod.chest.ChestRecord;
import com.example.examplemod.chest.ChestSearch;
import com.example.examplemod.chest.ChestStore;
import com.example.examplemod.feature.ChestMemory;
import com.example.examplemod.module.ModuleManager;
import com.example.examplemod.waypoint.Waypoint;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Suche im Kisten-Gedächtnis: je Sorte die Gesamtmenge und alle Fundorte; Klick führt hin. */
public final class ChestSearchScreen extends ClientScreen {
    private static final int GROUP_H = 22, HIT_H = 18, MAX_GROUPS = 300;

    private final ChestMemory feature = ModuleManager.CHEST_MEMORY;
    private final Scroll scroll = new Scroll();
    private final Map<String, ItemStack> icons = new HashMap<>();
    private EditBox search;
    private String query;
    private List<ChestSearch.Result> results = List.of();
    private long confirmClearUntil;
    private int lastSize = -1;

    public ChestSearchScreen(Screen parent, String query) {
        super(Component.literal("Kisten-Gedächtnis"), parent);
        this.query = query == null ? "" : query;
        refresh();
    }

    @Override protected int preferredWidth() { return 440; }
    @Override protected int preferredHeight() { return 310; }

    @Override
    protected void init() {
        super.init();
        int fieldW = Math.min(170, winW / 2 - 20);
        search = borderlessField(winX + winW - fieldW - 18, winY + 11, fieldW, "Item suchen …", 60);
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            refresh();
        });
        addRenderableWidget(search);
        setInitialFocus(search);
    }

    /**
     * Sucht im Hintergrund, damit das Fenster auch bei sehr großen Lagern beim Tippen flüssig bleibt.
     * Gespeicherte Einträge ändern sich nach dem Ablegen nicht mehr, eine Kopie der Liste genügt.
     * Nur das Ergebnis der neuesten Anfrage wird übernommen.
     */
    private void refresh() {
        int request = ++searchGeneration;
        List<ChestRecord> snapshot = new java.util.ArrayList<>(ChestStore.all());
        String text = query;
        boolean inside = feature.shulkers.get();
        SEARCHER.execute(() -> {
            List<ChestSearch.Result> found = ChestSearch.search(snapshot, text, inside);
            net.minecraft.client.Minecraft.getInstance().execute(() -> {
                if (request != searchGeneration) return;
                appliedGeneration = request;
                results = found;
                scroll.reset();
            });
        });
    }

    private static final java.util.concurrent.ExecutorService SEARCHER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Lumen-Kisten-Suche");
        thread.setDaemon(true);
        return thread;
    });
    private int searchGeneration, appliedGeneration;

    @Override
    protected void renderContent(GuiGraphics g, int mx, int my, float partialTick) {
        drawTitle(g, "Kisten-Gedächtnis", null);
        fieldBackground(g, search, 6, 4);

        int x = winX + 12, top = winY + 38, w = winW - 32, h = Math.max(0, winH - 74);
        int y = top - scroll.update(frameDelta), start = y;
        pushClip(g, x, top, w, h);
        if (ChestStore.size() == 0) {
            y += paragraph(g, "Noch keine Behälter gemerkt. Öffne einfach Truhen, Fässer oder Shulker-Kisten – "
                    + "Lumen merkt sich ihren Inhalt automatisch.", x, y, w, Theme.TEXT_DIM);
        } else if (results.isEmpty() && appliedGeneration != searchGeneration) {
            y += paragraph(g, "Suche …", x, y, w, Theme.TEXT_MUTED);
        } else if (results.isEmpty()) {
            y += paragraph(g, "Nichts gefunden für „" + query + "“.", x, y, w, Theme.TEXT_MUTED);
        }
        int shown = 0;
        for (ChestSearch.Result result : results) {
            if (shown++ >= MAX_GROUPS) {
                y += paragraph(g, "… weitere Treffer: Suche genauer eingrenzen.", x, y, w, Theme.TEXT_MUTED);
                break;
            }
            int groupH = GROUP_H + result.hits().size() * HIT_H + 4;
            if (y + groupH >= top && y <= top + h) renderGroup(g, mx, my, x, y, w, result);
            y += groupH + 4;
        }
        popClip(g);
        scroll.setMax(y - start - h);
        scrollbar(g, scroll, winX + winW - 14, top, h, y - start);

        int footer = winY + winH - 27;
        g.fill(winX + 1, footer - 5, winX + winW - 1, footer - 4, Theme.DIVIDER);
        button(g, mx, my, winX + 12, footer, 62, 18, "Zurück", Style.SECONDARY, this::onClose);
        String status = ChestStore.size() + " Behälter gemerkt · Klick auf einen Fundort führt hin";
        g.drawString(font, UiRender.ellipsize(font, status, winW - 210), winX + 84, footer + 5, Theme.TEXT_MUTED, false);
        boolean confirm = System.currentTimeMillis() < confirmClearUntil;
        button(g, mx, my, winX + winW - 122, footer, 110, 18, confirm ? "Wirklich löschen?" : "Alles vergessen",
                Style.DANGER, () -> {
                    if (System.currentTimeMillis() < confirmClearUntil) {
                        int count = ChestStore.clear();
                        confirmClearUntil = 0;
                        refresh();
                        Notifications.info("Kisten-Gedächtnis", count + " Behälter vergessen");
                    } else {
                        confirmClearUntil = System.currentTimeMillis() + 3000;
                    }
                });
    }

    private void renderGroup(GuiGraphics g, int mx, int my, int x, int y, int w, ChestSearch.Result result) {
        int groupH = GROUP_H + result.hits().size() * HIT_H + 4;
        UiRender.roundRect(g, x, y, w, groupH, 4, Theme.CARD);
        g.renderItem(icon(result.id()), x + 4, y + 3);
        g.drawString(font, UiRender.ellipsize(font, result.name(), w - 130), x + 26, y + 7, Theme.TEXT, false);
        String total = result.total() + " Stück · " + result.hits().size() + " Behälter";
        g.drawString(font, total, x + w - 8 - font.width(total), y + 7, Theme.ACCENT_2, false);

        int rowY = y + GROUP_H;
        String dimension = mc().level == null ? "" : mc().level.dimension().location().toString();
        for (ChestSearch.Hit hit : result.hits()) {
            ChestRecord chest = hit.chest();
            boolean hover = hovered(mx, my, x + 4, rowY, w - 26, HIT_H - 2);
            if (hover) UiRender.roundRect(g, x + 4, rowY, w - 26, HIT_H - 2, 3, Theme.CARD_HOVER);
            ItemStack blockIcon = blockIcon(chest.block);
            g.pose().pushMatrix();
            g.pose().translate(x + 10, rowY + 1);
            g.pose().scale(0.75F, 0.75F);
            g.renderItem(blockIcon, 0, 0);
            g.pose().popMatrix();

            String place = chest.enderChest() ? "Endertruhe · überall erreichbar"
                    : blockName(chest.block) + " · " + chest.x + " / " + chest.y + " / " + chest.z
                    + (dimension.equals(chest.dimension) ? " · " + distance(chest) : " · " + Waypoint.dimensionName(chest.dimension));
            String amount = hit.count() + "×" + (hit.inShulker() ? " (Shulker)" : "");
            String age = age(chest.seen);
            int ageW = font.width(age), amountW = font.width(amount);
            g.drawString(font, amount, x + 28, rowY + 4, Theme.TEXT, false);
            g.drawString(font, UiRender.ellipsize(font, place, w - 70 - amountW - ageW), x + 34 + amountW, rowY + 4, Theme.TEXT_DIM, false);
            g.drawString(font, age, x + w - 28 - ageW, rowY + 4, Theme.TEXT_MUTED, false);
            addHit(x + 4, rowY, w - 26, HIT_H - 2, (a, b, c) -> navigate(chest, hit.count() + "× " + result.name()));
            if (hover) {
                g.setTooltipForNextFrame(font, Component.literal(chest.enderChest() ? "Endertruhe" : "Hinführen und hervorheben"), mx, my);
            }

            // Eintrag vergessen
            int fx = x + w - 20;
            boolean fHover = hovered(mx, my, fx, rowY, 14, HIT_H - 2);
            g.drawString(font, "✕", fx + 3, rowY + 4, fHover ? Theme.DANGER : Theme.TEXT_MUTED, false);
            addHit(fx, rowY, 14, HIT_H - 2, (a, b, c) -> {
                ChestStore.remove(chest);
                refresh();
                Notifications.info("Kisten-Gedächtnis", "Eintrag vergessen");
            });
            if (fHover) g.setTooltipForNextFrame(font, Component.literal("Diesen Behälter vergessen"), mx, my);
            rowY += HIT_H;
        }
    }

    private void navigate(ChestRecord chest, String label) {
        if (chest.enderChest()) {
            Notifications.info("Kisten-Gedächtnis", "In deiner Endertruhe – von jeder Endertruhe erreichbar");
            return;
        }
        String dimension = mc().level == null ? "" : mc().level.dimension().location().toString();
        if (!dimension.equals(chest.dimension)) {
            Notifications.warning("Kisten-Gedächtnis", "Liegt im " + Waypoint.dimensionName(chest.dimension));
            return;
        }
        feature.highlight(chest, label);
        Notifications.success("Kisten-Gedächtnis", blockName(chest.block) + " markiert · Kompass zeigt hin");
        closeAll();
    }

    private net.minecraft.client.Minecraft mc() { return minecraft; }

    private String distance(ChestRecord chest) {
        if (minecraft.player == null) return "";
        double d = Math.sqrt(minecraft.player.distanceToSqr(chest.x + 0.5, chest.y + 0.5, chest.z + 0.5));
        return d >= 1000 ? String.format(Locale.ROOT, "%.1fk m", d / 1000) : String.format(Locale.ROOT, "%.0f m", d);
    }

    private static String age(long seen) {
        long minutes = Math.max(0, (System.currentTimeMillis() - seen) / 60000);
        if (minutes < 1) return "gerade eben";
        if (minutes < 60) return "vor " + minutes + " Min.";
        long hours = minutes / 60;
        if (hours < 24) return "vor " + hours + " Std.";
        long days = hours / 24;
        return days == 1 ? "vor 1 Tag" : "vor " + days + " Tagen";
    }

    private ItemStack icon(String id) {
        return icons.computeIfAbsent(id, key -> {
            ResourceLocation location = ResourceLocation.tryParse(key);
            return location == null ? new ItemStack(Items.BARRIER)
                    : BuiltInRegistries.ITEM.getOptional(location).map(ItemStack::new).orElse(new ItemStack(Items.BARRIER));
        });
    }

    private ItemStack blockIcon(String blockId) {
        return icons.computeIfAbsent("block|" + blockId, key -> {
            ResourceLocation location = ResourceLocation.tryParse(blockId);
            Block block = location == null ? null : BuiltInRegistries.BLOCK.getOptional(location).orElse(null);
            return block == null || block.asItem() == Items.AIR ? new ItemStack(Items.CHEST) : new ItemStack(block.asItem());
        });
    }

    private static String blockName(String blockId) {
        ResourceLocation location = ResourceLocation.tryParse(blockId);
        if (location == null) return blockId;
        return BuiltInRegistries.BLOCK.getOptional(location).map(block -> block.getName().getString()).orElse(blockId);
    }

    private int paragraph(GuiGraphics g, String text, int x, int y, int width, int color) {
        var lines = font.split(Component.literal(text), width);
        for (var line : lines) {
            g.drawString(font, line, x, y, color, false);
            y += 11;
        }
        return lines.size() * 11 + 6;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll.scroll(sy);
        return true;
    }

    /** Liste neu aufbauen, wenn sich das Gedächtnis ändert (z.B. abgebaute Kiste aufgeräumt). */
    @Override
    public void tick() {
        super.tick();
        if (ChestStore.size() != lastSize) {
            lastSize = ChestStore.size();
            refresh();
        }
    }
}
