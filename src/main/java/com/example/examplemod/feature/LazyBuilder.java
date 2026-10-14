package com.example.examplemod.feature;

import com.example.examplemod.ClientKeybinds;
import com.example.examplemod.blueprint.BlueprintPlan;
import com.example.examplemod.blueprint.LazyBuildGate;
import com.example.examplemod.blueprint.LazyBuildGeometry;
import com.example.examplemod.module.ModuleManager;
import com.example.examplemod.gui.Theme;
import com.example.examplemod.gui.Notifications;
import com.example.examplemod.render.Render3D;
import com.example.examplemod.render.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * Toggle-to-build through vanilla useItemOn only. Never rotates, teleports or breaks blocks.
 *
 * <p>Konzept: Taste G schaltet an; es bleibt an, bis G erneut gedrückt wird oder die Welt wechselt. Alles
 * Vorübergehende (Menü offen, manuelles Bauen, nichts in Reichweite, kein Material, Ebene gewechselt) pausiert
 * nur und läuft danach von selbst weiter. Gebaut wird mit dem, was im Inventar ist; fehlendes Material wird
 * übersprungen. Jede Platzierung wartet auf die Serverbestätigung; eine abgelehnte Stelle wird 10 s
 * übersprungen, erst mehrere Ablehnungen kurz hintereinander (geschützter Bereich) stoppen.
 */
public final class LazyBuilder {
    private static final int SKIP_TICKS = 200;
    private final Minecraft mc = Minecraft.getInstance();
    private final Blueprints feature;
    private final LazyBuildGate gate = new LazyBuildGate();
    private ClientLevel sessionLevel;
    private BlockPos pending, preview;
    private BlockState expected;
    private int originalSlot = -1, selectedSlot = -1;
    private long prepTick;
    private boolean shortcutDown;
    /** Vom Server abgelehnte oder nicht bestätigte Stellen, bis zu diesem Tick übersprungen. */
    private final java.util.Map<BlockPos, Long> skipped = new java.util.HashMap<>();
    private long tick;
    private ItemStack pendingInventory = ItemStack.EMPTY;
    private long inventoryDeadline;
    private String status = "Bereit";
    private record Candidate(int index, BlockPos pos, BlockState state, double distance) {}

    public LazyBuilder(Blueprints feature) { this.feature = feature; }
    public String status() { return status; }
    /**
     * Hinweis fürs HUD, nur wenn der Spieler etwas wissen oder tun muss (Pause, Material fehlt, näher gehen).
     * {@code null} = läuft normal bzw. ist einfach aus; Setzen, Serverbestätigung und Materialwechsel sind Routine.
     */
    public String displayStatus() {
        if (!active()) return status.equals("Aus") || status.equals("Bereit") ? null : status;
        return waiting() || status.equals("Baut") || status.startsWith("Setze ") || status.startsWith("Baumaterial auswählen")
                || status.startsWith("Warte auf Materialtausch") ? null : status;
    }
    public boolean waiting() { return gate.state() == LazyBuildGate.State.WAITING; }
    public boolean active() { return gate.active(); }
    private boolean held() { return ClientKeybinds.BLUEPRINT_BUILD != null && ClientKeybinds.BLUEPRINT_BUILD.isDown(); }
    public void cancel() {
        restoreSlot(); gate.release(); pending = preview = null; expected = null; sessionLevel = null;
        shortcutDown = held(); prepTick = 0; status = "Aus";
        pendingInventory = ItemStack.EMPTY; skipped.clear();
    }
    private void restoreSlot() {
        if (mc.player != null && mc.level == sessionLevel && originalSlot >= 0 && mc.player.getInventory().getSelectedSlot() == selectedSlot)
            mc.player.getInventory().setSelectedSlot(originalSlot);
        originalSlot = selectedSlot = -1;
    }
    private void stop(String reason) {
        gate.stop(); pending = preview = null; expected = null; restoreSlot();
        pendingInventory = ItemStack.EMPTY;
        status = reason;
        Notifications.warning("Lazy-Bauen pausiert", reason);
    }

