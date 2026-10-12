package com.example.examplemod.feature;

import com.example.examplemod.gui.hud.HudEditorScreen;
import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.Setting;
import com.example.examplemod.render.MinimapData;

/**
 * Kleine Karte von oben mit Wegpunkten, Todespunkten, Kompass-Ziel und Spielern.
 * Gezeichnet als verschiebbares HUD-Element ({@link com.example.examplemod.gui.hud.MinimapElement}).
 */
public class Minimap extends Module {
    public final Setting.Slider size;
    public final Setting.Slider range;
    public final Setting.Bool rotate;
    public final Setting.Mode caves;
    public final Setting.Bool waypoints;
    public final Setting.Bool players;
    public final Setting.Bool mobs;
    public final Setting.Bool coordinates;
    private final MinimapData data = new MinimapData();

    public Minimap() {
        super("minimap", "Minimap", "Karte von oben mit Wegpunkten, Todespunkten und Spielern", Category.WORLD);
        enabledByDefault();
        size = add(new Setting.Slider("Größe", "Kantenlänge der Karte auf dem Bildschirm", 110, 64, 200, 2, "px"));
        range = add(new Setting.Slider("Sichtweite", "Blöcke vom Spieler bis zum Kartenrand", 64, 16, 160, 8, "Bl."));
        rotate = add(new Setting.Bool("Mitdrehen", "Blickrichtung zeigt immer nach oben", false));
        caves = add(new Setting.Mode("Höhlenansicht", "Auto: unter der Erde und im Nether die Ebene des Spielers zeigen",
                "Auto", "Auto", "An", "Aus"));
        waypoints = add(new Setting.Bool("Wegpunkte", "Wegpunkte, Todespunkte und Ziel; außerhalb am Rand", true));
        players = add(new Setting.Bool("Spieler", "Andere Spieler als Punkte", true));
        mobs = add(new Setting.Bool("Mobs", "Monster rot, Tiere grün", false));
        coordinates = add(new Setting.Bool("Koordinaten", "Position unter der Karte", true));
        add(new Setting.Action("Position ändern", "Im HUD-Editor verschieben", () ->
                mc.setScreen(new HudEditorScreen(mc.screen))));
    }

    public MinimapData data() { return data; }

    public int radius() { return range.getInt(); }

    @Override
    public boolean showInModuleList() { return false; }

    @Override
    public void onTick() {
        data.tick(mc, radius(), rotate.get(), caves.get());
    }

    @Override
    protected void onDisable() { data.close(mc); }

    @Override
    public void onLeaveWorld() { data.close(mc); }
}
