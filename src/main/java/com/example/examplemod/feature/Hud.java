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
import net.minecraft.Util;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
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
    public final Setting.Bool fps;
    public final Setting.Bool coordinates;
    public final Setting.Bool netherCoordinates;
    public final Setting.Bool info;
    public final Setting.Bool ping;
    public final Setting.Bool time;
    public final Setting.Bool cps;
    public final Setting.Bool moduleList;
    public final Setting.Bool compass;
    public final Setting.Bool armor;
    public final Setting.Bool durabilityWarning;
    private final List<HudElement> elements;

    private static final Deque<Long> LEFT_CLICKS = new ArrayDeque<>();
    private static final Deque<Long> RIGHT_CLICKS = new ArrayDeque<>();
    private final Map<EquipmentSlot, Float> lastRatio = new EnumMap<>(EquipmentSlot.class);

    public Hud() {
        super("hud", "HUD", "Logo, Module, Koordinaten, Kompass und mehr – frei verschiebbar", Category.CLIENT);
        enabledByDefault();
        add(new Setting.Action("HUD bearbeiten", "Elemente mit der Maus verschieben", () ->
                mc.setScreen(new HudEditorScreen(mc.screen))));
        watermark = add(new Setting.Bool("Logo", "Client-Name oben links", true));
        fps = add(new Setting.Bool("FPS", "Bilder pro Sekunde neben dem Logo", true).visibleWhen(this::watermarkOn));
        moduleList = add(new Setting.Bool("Modulliste", "Aktive Module", true));
        coordinates = add(new Setting.Bool("Koordinaten", "Position und Blickrichtung", true));
        netherCoordinates = add(new Setting.Bool("Nether-Koordinaten", "Umgerechnete Position anzeigen", true)
                .visibleWhen(this::coordinatesOn));
        info = add(new Setting.Bool("Infos", "Ping, Uhrzeit im Spiel und Klicks pro Sekunde", true));
        ping = add(new Setting.Bool("Ping", "Verbindung zum Server in Millisekunden", true).visibleWhen(this::infoOn));
        time = add(new Setting.Bool("Uhrzeit", "Tageszeit in der Welt", true).visibleWhen(this::infoOn));
        cps = add(new Setting.Bool("CPS", "Klicks pro Sekunde (links | rechts)", true).visibleWhen(this::infoOn));
        compass = add(new Setting.Bool("Kompass", "Pfeil zum gewählten Ziel-Wegpunkt", true));
        armor = add(new Setting.Bool("Rüstung", "Rüstung und Werkzeug mit Haltbarkeit", true));
        durabilityWarning = add(new Setting.Bool("Haltbarkeitswarnung", "Hinweis, wenn Rüstung oder Werkzeug fast kaputt ist", true));
        add(new Setting.Action("Positionen zurücksetzen", "Alle Elemente an ihren Standardplatz", () -> {
            elements().forEach(HudElement::reset);
            ModuleManager.markDirty();
        }));
        elements = HudElements.create(this);
    }

    private boolean watermarkOn() { return watermark != null && watermark.get(); }
    private boolean coordinatesOn() { return coordinates != null && coordinates.get(); }
    private boolean infoOn() { return info != null && info.get(); }

    public List<HudElement> elements() { return elements; }

    @Override
    public boolean showInModuleList() { return false; }

    // ---- Klicks pro Sekunde ----

    public static void recordClick(int button) {
        Deque<Long> clicks = button == 0 ? LEFT_CLICKS : button == 1 ? RIGHT_CLICKS : null;
        if (clicks != null) clicks.addLast(Util.getMillis());
    }

    public static int cps(int button) {
        Deque<Long> clicks = button == 0 ? LEFT_CLICKS : RIGHT_CLICKS;
        long limit = Util.getMillis() - 1000;
        while (!clicks.isEmpty() && clicks.peekFirst() < limit) clicks.removeFirst();
        return clicks.size();
    }

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
