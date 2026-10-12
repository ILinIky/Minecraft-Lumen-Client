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

    public Design() {
        super("design", "Design", "Akzentfarbe und Stil der Menüs", Category.CLIENT);
        accent = add(new Setting.Mode("Akzentfarbe", "Farbe für Schalter, Buttons und Hervorhebungen",
                Theme.ACCENTS[0], Theme.ACCENTS).onChange(this::apply));
        style = add(new Setting.Mode("Stil", "Dunkel, Mitternacht (blau) oder Hell",
                Theme.STYLES[0], Theme.STYLES).onChange(this::apply));
    }

    private void apply() {
        Theme.apply(accent.get(), style.get());
    }

    @Override
    public boolean hasToggle() { return false; }

    @Override
    public boolean showInModuleList() { return false; }

    @Override
    public void loadExtra(JsonObject json) { apply(); }
}
