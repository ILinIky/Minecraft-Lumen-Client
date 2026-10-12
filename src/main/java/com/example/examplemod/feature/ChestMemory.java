package com.example.examplemod.feature;

import com.example.examplemod.chest.ChestRecord;
import com.example.examplemod.chest.ChestStore;
import com.example.examplemod.gui.ChestSearchScreen;
import com.example.examplemod.gui.Notifications;
import com.example.examplemod.gui.Theme;
import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.Setting;
import com.example.examplemod.render.Render3D;
import com.example.examplemod.render.WorldRenderContext;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kisten-Gedächtnis: merkt sich den Inhalt jedes geöffneten Behälters (Truhe, Fass, Shulker-Kiste,
 * Spender, Trichter, Endertruhe) und findet Items später wieder. Rein clientseitig und fair:
 * es kennt nur, was der Spieler selbst gesehen hat.
 */
public class ChestMemory extends Module {
    /** So lange nach dem Rechtsklick darf sich das Menü öffnen, um zum Block zu gehören. */
    private static final int INTERACTION_TICKS = 100;
    private static final int CLEANUP_INTERVAL = 100;
    private static final double CLEANUP_DISTANCE = 32;

    public final Setting.Bool shulkers;
    public final Setting.Bool enderChest;
    private final Setting.Slider highlightSeconds;

    /** Zuletzt angeklickter Block (aus dem Forge-Event). */
    private BlockPos lastInteraction;
    private long lastInteractionTick = -INTERACTION_TICKS;
    /** Gerade geöffneter, gemerkter Behälter. */
    private Session session;
    private ChestRecord highlighted;
    private String highlightLabel;
    private long highlightUntil;
    private long ticks;

    /** Geöffneter Behälter; {@code lastState} = zuletzt gelesener Änderungszähler des Menüs. */
    private static final class Session {
        final int containerId;
        final ChestRecord record;
        int lastState = -1;

        Session(int containerId, ChestRecord record) {
            this.containerId = containerId;
            this.record = record;
        }
    }

    /** Ziel für den HUD-Kompass. */
    public record Target(double x, double y, double z, String label, String dimension) {}

    public ChestMemory() {
        super("chest_memory", "Kisten-Gedächtnis", "Merkt sich Kisteninhalte und findet Items wieder", Category.WORLD);
        enabledByDefault();
        add(new Setting.Action("Kisten durchsuchen", "Suche über alle gemerkten Behälter (Taste K)", () ->
                mc.setScreen(new ChestSearchScreen(mc.screen, ""))));
        shulkers = add(new Setting.Bool("Shulker-Inhalte", "Auch in Shulker-Kisten innerhalb von Behältern suchen", true));
        enderChest = add(new Setting.Bool("Endertruhe merken", "Inhalt deiner Endertruhe mitspeichern", true));
        highlightSeconds = add(new Setting.Slider("Hervorhebung", "So lange leuchtet eine gesuchte Kiste", 60, 10, 300, 10, "s"));
    }

    @Override
    public String getHudInfo() { return String.valueOf(ChestStore.size()); }

    @Override
    public boolean showInModuleList() { return false; }

    /** Aus dem Forge-Event: Rechtsklick auf einen Block (nur Client-Seite). */
    public void onInteract(BlockPos pos) {
        lastInteraction = pos.immutable();
        lastInteractionTick = ticks;
    }

