package com.example.examplemod.feature;

import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.Setting;
import com.example.examplemod.render.Render3D;
import com.example.examplemod.render.WorldRenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Markiert Blöcke, auf denen Monster spawnen können: rot = jederzeit (kein Licht),
 * gelb = nur nachts (Himmelslicht, aber kein Fackellicht).
 */
public class LightLevel extends Module {
    private static final int DANGER = 0xFFF87171;
    private static final int NIGHT = 0xFFFBBF24;

    private final Setting.Slider radius;
    private final Setting.Bool showNight;
    private List<Spot> spots = List.of();
    private int ticksUntilScan;

    private record Spot(BlockPos pos, int color) {}

    public LightLevel() {
        super("light_level", "Lichtlevel", "Zeigt, wo Monster spawnen können", Category.RENDER);
        radius = add(new Setting.Slider("Radius", "Bereich um dich herum", 16, 8, 32, 1, "Bl.").onChange(() -> ticksUntilScan = 0));
        showNight = add(new Setting.Bool("Nachts gefährlich", "Gelb markieren, wo nur nachts Monster spawnen", true));
    }

    @Override
    protected void onDisable() { spots = List.of(); }

    @Override
    public void onTick() {
        if (mc.player == null || mc.level == null || --ticksUntilScan > 0) return;
        ticksUntilScan = 10;
        int r = radius.getInt();
        BlockPos center = mc.player.blockPosition();
        List<Spot> result = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                for (int y = -8; y <= 8; y++) {
                    pos.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    BlockState state = mc.level.getBlockState(pos);
                    if (!state.getCollisionShape(mc.level, pos).isEmpty() || !state.getFluidState().isEmpty()) continue;
                    below.set(pos.getX(), pos.getY() - 1, pos.getZ());
                    BlockState ground = mc.level.getBlockState(below);
                    if (!ground.isValidSpawn(mc.level, below, EntityType.ZOMBIE)) continue;
                    if (mc.level.getBrightness(LightLayer.BLOCK, pos) > 0) continue;
                    boolean sky = mc.level.getBrightness(LightLayer.SKY, pos) > 0;
                    if (sky && !showNight.get()) continue;
                    result.add(new Spot(pos.immutable(), sky ? NIGHT : DANGER));
                }
            }
        }
        spots = result;
    }

    @Override
    public String getHudInfo() { return String.valueOf(spots.size()); }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        if (spots.isEmpty()) return;
        VertexConsumer lines = Render3D.worldLines(context);
        PoseStack.Pose pose = context.poseStack().last();
        for (Spot spot : spots) {
            float x1 = context.rx(spot.pos.getX() + 0.2), x2 = context.rx(spot.pos.getX() + 0.8);
            float z1 = context.rz(spot.pos.getZ() + 0.2), z2 = context.rz(spot.pos.getZ() + 0.8);
            float y = context.ry(spot.pos.getY() + 0.02);
            Render3D.line(pose, lines, x1, y, z1, x2, y, z2, spot.color);
            Render3D.line(pose, lines, x1, y, z2, x2, y, z1, spot.color);
        }
        Render3D.flushWorld(context);
    }
}
