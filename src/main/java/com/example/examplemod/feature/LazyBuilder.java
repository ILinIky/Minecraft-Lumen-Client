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

/** Toggle-to-build through vanilla useItemOn only. Never rotates, teleports or breaks blocks. */
public final class LazyBuilder {
    private final Minecraft mc = Minecraft.getInstance();
    private final Blueprints feature;
    private final LazyBuildGate gate = new LazyBuildGate();
    private BlueprintPlan sessionPlan;
    private ClientLevel sessionLevel;
    private BlockPos origin, pending, preview;
    private BlockState expected;
    private int turns, layer, acceptedVersion, originalSlot = -1, selectedSlot = -1, prepTick;
    private boolean mirror, shortcutDown;
    private BlueprintPlan.Layers layers;
    private BlueprintPlan.Material focus;
    private long tick;
    private ItemStack pendingInventory = ItemStack.EMPTY;
    private long inventoryDeadline;
    private String status = "Bereit";
    private record Candidate(int index, BlockPos pos, BlockState state, double distance) {}

    public LazyBuilder(Blueprints feature) { this.feature = feature; }
    public String status() { return status; }
    public int confirmed() { return gate.confirmed(); }
    public boolean waiting() { return gate.state() == LazyBuildGate.State.WAITING; }
    public boolean active() { return gate.active(); }
    private boolean held() { return ClientKeybinds.BLUEPRINT_BUILD != null && ClientKeybinds.BLUEPRINT_BUILD.isDown(); }
    public void cancel() {
        restoreSlot(); gate.release(); pending = preview = null; expected = null; sessionPlan = null;
        shortcutDown = held(); prepTick = 0; status = "Aus";
        pendingInventory = ItemStack.EMPTY;
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
        if (!feature.lazyBuild.get()) { cancel(); status = "Lazy-Bauen im Bauplan-Menü aktivieren"; return; }
        if (!active() && !pressed) return;
        if (mc.player == null || mc.level == null || mc.gameMode == null || mc.screen != null || !mc.isWindowActive()
                || mc.isPaused() || Freecam.active() || !mc.player.isAlive() || mc.player.isSpectator()
                || !mc.player.mayBuild() || mc.player.isPassenger()) {
            stop("Bauen ausgeschaltet"); return;
        }
        if (feature.loading()) { stop("Neuer Bauplan wird geladen"); return; }
        if (plan == null || !plan.inDimension(mc.level) || plan.layers == BlueprintPlan.Layers.ALL || plan.focus != null) {
            stop("Eine Ebene ohne Materialfokus wählen"); return;
        }
        if (mc.player.containerMenu != mc.player.inventoryMenu || !mc.player.inventoryMenu.getCarried().isEmpty()) {
            stop("Item am Mauszeiger ablegen und Inventar schließen"); return;
        }
        if (pressed && gate.toggle()) {
            sessionPlan = plan; sessionLevel = mc.level; origin = plan.origin(); turns = plan.turns(); mirror = plan.mirrored();
            layer = plan.layer; layers = plan.layers; focus = plan.focus; acceptedVersion = plan.acceptedVersion();
            originalSlot = selectedSlot = mc.player.getInventory().getSelectedSlot();
        }
        if (plan != sessionPlan || mc.level != sessionLevel || !plan.origin().equals(origin) || plan.turns() != turns
                || plan.mirrored() != mirror || plan.layer != layer || plan.layers != layers || plan.focus != focus
                || plan.acceptedVersion() != acceptedVersion) { stop("Bauplan oder Ebene geändert"); return; }
        if (mc.player.getInventory().getSelectedSlot() != selectedSlot) { stop("Hotbar manuell gewechselt"); return; }
        if (gate.timedOut(tick)) { stop("Keine Serverbestätigung"); return; }
        if (waiting()) { status = "Warte auf Server …"; return; }
        if (!pendingInventory.isEmpty()) {
            if (ItemStack.isSameItemSameComponents(mc.player.getMainHandItem(), pendingInventory) && !mc.player.getMainHandItem().isEmpty())
                pendingInventory = ItemStack.EMPTY;
            else {
                if (tick >= inventoryDeadline) stop("Materialtausch nicht bestätigt · Inventar prüfen");
                else status = "Warte auf Materialtausch …";
                return;
            }
        }
        // Vanilla input has priority; do not compete with a manual right click or mining.
        if (mc.player.isUsingItem() || mc.gameMode.isDestroying() || mc.options.keyAttack.isDown() || mc.options.keyUse.isDown()) {
            preview = null; prepTick = (int) tick + 4;
            status = "An · manuelles Bauen hat Vorrang"; return;
        }
        if (!gate.maySend(tick) || tick < prepTick) return;
        // Up to five short planning passes per second; no per-frame world scans.
        prepTick = (int) tick + 4;
        Candidate choice = null;
        BlockHitResult hit = null;
        boolean missingMaterial = false, unsupported = false, missing = false;
        ItemStack missingStack = ItemStack.EMPTY;
        Vec3 eye = mc.player.getEyePosition();
        double reach = mc.player.blockInteractionRange();
        var candidates = new ArrayList<Candidate>();
        var nearby = plan.nearby(mc.player.blockPosition(), (int) Math.ceil(reach) + 1);
        for (int n = 0; n < nearby.size(); n++) {
            int index = nearby.getInt(n);
            var cell = plan.blueprint.cells().get(index);
            if (cell.y() != plan.layer || plan.ignored(index) || plan.accepted(index) || plan.unsupported(cell)) continue;
            BlockPos pos = plan.position(cell);
            double distance = eye.distanceToSqr(Vec3.atCenterOf(pos));
            if (distance > (reach + 0.9) * (reach + 0.9)) continue;
            boolean aimed = mc.hitResult instanceof BlockHitResult aim && aim.getType() == HitResult.Type.BLOCK
                    && (aim.getBlockPos().equals(pos) || aim.getBlockPos().relative(aim.getDirection()).equals(pos));
            if (!aimed && feature.lazyMode.is("Im Blickfeld") && Vec3.atCenterOf(pos).subtract(eye).normalize().dot(mc.player.getViewVector(1)) < 0.9) continue;
            if (plan.compare(mc.level, index, pos) != BlueprintPlan.Status.MISSING) continue;
            missing = true;
            BlockState state = plan.state(cell);
            if (!supported(state)) { unsupported = true; continue; }
            candidates.add(new Candidate(index, pos, state, aimed ? -1 : distance));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::distance));
        int[] rayBudget = {48};
        int inspected = 0;
        for (var candidate : candidates) {
            if (++inspected > 64 || rayBudget[0] <= 0) break;
            ItemStack wanted = new ItemStack(BlueprintPlan.materialItem(candidate.state));
            if (findItem(wanted) < 0) { missingMaterial = true; if (missingStack.isEmpty()) missingStack = wanted; continue; }
            BlockHitResult attempt = placement(candidate, wanted, reach, rayBudget);
            if (attempt != null) { choice = candidate; hit = attempt; break; }
        }
        if (choice == null) {
            preview = null;
            if (missingMaterial) materialPause(plan, missingStack);
            else if (unsupported && candidates.isEmpty()) stop("Spezialblöcke bitte manuell setzen");
            else status = missing ? "Näher herangehen / passende Fläche ansehen" : "Keine offene Stelle hier";
            return;
        }
        preview = choice.pos;
        ItemStack wanted = new ItemStack(BlueprintPlan.materialItem(choice.state));
        if (!selectItem(wanted)) return;
        // Actual selected stack and placement context, not a synthetic item, govern the final check.
        hit = placement(choice, mc.player.getMainHandItem(), reach, new int[]{18});
        if (hit == null) { status = "Ausrichtung oder Platzierung passt noch nicht"; return; }
        pending = choice.pos; expected = choice.state;
        gate.submitted(tick, feature.lazyTempo.is("Ruhig") ? 10 : feature.lazyTempo.is("Zügig") ? 4 : 6);
        status = "Setze " + expected.getBlock().getName().getString();
        var result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        if (!result.consumesAction()) { stop("Platzierung nicht möglich"); return; }
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    /** Deliberate single-block whitelist; complex/multi-block/interactive placements remain manual. */
    private static boolean supported(BlockState state) {
        if (state == null || state.hasBlockEntity() || state.getBlock() instanceof FallingBlock) return false;
        Block block = state.getBlock();
        if (!(block.asItem() instanceof BlockItem)) return false;
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) return false;
        if (state.hasProperty(BlockStateProperties.SLAB_TYPE) && state.getValue(BlockStateProperties.SLAB_TYPE) == SlabType.DOUBLE) return false;
        return block.getClass() == Block.class || block instanceof HalfTransparentBlock || block instanceof RotatedPillarBlock
                || block instanceof StairBlock || block instanceof SlabBlock;
    }
    private BlockHitResult placement(Candidate target, ItemStack stack, double reach, int[] budget) {
        if (!(stack.getItem() instanceof BlockItem item) || !mc.level.getWorldBorder().isWithinBounds(target.pos)
                || !mc.level.hasChunk(target.pos.getX() >> 4, target.pos.getZ() >> 4)
                || !mc.level.getFluidState(target.pos).isEmpty()) return null;
        Vec3 eye = mc.player.getEyePosition();
        // The ordinary crosshair hit is already ray-traced by Minecraft. Prefer precisely that hit.
        if (mc.hitResult instanceof BlockHitResult aim && aim.getType() == HitResult.Type.BLOCK
                && mc.level.hasChunk(aim.getBlockPos().getX() >> 4, aim.getBlockPos().getZ() >> 4)
                && supported(mc.level.getBlockState(aim.getBlockPos()))
                && validPlacement(target, stack, item, aim, reach)) return aim;
        for (Direction face : Direction.values()) {
            BlockPos support = target.pos.relative(face.getOpposite());
            if (!mc.level.hasChunk(support.getX() >> 4, support.getZ() >> 4)) continue;
            BlockState base = mc.level.getBlockState(support);
            if (!supported(base) || base.canBeReplaced()) continue;
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
    private void materialPause(BlueprintPlan plan, ItemStack wanted) {
        int available = 0;
        for (int i = 0; i < 36; i++) {
            var stack = mc.player.getInventory().getItem(i);
            if (ItemStack.isSameItemSameComponents(stack, wanted)) available += stack.getCount();
        }
        int missing = Math.max(1, plan.missingOnLayer(wanted.getItem()) - available);
        String prefix = !plan.layerProgress().checked() || plan.layerProgress().unknown() > 0 ? "Mind. " : "";
        stop(prefix + missing + " " + wanted.getHoverName().getString() + " fehlen · Ebene " + (plan.layer + 1));
    }
    private boolean selectItem(ItemStack wanted) {
        var inventory = mc.player.getInventory();
        if (ItemStack.isSameItemSameComponents(mc.player.getMainHandItem(), wanted) && !mc.player.getMainHandItem().isEmpty()) return true;
        int slot = findItem(wanted);
        if (slot < 0) { materialPause(sessionPlan, wanted); return false; }
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
        prepTick = (int) tick + 2; status = "Baumaterial auswählen …";
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
        gate.acknowledge(matches);
        if (!matches) stop("Server hat die Platzierung nicht bestätigt");
        else { pending = null; expected = null; status = gate.confirmed() + " Blöcke bestätigt"; }
    }
    public void render(WorldRenderContext context) {
        if (preview == null || !active() || mc.level != sessionLevel) return;
        var lines = Render3D.worldLines(context);
        Render3D.box(context, lines, new AABB(preview).inflate(0.005), waiting() ? Theme.WARNING : Theme.ACCENT_2);
        Render3D.flushWorld(context);
    }
}