    @Override
    public void onTick() {
        ticks++;
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == player.inventoryMenu) {
            if (session != null) finish();
        } else {
            if (session != null && session.containerId != menu.containerId) finish();
            if (session == null) session = begin(level, menu);
            // Nur lesen, wenn der Server den Inhalt geändert hat (Zähler steigt bei jeder Änderung).
            if (session != null && menu.getStateId() != session.lastState) {
                session.lastState = menu.getStateId();
                snapshot(menu, session.record);
            }
        }
        if (highlighted != null && ticks > highlightUntil) highlighted = null;
        if (ticks % CLEANUP_INTERVAL == 0) cleanup(level, player);
    }

    /** Ordnet das neue Menü dem zuletzt angeklickten Behälter zu, sonst wird es ignoriert (Händler, Lore usw.). */
    private Session begin(ClientLevel level, AbstractContainerMenu menu) {
        if (lastInteraction == null || ticks - lastInteractionTick > INTERACTION_TICKS) return null;
        BlockPos pos = lastInteraction;
        lastInteraction = null;
        BlockState state = level.getBlockState(pos);
        if (!supported(state.getBlock())) return null;
        ChestRecord record;
        if (state.getBlock() instanceof EnderChestBlock) {
            if (!enderChest.get()) return null;
            record = new ChestRecord(0, 0, 0, ChestRecord.ENDER_CHEST, id(state.getBlock()));
        } else {
            BlockPos main = pos;
            BlockPos other = null;
            if (state.getBlock() instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)
                    && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
                // Doppeltruhe: immer unter derselben Hälfte speichern, egal welche geöffnet wurde.
                BlockPos connected = pos.relative(ChestBlock.getConnectedDirection(state));
                if (compare(connected, pos) < 0) {
                    main = connected;
                    other = pos;
                } else {
                    other = connected;
                }
            }
            record = new ChestRecord(main.getX(), main.getY(), main.getZ(), level.dimension().location().toString(), id(state.getBlock()));
            if (other != null) record.other = new int[]{other.getX(), other.getY(), other.getZ()};
        }
        if (highlighted != null && highlighted.key().equals(record.key())) {
            highlighted = null;
            Notifications.success("Kisten-Gedächtnis", "Gefunden");
        }
        return new Session(menu.containerId, record);
    }

    private void finish() {
        Session finished = session;
        session = null;
        // Beim Schließen zeigt das Menü bereits das Spielerinventar; der letzte Stand stammt aus snapshot().
        if (finished.record.seen == 0) return;
        ChestStore.put(finished.record);
    }

    /** Liest die Behälter-Plätze (ohne Spielerinventar) und fasst gleiche Sorten zusammen. */
    private void snapshot(AbstractContainerMenu menu, ChestRecord record) {
        // Solange der Server den Inhalt noch nicht geschickt hat (stateId 0), wirken die Plätze leer.
        if (menu.getStateId() == 0) return;
        Map<String, ChestRecord.StoredItem> items = new LinkedHashMap<>();
        int containerSlots = 0;
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory) continue;
            containerSlots++;
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) continue;
            ChestRecord.StoredItem stored = store(stack);
            String key = stored.id + "|" + stored.name;
            ChestRecord.StoredItem existing = items.get(key);
            if (existing == null) {
                items.put(key, stored);
            } else {
                existing.count += stored.count;
                if (stored.inside != null) {
                    if (existing.inside == null) existing.inside = new ArrayList<>();
                    merge(existing.inside, stored.inside);
                }
            }
        }
        if (containerSlots == 0) return;
        record.items = new ArrayList<>(items.values());
        record.seen = System.currentTimeMillis();
    }

    private static ChestRecord.StoredItem store(ItemStack stack) {
        ChestRecord.StoredItem stored = new ChestRecord.StoredItem(
                BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getHoverName().getString(), stack.getCount());
        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents != null) {
            List<ChestRecord.StoredItem> inside = new ArrayList<>();
            for (ItemStack inner : contents.nonEmptyItems()) {
                merge(inside, List.of(new ChestRecord.StoredItem(BuiltInRegistries.ITEM.getKey(inner.getItem()).toString(),
                        inner.getHoverName().getString(), inner.getCount())));
            }
            if (!inside.isEmpty()) stored.inside = inside;
        }
        return stored;
    }

    private static void merge(List<ChestRecord.StoredItem> target, List<ChestRecord.StoredItem> add) {
        for (ChestRecord.StoredItem item : add) {
            ChestRecord.StoredItem match = null;
            for (ChestRecord.StoredItem existing : target) {
                if (existing.id.equals(item.id) && existing.name.equals(item.name)) match = existing;
            }
            if (match == null) target.add(new ChestRecord.StoredItem(item.id, item.name, item.count));
            else match.count += item.count;
        }
    }

    /** Entfernt Einträge in der Nähe, an deren Stelle kein Behälter mehr steht (abgebaut). */
    private void cleanup(ClientLevel level, LocalPlayer player) {
        String dimension = level.dimension().location().toString();
        List<ChestRecord> gone = new ArrayList<>();
        for (ChestRecord record : ChestStore.all()) {
            if (!dimension.equals(record.dimension)) continue;
            if (player.distanceToSqr(record.x + 0.5, record.y + 0.5, record.z + 0.5) > CLEANUP_DISTANCE * CLEANUP_DISTANCE) continue;
            BlockPos pos = new BlockPos(record.x, record.y, record.z);
            if (!level.hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) continue;
            if (!supported(level.getBlockState(pos).getBlock())) gone.add(record);
        }
        for (ChestRecord record : gone) {
            ChestStore.remove(record);
            if (highlighted != null && highlighted.key().equals(record.key())) highlighted = null;
        }
    }

    public static boolean supported(Block block) {
        return block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof ShulkerBoxBlock
                || block instanceof DispenserBlock || block instanceof HopperBlock || block instanceof EnderChestBlock;
    }

    private static String id(Block block) { return BuiltInRegistries.BLOCK.getKey(block).toString(); }

    private static int compare(BlockPos a, BlockPos b) {
        if (a.getX() != b.getX()) return Integer.compare(a.getX(), b.getX());
        if (a.getY() != b.getY()) return Integer.compare(a.getY(), b.getY());
        return Integer.compare(a.getZ(), b.getZ());
    }

    // ---- Hervorheben und hinführen ----

    /** Lässt den Behälter leuchten und setzt ihn als Kompass-Ziel. */
    public void highlight(ChestRecord record, String label) {
        highlighted = record;
        highlightLabel = label;
        highlightUntil = ticks + highlightSeconds.getInt() * 20L;
        if (!isEnabled()) setEnabled(true, false);
    }

    public Target target() {
        if (!isEnabled() || highlighted == null || highlighted.enderChest()) return null;
        double cx = highlighted.x + 0.5, cz = highlighted.z + 0.5;
        if (highlighted.other != null) {
            cx = (highlighted.x + highlighted.other[0]) / 2.0 + 0.5;
            cz = (highlighted.z + highlighted.other[2]) / 2.0 + 0.5;
        }
        return new Target(cx, highlighted.y, cz, highlightLabel, highlighted.dimension);
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        if (highlighted == null || mc.level == null || highlighted.enderChest()) return;
        if (!mc.level.dimension().location().toString().equals(highlighted.dimension)) return;
        AABB box = new AABB(highlighted.x, highlighted.y, highlighted.z, highlighted.x + 1, highlighted.y + 1, highlighted.z + 1);
        if (highlighted.other != null) {
            box = box.minmax(new AABB(highlighted.other[0], highlighted.other[1], highlighted.other[2],
                    highlighted.other[0] + 1, highlighted.other[1] + 1, highlighted.other[2] + 1));
        }
        // Sanftes Pulsieren in der Akzentfarbe, durch Wände sichtbar.
        float pulse = 0.55F + 0.45F * (float) Math.sin(System.currentTimeMillis() / 180.0);
        VertexConsumer lines = Render3D.lines(context);
        Render3D.box(context, lines, box.inflate(0.02), Theme.alpha(Theme.ACCENT, pulse));
        Render3D.box(context, lines, box.inflate(0.08), Theme.alpha(Theme.ACCENT_2, pulse * 0.6F));
        Render3D.flush(context);
        Vec3 top = new Vec3(box.getCenter().x, box.maxY + 0.6, box.getCenter().z);
        Render3D.label(context, top, "▣ " + highlightLabel, Theme.ACCENT_2, 0.025F);
        context.buffers().endBatch();
    }

    @Override
    public void onLeaveWorld() {
        session = null;
        highlighted = null;
        lastInteraction = null;
    }
}
