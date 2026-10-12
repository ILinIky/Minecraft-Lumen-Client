package com.example.examplemod.feature;

import com.example.examplemod.compat.RenderTypes;
import com.example.examplemod.gui.Theme;
import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.Setting;
import com.example.examplemod.render.Render3D;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.client.event.RenderHighlightEvent;

/** Ersetzt die Vanilla-Umrandung des anvisierten Blocks durch eine in Akzentfarbe und wählbarer Dicke. */
public class BlockOutline extends Module {
    private final Setting.Mode color;
    private final Setting.Slider width;
    private final Setting.Slider opacity;
    private final Setting.Bool fill;
    private final Setting.Slider fillOpacity;

    public BlockOutline() {
        super("block_outline", "Blockumrandung", "Farbe und Dicke der Umrandung am anvisierten Block", Category.RENDER);
        enabledByDefault();
        color = add(new Setting.Mode("Farbe", "Akzent und Verlauf folgen dem Design-Modul",
                "Akzent", "Akzent", "Verlauf", "Zweitakzent", "Weiß", "Schwarz"));
        width = add(new Setting.Slider("Dicke", "Linienstärke (Vanilla etwa 2.5)", 3, 1, 10, 0.5, "px"));
        opacity = add(new Setting.Slider("Deckkraft", "Sichtbarkeit der Linien", 100, 10, 100, 5, "%"));
        fill = add(new Setting.Bool("Füllung", "Block zusätzlich halbtransparent einfärben", false));
        fillOpacity = add(new Setting.Slider("Füllstärke", "Deckkraft der Füllung", 20, 5, 60, 5, "%")
                .visibleWhen(fill::get));
    }

    @Override
    public boolean showInModuleList() { return false; }

    private int baseColor() {
        return switch (color.get()) {
            case "Verlauf" -> Theme.accentWave(0);
            case "Zweitakzent" -> Theme.ACCENT_2;
            case "Weiß" -> 0xFFFFFFFF;
            case "Schwarz" -> 0xFF000000;
            default -> Theme.ACCENT;
        };
    }

    private RenderType lineType() {
        // Wie Vanilla mit der Fensterbreite skalieren, damit die Dicke bei jeder Auflösung gleich wirkt.
        float scale = Math.max(1.0F, mc.getWindow().getWidth() / 1920.0F);
        return RenderTypes.outline(width.get() * scale);
    }

    /** Aus dem Forge-Event; {@code true} unterdrückt die Vanilla-Umrandung. */
    public boolean render(RenderHighlightEvent.Block event) {
        ClientLevel level = mc.level;
        if (!isEnabled() || level == null) return false;
        BlockHitResult hit = event.getTarget();
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        Camera camera = event.getCamera();
        VoxelShape shape = state.getShape(level, pos, CollisionContext.of(camera.getEntity()));
        if (shape.isEmpty()) return false;

        Vec3 cam = camera.getPosition();
        double ox = pos.getX() - cam.x, oy = pos.getY() - cam.y, oz = pos.getZ() - cam.z;
        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int base = baseColor();

        if (fill.get()) {
            int fillColor = Theme.alpha(base | 0xFF000000, (float) fillOpacity.get() / 100F);
            RenderType quadsType = RenderTypes.filledQuads();
            VertexConsumer quads = buffers.getBuffer(quadsType);
            for (AABB box : shape.toAabbs()) {
                Render3D.filledBox(pose, quads, box.move(ox, oy, oz).inflate(0.002), fillColor);
            }
            if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch(quadsType);
        }

        int lineColor = Theme.alpha(base | 0xFF000000, (float) opacity.get() / 100F);
        RenderType type = lineType();
        VertexConsumer lines = buffers.getBuffer(type);
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> Render3D.line(pose, lines,
                (float) (x1 + ox), (float) (y1 + oy), (float) (z1 + oz),
                (float) (x2 + ox), (float) (y2 + oy), (float) (z2 + oz), lineColor));
        if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch(type);
        return true;
    }
}
