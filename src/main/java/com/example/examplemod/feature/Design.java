package com.example.examplemod.feature;

import com.example.examplemod.gui.Theme;
import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.Setting;
import com.google.gson.JsonObject;

/** Akzentfarbe und Stil der Menüs und des HUDs. Wirkt immer, der Schalter hat keine Bedeutung. */
public class Design extends Module {
    private final Setting.Mode accent;
    private final Setting.Mode style;
    private final Setting.Slider menuOpacity;
    private final Setting.Slider backgroundDim;

    public Design() {
        super("design", "Design", "Akzentfarbe und Stil der Menüs", Category.CLIENT);
        accent = add(new Setting.Mode("Akzentfarbe", "Farbe für Schalter, Buttons und Hervorhebungen",
                Theme.ACCENTS[0], Theme.ACCENTS).onChange(this::apply));
        style = add(new Setting.Mode("Stil", "Dunkel, Mitternacht (blau) oder Hell",
                Theme.STYLES[0], Theme.STYLES).onChange(this::apply));
        menuOpacity = add(new Setting.Slider("Menü-Deckkraft", "Niedriger = transparent; Texte und Suchfelder bleiben gut lesbar",
                95, 20, 100, 5, "%").onChange(this::apply));
        backgroundDim = add(new Setting.Slider("Hintergrund abdunkeln", "0 % lässt die Spielwelt hinter dem Menü unverändert hell",
                60, 0, 80, 5, "%").onChange(this::apply));
        apply();
    }

    private void apply() {
        Theme.apply(accent.get(), style.get());
        Theme.applyTransparency(menuOpacity.getInt(), backgroundDim.getInt());
    }

    @Override
    public boolean hasToggle() { return false; }

    @Override
    public boolean showInModuleList() { return false; }

    @Override
    public void loadExtra(JsonObject json) { apply(); }
}
