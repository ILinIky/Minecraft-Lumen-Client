package com.example.examplemod.feature;

import com.example.examplemod.gui.ContainerPreview;
import com.example.examplemod.gui.Theme;
import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.Setting;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Erweiterte Item-Tooltips: Shulker-Inhalt als Raster, Haltbarkeit als Zahl, Hunger und Sättigung. */
public class Tooltips extends Module {
    private static final int SHULKER_SLOTS = 27;
    /** Farbe einer ungefärbten Shulkerkiste. */
    private static final int SHULKER_DEFAULT = 0xFF976997;

    private final Setting.Bool containers;
    private final Setting.Bool durability;
    private final Setting.Bool food;

    public Tooltips() {
        super("tooltips", "Tooltips", "Shulker-Vorschau, Haltbarkeit und Sättigung in Item-Tooltips", Category.RENDER);
        enabledByDefault();
        containers = add(new Setting.Bool("Shulker-Vorschau", "Inhalt als Raster statt als Textliste", true));
        durability = add(new Setting.Bool("Haltbarkeit", "Verbleibende Haltbarkeit als Zahl", true));
        food = add(new Setting.Bool("Sättigung", "Hunger- und Sättigungswerte bei Essen", true));
    }

    @Override
    public boolean showInModuleList() { return false; }

    /** Textzeilen ergänzen bzw. die Vanilla-Inhaltsliste entfernen, wenn das Raster sie ersetzt. */
    public void addLines(ItemTooltipEvent event) {
        if (!isEnabled()) return;
        ItemStack stack = event.getItemStack();
        List<Component> lines = event.getToolTip();

        if (containers.get() && hasContents(stack)) {
            lines.removeIf(line -> line.getContents() instanceof TranslatableContents contents
                    && (contents.getKey().equals("item.container.item_count")
                    || contents.getKey().equals("item.container.more_items")));
        }

        // Mit erweiterten Tooltips (F3+H) zeigt Vanilla die Haltbarkeit bereits an.
        if (durability.get() && stack.isDamageableItem() && !event.getFlags().isAdvanced()) {
            int max = stack.getMaxDamage();
            int left = max - stack.getDamageValue();
            float ratio = left / (float) max;
            int color = ratio > 0.5F ? Theme.SUCCESS : ratio > 0.2F ? Theme.WARNING : Theme.DANGER;
            lines.add(Component.literal("Haltbarkeit: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(left + " / " + max).withColor(color & 0xFFFFFF))
                    .append(Component.literal(String.format(Locale.ROOT, "  (%d %%)", Math.round(ratio * 100)))
                            .withStyle(ChatFormatting.DARK_GRAY)));
        }

        FoodProperties properties = stack.get(DataComponents.FOOD);
        if (food.get() && properties != null) {
            lines.add(Component.literal("Hunger ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("+" + properties.nutrition()).withColor(Theme.WARNING & 0xFFFFFF))
                    .append(Component.literal("  ·  Sättigung ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(String.format(Locale.GERMANY, "+%.1f", properties.saturation()))
                            .withColor(0xF97316)));
        }
    }

    /** Fügt das Raster direkt unter dem Item-Namen ein. */
    public void addPreview(RenderTooltipEvent.GatherComponents event) {
        if (!isEnabled() || !containers.get()) return;
        ItemStack stack = event.getItemStack();
        if (!hasContents(stack)) return;
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        List<ItemStack> items = new ArrayList<>(contents.stream().toList());

        int color = SHULKER_DEFAULT;
        int columns;
        if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof ShulkerBoxBlock shulker) {
            DyeColor dye = shulker.getColor();
            if (dye != null) color = dye.getTextureDiffuseColor();
            while (items.size() < SHULKER_SLOTS) items.add(ItemStack.EMPTY);
            columns = 9;
        } else {
            color = Theme.ACCENT;
            columns = items.size() == 9 ? 3 : Math.max(1, Math.min(9, items.size()));
        }
        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        elements.add(Math.min(1, elements.size()), Either.right(new ContainerPreview(items, columns, color)));
    }

    private static boolean hasContents(ItemStack stack) {
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        return contents != null && contents.nonEmptyItems().iterator().hasNext();
    }
}
