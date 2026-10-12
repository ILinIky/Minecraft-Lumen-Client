package com.example.examplemod.chest;

import java.util.ArrayList;
import java.util.List;

/**
 * Gemerkter Behälter: Ort, Art und zuletzt gesehener Inhalt. Felder öffentlich und einfach gehalten,
 * damit Gson sie direkt speichern kann (eine Datei pro Welt bzw. Server).
 */
public class ChestRecord {
    /** Dimension der Endertruhe: ihr Inhalt gehört zum Spieler, nicht zu einem Ort. */
    public static final String ENDER_CHEST = "lumen:enderchest";

    public int x, y, z;
    public String dimension;
    /** Block-ID, z.B. minecraft:chest. */
    public String block;
    /** Zweite Hälfte einer Doppeltruhe, sonst {@code null}. */
    public int[] other;
    /** Zuletzt gesehen (Systemzeit in Millisekunden). */
    public long seen;
    public List<StoredItem> items = new ArrayList<>();

    public ChestRecord() {}

    public ChestRecord(int x, int y, int z, String dimension, String block) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.dimension = dimension;
        this.block = block;
    }

    public boolean enderChest() { return ENDER_CHEST.equals(dimension); }

    /** Schlüssel für "dieselbe Kiste": Dimension und Position. */
    public String key() { return dimension + "@" + x + "," + y + "," + z; }

    public int totalItems() {
        int total = 0;
        for (StoredItem item : items) total += item.count;
        return total;
    }

    /** Eine Item-Sorte im Behälter; bei Shulker-Kisten mit deren Inhalt. */
    public static class StoredItem {
        /** Item-ID, z.B. minecraft:diamond. */
        public String id;
        /** Angezeigter Name zum Zeitpunkt des Öffnens (auch eigene Namen). */
        public String name;
        public int count;
        /** Inhalt einer Shulker-Kiste o.ä., sonst {@code null}. */
        public List<StoredItem> inside;
        /** Vorbereiteter Suchtext (klein geschrieben), wird nicht gespeichert. */
        transient String searchText;

        public StoredItem() {}

        public StoredItem(String id, String name, int count) {
            this.id = id;
            this.name = name;
            this.count = count;
        }
    }
}
