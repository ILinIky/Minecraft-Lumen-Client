package com.example.examplemod.chest;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Suche über die gemerkten Behälter. Ohne Minecraft-Abhängigkeit, daher testbar. */
public final class ChestSearch {
    /** Ein Behälter, in dem die Sorte liegt. */
    public record Hit(ChestRecord chest, int count, boolean inShulker) {}

    /** Eine Sorte mit Gesamtmenge und allen Fundorten (größte zuerst). */
    public record Result(String id, String name, int total, List<Hit> hits) {}

    private ChestSearch() {}

    /**
     * @param query         Teil des Namens oder der ID; leer = alles (zum Stöbern)
     * @param includeInside auch in Shulker-Kisten innerhalb der Behälter suchen
     */
    public static List<Result> search(Collection<ChestRecord> chests, String query, boolean includeInside) {
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Map<String, Builder> groups = new LinkedHashMap<>();
        for (ChestRecord chest : chests) {
            for (ChestRecord.StoredItem item : chest.items) {
                if (matches(item, needle)) groups.computeIfAbsent(groupKey(item), k -> new Builder(item)).add(chest, item.count, false);
                if (includeInside && item.inside != null) {
                    for (ChestRecord.StoredItem inner : item.inside) {
                        if (matches(inner, needle)) {
                            groups.computeIfAbsent(groupKey(inner), k -> new Builder(inner)).add(chest, inner.count, true);
                        }
                    }
                }
            }
        }
        List<Result> results = new ArrayList<>();
        for (Builder builder : groups.values()) results.add(builder.build());
        results.sort(Comparator.comparingInt(Result::total).reversed().thenComparing(Result::name, String.CASE_INSENSITIVE_ORDER));
        return results;
    }

    private static String groupKey(ChestRecord.StoredItem item) { return item.id + "|" + item.name; }

    static boolean matches(ChestRecord.StoredItem item, String needle) {
        if (needle.isEmpty()) return true;
        String text = item.searchText;
        if (text == null) {
            // Einmal je Item vorbereiten: Name, ID und ID mit Leerzeichen, getrennt durch Zeilenumbrüche.
            String id = item.id == null ? "" : item.id;
            String path = id.substring(id.indexOf(':') + 1).replace('_', ' ');
            text = ((item.name == null ? "" : item.name) + '\n' + id + '\n' + path).toLowerCase(Locale.ROOT);
            item.searchText = text;
        }
        return text.contains(needle);
    }

    private static final class Builder {
        private final String id, name;
        private int total;
        /** Je Behälter zusammengefasst (gleiche Sorte lose und in Shulker). */
        private final Map<ChestRecord, int[]> perChest = new LinkedHashMap<>();
        private final Map<ChestRecord, Boolean> shulker = new LinkedHashMap<>();

        Builder(ChestRecord.StoredItem first) {
            this.id = first.id;
            this.name = first.name == null ? first.id : first.name;
        }

        void add(ChestRecord chest, int count, boolean inShulker) {
            total += count;
            perChest.computeIfAbsent(chest, c -> new int[1])[0] += count;
            if (inShulker) shulker.put(chest, true);
        }

        Result build() {
            List<Hit> hits = new ArrayList<>();
            perChest.forEach((chest, count) -> hits.add(new Hit(chest, count[0], shulker.getOrDefault(chest, false))));
            hits.sort(Comparator.comparingInt(Hit::count).reversed());
            return new Result(id, name, total, List.copyOf(hits));
        }
    }
}
