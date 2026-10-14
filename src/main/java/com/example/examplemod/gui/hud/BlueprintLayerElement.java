package com.example.examplemod.gui.hud;

import com.example.examplemod.blueprint.BlueprintPlan;
import com.example.examplemod.gui.Theme;
import com.example.examplemod.gui.UiRender;
import com.example.examplemod.module.ModuleManager;
import com.example.examplemod.module.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public final class BlueprintLayerElement extends HudElement {
    private String label, detail, lazy, lazyDetail;
    private int percent;
    public BlueprintLayerElement(Setting.Bool visible) { super("blueprint_layer", "Bauplan-Ebene", visible, 0.5F, 1, 0, 62); }
    @Override public void measure(Minecraft mc, boolean editing) {
        var feature = ModuleManager.BLUEPRINTS;
        var plan = feature.plan();
        if (!editing && (!feature.isEnabled() || plan == null || !plan.inDimension(mc.level)
                || plan.layers == BlueprintPlan.Layers.ALL || plan.focus != null)) { w = h = 0; return; }
        var progress = plan == null ? null : plan.layerProgress();
        percent = progress == null ? 87 : progress.percent();
        label = plan == null ? "Ebene 3/12 – 87 %" : "Ebene " + (plan.layer + 1) + "/" + plan.blueprint.sizeY() + " – " + percent + " %";
        detail = progress == null ? "Automatik an" : !progress.checked() ? "Wird geprüft …"
                : progress.unknown() > 0 ? progress.unknown() + " unbekannt / ungeladen"
                : feature.autoLayer.get() ? (progress.complete() ? "Ebene fertig" : "Automatik an") : "Manueller Wechsel";
        w = Math.max(150, Math.max(mc.font.width(label), mc.font.width(detail)) + 16); h = 38;
        String key = com.example.examplemod.ClientKeybinds.BLUEPRINT_BUILD == null ? "G"
                : com.example.examplemod.ClientKeybinds.BLUEPRINT_BUILD.getTranslatedKeyMessage().getString();
        lazy = feature.lazyBuild.get() ? (feature.lazyBuilder.active() ? "Lazy an · " + key + ": Stopp · " + feature.lazyBuilder.confirmed() + " bestätigt"
                : "Lazy aus · " + key + ": Start") : null;
        lazyDetail = feature.lazyBuilder.status();
        if (lazy != null) { w = Math.max(w, Math.min(280, Math.max(mc.font.width(lazy), mc.font.width(lazyDetail)) + 16)); h += 26; }
    }
    @Override public void render(GuiGraphics g, Minecraft mc, boolean editing, float delta) {
        UiRender.roundRect(g, x, y, w, h, 5, Theme.HUD_BG);
        g.drawString(mc.font, label, x + 8, y + 6, Theme.TEXT, false);
        UiRender.roundRect(g, x + 8, y + 19, w - 16, 4, 2, Theme.CARD);
        if (percent > 0) UiRender.gradientH(g, x + 8, y + 19, (w - 16) * percent / 100, 4, Theme.ACCENT, Theme.ACCENT_2);
        g.drawString(mc.font, detail, x + 8, y + 27, Theme.TEXT_MUTED, false);
        if (lazy != null) {
            g.drawString(mc.font, UiRender.ellipsize(mc.font, lazy, w - 16), x + 8, y + 40, Theme.ACCENT_2, false);
            g.drawString(mc.font, UiRender.ellipsize(mc.font, lazyDetail, w - 16), x + 8, y + 52, Theme.TEXT_MUTED, false);
        }
    }
}
