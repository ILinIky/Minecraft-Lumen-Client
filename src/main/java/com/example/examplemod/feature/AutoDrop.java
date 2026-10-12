package com.example.examplemod.feature;

import com.example.examplemod.gui.ItemSelectScreen;
import com.example.examplemod.gui.Notifications;
import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.ModuleManager;
import com.example.examplemod.module.Setting;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Wirft ausgewählte Items automatisch aus dem Inventar, wie Strg+Q im Inventar.
 *
 * <p>Clientseitig geht das nur über normale Inventar-Klicks; deshalb vorsichtig: nur ohne offenes
 * Menü und ohne Item am Mauszeiger, höchstens ein Stapel pro Verzögerung, Rüstung und Zweithand nie,
 * wertvolle Items und das gehaltene Item auf Wunsch nie. Wird eine Sorte immer wieder aufgehoben,
 * pausiert sie ({@link DropGuard}).
 *
 * <p>Je Item lässt sich eine Menge festlegen, die behalten wird (z.B. 64 Bruchstein). Abgeworfen wird nur
 * der Überschuss: zuerst ganze Stapel, die komplett zu viel sind, sonst einzelne Items.
 */
public class AutoDrop extends Module {
    /** Nach Betreten der Welt bzw. Respawn erst warten, bis das Inventar vom Server synchron ist. */
    private static final int START_DELAY = 40;
    private static final int MAIN_INVENTORY = 36;
    private static final int HOTBAR = 9;

    private final Setting.Slider delay;
    private final Setting.Bool hotbar;
    private final Setting.Bool protectHeld;
    private final Setting.Bool protectValuable;
    private final Setting.Bool survivalOnly;
    private final Setting.Bool messages;

    /** Gewichte für den Schleifenschutz: 6 Stapel oder 96 einzelne Items in 20 s lösen 30 s Pause aus. */
    private static final int STACK_WEIGHT = 16, SINGLE_WEIGHT = 1;

    private Set<Item> items = new LinkedHashSet<>();
    /** Menge, die je Item im Inventar bleiben soll; fehlt = alles abwerfen. */
    private Map<Item, Integer> keep = new LinkedHashMap<>();
    private final DropGuard<Item> guard = new DropGuard<>(6 * STACK_WEIGHT, 400, 600);
    private final Map<Item, Integer> summary = new LinkedHashMap<>();
    private long ticks, nextDrop, nextSummary;

    public AutoDrop() {
        super("auto_drop", "Auto-Abwurf", "Ausgewählte Items automatisch aus dem Inventar werfen", Category.PLAYER);
        add(new Setting.Action("Items auswählen",
                "Liste der Items; Rechtsklick oder Mausrad auf ein Item: Menge, die behalten wird", () ->
                mc.setScreen(new ItemSelectScreen(mc.screen, "Auto-Abwurf", items, keep, (selected, amounts) -> {
                    items = new LinkedHashSet<>(selected);
                    keep = new LinkedHashMap<>(amounts);
                    guard.reset();
                    ModuleManager.markDirty();
                }))));
        add(new Setting.Action("Gehaltenes Item hinzufügen", "Das Item in der Hand auf die Liste setzen", this::addHeld));
        delay = add(new Setting.Slider("Verzögerung", "Wartezeit zwischen zwei Abwürfen (20 Ticks = 1 Sekunde)",
                4, 2, 20, 1, "Ticks"));
        hotbar = add(new Setting.Bool("Hotbar einbeziehen", "Auch die Schnellleiste leeren, nicht nur das Inventar", true));
        protectHeld = add(new Setting.Bool("Gehaltenes Item schützen", "Den ausgewählten Hotbar-Platz nie abwerfen", true));
        protectValuable = add(new Setting.Bool("Wertvolles schützen",
                "Verzauberte, umbenannte und gefüllte Items (Shulker, Bündel) nie abwerfen", true));
        survivalOnly = add(new Setting.Bool("Nur Überleben", "Im Kreativ- und Zuschauermodus nichts abwerfen", true));
        messages = add(new Setting.Bool("Meldungen", "Kurz anzeigen, was abgeworfen wurde", true));
    }

