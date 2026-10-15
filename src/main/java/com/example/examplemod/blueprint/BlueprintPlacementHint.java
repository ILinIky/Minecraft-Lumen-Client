package com.example.examplemod.blueprint;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.phys.shapes.CollisionContext;
import java.util.ArrayList;
import java.util.List;

/** Explains a target using loaded world data; never attempts placement or changes the inventory. */
public record BlueprintPlacementHint(List<String> reasons, BlockPos support) {
    public static BlueprintPlacementHint inspect(Minecraft mc, BlueprintPlan plan, int index) {
        var reasons = new ArrayList<String>();
        var cell = plan.blueprint.cells().get(index);
        BlockPos pos = plan.position(cell), support = null;
        BlockState state = plan.state(cell);
        if (mc.player == null || !plan.inDimension(mc.level) || !mc.level.hasChunkAt(pos))
            return new BlueprintPlacementHint(List.of("Bereich nicht geladen"), null);
        var status = plan.compare(mc.level, index, pos);
        if (status == BlueprintPlan.Status.UNCHECKED) return new BlueprintPlacementHint(List.of("Position nicht prüfbar"), null);
        if (status == BlueprintPlan.Status.WRONG_BLOCK) reasons.add("Platz belegt – falschen Block entfernen");
        if (status == BlueprintPlan.Status.WRONG_STATE) reasons.add("Ausrichtung / Form korrigieren (Soll und Ist unten)");
        if (status == BlueprintPlan.Status.UNSUPPORTED || state == null)
            return new BlueprintPlacementHint(List.of("Blockzustand in dieser Version nicht unterstützt"), null);
        var eye = mc.player.getEyePosition();
        double dx = Math.max(pos.getX() - eye.x, Math.max(0, eye.x - pos.getX() - 1));
        double dy = Math.max(pos.getY() - eye.y, Math.max(0, eye.y - pos.getY() - 1));
        double dz = Math.max(pos.getZ() - eye.z, Math.max(0, eye.z - pos.getZ() - 1));
        double reach = mc.player.blockInteractionRange();
        if (dx * dx + dy * dy + dz * dz > reach * reach) reasons.add("Außer Reichweite");
        if (status == BlueprintPlan.Status.MISSING) {
            if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER)
                reasons.add("Zweite Hälfte – an der unteren Blockposition setzen");
            BlockPos base = supportPosition(state, pos);
            boolean neighborsLoaded = true;
            for (Direction direction : Direction.values())
                if (!mc.level.hasChunkAt(pos.relative(direction))) neighborsLoaded = false;
            if (!neighborsLoaded) reasons.add("Befestigungsbereich nicht geladen");
            else if (!state.canSurvive(mc.level, pos)) {
                reasons.add("Befestigung fehlt oder Untergrund ungeeignet");
                support = base;
            }
            if (!mc.level.isUnobstructed(state, pos, CollisionContext.empty())) reasons.add("Platz durch Spieler oder Kreatur belegt");
            var item = BlueprintPlan.materialItem(state);
            boolean found = false;
            for (int slot = 0; slot < mc.player.getInventory().getContainerSize(); slot++)
                if (mc.player.getInventory().getItem(slot).is(item)) { found = true; break; }
            if (!mc.player.isCreative() && !found && item != net.minecraft.world.item.Items.AIR) reasons.add("Material fehlt im Inventar");
        }
        return new BlueprintPlacementHint(List.copyOf(reasons), support);
    }
    private static BlockPos supportPosition(BlockState state, BlockPos pos) {
        if (BlueprintPlan.needsCeiling(state)) return pos.above();
        if (state.hasProperty(BlockStateProperties.ATTACH_FACE)) {
            var face = state.getValue(BlockStateProperties.ATTACH_FACE);
            if (face == AttachFace.WALL) return pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
            return pos.below();
        }
        var block = state.getBlock();
        if (block instanceof WallTorchBlock || block instanceof LadderBlock || block instanceof WallSignBlock
                || block instanceof WallBannerBlock || block instanceof TripWireHookBlock)
            return pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
        if (block instanceof CocoaBlock) return pos.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        if (block instanceof AmethystClusterBlock) return pos.relative(state.getValue(BlockStateProperties.FACING).getOpposite());
        if (block instanceof BushBlock || block instanceof BaseRailBlock || block instanceof TorchBlock
                || block instanceof DoorBlock || block instanceof FallingBlock || block instanceof LanternBlock
                || block instanceof PointedDripstoneBlock) return pos.below();
        return null;
    }
}
