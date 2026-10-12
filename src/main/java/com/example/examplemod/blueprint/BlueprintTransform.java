package com.example.examplemod.blueprint;

/** Reflection first, then clockwise rotation, keeping the lower bounding corner at the anchor. */
public record BlueprintTransform(int sizeX, int sizeZ, int turns, boolean mirror) {
    public BlueprintTransform { turns = Math.floorMod(turns, 4); }
    public record XZ(int x, int z) {}

    public XZ apply(int x, int z) {
        if (mirror) x = sizeX - 1 - x;
        return switch (turns) {
            case 1 -> new XZ(sizeZ - 1 - z, x);
            case 2 -> new XZ(sizeX - 1 - x, sizeZ - 1 - z);
            case 3 -> new XZ(z, sizeX - 1 - x);
            default -> new XZ(x, z);
        };
    }

    public XZ inverse(int x, int z) {
        XZ value = switch (turns) {
            case 1 -> new XZ(z, sizeZ - 1 - x);
            case 2 -> new XZ(sizeX - 1 - x, sizeZ - 1 - z);
            case 3 -> new XZ(sizeX - 1 - z, x);
            default -> new XZ(x, z);
        };
        return mirror ? new XZ(sizeX - 1 - value.x, value.z) : value;
    }

    public int width() { return turns % 2 == 0 ? sizeX : sizeZ; }
    public int depth() { return turns % 2 == 0 ? sizeZ : sizeX; }
}
