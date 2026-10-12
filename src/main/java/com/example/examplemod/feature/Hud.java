package com.example.examplemod.feature;

import com.example.examplemod.gui.Notifications;
import com.example.examplemod.gui.hud.HudEditorScreen;
import com.example.examplemod.gui.hud.HudElement;
import com.example.examplemod.gui.hud.HudElements;
import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.ModuleManager;
import com.example.examplemod.module.Setting;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Bildschirmanzeige mit verschiebbaren Elementen (Logo, Koordinaten, Infos, Modulliste,
 * Kompass, Rüstung). Gezeichnet in {@link com.example.examplemod.gui.hud.HudRenderer}.
 */
public class Hud extends Module {
    private static final float WARN_RATIO = 0.1F;

    public final Setting.Bool watermark;
    public final Setting.Bool coordinates;
    public final Setting.Bool ping;
    public final Setting.Bool moduleList;
    public final Setting.Bool compass;
    public final Setting.Bool armor;
    public final Setting.Bool durabilityWarning;
    public final Setting.Bool materialList;
    public final Setting.Bool playerRadar;
    private final List<HudElement> elements;

    private final Map<EquipmentSlot, Float> lastRatio = new EnumMap<>(EquipmentSlot.class);

    public Hud() {
        super("hud", "HUD", "Logo, Module, Koordinaten, Kompass und mehr – frei verschiebbar", Category.CLIENT);
        enabledByDefault();
        add(new Setting.Action("HUD bearbeiten", "Elemente mit der Maus verschieben", () ->
                mc.setScreen(new HudEditorScreen(mc.screen))));
        // Jedes Element ein eigener Schalter.
        watermark = add(new Setting.Bool("Logo + FPS", "Client-Name und Bilder pro Sekunde, oben links", true));
        coordinates = add(new Setting.Bool("Koordinaten",
                "Position, Blickrichtung und die passende Stelle in der anderen Dimension (Nether ↔ Oberwelt, z.B. für Portale)", true));
        ping = add(new Setting.Bool("Ping", "Verbindung zum Server in Millisekunden", true));
        moduleList = add(new Setting.Bool("Modulliste", "Aktive Module, oben rechts", true));
        compass = add(new Setting.Bool("Kompass", "Pfeil zum gewählten Ziel-Wegpunkt", true));
        armor = add(new Setting.Bool("Rüstung", "Rüstung und Werkzeug mit Haltbarkeit", true));
        durabilityWarning = add(new Setting.Bool("Haltbarkeitswarnung", "Hinweis, wenn Rüstung oder Werkzeug fast kaputt ist", true));
        materialList = add(new Setting.Bool("Sammelliste", "Angeheftete Materialien und Fortschritt", true));
        playerRadar = add(new Setting.Bool("Spieler-Radar", "Ausgewählte Spieler im eingestellten Radius, standardmäßig rechts", true));
        add(new Setting.Action("Positionen zurücksetzen", "Alle Elemente an ihren Standardplatz", () -> {
            elements().forEach(HudElement::reset);
            ModuleManager.markDirty();
        }));
        elements = HudElements.create(this);
    }

    public List<HudElement> elements() { return elements; }

    @Override
    public boolean showInModuleList() { return false; }

    // ---- Haltbarkeitswarnung ----

    @Override
    public void onTick() {
        LocalPlayer player = mc.player;
        if (player == null || !durabilityWarning.get()) return;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty() || !stack.isDamageableItem()) {
                lastRatio.remove(slot);
                continue;
            }
            float ratio = 1 - stack.getDamageValue() / (float) stack.getMaxDamage();
            Float previous = lastRatio.put(slot, ratio);
            if (previous != null && previous > WARN_RATIO && ratio <= WARN_RATIO) {
                int left = stack.getMaxDamage() - stack.getDamageValue();
                Notifications.warning("Haltbarkeit niedrig", stack.getHoverName().getString() + " · noch " + left);
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 0.7F, 0.6F));
            }
        }
    }

    // ---- Positionen speichern ----

    @Override
    public void saveExtra(JsonObject json) {
        JsonObject layout = new JsonObject();
        for (HudElement element : elements) {
            JsonArray values = new JsonArray();
            values.add(element.anchorX);
            values.add(element.anchorY);
            values.add(element.offsetX);
            values.add(element.offsetY);
            layout.add(element.id, values);
        }
        json.add("layout", layout);
    }

    @Override
    public void loadExtra(JsonObject json) {
        if (!json.has("layout")) return;
        JsonObject layout = json.getAsJsonObject("layout");
        for (HudElement element : elements) {
            if (!layout.has(element.id)) continue;
            JsonArray values = layout.getAsJsonArray(element.id);
            if (values.size() < 4) continue;
            element.anchorX = values.get(0).getAsFloat();
            element.anchorY = values.get(1).getAsFloat();
            element.offsetX = values.get(2).getAsInt();
            element.offsetY = values.get(3).getAsInt();
        }
    }
}
