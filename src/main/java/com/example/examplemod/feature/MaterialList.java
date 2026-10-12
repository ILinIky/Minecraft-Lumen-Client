package com.example.examplemod.feature;

import com.example.examplemod.building.MaterialTargets;
import com.example.examplemod.gui.MaterialListScreen;
import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.ModuleManager;
import com.example.examplemod.module.Setting;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

public final class MaterialList extends Module {
    public record Row(Item item, String name, int have, int target, ItemStack icon) {
        public int missing() { return MaterialTargets.missing(target, have); }
    }
    public final Setting.Bool followPlan, hideDone;
    public final Setting.Slider hudRows;
    private final Map<String, Integer> manual = new LinkedHashMap<>();
    private List<Row> rows = List.of();
    private List<Row> incomplete = List.of();
    private int ticks;
    /** Nur dieses Item im HUD zeigen (Rechtsklick-Menü), sonst {@code null}. Wird nicht gespeichert. */
    private Item solo;

    public MaterialList() {
        super("material_list", "Sammelliste", "Materialbedarf aus Bauplänen und eigene Sammelziele im HUD", Category.WORLD);
        enabledByDefault();
        followPlan = add(new Setting.Bool("Bauplan folgen", "Offenen Bedarf des aktuellen Bauplans automatisch anzeigen", false));
        hideDone = add(new Setting.Bool("Erledigte ausblenden", "Vollständige Einträge im HUD ausblenden", true));
        hudRows = add(new Setting.Slider("HUD-Zeilen", "Liste mit Shift + Mausrad oder bei geöffnetem Chat unter dem Mauszeiger scrollen", 6, 3, 12, 1, ""));
        add(new Setting.Action("Sammelliste öffnen", "Items und genaue Zielmengen bearbeiten", () -> mc.setScreen(new MaterialListScreen(mc.screen))));
    }
    public Map<String, Integer> manual() { return Collections.unmodifiableMap(manual); }
    public List<Row> rows() { return rows; }
    public List<Row> hudRows() {
        if (solo != null) return rows.stream().filter(r -> r.item() == solo).toList();
        return hideDone.get() ? incomplete : rows;
    }
    public Item solo() { return solo; }
    /**
     * Nur dieses Item zeigen; {@code null} oder ein Item, das nicht in der Liste steht, zeigt wieder alle.
     * Aufruf über {@link Blueprints#focusItem}, damit Sammelliste und Bauplan gemeinsam filtern.
     */
    public void solo(Item item) { solo = item != null && rows.stream().anyMatch(r -> r.item() == item) ? item : null; }
    public boolean isManual(Item item) { return manual.containsKey(BuiltInRegistries.ITEM.getKey(item).toString()); }
    public void pinPlan() {
        followPlan.set(true);
        setEnabled(true, false);
        ModuleManager.HUD.setEnabled(true, false);
        ModuleManager.HUD.materialList.set(true);
        refresh();
        ModuleManager.saveNow();
    }
    public void set(Item item, int amount) {
        if (item == Items.AIR) return;
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        if (amount <= 0) manual.remove(id);
        else {
            if (!manual.containsKey(id) && manual.size() >= MaterialTargets.MAX_ENTRIES) {
                com.example.examplemod.gui.Notifications.warning("Sammelliste", "Maximal 256 eigene Einträge"); return;
            }
            manual.put(id, Math.min(MaterialTargets.MAX_AMOUNT, amount));
        }
        refresh();
        ModuleManager.markDirty();
    }
    public void refresh() {
        if (mc.player == null) { rows = incomplete = List.of(); return; }
        Map<String, Integer> fromPlan = new LinkedHashMap<>();
        var plan = ModuleManager.BLUEPRINTS.plan();
        if (followPlan.get() && plan != null) for (var material : plan.materials) {
            if (material.item != Items.AIR && material.remaining() > 0)
                fromPlan.merge(BuiltInRegistries.ITEM.getKey(material.item).toString(), material.remaining(), Integer::sum);
        }
        Map<Item, Integer> inventory = new HashMap<>();
        var slots = mc.player.getInventory();
        for (int i = 0; i < slots.getContainerSize(); i++) {
            ItemStack stack = slots.getItem(i);
            if (!stack.isEmpty()) inventory.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        List<Row> next = new ArrayList<>();
        MaterialTargets.combine(manual, fromPlan).forEach((id, amount) -> {
            ResourceLocation key = ResourceLocation.tryParse(id);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return;
            Item item = BuiltInRegistries.ITEM.getValue(key);
            if (item != Items.AIR) {
                ItemStack icon = new ItemStack(item);
                next.add(new Row(item, icon.getHoverName().getString(), inventory.getOrDefault(item, 0), amount, icon));
            }
        });
        next.sort(Comparator.comparing((Row r) -> r.missing() == 0).thenComparing(Row::name));
        rows = List.copyOf(next);
        incomplete = rows.stream().filter(r -> r.missing() > 0).toList();
        // Verschwindet das einzeln gezeigte Item aus der Liste, wieder alle zeigen (sonst wäre das HUD leer).
        if (solo != null && rows.stream().noneMatch(r -> r.item() == solo)) solo = null;
    }
    @Override public void onTick() { if (++ticks % 5 == 0) refresh(); }
    @Override public void onLeaveWorld() { rows = incomplete = List.of(); ticks = 0; solo = null; }
    @Override public void saveExtra(JsonObject json) {
        JsonObject values = new JsonObject(); manual.forEach(values::addProperty); json.add("targets", values);
    }
    @Override public void loadExtra(JsonObject json) {
        manual.clear();
        if (!json.has("targets") || !json.get("targets").isJsonObject()) return;
        for (var entry : json.getAsJsonObject("targets").entrySet()) {
            if (manual.size() >= MaterialTargets.MAX_ENTRIES) break;
            try {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                int amount = entry.getValue().getAsInt();
                if (id != null && amount > 0) manual.put(id.toString(), Math.min(MaterialTargets.MAX_AMOUNT, amount));
            } catch (RuntimeException ignored) { }
        }
    }
}
