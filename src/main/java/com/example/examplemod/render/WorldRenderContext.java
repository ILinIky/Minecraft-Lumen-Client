package com.example.examplemod.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

/**
 * Alles, was Module zum Zeichnen in der Welt brauchen. Koordinaten werden relativ zur
 * Kamera angegeben ({@link #relative}), damit auch weit vom Ursprung keine Genauigkeit verloren geht.
 */
public record WorldRenderContext(PoseStack poseStack, Camera camera, Vec3 cameraPos,
                                 MultiBufferSource.BufferSource buffers, float partialTick) {

    public float rx(double x) { return (float) (x - cameraPos.x); }
    public float ry(double y) { return (float) (y - cameraPos.y); }
    public float rz(double z) { return (float) (z - cameraPos.z); }

    public Vec3 relative(Vec3 world) { return world.subtract(cameraPos); }
}
