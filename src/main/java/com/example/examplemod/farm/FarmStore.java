package com.example.examplemod.farm;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.gui.Notifications;
import com.example.examplemod.module.ClientConfig;
import com.example.examplemod.waypoint.WaypointStore;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Same world key and atomic JSON writer as waypoints/chests; serial background I/O. */
public final class FarmStore {
    private static final Gson GSON = new Gson();
    private static final Map<String, FarmRecord> FARMS = new LinkedHashMap<>();
    private static final Map<String, Set<FarmRecord.Plot>> OCCUPIED = new HashMap<>();
    private static final Map<String, Map<Integer, FarmAreas.Bounds>> AREAS = new HashMap<>();
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), r -> new Thread(r, "Lumen-Farmen-Speichern"));
    static { IO.allowCoreThreadTimeOut(true); }
    private static String world;
    private static int generation;
    private static boolean dirty, ready;
    private static long nextSave;
    private FarmStore() {}
    public static List<FarmRecord> all() { return List.copyOf(FARMS.values()); }
    public static FarmRecord get(String id) { return FARMS.get(id); }
    public static boolean ready() { return ready && world != null; }
    /** Includes directly connected extensions using the discovery connectivity rule. */
    public static boolean overlaps(String dimension, List<FarmRecord.Plot> plots) {
        Set<FarmRecord.Plot> existing = OCCUPIED.getOrDefault(dimension, Set.of());
        return FarmDetection.touchesKnown(plots, existing);
    }
    public static boolean contains(String dimension, List<FarmRecord.Plot> plots) {
        var occupied = OCCUPIED.getOrDefault(dimension, Set.of());
        var manual = FARMS.values().stream().filter(f -> f.area() != null && f.dimension().equals(dimension)).toList();
        return plots.stream().allMatch(p -> occupied.contains(p) || manual.stream().anyMatch(f -> f.area().contains(p)));
    }
    public static boolean nearby(String dimension, List<FarmRecord.Plot> plots, int gap) {
        return nearby(dimension, FarmAreas.bounds(plots), gap);
    }
    public static boolean nearby(String dimension, Map<Integer, FarmAreas.Bounds> bounds, int gap) {
        return FARMS.values().stream().anyMatch(f -> f.area() == null && f.dimension().equals(dimension)
                && FarmAreas.near(bounds, AREAS.get(f.id()), gap));
    }
    public static boolean inManual(String dimension, FarmRecord.Plot pos) {
        return FARMS.values().stream().anyMatch(f -> f.area() != null && f.dimension().equals(dimension) && f.area().contains(pos));
    }
    public static boolean acceptArea(FarmRecord record, int gap) {
        if (!ready() || !record.valid()) return false;
        if (FARMS.values().stream().anyMatch(f -> f.area() != null && f.dimension().equals(record.dimension())
                && record.plots().stream().anyMatch(f.area()::contains))) return false;
        var candidates = new ArrayList<>(all()); candidates.add(record);
        try {
            var merged = FarmAreas.merge(candidates, gap);
            if (merged.size() > FarmRecord.MAX_FARMS) return false;
            replaceAreas(merged); return true;
        } catch (IllegalArgumentException e) { return false; }
    }
    public static boolean acceptManual(FarmRecord record) {
        if (!ready() || !record.valid() || record.area() == null) return false;
        var positions = new HashSet<>(record.plots());
        var keep = new ArrayList<FarmRecord>();
        boolean notified = record.harvestNotified();
        for (var farm : FARMS.values()) {
            if (!farm.dimension().equals(record.dimension())) { keep.add(farm); continue; }
            boolean intersects = farm.plots().stream().anyMatch(record.area()::contains);
            if (farm.area() != null) {
                var a = farm.area(); var b = record.area();
                intersects |= a.minX() <= b.maxX() && b.minX() <= a.maxX() && a.minY() <= b.maxY()
                        && b.minY() <= a.maxY() && a.minZ() <= b.maxZ() && b.minZ() <= a.maxZ();
            }
            if (!intersects) keep.add(farm);
            else {
                if (!positions.containsAll(farm.plots())) return false;
                notified |= farm.harvestNotified();
            }
        }
        if (keep.size() >= FarmRecord.MAX_FARMS) return false;
        keep.add(record.withHarvestNotified(notified)); replaceAreas(keep); return true;
    }
    public static boolean observe(FarmRecord previous, FarmRecord record) {
        if (!ready() || FARMS.get(previous.id()) != previous || !record.valid()) return false;
        if (previous.area() == null && previous.plots() != record.plots()) return false;
        if (!Objects.equals(previous.area(), record.area())) return false;
        if (previous.plots() != record.plots()) {
            OCCUPIED.computeIfAbsent(record.dimension(), key -> new HashSet<>()).addAll(record.plots());
            AREAS.put(record.id(), FarmAreas.bounds(record.plots()));
        }
        FARMS.put(record.id(), record); dirty = true; return true;
    }
    public static void consolidate(int gap) {
        if (!ready()) return;
        try {
            var merged = FarmAreas.merge(all(), gap);
            if (merged.size() != FARMS.size()) {
                replaceAreas(merged);
                Notifications.info("Farmbereiche", "Benachbarte Beete zu gemeinsamen Farmen verbunden");
            }
        } catch (IllegalArgumentException e) {
            Notifications.warning("Farmbereiche", "Bereich zu groß: maximal 4096 Pflanzplätze. Abstand verkleinern.");
        }
    }
    private static void replaceAreas(List<FarmRecord> records) {
        // Queue a recoverable snapshot before the merged version, on the same serial I/O worker.
        var before = all(); Path backup = file(world).resolveSibling(file(world).getFileName() + ".before-areas-" + UUID.randomUUID() + ".bak");
        IO.execute(() -> {
            JsonObject root = new JsonObject(); root.addProperty("version", 2); root.add("farms", GSON.toJsonTree(before));
            ClientConfig.writeAtomically(backup, GSON.toJson(root));
        });
        FARMS.clear(); OCCUPIED.clear(); AREAS.clear();
        for (var farm : records) {
            FARMS.put(farm.id(), farm);
            AREAS.put(farm.id(), FarmAreas.bounds(farm.plots()));
            OCCUPIED.computeIfAbsent(farm.dimension(), key -> new HashSet<>()).addAll(farm.plots());
        }
        dirty = true;
    }
    private static Path file(String key) { return ClientConfig.directory().resolve("farms").resolve(key.replaceAll("[^a-zA-Z0-9.-]", "_") + ".json"); }
    public static void onJoin() {
        FARMS.clear(); OCCUPIED.clear(); AREAS.clear(); dirty = ready = false; nextSave = 0;
        world = WaypointStore.worldKey();
        int request = ++generation;
        if (world == null) return;
        Path path = file(world);
        IO.execute(() -> {
            try {
                List<FarmRecord> records = new ArrayList<>();
                Map<String, Set<FarmRecord.Plot>> occupied = new HashMap<>();
                Set<String> ids = new HashSet<>();
                if (Files.exists(path)) {
                    if (Files.size(path) > 40_000_000) throw new IllegalArgumentException("Farmdatei ist zu groß");
                    JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                    if (root.get("version").getAsInt() != 1 && root.get("version").getAsInt() != 2) throw new IllegalArgumentException("Unbekannte Farmdatei-Version");
                    for (var json : root.getAsJsonArray("farms")) {
                        FarmRecord record = GSON.fromJson(json, FarmRecord.class);
                        if (record == null || !record.valid() || records.size() >= FarmRecord.MAX_FARMS)
                            throw new IllegalArgumentException("Ungültige Farmdaten");
                        Set<FarmRecord.Plot> positions = occupied.computeIfAbsent(record.dimension(), k -> new HashSet<>());
                        // Spatial duplicates from older files are repaired below, not rejected wholesale.
                        positions.addAll(record.plots());
                        records.add(record.renamed(record.name()));
                    }
                }
                List<FarmRecord> normalized = FarmDetection.deduplicate(records);
                boolean repaired = normalized.size() != records.size();
                for (var record : normalized) if (!ids.add(record.id())) throw new IllegalArgumentException("Mehrdeutige Farm-ID");
                if (repaired) {
                    // Keep every original name, waypoint reference and measurement recoverable.
                    Files.copy(path, path.resolveSibling(path.getFileName() + ".before-dedup-" + UUID.randomUUID() + ".bak"));
                }
                Map<String, Map<Integer, FarmAreas.Bounds>> areas = new HashMap<>();
                for (var record : normalized) areas.put(record.id(), FarmAreas.bounds(record.plots()));
                Minecraft.getInstance().execute(() -> {
                    if (request != generation) return;
                    for (var record : normalized) {
                        FARMS.put(record.id(), record);
                    }
                    AREAS.putAll(areas);
                    OCCUPIED.putAll(occupied);
                    ready = true;
                    dirty = repaired;
                    if (repaired) Notifications.info("Farm Tracker", "Doppelte Farmflächen zusammengeführt; Originaldatei gesichert");
                });
            } catch (Exception e) {
                ExampleMod.LOGGER.error("Farmen konnten nicht geladen werden: {}", path, e);
                Minecraft.getInstance().execute(() -> {
                    if (request == generation) Notifications.error("Farm Tracker", "Farmdatei nicht lesbar. Sie bleibt unverändert.");
                });
            }
        });
    }
    public static boolean put(FarmRecord record) {
        if (!ready()) return false;
        FarmRecord previous = FARMS.get(record.id());
        // Observations and renames retain the immutable area; do not revalidate thousands of plots each scan.
        if (previous != null && previous.plots() == record.plots() && previous.dimension().equals(record.dimension())) {
            if (record.ripe() < 0 || record.planted() < record.ripe() || record.planted() > record.plots().size()) return false;
        } else {
            if (!record.valid() || previous != null || FARMS.size() >= FarmRecord.MAX_FARMS || overlaps(record.dimension(), record.plots())) return false;
            OCCUPIED.computeIfAbsent(record.dimension(), k -> new HashSet<>()).addAll(record.plots());
            AREAS.put(record.id(), FarmAreas.bounds(record.plots()));
        }
        FARMS.put(record.id(), record); dirty = true; return true;
    }
    public static void remove(String id) {
        FarmRecord farm = FARMS.remove(id);
        AREAS.remove(id);
        if (farm != null) { OCCUPIED.get(farm.dimension()).removeAll(farm.plots()); dirty = true; }
    }
    public static void tick() {
        if (dirty && System.currentTimeMillis() >= nextSave) {
            nextSave = System.currentTimeMillis() + 3000; save();
        }
    }
    private static void save() {
        if (!ready() || !dirty) return;
        var snapshot = all(); Path target = file(world); dirty = false;
        IO.execute(() -> {
            JsonObject root = new JsonObject(); root.addProperty("version", 2); root.add("farms", GSON.toJsonTree(snapshot));
            ClientConfig.writeAtomically(target, GSON.toJson(root));
        });
    }
    public static void onLeave() { save(); generation++; FARMS.clear(); OCCUPIED.clear(); AREAS.clear(); world = null; ready = dirty = false; }
}