    public void tick(BlueprintPlan plan) {
        tick++;
        boolean down = held(), pressed = down && !shortcutDown;
        shortcutDown = down;
        if (pressed && active()) { cancel(); return; }
        if (!feature.lazyBuild.get()) {
            if (active()) cancel();
            status = "Lazy-Bauen im Bauplan-Menü aktivieren"; return;
        }
        if (!active() && !pressed) return;
        if (mc.player == null || mc.level == null || mc.gameMode == null) { cancel(); return; }
        if (pressed) {
            if (!gate.toggle()) return;
            sessionLevel = mc.level; skipped.clear(); pendingInventory = ItemStack.EMPTY;
            originalSlot = selectedSlot = mc.player.getInventory().getSelectedSlot();
            status = "Baut"; prepTick = 0;
        }
        // Nur ein Weltwechsel beendet die Sitzung; alles andere pausiert höchstens.
        if (mc.level != sessionLevel) { cancel(); return; }
        if (gate.timedOut(tick)) {
            skip(pending); pending = null; expected = null;
            if (refused()) return;
        }
        if (waiting()) return;
        String pause = pauseReason(plan);
        if (pause != null) { preview = null; status = pause; prepTick = tick + 4; return; }
        // Selbst gewählter Hotbar-Platz: übernehmen statt abbrechen.
        int current = mc.player.getInventory().getSelectedSlot();
        if (current != selectedSlot) { selectedSlot = current; originalSlot = current; pendingInventory = ItemStack.EMPTY; }
        if (!pendingInventory.isEmpty()) {
            if (ItemStack.isSameItemSameComponents(mc.player.getMainHandItem(), pendingInventory) && !mc.player.getMainHandItem().isEmpty())
                pendingInventory = ItemStack.EMPTY;
            else if (tick < inventoryDeadline) { status = "Warte auf Materialtausch …"; return; }
            else pendingInventory = ItemStack.EMPTY;
        }
        if (!gate.maySend(tick) || tick < prepTick) return;
        // Up to five short planning passes per second; no per-frame world scans.
        prepTick = tick + 4;
        skipped.values().removeIf(until -> until <= tick);
        Candidate choice = null;
        BlockHitResult hit = null;
        boolean noMaterial = false, special = false, missing = false, hasMaterial = false;
        Vec3 eye = mc.player.getEyePosition();
        double reach = mc.player.blockInteractionRange();
        boolean viewOnly = feature.lazyMode.is("Im Blickfeld");
        Vec3 view = mc.player.getViewVector(1);
        var candidates = new ArrayList<Candidate>();
        var nearby = plan.nearby(mc.player.blockPosition(), (int) Math.ceil(reach) + 1);
        for (int n = 0; n < nearby.size(); n++) {
            int index = nearby.getInt(n);
            var cell = plan.blueprint.cells().get(index);
            if (cell.y() != plan.layer || plan.ignored(index) || plan.accepted(index) || plan.unsupported(cell)) continue;
            BlockPos pos = plan.position(cell);
            double distance = eye.distanceToSqr(Vec3.atCenterOf(pos));
            if (distance > (reach + 0.9) * (reach + 0.9) || skipped.containsKey(pos)) continue;
            boolean aimed = mc.hitResult instanceof BlockHitResult aim && aim.getType() == HitResult.Type.BLOCK
                    && (aim.getBlockPos().equals(pos) || aim.getBlockPos().relative(aim.getDirection()).equals(pos));
            // Blickfeld ca. 65° um die Blickrichtung: auch der Block direkt vor den Füßen zählt.
            if (!aimed && viewOnly && Vec3.atCenterOf(pos).subtract(eye).normalize().dot(view) < 0.42) continue;
            if (plan.compare(mc.level, index, pos) != BlueprintPlan.Status.MISSING) continue;
            missing = true;
            BlockState state = plan.state(cell);
            if (!placeable(state, pos)) { special = true; continue; }
            // Mit dem arbeiten, was da ist: fehlendes Material überspringen statt anzuhalten.
            if (findItem(new ItemStack(BlueprintPlan.materialItem(state))) < 0) { noMaterial = true; continue; }
            hasMaterial = true;
            candidates.add(new Candidate(index, pos, state, aimed ? -1 : distance));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::distance));
        int[] rayBudget = {64};
        int inspected = 0;
        for (var candidate : candidates) {
            if (++inspected > 64 || rayBudget[0] <= 0) break;
            BlockHitResult attempt = placement(candidate, new ItemStack(BlueprintPlan.materialItem(candidate.state)), reach, rayBudget);
            if (attempt != null) { choice = candidate; hit = attempt; break; }
        }
        if (choice == null) {
            preview = null;
            status = hasMaterial ? (viewOnly ? "Zu offenen Stellen schauen oder näher gehen" : "Näher an offene Stellen gehen")
                    : noMaterial ? "Kein passendes Material für die Stellen hier"
                    : special ? "Spezialblöcke hier bitte von Hand setzen"
                    : missing ? "Näher herangehen" : "Hier ist nichts mehr offen";
            return;
        }
        preview = choice.pos;
        ItemStack wanted = new ItemStack(BlueprintPlan.materialItem(choice.state));
        if (!selectItem(wanted)) return;
        // Actual selected stack and placement context, not a synthetic item, govern the final check.
        hit = placement(choice, mc.player.getMainHandItem(), reach, new int[]{24});
        if (hit == null) { status = "Baut"; return; }
        pending = choice.pos; expected = choice.state;
        gate.submitted(tick, feature.lazyTempo.is("Ruhig") ? 10 : feature.lazyTempo.is("Zügig") ? 4 : 6);
        status = "Setze " + expected.getBlock().getName().getString();
        var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        if (!result.consumesAction()) {
            // Vanilla hat lokal abgelehnt: diese Stelle überspringen, mit den anderen weitermachen.
            gate.acknowledge(false, tick); skip(pending); pending = null; expected = null;
            refused(); return;
        }
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    /** Vorübergehende Gründe, gerade nicht zu bauen; die Sitzung bleibt an und läuft danach weiter. */
    private String pauseReason(BlueprintPlan plan) {
        if (mc.screen != null || !mc.isWindowActive() || mc.isPaused()) return "Pausiert · Menü offen";
        if (Freecam.active() || !mc.player.isAlive() || mc.player.isSpectator() || !mc.player.mayBuild() || mc.player.isPassenger())
            return "Pausiert · Bauen gerade nicht möglich";
        if (feature.loading()) return "Bauplan wird geladen …";
        if (plan == null || !plan.inDimension(mc.level)) return "Kein Bauplan in dieser Dimension";
        if (plan.layers == BlueprintPlan.Layers.ALL || plan.focus != null) return "Pausiert · eine einzelne Ebene wählen";
        if (mc.player.containerMenu != mc.player.inventoryMenu || !mc.player.inventoryMenu.getCarried().isEmpty())
            return "Pausiert · Item am Mauszeiger ablegen";
        // Vanilla input has priority; do not compete with a manual right click or mining.
        if (mc.player.isUsingItem() || mc.gameMode.isDestroying() || mc.options.keyAttack.isDown() || mc.options.keyUse.isDown())
            return "Baut · manuelles Bauen hat Vorrang";
        return null;
    }

