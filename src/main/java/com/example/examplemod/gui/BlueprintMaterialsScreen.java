package com.example.examplemod.gui;

import com.example.examplemod.feature.Blueprints;
import com.example.examplemod.module.ModuleManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import java.util.List;

public final class BlueprintMaterialsScreen extends ClientScreen {
    private final Blueprints feature = ModuleManager.BLUEPRINTS;
    private final Scroll scroll = new Scroll();
    private final List<String> sources;
    public BlueprintMaterialsScreen(Screen parent) {
        super(Component.literal("Bauplan-Material ersetzen"), parent);
        sources = feature.sourceBlueprint() == null ? List.of() : java.util.stream.IntStream.range(0, feature.sourceBlueprint().palette().size())
                .filter(i -> feature.plan().paletteUsed(i))
                .mapToObj(i -> feature.sourceBlueprint().palette().get(i).name()).distinct().sorted().toList();
    }
    @Override protected int preferredWidth() { return 500; }
    @Override protected int preferredHeight() { return 320; }
    private String name(String id) {
        var key = ResourceLocation.tryParse(id);
        return key != null && BuiltInRegistries.BLOCK.containsKey(key) ? BuiltInRegistries.BLOCK.getValue(key).getName().getString() : id;
    }
    @Override protected void renderContent(GuiGraphics g, int mx, int my, float dt) {
        drawTitle(g, "Bauplan-Material ersetzen", null);
        int x = winX + 12, w = winW - 30;
        g.drawString(font, UiRender.ellipsize(font, "Block wählen → Ersatz wählen. Gemeinsame Eigenschaften bleiben erhalten.", w), x, winY + 40, Theme.TEXT_DIM, false);
        g.drawString(font, UiRender.ellipsize(font, "Pro Blockart; z.B. Bretter, Treppen und Stufen jeweils einzeln ersetzen.", w), x, winY + 53, Theme.TEXT_MUTED, false);
        int top = winY + 74, h = Math.max(1, winH - 111); scroll.setMax(sources.size() * 39 - h); int offset = scroll.update(frameDelta);
        pushClip(g, x, top, w, h);
        for (int i = Math.max(0, offset / 39); i < Math.min(sources.size(), (offset + h) / 39 + 1); i++) {
            String source = sources.get(i), target = feature.replacements().getOrDefault(source, source);
            int y = top + i * 39 - offset;
            button(g, mx, my, x, y, w - 76, 35, "", Style.SECONDARY,
                    () -> minecraft.setScreen(new ItemPickerScreen(this, "Ersatzblock wählen", item -> item instanceof BlockItem block
                            && feature.canReplace(source, BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString()),
                            item -> feature.replaceMaterial(source, BuiltInRegistries.BLOCK.getKey(((BlockItem)item).getBlock()).toString()))));
            g.drawString(font, UiRender.ellipsize(font, name(source), w - 90), x + 6, y + 5, Theme.TEXT, false);
            g.drawString(font, UiRender.ellipsize(font, "→ " + name(target), w - 90), x + 6, y + 20, source.equals(target) ? Theme.TEXT_MUTED : Theme.ACCENT_2, false);
            if (!source.equals(target)) button(g, mx, my, x + w - 72, y, 72, 35, "Original", Style.SECONDARY, () -> feature.replaceMaterial(source, source));
        }
        popClip(g); scrollbar(g, scroll, winX + winW - 12, top, h, sources.size() * 39);
        button(g, mx, my, x, winY + winH - 27, Math.min(200, w - 100), 19, "Alle Originalmaterialien", Style.SECONDARY, feature::resetMaterials);
        button(g, mx, my, winX + winW - 100, winY + winH - 27, 88, 19, "Fertig", Style.PRIMARY, this::onClose);
    }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) { scroll.scroll(sy); return true; }
}
