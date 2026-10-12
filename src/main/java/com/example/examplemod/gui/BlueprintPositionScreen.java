package com.example.examplemod.gui;

import com.example.examplemod.blueprint.BlueprintPlan;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

final class BlueprintPositionScreen extends ClientScreen {
    private final String[] values;
    private final EditBox[] fields = new EditBox[3];
    private final Consumer<BlockPos> apply;
    private String error;
    BlueprintPositionScreen(Screen parent, BlueprintPlan plan, Consumer<BlockPos> apply) {
        super(Component.literal("Bauplan-Ursprung"), parent);
        values = new String[]{"" + plan.origin().getX(), "" + plan.origin().getY(), "" + plan.origin().getZ()};
        this.apply = apply;
    }
    @Override protected int preferredWidth() { return 300; }
    @Override protected int preferredHeight() { return 142; }
    @Override protected boolean isModal() { return true; }
    @Override protected void init() {
        super.init();
        int fieldW = (winW - 56) / 3;
        for (int i = 0; i < 3; i++) {
            final int index = i;
            EditBox box = borderlessField(winX + 20 + i * (fieldW + 8), winY + 63, fieldW, "0", 10);
            box.setFilter(text -> text.matches("-?\\d*")); box.setValue(values[i]);
            box.setResponder(value -> { values[index] = value; error = null; });
            fields[i] = box;
            addRenderableWidget(box);
        }
        setInitialFocus(fields[0]);
    }
    @Override protected void renderContent(GuiGraphics g, int mx, int my, float delta) {
        drawTitle(g, "Bauplan-Ursprung", null);
        for (int i = 0; i < 3; i++) {
            g.drawString(font, new String[]{"X", "Y", "Z"}[i], fields[i].getX(), winY + 45, Theme.TEXT_MUTED, false);
            fieldBackground(g, fields[i], 6, 4);
        }
        if (error != null) g.drawString(font, error, winX + 16, winY + 88, Theme.DANGER, false);
        int y = winY + winH - 28;
        button(g, mx, my, winX + 14, y, 90, 18, "Abbrechen", Style.SECONDARY, this::onClose);
        button(g, mx, my, winX + winW - 116, y, 102, 18, "Übernehmen", Style.PRIMARY, this::save);
    }
    private void save() {
        try {
            BlockPos pos = new BlockPos(Integer.parseInt(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2]));
            apply.accept(pos); onClose();
        } catch (NumberFormatException failure) { error = "Bitte gültige ganze Zahlen eingeben."; }
    }
    @Override public boolean keyPressed(int key, int code, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) { save(); return true; }
        return super.keyPressed(key, code, modifiers);
    }
}
