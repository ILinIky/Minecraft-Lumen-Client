package com.example.examplemod.chest;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.module.ClientConfig;
import com.example.examplemod.waypoint.WaypointStore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Gemerkte Behälter pro Welt bzw. Server in config/examplemod/chests/*.json. Der Schlüssel der Welt
 * kommt von {@link WaypointStore}, damit Wegpunkte und Kisten immer zur selben Welt gehören.
 *
 * <p>Damit große Lager nicht ruckeln, wird nie auf dem Spiel-Thread geschrieben: Änderungen werden
 * gesammelt und höchstens alle {@link #SAVE_INTERVAL_MS} ms im Hintergrund gespeichert. Gespeicherte
 * Einträge werden nach dem Ablegen nicht mehr verändert, daher genügt eine Kopie der Liste.
 */
public final class ChestStore {
    /** Obergrenze je Welt; darüber fallen die am längsten nicht gesehenen Behälter heraus. */
    public static final int MAX_RECORDS = 20_000;
    private static final long SAVE_INTERVAL_MS = 3000;

    private static final Gson GSON = new GsonBuilder().create();
    private static final Type LIST_TYPE = new TypeToken<ArrayList<ChestRecord>>() {}.getType();
    private static final Map<String, ChestRecord> CHESTS = new LinkedHashMap<>();
    /**
     * Ein Schreib-Thread, der nach 10 s Leerlauf endet. Kein Daemon, damit der letzte Stand beim
     * Beenden des Spiels noch fertig geschrieben wird.
     */
    private static final ThreadPoolExecutor SAVER = new ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), runnable -> new Thread(runnable, "Lumen-Kisten-Speichern"));
    static {
        SAVER.allowCoreThreadTimeOut(true);
    }

    private static String worldKey;
    private static boolean dirty;
    private static long nextSave;

    private ChestStore() {}

    public static Collection<ChestRecord> all() { return Collections.unmodifiableCollection(CHESTS.values()); }

    public static int size() { return CHESTS.size(); }

    private static Path file(String key) {
        return ClientConfig.directory().resolve("chests").resolve(key.replaceAll("[^a-zA-Z0-9.-]", "_") + ".json");
    }

    /** Einmal beim Betreten der Welt (Ladebildschirm, nicht während des Spielens). */
    public static void onJoin() {
        CHESTS.clear();
        dirty = false;
        worldKey = WaypointStore.worldKey();
        if (worldKey == null) return;
        Path file = file(worldKey);
        if (!Files.exists(file)) return;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            List<ChestRecord> loaded = GSON.fromJson(reader, LIST_TYPE);
            if (loaded == null) return;
            for (ChestRecord record : loaded) {
                if (record != null && record.dimension != null && record.items != null) CHESTS.put(record.key(), record);
            }
        } catch (IOException | RuntimeException e) {
            ExampleMod.LOGGER.error("Kisten-Gedächtnis konnte nicht geladen werden: {}", file, e);
        }
    }

    public static void onLeave() {
        if (dirty) saveInBackground();
        CHESTS.clear();
        worldKey = null;
    }

    /** Jeden Tick: gesammelte Änderungen höchstens alle paar Sekunden im Hintergrund speichern. */
    public static void tick() {
        if (!dirty) return;
        long now = System.currentTimeMillis();
        if (now < nextSave) return;
        nextSave = now + SAVE_INTERVAL_MS;
        saveInBackground();
    }

    private static void saveInBackground() {
        if (worldKey == null) return;
        dirty = false;
        // Kopie auf dem Spiel-Thread; Umwandeln in JSON und Schreiben im Hintergrund.
        List<ChestRecord> copy = new ArrayList<>(CHESTS.values());
        Path target = file(worldKey);
        SAVER.execute(() -> ClientConfig.writeAtomically(target, GSON.toJson(copy, LIST_TYPE)));
    }

    /** Ersetzt den Eintrag für dieselbe Position. */
    public static void put(ChestRecord record) {
        CHESTS.remove(record.key());
        CHESTS.put(record.key(), record);
        if (CHESTS.size() > MAX_RECORDS) trimOldest();
        dirty = true;
    }

    private static void trimOldest() {
        List<ChestRecord> sorted = new ArrayList<>(CHESTS.values());
        sorted.sort(Comparator.comparingLong(r -> r.seen));
        for (int i = 0; i < sorted.size() - MAX_RECORDS; i++) CHESTS.remove(sorted.get(i).key());
    }

    public static ChestRecord get(String key) { return CHESTS.get(key); }

    public static boolean remove(ChestRecord record) {
        boolean removed = CHESTS.remove(record.key()) != null;
        if (removed) dirty = true;
        return removed;
    }

    /** Alles für diese Welt vergessen. */
    public static int clear() {
        int count = CHESTS.size();
        CHESTS.clear();
        dirty = true;
        return count;
    }
}
