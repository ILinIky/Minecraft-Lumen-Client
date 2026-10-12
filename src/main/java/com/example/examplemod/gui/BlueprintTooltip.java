package com.example.examplemod.gui;

import com.example.examplemod.blueprint.Blueprint;
import com.example.examplemod.blueprint.BlueprintPlan;
import com.example.examplemod.feature.Blueprints;
import com.example.examplemod.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Kleiner Hinweis neben dem Fadenkreuz, wenn ein noch nicht gebauter Bauplan-Block anvisiert wird:
 * Name, Status, Ausrichtung und wie viele davon im Inventar sind.
 */
public final class BlueprintTooltip {
    private static final int PADDING = 5;
    private static final int LINE = 10;

    /** Eigenschaften, die beim Bauen wichtig sind, mit deutscher Bezeichnung. */
    private static final Map<String, String> PROPERTY_NAMES = Map.ofEntries(
            Map.entry("facing", "Ausrichtung"), Map.entry("horizontal_facing", "Ausrichtung"),
            Map.entry("axis", "Achse"), Map.entry("half", "Hälfte"), Map.entry("type", "Form"),
            Map.entry("hinge", "Scharnier"), Map.entry("face", "Befestigung"), Map.entry("attachment", "Befestigung"),
            Map.entry("rotation", "Drehung"), Map.entry("waterlogged", "Unter Wasser"), Map.entry("orientation", "Ausrichtung"),
            Map.entry("hanging", "Hängend"), Map.entry("layers", "Schichten"), Map.entry("candles", "Kerzen"),
            Map.entry("pickles", "Anzahl"), Map.entry("delay", "Verzögerung"), Map.entry("mode", "Modus"));

    private static final Map<String, String> VALUE_NAMES = Map.ofEntries(
            Map.entry("north", "Norden"), Map.entry("south", "Süden"), Map.entry("east", "Osten"), Map.entry("west", "Westen"),
            Map.entry("up", "oben"), Map.entry("down", "unten"), Map.entry("top", "oben"), Map.entry("bottom", "unten"),
            Map.entry("upper", "oben"), Map.entry("lower", "unten"), Map.entry("double", "doppelt"),
            Map.entry("left", "links"), Map.entry("right", "rechts"), Map.entry("floor", "Boden"), Map.entry("wall", "Wand"),
            Map.entry("ceiling", "Decke"), Map.entry("single_wall", "Wand"), Map.entry("double_wall", "zwei Wände"),
            Map.entry("x", "X (Ost-West)"), Map.entry("y", "Y (senkrecht)"), Map.entry("z", "Z (Nord-Süd)"),
            Map.entry("true", "ja"), Map.entry("false", "nein"), Map.entry("compare", "Vergleichen"), Map.entry("subtract", "Subtrahieren"));

    private record Line(String text, int color) {}

    private BlueprintTooltip() {}

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        Blueprints feature = ModuleManager.BLUEPRINTS;
        if (feature == null || mc.player == null || mc.level == null || mc.screen != null || mc.options.hideGui) return;
        Blueprints.Hover hover = feature.hover();
        BlueprintPlan plan = feature.plan();
        if (hover == null || plan == null || hover.index() >= plan.blueprint.cells().size()) return;

        Blueprint.Cell cell = plan.blueprint.cells().get(hover.index());
        BlockState expected = plan.state(cell);
        List<Line> lines = new ArrayList<>();
        ItemStack icon = ItemStack.EMPTY;
        String title;
        if (expected == null) {
            title = plan.blueprint.palette().get(cell.state()).name();
        } else {
            title = expected.getBlock().getName().getString();
            Item item = BlueprintPlan.materialItem(expected);
            if (item != Items.AIR) icon = new ItemStack(item);
        }

        BlockState actual = mc.level.getBlockState(hover.pos());
        switch (hover.status()) {
            case MISSING -> lines.add(new Line("Noch nicht gebaut", hover.status().color));
            case WRONG_BLOCK -> lines.add(new Line("Falscher Block: " + actual.getBlock().getName().getString(), hover.status().color));
            case WRONG_STATE -> lines.add(new Line("Falsche Ausrichtung oder Form", hover.status().color));
            case UNSUPPORTED -> lines.add(new Line("Unbekannt in dieser Version", hover.status().color));
            default -> { }
        }
        if (expected != null) {
            boolean compare = hover.status() == BlueprintPlan.Status.WRONG_STATE && actual.getBlock() == expected.getBlock();
            for (Property<?> property : expected.getProperties()) {
                String label = PROPERTY_NAMES.get(property.getName());
                if (label == null) continue;
                String want = value(expected, property);
                if (compare) {
                    String have = value(actual, property);
                    if (!have.equals(want)) lines.add(new Line(label + ": " + want + "  (ist " + have + ")", Theme.WARNING));
                } else if (lines.size() < 4) {
                    lines.add(new Line(label + ": " + want, Theme.TEXT_DIM));
                }
            }
            BlueprintPlan.Material material = plan.material(cell);
            if (material != null && material.item != Items.AIR) {
                int inventory = 0;
                var slots = mc.player.getInventory();
                for (int i = 0; i < slots.getContainerSize(); i++) {
                    if (slots.getItem(i).is(material.item)) inventory += slots.getItem(i).getCount();
                }
                lines.add(new Line(inventory > 0 ? "Im Inventar: " + inventory : "Nicht im Inventar",
                        inventory > 0 ? Theme.SUCCESS : Theme.DANGER));
                lines.add(new Line("Im Bauplan noch offen: " + material.remaining(), Theme.TEXT_MUTED));
            }
        }

        Font font = mc.font;
        int textX = icon.isEmpty() ? PADDING + 4 : PADDING + 22;
        int width = font.width(title);
        for (Line line : lines) width = Math.max(width, font.width(line.text));
        int w = textX + width + PADDING + 2;
        int h = PADDING * 2 + 10 + lines.size() * LINE;
        int x = g.guiWidth() / 2 + 12;
        int y = g.guiHeight() / 2 + 8;
        if (x + w > g.guiWidth() - 4) x = g.guiWidth() / 2 - 12 - w;
        y = Math.min(y, g.guiHeight() - h - 4);

        UiRender.roundRect(g, x, y, w, h, 4, Theme.TOAST_BG);
        UiRender.roundRect(g, x, y, 2, h, 1, hover.status().color);
        if (!icon.isEmpty()) g.renderItem(icon, x + PADDING + 1, y + PADDING - 1);
        g.drawString(font, title, x + textX, y + PADDING + 1, Theme.TEXT, false);
        int ly = y + PADDING + 12;
        for (Line line : lines) {
            g.drawString(font, line.text, x + textX, ly, line.color, false);
            ly += LINE;
        }
    }

    private static <T extends Comparable<T>> String value(BlockState state, Property<T> property) {
        if (!state.hasProperty(property)) return "-";
        String raw = property.getName(state.getValue(property));
        if (property.getName().equals("rotation")) return raw + "/16";
        return VALUE_NAMES.getOrDefault(raw, raw);
    }
}
