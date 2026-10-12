package com.example.examplemod.mixin;

import com.example.examplemod.gui.hud.HudRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Forge 1.21.7 bietet kein RenderGuiEvent mehr, daher direkt nach dem Vanilla-HUD zeichnen.
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "render", at = @At("TAIL"), remap = false)
    private void examplemod$renderHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        HudRenderer.render(graphics);
    }
}