    @Override
    public String getHudInfo() { return String.valueOf(items.size()); }

    /** Menge, die von diesem Item behalten wird (0 = alles abwerfen). */
    public int keepAmount(Item item) { return keep.getOrDefault(item, 0); }

    public Set<Item> items() { return Set.copyOf(items); }

    private void addHeld() {
        LocalPlayer player = mc.player;
        ItemStack held = player == null ? ItemStack.EMPTY : player.getMainHandItem();
        if (held.isEmpty()) {
            Notifications.warning("Auto-Abwurf", "Kein Item in der Hand");
            return;
        }
        if (items.add(held.getItem())) {
            ModuleManager.markDirty();
            Notifications.success("Auto-Abwurf", held.getHoverName().getString() + " hinzugefügt");
        } else {
            Notifications.info("Auto-Abwurf", held.getHoverName().getString() + " ist schon auf der Liste");
        }
    }

    @Override
    protected void onEnable() {
        guard.reset();
        summary.clear();
        nextDrop = ticks + START_DELAY;
        if (items.isEmpty()) Notifications.info("Auto-Abwurf", "Noch keine Items ausgewählt");
    }

    @Override
    public void onLeaveWorld() {
        guard.reset();
        summary.clear();
    }

    @Override
    public void onTick() {
        ticks++;
        flushSummary();
        if (items.isEmpty() || ticks < nextDrop || !safeToDrop()) return;
        LocalPlayer player = mc.player;
        Inventory inventory = player.getInventory();
        // Gesamtmenge je Sorte im Inventar (inkl. geschützter Plätze), um "Behalten" zu berechnen.
        Map<Item, Integer> totals = new LinkedHashMap<>();
        for (int index = 0; index < MAIN_INVENTORY; index++) {
            ItemStack stack = inventory.getItem(index);
            if (!stack.isEmpty() && items.contains(stack.getItem())) totals.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        for (Map.Entry<Item, Integer> entry : totals.entrySet()) {
            Item item = entry.getKey();
            int excess = entry.getValue() - keep.getOrDefault(item, 0);
            if (excess <= 0 || !guard.allowed(item, ticks)) continue;
            if (dropExcess(player, inventory, item, excess)) return;
        }
    }

    /**
     * Wirft einen Teil des Überschusses ab: einen ganzen Stapel, wenn er komplett zu viel ist
     * (kleinster passender zuerst), sonst ein einzelnes Item. Liefert {@code true}, wenn geklickt wurde.
     */
    private boolean dropExcess(LocalPlayer player, Inventory inventory, Item item, int excess) {
        int wholeStack = -1, partial = -1;
        for (int index = 0; index < MAIN_INVENTORY; index++) {
            if (!droppable(inventory, index, item)) continue;
            int count = inventory.getItem(index).getCount();
            if (count <= excess) {
                if (wholeStack < 0 || count < inventory.getItem(wholeStack).getCount()) wholeStack = index;
            } else if (partial < 0) {
                partial = index;
            }
        }
        int index = wholeStack >= 0 ? wholeStack : partial;
        if (index < 0) return false;
        boolean whole = wholeStack >= 0;
        ItemStack stack = inventory.getItem(index);
        String name = stack.getHoverName().getString();
        int dropped = whole ? stack.getCount() : 1;

        // Inventar-Platz → Platz im Spieler-Menü: Hotbar 0–8 liegt dort bei 36–44, das Inventar 9–35 bleibt.
        int menuSlot = index < HOTBAR ? InventoryMenu.USE_ROW_SLOT_START + index : index;
        // THROW mit Knopf 1 = ganzer Stapel (Strg+Q), Knopf 0 = ein Item (Q).
        mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, menuSlot, whole ? 1 : 0, ClickType.THROW, player);

        // Meldungen kurz sammeln, damit beim Leeren mehrerer Stapel nur eine erscheint.
        if (summary.isEmpty()) nextSummary = ticks + 20;
        summary.merge(item, dropped, Integer::sum);
        // Einzelne Items zügig nacheinander, ganze Stapel mit der eingestellten Verzögerung.
        nextDrop = ticks + (whole ? delay.getInt() : 1);
        if (guard.record(item, ticks, whole ? STACK_WEIGHT : SINGLE_WEIGHT)) {
            Notifications.warning("Auto-Abwurf pausiert", name + " wird immer wieder aufgehoben · 30 s Pause");
        }
        return true;
    }

