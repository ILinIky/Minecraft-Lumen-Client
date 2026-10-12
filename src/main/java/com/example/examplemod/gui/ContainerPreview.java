package com.example.examplemod.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Inhalt einer Shulkerkiste (oder eines anderen Behälters) als Raster im Tooltip. */
public record ContainerPreview(List<ItemStack> items, int columns, int color) implements TooltipComponent {
    private static final int SLOT = 18;
    private static final int PADDING = 3;

    public int rows() { return Math.max(1, (items.size() + columns - 1) / columns); }

    /** Zeichnet die Vorschau; registriert in {@code ExampleModClient}. */
    public static final class Client implements ClientTooltipComponent {
        private final ContainerPreview preview;

        public Client(ContainerPreview preview) {
            this.preview = preview;
        }

        @Override
        public int getHeight(Font font) { return preview.rows() * SLOT + PADDING * 2 + 2; }

        @Override
        public int getWidth(Font font) { return preview.columns() * SLOT + PADDING * 2; }

        @Override
        public void renderImage(Font font, int x, int y, int width, int height, GuiGraphics g) {
            int w = getWidth(font), h = preview.rows() * SLOT + PADDING * 2;
            int tint = preview.color() | 0xFF000000;
            UiRender.roundRect(g, x, y, w, h, 3, Theme.lerp(0xF0101018, tint, 0.28F));
            UiRender.roundRect(g, x, y, w, 1, 0, Theme.alpha(tint, 0.9F));
            int slotColor = Theme.lerp(0xFF08080C, tint, 0.18F);
            for (int i = 0; i < preview.rows() * preview.columns(); i++) {
                int sx = x + PADDING + (i % preview.columns()) * SLOT;
                int sy = y + PADDING + (i / preview.columns()) * SLOT;
                g.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, slotColor);
                if (i >= preview.items().size()) continue;
                ItemStack stack = preview.items().get(i);
                if (stack.isEmpty()) continue;
                g.renderItem(stack, sx + 1, sy + 1);
                g.renderItemDecorations(font, stack, sx + 1, sy + 1);
            }
        }
    }
}
