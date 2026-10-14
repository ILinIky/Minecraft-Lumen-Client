package com.example.examplemod.blueprint;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Surface sampling uses actual outline boxes, including slabs and stairs. */
public final class LazyBuildGeometry {
    private LazyBuildGeometry() {}
    public static Vec3 nearestFacePoint(AABB box, Direction face, Vec3 eye) {
        double x = inside(eye.x, box.minX, box.maxX);
        double y = inside(eye.y, box.minY, box.maxY);
        double z = inside(eye.z, box.minZ, box.maxZ);
        return switch (face) {
            case WEST -> new Vec3(box.minX, y, z);
            case EAST -> new Vec3(box.maxX, y, z);
            case DOWN -> new Vec3(x, box.minY, z);
            case UP -> new Vec3(x, box.maxY, z);
            case NORTH -> new Vec3(x, y, box.minZ);
            case SOUTH -> new Vec3(x, y, box.maxZ);
        };
    }
    private static double inside(double value, double min, double max) {
        double inset = Math.min(0.00001, (max - min) / 4);
        return Math.clamp(value, min + inset, max - inset);
    }
}
