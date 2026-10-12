package com.example.examplemod.waypoint;

import net.minecraft.world.phys.Vec3;

/** Gespeicherter Ort. Die Feldnamen entsprechen dem bisherigen JSON-Format der Teleport-Dateien. */
public class Waypoint {
    public static final int[] PALETTE = {0xFF8B5CF6, 0xFF22D3EE, 0xFF34D399, 0xFFFBBF24, 0xFFF472B6, 0xFF60A5FA, 0xFFF87171, 0xFFF97316};
    public static final String[] ICONS = {"◆", "★", "⌂", "⚑", "✦", "●", "♥", "☠", "⚒", "✿"};

    public int id;
    public double x, y, z;
    public float yaw, pitch;
    public String dimension;
    public String name;
    /** Fehlend/leer in alten Dateien bedeutet: ohne Gruppe. */
    public String group;
    public long created;
    /** 0 = automatische Farbe. */
    public int color;
    public String icon;
    public boolean death;
    /** Kompass-Ziel dieser Welt; {@code null} statt false, damit es nur beim Ziel in der Datei steht. */
    public Boolean target;

    public Waypoint() {}

    public Waypoint(int id, String name, Vec3 pos, float yaw, float pitch, String dimension) {
        this.id = id;
        this.name = name;
        this.x = pos.x;
        this.y = pos.y;
        this.z = pos.z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.dimension = dimension;
        this.created = System.currentTimeMillis();
    }

    public Vec3 position() { return new Vec3(x, y, z); }

    public String groupName() { return WaypointGroups.normalize(group); }
    public String groupLabel() { return groupName().isEmpty() ? "Ohne Gruppe" : groupName(); }

    public String displayName() {
        return name == null || name.isBlank() ? "Wegpunkt #" + id : name;
    }

    public String dimensionName() { return dimensionName(dimension); }

    public static String dimensionName(String dimension) {
        if (dimension == null) return "?";
        if (dimension.endsWith("overworld")) return "Oberwelt";
        if (dimension.endsWith("the_nether")) return "Nether";
        if (dimension.endsWith("the_end")) return "End";
        return dimension.substring(dimension.lastIndexOf(':') + 1);
    }

    public static int dimensionColor(String dimension) {
        if (dimension == null) return 0xFFA3A6B8;
        if (dimension.endsWith("the_nether")) return 0xFFF87171;
        if (dimension.endsWith("the_end")) return 0xFFC084FC;
        return 0xFF34D399;
    }

    /** Feste Farbe pro Wegpunkt, damit Marker unterscheidbar bleiben. */
    public int color() { return color != 0 ? color : death ? 0xFFF87171 : PALETTE[Math.floorMod(id, PALETTE.length)]; }

    public String icon() { return icon != null ? icon : death ? "☠" : "◆"; }
}