    private void skip(BlockPos pos) { if (pos != null) skipped.put(pos, tick + SKIP_TICKS); }

    /** Nach wiederholter Ablehnung: Sitzung anhalten, Neustart nur mit der Taste. */
    private boolean refused() {
        if (gate.state() != LazyBuildGate.State.STOPPED) return false;
        stop("Server lehnt Platzierungen hier ab (z.B. geschützter Bereich)");
        return true;
    }

    /**
     * Ziel: alles, was mit seinem eigenen Item als einzelner Block gesetzt wird. Ausgenommen sind Blöcke mit
     * Inhalt (Truhen, Schilder öffnen Fenster), Zwei-Block-Objekte, wassergeflutete und doppelte Stufen;
     * fallende Blöcke nur auf festem Grund. Ob Ausrichtung/Form passen, prüft {@link #validPlacement}.
     */
    private boolean placeable(BlockState state, BlockPos pos) {
        if (state == null || state.isAir() || state.hasBlockEntity()) return false;
        Block block = state.getBlock();
        if (!(block.asItem() instanceof BlockItem item) || item.getBlock() != block
                || item instanceof net.minecraft.world.item.DoubleHighBlockItem || item instanceof net.minecraft.world.item.BedItem) return false;
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) || state.hasProperty(BlockStateProperties.BED_PART)) return false;
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) return false;
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) return false;
        return !(block instanceof FallingBlock) || !mc.level.getBlockState(pos.below()).canBeReplaced();
    }

    /**
     * Untergrund, gegen den geklickt werden darf: jeder feste Block, der beim Rechtsklick nichts öffnet oder
     * umschaltet (Vanilla führt diese Aktion auch mit einem Block in der Hand aus).
     */
    private static boolean clickable(BlockState base) {
        if (base.isAir() || base.canBeReplaced() || base.hasBlockEntity()) return false;
        Block b = base.getBlock();
        return !(b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock || b instanceof ButtonBlock
                || b instanceof LeverBlock || b instanceof BedBlock || b instanceof CraftingTableBlock || b instanceof CartographyTableBlock
                || b instanceof GrindstoneBlock || b instanceof LoomBlock || b instanceof StonecutterBlock || b instanceof AnvilBlock
                || b instanceof BellBlock || b instanceof NoteBlock || b instanceof DiodeBlock || b instanceof CakeBlock
                || b instanceof CandleCakeBlock || b instanceof CandleBlock || b instanceof RespawnAnchorBlock || b instanceof ComposterBlock
                || b instanceof AbstractCauldronBlock || b instanceof FlowerPotBlock || b instanceof DragonEggBlock || b instanceof TntBlock
                || b instanceof RedStoneWireBlock || b instanceof CaveVinesBlock || b instanceof CaveVinesPlantBlock
                || b instanceof SweetBerryBushBlock || b instanceof LightBlock);
    }
    private BlockHitResult placement(Candidate target, ItemStack stack, double reach, int[] budget) {
        if (!(stack.getItem() instanceof BlockItem item) || !mc.level.getWorldBorder().isWithinBounds(target.pos)
                || !mc.level.hasChunk(target.pos.getX() >> 4, target.pos.getZ() >> 4)
                || !mc.level.getFluidState(target.pos).isEmpty()) return null;
        Vec3 eye = mc.player.getEyePosition();
        // The ordinary crosshair hit is already ray-traced by Minecraft. Prefer precisely that hit.
        if (mc.hitResult instanceof BlockHitResult aim && aim.getType() == HitResult.Type.BLOCK
                && mc.level.hasChunk(aim.getBlockPos().getX() >> 4, aim.getBlockPos().getZ() >> 4)
                && clickable(mc.level.getBlockState(aim.getBlockPos()))
                && validPlacement(target, stack, item, aim, reach)) return aim;
        for (Direction face : Direction.values()) {
            BlockPos support = target.pos.relative(face.getOpposite());
            if (!mc.level.hasChunk(support.getX() >> 4, support.getZ() >> 4)) continue;
            BlockState base = mc.level.getBlockState(support);
            if (!clickable(base)) continue;
            int boxes = 0;
            for (AABB local : base.getShape(mc.level, support).toAabbs()) {
                if (++boxes > 16) break;
                AABB box = local.move(support);
                Vec3 center = box.getCenter();
                Vec3[] samples = {
                        LazyBuildGeometry.nearestFacePoint(box, face, eye),
                        LazyBuildGeometry.nearestFacePoint(box, face, center),
                        LazyBuildGeometry.nearestFacePoint(box, face, new Vec3(center.x, box.minY + box.getYsize() * 0.25, center.z)),
                        LazyBuildGeometry.nearestFacePoint(box, face, new Vec3(center.x, box.minY + box.getYsize() * 0.75, center.z))
                };
                for (Vec3 point : new java.util.LinkedHashSet<>(java.util.List.of(samples))) {
                    if (eye.distanceToSqr(point) > reach * reach) continue;
                    if (budget[0]-- <= 0) return null;
                    Vec3 inside = point.add(-face.getStepX() * 0.000001, -face.getStepY() * 0.000001, -face.getStepZ() * 0.000001);
                    BlockHitResult visible = mc.level.clip(new ClipContext(eye, inside, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
                    if (visible.getType() != HitResult.Type.BLOCK || !visible.getBlockPos().equals(support) || visible.getDirection() != face) continue;
                    if (validPlacement(target, stack, item, visible, reach)) return visible;
                }
            }
        }
        return null;
    }
    private boolean validPlacement(Candidate target, ItemStack stack, BlockItem item, BlockHitResult hit, double reach) {
        if (mc.player.getEyePosition().distanceToSqr(hit.getLocation()) > reach * reach) return false;
        BlockPlaceContext context = item.updatePlacementContext(new BlockPlaceContext(mc.player, InteractionHand.MAIN_HAND, stack, hit));
        if (context == null || !context.getClickedPos().equals(target.pos) || !context.canPlace()) return false;
        BlockState placed = item.getBlock().getStateForPlacement(context);
        return placed != null && BlueprintPlan.matches(placed, target.state) && placed.canSurvive(mc.level, target.pos)
                && mc.level.isUnobstructed(placed, target.pos, CollisionContext.empty());
    }
    private int findItem(ItemStack wanted) {
        for (int i = 0; i < 36; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, wanted)) return i;
        }
        return -1;
    }
    private boolean selectItem(ItemStack wanted) {
        var inventory = mc.player.getInventory();
        if (ItemStack.isSameItemSameComponents(mc.player.getMainHandItem(), wanted) && !mc.player.getMainHandItem().isEmpty()) return true;
        int slot = findItem(wanted);
        if (slot < 0) return false;
        if (slot >= 9) {
            int destination = -1;
            for (int i = 0; i < 9; i++) if (inventory.getItem(i).isEmpty()) { destination = i; break; }
            if (destination < 0) for (int i = 0; i < 9; i++) {
                ItemStack old = inventory.getItem(i);
                if (old.getItem() instanceof BlockItem && ItemStack.isSameItemSameComponents(old, new ItemStack(old.getItem()))) { destination = i; break; }
            }
            // SWAP exchanges both stacks, even with a full inventory; nothing is dropped or deleted.
            if (destination < 0) destination = inventory.getSelectedSlot();
            mc.gameMode.handleInventoryMouseClick(mc.player.inventoryMenu.containerId, slot, destination, ClickType.SWAP, mc.player);
            pendingInventory = wanted.copy(); inventoryDeadline = tick + 40;
            slot = destination;
        }
        inventory.setSelectedSlot(slot); selectedSlot = slot;
        prepTick = tick + 2; status = "Baumaterial auswählen …";
        return false;
    }
    public static void onServerBlock(BlockPos pos, BlockState state) {
        if (ModuleManager.isReady() && ModuleManager.BLUEPRINTS != null) ModuleManager.BLUEPRINTS.lazyBuilder.serverBlock(pos, state);
    }
    public static boolean awaitingServerBlock() {
        return ModuleManager.isReady() && ModuleManager.BLUEPRINTS != null && ModuleManager.BLUEPRINTS.lazyBuilder.waiting();
    }
    private void serverBlock(BlockPos pos, BlockState state) {
        if (!waiting() || pending == null || !pending.equals(pos) || mc.level != sessionLevel) return;
        boolean matches = BlueprintPlan.matches(state, expected);
        gate.acknowledge(matches, tick);
        if (!matches) skip(pending);
        pending = null; expected = null; status = "Baut";
        if (!matches) refused();
    }
    public void render(WorldRenderContext context) {
        if (preview == null || !active() || mc.level != sessionLevel) return;
        var lines = Render3D.worldLines(context);
        Render3D.box(context, lines, new AABB(preview).inflate(0.005), waiting() ? Theme.WARNING : Theme.ACCENT_2);
        Render3D.flushWorld(context);
    }
}
