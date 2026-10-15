package com.example.examplemod.gui;

import com.example.examplemod.building.MaterialTargets;
import com.example.examplemod.module.ModuleManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class MaterialAmountScreen extends ClientScreen {
    private final Item item;
    private String value;
    private EditBox amount;
    public MaterialAmountScreen(Screen parent, Item item, int initial) {
        super(Component.literal("Sammelziel"), parent); this.item = item; value = String.valueOf(initial);
    }
    @Override protected boolean isModal() { return true; }
    @Override protected int preferredWidth() { return 320; }
    @Override protected int preferredHeight() { return 170; }
    @Override protected void init() {
        super.init(); amount = borderlessField(winX + 20, winY + 72, winW - 40, "Zielmenge", 7);
        amount.setFilter(s -> s.matches("[0-9]*")); amount.setValue(value); amount.setResponder(s -> value = s);
        addRenderableWidget(amount); setInitialFocus(amount);
    }
    @Override protected void renderContent(GuiGraphics g, int mx, int my, float dt) {
        drawTitle(g, "Sammelziel", null);
        g.drawString(font, UiRender.ellipsize(font, new ItemStack(item).getHoverName().getString(), winW - 30), winX + 15, winY + 44, Theme.TEXT, false);
        fieldBackground(g, amount, 6, 6);
        g.drawString(font, "1–2.000.000 Items · 0 entfernt den Eintrag", winX + 15, winY + 99, Theme.TEXT_MUTED, false);
        button(g, mx, my, winX + 12, winY + winH - 31, 90, 20, "Abbrechen", Style.SECONDARY, this::onClose);
        button(g, mx, my, winX + winW - 112, winY + winH - 31, 100, 20, "Übernehmen", Style.PRIMARY, () -> {
            try {
                int count = Integer.parseInt(value);
                if (count > MaterialTargets.MAX_AMOUNT) throw new NumberFormatException();
                ModuleManager.MATERIAL_LIST.followPlan.set(false);
                ModuleManager.MATERIAL_LIST.set(item, count); ModuleManager.saveNow(); onClose();
            } catch (NumberFormatException e) { Notifications.warning("Sammelziel", "Bitte eine Menge von 0 bis 2.000.000 eingeben"); }
        });
    }
}
