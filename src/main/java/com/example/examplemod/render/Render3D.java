package com.example.examplemod.render;

import com.example.examplemod.ExampleMod;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;


/** Zeichenhilfen für Linien, Boxen und Beschriftungen, die durch Wände sichtbar sind. */
public final class Render3D {
    private static final RenderPipeline LINES_THROUGH_WALLS = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "pipeline/esp_lines"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build();

    // MAIN_TARGET statt ITEM_ENTITY_TARGET: wir zeichnen nach dem Zusammensetzen der Fabulous-Puffer.
    public static final RenderType ESP_LINES = RenderType.create("examplemod_esp_lines", 1536, LINES_THROUGH_WALLS,
            RenderType.CompositeState.builder()
                    .setLineState(RenderStateShard.DEFAULT_LINE)
                    .setLayeringState(RenderStateShard.NO_LAYERING)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .createCompositeState(false));

    private static final RenderPipeline LINES_DEPTH = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "pipeline/world_lines"))
            .withDepthWrite(false)
            .build();

    /** Linien, die von Blöcken verdeckt werden (Lichtlevel, Chunk-Grenzen). */
    public static final RenderType WORLD_LINES = RenderType.create("examplemod_world_lines", 1536, LINES_DEPTH,
            RenderType.CompositeState.builder()
                    .setLineState(RenderStateShard.DEFAULT_LINE)
                    .setLayeringState(RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                    .setOutputState(RenderStateShard.MAIN_TARGET)
                    .createCompositeState(false));

    private Render3D() {}

    public static VertexConsumer worldLines(WorldRenderContext context) {
        return context.buffers().getBuffer(WORLD_LINES);
    }

    public static void flushWorld(WorldRenderContext context) {
        context.buffers().endBatch(WORLD_LINES);
    }

    public static VertexConsumer lines(WorldRenderContext context) {
        return context.buffers().getBuffer(ESP_LINES);
    }

    public static void flush(WorldRenderContext context) {
        context.buffers().endBatch(ESP_LINES);
    }

    /** Zeichnet eine Box; {@code box} ist in Weltkoordinaten. */
    public static void box(WorldRenderContext context, VertexConsumer consumer, AABB box, int argb) {
        PoseStack.Pose pose = context.poseStack().last();
        float x1 = context.rx(box.minX), y1 = context.ry(box.minY), z1 = context.rz(box.minZ);
        float x2 = context.rx(box.maxX), y2 = context.ry(box.maxY), z2 = context.rz(box.maxZ);

        line(pose, consumer, x1, y1, z1, x2, y1, z1, argb);
        line(pose, consumer, x2, y1, z1, x2, y1, z2, argb);
        line(pose, consumer, x2, y1, z2, x1, y1, z2, argb);
        line(pose, consumer, x1, y1, z2, x1, y1, z1, argb);

        line(pose, consumer, x1, y2, z1, x2, y2, z1, argb);
        line(pose, consumer, x2, y2, z1, x2, y2, z2, argb);
        line(pose, consumer, x2, y2, z2, x1, y2, z2, argb);
        line(pose, consumer, x1, y2, z2, x1, y2, z1, argb);

        line(pose, consumer, x1, y1, z1, x1, y2, z1, argb);
        line(pose, consumer, x2, y1, z1, x2, y2, z1, argb);
        line(pose, consumer, x2, y1, z2, x2, y2, z2, argb);
        line(pose, consumer, x1, y1, z2, x1, y2, z2, argb);
    }

    /** Gefüllter Quader für {@code RenderType.debugQuads()}; {@code b} ist bereits relativ zur Kamera. */
    public static void filledBox(PoseStack.Pose pose, VertexConsumer consumer, AABB b, int argb) {
        float x1 = (float) b.minX, y1 = (float) b.minY, z1 = (float) b.minZ;
        float x2 = (float) b.maxX, y2 = (float) b.maxY, z2 = (float) b.maxZ;
        quad(pose, consumer, argb, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
        quad(pose, consumer, argb, x1, y2, z1, x1, y2, z2, x2, y2, z2, x2, y2, z1);
        quad(pose, consumer, argb, x1, y1, z1, x1, y2, z1, x2, y2, z1, x2, y1, z1);
        quad(pose, consumer, argb, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
        quad(pose, consumer, argb, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
        quad(pose, consumer, argb, x2, y1, z1, x2, y2, z1, x2, y2, z2, x2, y1, z2);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer consumer, int argb,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        consumer.addVertex(pose, ax, ay, az).setColor(argb);
        consumer.addVertex(pose, bx, by, bz).setColor(argb);
        consumer.addVertex(pose, cx, cy, cz).setColor(argb);
        consumer.addVertex(pose, dx, dy, dz).setColor(argb);
    }

    /** Linie von der Bildschirmmitte zu einem Weltpunkt. */
    public static void tracer(WorldRenderContext context, VertexConsumer consumer, Vec3 target, int argb) {
        Vec3 look = Vec3.directionFromRotation(context.camera().getXRot(), context.camera().getYRot());
        line(context.poseStack().last(), consumer,
                (float) look.x, (float) look.y, (float) look.z,
                context.rx(target.x), context.ry(target.y), context.rz(target.z), argb);
    }

    public static void worldLine(WorldRenderContext context, VertexConsumer consumer, Vec3 from, Vec3 to, int argb) {
        line(context.poseStack().last(), consumer,
                context.rx(from.x), context.ry(from.y), context.rz(from.z),
                context.rx(to.x), context.ry(to.y), context.rz(to.z), argb);
    }

    /**
     * Der Linien-Shader extrudiert entlang der Normalen in Bildschirmrichtung, daher muss die
     * Normale die Linienrichtung sein (eine feste Normale liess Linien verschwinden).
     */
    public static void line(PoseStack.Pose pose, VertexConsumer consumer,
                            float x1, float y1, float z1, float x2, float y2, float z2, int argb) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 1.0E-4F) return;
        dx /= length; dy /= length; dz /= length;
        consumer.addVertex(pose, x1, y1, z1).setColor(argb).setNormal(pose, dx, dy, dz);
        consumer.addVertex(pose, x2, y2, z2).setColor(argb).setNormal(pose, dx, dy, dz);
    }

    /**
     * Zeichnet eine zur Kamera gedrehte Beschriftung. Weit entfernte Punkte werden näher
     * an die Kamera geholt und passend skaliert, damit sie lesbar bleiben.
     */
    public static void label(WorldRenderContext context, Vec3 worldPos, String text, int textColor, float baseScale) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Vec3 relative = context.relative(worldPos);
        double distance = relative.length();
        double maxDistance = 48.0;
        float scale = baseScale * (float) Math.max(1.0, distance / 8.0);
        if (distance > maxDistance) {
            relative = relative.scale(maxDistance / distance);
            scale = baseScale * (float) (maxDistance / 8.0);
        }

        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();
        poseStack.translate(relative.x, relative.y, relative.z);
        poseStack.mulPose(context.camera().rotation());
        poseStack.scale(scale, -scale, scale);
        Matrix4f matrix = poseStack.last().pose();
        float x = -font.width(text) / 2.0F;
        font.drawInBatch(text, x, 0, textColor, false, matrix, context.buffers(),
                Font.DisplayMode.SEE_THROUGH, 0x90101018, LightTexture.FULL_BRIGHT);
        poseStack.popPose();
    }
}
