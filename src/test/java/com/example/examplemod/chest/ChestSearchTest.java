package com.example.examplemod.chest;

import java.util.ArrayList;
import java.util.List;

/** Ohne gestarteten Minecraft-Client ausführbare Prüfung der Suche im Kisten-Gedächtnis. */
public final class ChestSearchTest {
    private static int assertions;

    public static void main(String[] args) {
        ChestRecord truhe = chest(0, 64, 0, item("minecraft:diamond", "Diamant", 17), item("minecraft:cobblestone", "Bruchstein", 128));
        ChestRecord fass = chest(5, 64, 0, item("minecraft:diamond", "Diamant", 6));
        ChestRecord.StoredItem shulker = item("minecraft:shulker_box", "Shulker-Kiste", 1);
        shulker.inside = new ArrayList<>(List.of(item("minecraft:diamond", "Diamant", 40), item("minecraft:emerald", "Smaragd", 3)));
        ChestRecord lager = chest(9, 64, 0, shulker);
        List<ChestRecord> all = List.of(truhe, fass, lager);

        List<ChestSearch.Result> diamonds = ChestSearch.search(all, "diamant", true);
        check(diamonds.size() == 1, "Gleiche Sorte aus mehreren Behältern wird zusammengefasst");
        ChestSearch.Result result = diamonds.get(0);
        check(result.total() == 63 && result.hits().size() == 3, "Gesamtmenge inkl. Shulker-Inhalt");
        check(result.hits().get(0).chest() == lager && result.hits().get(0).inShulker(), "Größter Fundort zuerst, als Shulker markiert");
        check(!result.hits().get(1).inShulker(), "Lose Items sind nicht als Shulker markiert");

        check(ChestSearch.search(all, "diamant", false).get(0).total() == 23, "Ohne Shulker-Suche nur lose Items");
        check(!ChestSearch.search(all, "cobble", true).isEmpty(), "Suche auch über die englische ID");
        check(!ChestSearch.search(all, "shulker box", true).isEmpty(), "Unterstriche der ID als Leerzeichen");
        check(ChestSearch.search(all, "xyz", true).isEmpty(), "Kein Treffer");

        List<ChestSearch.Result> everything = ChestSearch.search(all, "", true);
        check(everything.size() == 4, "Leere Suche zeigt alle Sorten");
        check(everything.get(0).name().equals("Bruchstein"), "Sortiert nach Gesamtmenge");
        performance();
        System.out.println("ChestSearchTest: " + assertions + " assertions passed");
    }

    /**
     * Schlimmster Fall: so viele Behälter wie erlaubt, jeder voll mit verschiedenen Sorten, jeder zehnte mit
     * einer gefüllten Shulker-Kiste. Gemessen wird die Suche (läuft im Menü) und das Umwandeln in JSON
     * (läuft im Hintergrund). Die Grenzen sind großzügig, damit der Test auf langsamen Rechnern nicht wackelt.
     */
    private static void performance() {
        List<ChestRecord> lager = new ArrayList<>();
        for (int c = 0; c < ChestStore.MAX_RECORDS; c++) {
            ChestRecord chest = new ChestRecord(c, 64, c / 100, "minecraft:overworld", "minecraft:chest");
            List<ChestRecord.StoredItem> items = new ArrayList<>();
            for (int s = 0; s < 27; s++) {
                int type = (c * 31 + s * 7) % 400;
                ChestRecord.StoredItem item = item("minecraft:item_" + type, "Gegenstand " + type, 64);
                if (s == 0 && c % 10 == 0) {
                    item = item("minecraft:shulker_box", "Shulker-Kiste", 1);
                    item.inside = new ArrayList<>();
                    for (int i = 0; i < 27; i++) item.inside.add(item("minecraft:item_" + ((c + i) % 400), "Gegenstand " + ((c + i) % 400), 64));
                }
                items.add(item);
            }
            chest.items = items;
            lager.add(chest);
        }
        long start = System.nanoTime();
        List<ChestSearch.Result> first = ChestSearch.search(lager, "gegenstand 12", true);
        long firstMs = (System.nanoTime() - start) / 1_000_000;
        start = System.nanoTime();
        ChestSearch.search(lager, "gegenstand 3", true);
        long againMs = (System.nanoTime() - start) / 1_000_000;
        start = System.nanoTime();
        String json = new com.google.gson.Gson().toJson(lager);
        long jsonMs = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("Messung: %,d Behälter · erste Suche %d ms · weitere Suche %d ms · JSON %d ms (%,d KB, Hintergrund)%n",
                lager.size(), firstMs, againMs, jsonMs, json.length() / 1024);
        check(!first.isEmpty(), "Suche im Riesenlager findet Treffer");
        check(againMs < 1000, "Suche im Riesenlager bleibt unter einer Sekunde");
        check(!json.contains("searchText"), "Vorbereiteter Suchtext wird nicht mitgespeichert");
    }

    private static ChestRecord chest(int x, int y, int z, ChestRecord.StoredItem... items) {
        ChestRecord record = new ChestRecord(x, y, z, "minecraft:overworld", "minecraft:chest");
        record.items = new ArrayList<>(List.of(items));
        return record;
    }

    private static ChestRecord.StoredItem item(String id, String name, int count) {
        return new ChestRecord.StoredItem(id, name, count);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
