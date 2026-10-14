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
    private float barPercent;
    private BlueprintPlan shownPlan;
    private int shownLayer = -1, shownConfirmed;
    private boolean shownActive;
    private long nextCounterUpdate;
    public BlueprintLayerElement(Setting.Bool visible) { super("blueprint_layer", "Bauplan-Ebene", visible, 0.5F, 1, 0, 62); }
    @Override public void measure(Minecraft mc, boolean editing) {
        var feature = ModuleManager.BLUEPRINTS;
        var plan = feature.plan();
        if (!editing && (!feature.isEnabled() || plan == null || !plan.inDimension(mc.level)
                || plan.layers == BlueprintPlan.Layers.ALL || plan.focus != null)) { w = h = 0; return; }
        var progress = plan == null ? null : plan.displayedLayerProgress();
        percent = progress == null ? 87 : progress.percent();
        if (shownPlan != plan || shownLayer != (plan == null ? -1 : plan.layer)) {
            shownPlan = plan; shownLayer = plan == null ? -1 : plan.layer;
            barPercent = percent;
            nextCounterUpdate = 0;
        }
        label = plan == null ? "Ebene 3/12 – 87 %" : "Ebene " + (plan.layer + 1) + "/" + plan.blueprint.sizeY()
                + " – " + (progress.checked() ? percent + " %" : "…");
        detail = progress == null ? "Automatik an" : !progress.checked() ? "Wird geprüft …"
                : progress.unknown() > 0 ? progress.unknown() + " unbekannt / ungeladen"
                : feature.autoLayer.get() ? (progress.complete() ? "Ebene fertig" : "Automatik an") : "Manueller Wechsel";
        // Fixed widths prevent recentering whenever the number or working status changes.
        w = 240; h = 38;
        String key = com.example.examplemod.ClientKeybinds.BLUEPRINT_BUILD == null ? "G"
                : com.example.examplemod.ClientKeybinds.BLUEPRINT_BUILD.getTranslatedKeyMessage().getString();
        long now = net.minecraft.Util.getMillis();
        if (now >= nextCounterUpdate || shownActive != feature.lazyBuilder.active()) {
            shownConfirmed = feature.lazyBuilder.confirmed();
            shownActive = feature.lazyBuilder.active();
            nextCounterUpdate = now + 1000;
        }
        lazy = feature.lazyBuild.get() ? (feature.lazyBuilder.active() ? "Lazy an · " + key + ": Stopp · " + shownConfirmed + " bestätigt"
                : "Lazy aus · " + key + ": Start") : null;
        lazyDetail = feature.lazyBuilder.displayStatus();
        if (lazy != null) { w = 280; h += 26; }
    }
    @Override public void render(GuiGraphics g, Minecraft mc, boolean editing, float delta) {
        UiRender.roundRect(g, x, y, w, h, 5, Theme.HUD_BG);
        g.drawString(mc.font, UiRender.ellipsize(mc.font, label, w - 16), x + 8, y + 6, Theme.TEXT, false);
        UiRender.roundRect(g, x + 8, y + 19, w - 16, 4, 2, Theme.CARD);
        barPercent += (percent - barPercent) * (float) (1 - Math.exp(-Math.max(0, delta) * 12));
        if (Math.abs(percent - barPercent) < 0.05F) barPercent = percent;
        if (barPercent > 0) UiRender.gradientH(g, x + 8, y + 19, Math.round((w - 16) * barPercent / 100), 4, Theme.ACCENT, Theme.ACCENT_2);
        g.drawString(mc.font, UiRender.ellipsize(mc.font, detail, w - 16), x + 8, y + 27, Theme.TEXT_MUTED, false);
        if (lazy != null) {
            g.drawString(mc.font, UiRender.ellipsize(mc.font, lazy, w - 16), x + 8, y + 40, Theme.ACCENT_2, false);
            g.drawString(mc.font, UiRender.ellipsize(mc.font, lazyDetail, w - 16), x + 8, y + 52, Theme.TEXT_MUTED, false);
        }
    }
}