    /** Darf dieser Inventarplatz für diese Sorte angefasst werden? */
    private boolean droppable(Inventory inventory, int index, Item item) {
        boolean inHotbar = index < HOTBAR;
        if (inHotbar && !hotbar.get()) return false;
        if (inHotbar && protectHeld.get() && index == inventory.getSelectedSlot()) return false;
        ItemStack stack = inventory.getItem(index);
        if (stack.isEmpty() || stack.getItem() != item) return false;
        return !protectValuable.get() || !valuable(stack);
    }

    /** Nur abwerfen, wenn kein Menü offen ist, nichts am Mauszeiger hängt und das Inventar synchron ist. */
    private boolean safeToDrop() {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || mc.screen != null) return false;
        if (player.isDeadOrDying() || player.isSpectator() || player.tickCount < START_DELAY) return false;
        if (survivalOnly.get() && !mc.gameMode.getPlayerMode().isSurvival()) return false;
        return player.containerMenu == player.inventoryMenu && player.inventoryMenu.getCarried().isEmpty();
    }

    /** Verzaubert, umbenannt oder mit Inhalt: lieber behalten. */
    public static boolean valuable(ItemStack stack) {
        if (stack.isEnchanted() || stack.has(DataComponents.CUSTOM_NAME)) return true;
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null && !stored.isEmpty()) return true;
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null && contents.nonEmptyItems().iterator().hasNext()) return true;
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        return bundle != null && !bundle.isEmpty();
    }

    /** Fasst die Abwürfe der letzten Sekunden in einer Meldung zusammen, z.B. "12× Erde · 3× Kies". */
    private void flushSummary() {
        if (summary.isEmpty() || ticks < nextSummary) return;
        if (messages.get()) {
            StringBuilder text = new StringBuilder();
            int shown = 0;
            for (Map.Entry<Item, Integer> entry : summary.entrySet()) {
                if (shown == 2) {
                    text.append(" · …");
                    break;
                }
                if (shown > 0) text.append(" · ");
                text.append(entry.getValue()).append("× ").append(new ItemStack(entry.getKey()).getHoverName().getString());
                shown++;
            }
            Notifications.info("Auto-Abwurf", text.toString());
        }
        summary.clear();
    }

    @Override
    public void saveExtra(JsonObject json) {
        JsonArray array = new JsonArray();
        for (Item item : items) array.add(BuiltInRegistries.ITEM.getKey(item).toString());
        json.add("items", array);
        JsonObject amounts = new JsonObject();
        keep.forEach((item, amount) -> {
            if (items.contains(item) && amount > 0) amounts.addProperty(BuiltInRegistries.ITEM.getKey(item).toString(), amount);
        });
        json.add("keep", amounts);
    }

    @Override
    public void loadExtra(JsonObject json) {
        if (!json.has("items")) return;
        Set<Item> loaded = new LinkedHashSet<>();
        for (JsonElement element : json.getAsJsonArray("items")) {
            ResourceLocation id = ResourceLocation.tryParse(element.getAsString());
            if (id == null) continue;
            BuiltInRegistries.ITEM.getOptional(id).filter(item -> item != Items.AIR).ifPresent(loaded::add);
        }
        items = loaded;
        Map<Item, Integer> amounts = new LinkedHashMap<>();
        if (json.has("keep") && json.get("keep").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("keep").entrySet()) {
                ResourceLocation id = ResourceLocation.tryParse(entry.getKey());
                if (id == null || !entry.getValue().isJsonPrimitive()) continue;
                int amount = entry.getValue().getAsInt();
                BuiltInRegistries.ITEM.getOptional(id).filter(items::contains).ifPresent(item -> {
                    if (amount > 0) amounts.put(item, amount);
                });
            }
        }
        keep = amounts;
    }
}
