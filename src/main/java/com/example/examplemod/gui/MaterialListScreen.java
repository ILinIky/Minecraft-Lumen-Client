package com.example.examplemod.gui;

import com.example.examplemod.feature.MaterialList;
import com.example.examplemod.module.ModuleManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;

public final class MaterialListScreen extends ClientScreen {
    private final MaterialList feature = ModuleManager.MATERIAL_LIST;
    private final Scroll scroll = new Scroll();
    private int ticks;
    public MaterialListScreen(Screen parent) { super(Component.literal("Sammelliste"), parent); feature.refresh(); }
    @Override protected int preferredWidth() { return 510; }
    @Override protected int preferredHeight() { return 310; }
    @Override public void tick() { if (++ticks % 5 == 0) feature.refresh(); }
    @Override protected void renderContent(GuiGraphics g, int mx, int my, float dt) {
        drawTitle(g, "Sammelliste", "Inventar / Ziel"); int x = winX + 12, w = winW - 28;
        int third = (w - 8) / 3;
        button(g, mx, my, x, winY + 38, third, 20, "Item hinzufügen", Style.PRIMARY, () -> minecraft.setScreen(new ItemPickerScreen(this,
                "Material wählen", item -> true, item -> minecraft.setScreen(new MaterialAmountScreen(this, item, 64)))));
        button(g, mx, my, x + third + 4, winY + 38, third, 20, "Bauplan: " + (feature.followPlan.get() ? "Verknüpft" : "Aus"), Style.SECONDARY,
                () -> { feature.followPlan.toggle(); feature.refresh(); });
        button(g, mx, my, x + 2 * (third + 4), winY + 38, third, 20, "HUD: " + (feature.isEnabled() ? "An" : "Aus"), Style.SECONDARY, feature::toggle);
        String note = "Eigene Ziele bleiben gespeichert. Gleiche Items: größeres Ziel zählt.";
        g.drawString(font, UiRender.ellipsize(font, note, w), x, winY + 65, Theme.TEXT_MUTED, false);
        var plan = ModuleManager.BLUEPRINTS.plan();
        String state = !feature.followPlan.get() ? "Bauplan-Verknüpfung ist aus."
                : plan == null ? "Verknüpft: wartet auf einen Bauplan."
                : (!ModuleManager.BLUEPRINTS.isEnabled() || !plan.inDimension(minecraft.level)) ? "Bauplan: letzter Abgleich (pausiert / andere Dimension)."
                : "Bauplan: offener Bedarf; ungeprüfte Blöcke zählen mit.";
        g.drawString(font, UiRender.ellipsize(font, state, w), x, winY + 78, Theme.TEXT_DIM, false);
        int top = winY + 96, h = Math.max(1, winH - 130);
        var rows = feature.rows(); scroll.setMax(rows.size() * 36 - h); int offset = scroll.update(frameDelta);
        pushClip(g, x, top, w, h);
        for (int i = Math.max(0, offset / 36); i < Math.min(rows.size(), (offset + h) / 36 + 1); i++) {
            var row = rows.get(i); int y = top + i * 36 - offset;
            String id = BuiltInRegistries.ITEM.getKey(row.item()).toString();
            int own = feature.manual().getOrDefault(id, 0);
            button(g, mx, my, x, y, w, 33, "", Style.SECONDARY,
                    () -> minecraft.setScreen(new MaterialAmountScreen(this, row.item(), own > 0 ? own : row.target())));
            g.renderItem(new net.minecraft.world.item.ItemStack(row.item()), x + 5, y + 8);
            g.drawString(font, UiRender.ellipsize(font, row.name() + " · " + row.have() + "/" + row.target(), w - 34), x + 27, y + 5,
                    row.missing() == 0 ? Theme.ACCENT_2 : Theme.TEXT, false);
            g.drawString(font, UiRender.ellipsize(font, "Fehlt " + row.missing() + (own > 0 ? " · eigenes Ziel " + own : " · aus Bauplan") + " · Klick: bearbeiten", w - 34), x + 27, y + 19, Theme.TEXT_MUTED, false);
        }
        if (rows.isEmpty()) g.drawString(font, "Item hinzufügen oder einen Bauplan verknüpfen.", x + 6, top + 12, Theme.TEXT_MUTED, false);
        popClip(g); scrollbar(g, scroll, winX + winW - 12, top, h, rows.size() * 36);
        button(g, mx, my, winX + winW - 100, winY + winH - 26, 88, 18, "Fertig", Style.PRIMARY, this::onClose);
    }
    @Override public boolean mouseScrolled(double x, double y, double sx, double sy) { scroll.scroll(sy); return true; }
    @Override public void onClose() { ModuleManager.saveNow(); super.onClose(); }
}
