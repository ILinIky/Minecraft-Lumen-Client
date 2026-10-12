package com.example.examplemod.waypoint;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.module.ClientConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wegpunkte pro Server bzw. pro Einzelspielerwelt in config/examplemod/teleports/*.json.
 * Frühere Einzelspieler-Punkte (singleplayer.json) werden beim ersten Öffnen einer Welt übernommen.
 */
public final class WaypointStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<ArrayList<Waypoint>>() {}.getType();
    private static final String LEGACY_SINGLEPLAYER = "singleplayer";

    private static final List<Waypoint> WAYPOINTS = new ArrayList<>();
    private static String worldKey;
    private static int nextId = 1;
    private static int targetId = -1;

    private WaypointStore() {}

    private static Path directory() {
        return ClientConfig.directory().resolve("teleports");
    }

    public static List<Waypoint> all() { return Collections.unmodifiableList(WAYPOINTS); }

    public static String worldKey() { return worldKey; }

    public static boolean isLoaded() { return worldKey != null; }

    public static Path exportDirectory() { return directory().resolve("exports"); }

    public static Path export(List<Waypoint> visibleWaypoints, String group) throws IOException {
        return WaypointExport.write(exportDirectory(), List.copyOf(visibleWaypoints), group);
    }

    public static List<String> groups() { return WaypointGroups.names(WAYPOINTS); }

    public static void assignGroup(Waypoint waypoint, String group) {
        String name = WaypointGroups.canonical(group, WAYPOINTS);
        waypoint.group = name.isEmpty() ? null : name;
    }

    public static void onJoin() {
        worldKey = currentWorldKey();
        load();
    }

    public static void onLeave() {
        if (worldKey != null) save();
        WAYPOINTS.clear();
        worldKey = null;
        targetId = -1;
    }

    private static String currentWorldKey() {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server != null) {
            Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            String folder = root.getFileName() != null ? root.getFileName().toString() : "world";
            return LEGACY_SINGLEPLAYER + "_" + folder;
        }
        if (mc.getCurrentServer() != null) return mc.getCurrentServer().ip;
        return LEGACY_SINGLEPLAYER;
    }

    private static Path fileFor(String key) {
        return directory().resolve(key.replaceAll("[^a-zA-Z0-9.-]", "_") + ".json");
    }

    private static void load() {
        WAYPOINTS.clear();
        Path file = fileFor(worldKey);
        if (!Files.exists(file) && worldKey.startsWith(LEGACY_SINGLEPLAYER + "_")) {
            Path legacy = fileFor(LEGACY_SINGLEPLAYER);
            if (Files.exists(legacy)) file = legacy;
        }
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                List<Waypoint> loaded = GSON.fromJson(reader, LIST_TYPE);
                if (loaded != null) {
                    for (Waypoint waypoint : loaded) {
                        if (waypoint != null && waypoint.dimension != null) WAYPOINTS.add(waypoint);
                    }
                }
            } catch (IOException | RuntimeException e) {
                ExampleMod.LOGGER.error("Wegpunkte konnten nicht geladen werden: {}", file, e);
            }
        }
        nextId = WAYPOINTS.stream().mapToInt(w -> w.id).max().orElse(0) + 1;
        targetId = WAYPOINTS.stream().filter(w -> Boolean.TRUE.equals(w.target)).mapToInt(w -> w.id).findFirst().orElse(-1);
    }

    public static void save() {
        if (worldKey == null) return;
        for (Waypoint waypoint : WAYPOINTS) waypoint.target = waypoint.id == targetId ? Boolean.TRUE : null;
        ClientConfig.writeAtomically(fileFor(worldKey), GSON.toJson(WAYPOINTS, LIST_TYPE));
    }

    public static Waypoint add(String name, double x, double y, double z, float yaw, float pitch, String dimension) {
        Waypoint waypoint = new Waypoint(nextId++, name, new Vec3(x, y, z), yaw, pitch, dimension);
        WAYPOINTS.add(waypoint);
        save();
        return waypoint;
    }

    /** Speichert den Todespunkt; ältere Todespunkte über {@code keep} hinaus werden entfernt. */
    public static Waypoint addDeath(double x, double y, double z, String dimension, int keep) {
        String time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        Waypoint waypoint = new Waypoint(nextId++, "Tod " + time, new Vec3(x, y, z), 0, 0, dimension);
        waypoint.death = true;
        WAYPOINTS.add(waypoint);
        List<Waypoint> deaths = new ArrayList<>(WAYPOINTS.stream().filter(w -> w.death).toList());
        deaths.sort(java.util.Comparator.comparingLong((Waypoint w) -> w.created).reversed());
        for (int i = Math.max(1, keep); i < deaths.size(); i++) {
            Waypoint old = deaths.get(i);
            WAYPOINTS.remove(old);
            if (targetId == old.id) targetId = -1;
        }
        save();
        return waypoint;
    }

    // ---- Kompass-Ziel (wird mit den Wegpunkten der Welt gespeichert) ----

    public static Waypoint target() {
        Waypoint target = targetId < 0 ? null : find(targetId);
        if (target == null) targetId = -1;
        return target;
    }

    public static boolean isTarget(Waypoint waypoint) { return waypoint.id == targetId; }

    public static void setTarget(Waypoint waypoint) {
        int id = waypoint == null ? -1 : waypoint.id;
        if (id == targetId) return;
        targetId = id;
        save();
    }

    public static Waypoint find(int id) {
        for (Waypoint waypoint : WAYPOINTS) {
            if (waypoint.id == id) return waypoint;
        }
        return null;
    }

    /** Sucht einen Wegpunkt zuerst nach ID, dann nach Namen (ohne Groß-/Kleinschreibung). */
    public static Waypoint find(String idOrName) {
        try {
            return find(Integer.parseInt(idOrName.trim()));
        } catch (NumberFormatException ignored) {
            for (Waypoint waypoint : WAYPOINTS) {
                if (waypoint.displayName().equalsIgnoreCase(idOrName.trim())) return waypoint;
            }
            return null;
        }
    }

    /** Ein Wegpunkt in derselben Dimension, der näher als {@code distance} Blöcke liegt. */
    public static Waypoint near(double x, double y, double z, String dimension, double distance) {
        double maxSq = distance * distance;
        for (Waypoint waypoint : WAYPOINTS) {
            if (!waypoint.dimension.equals(dimension)) continue;
            double dx = waypoint.x - x, dy = waypoint.y - y, dz = waypoint.z - z;
            if (dx * dx + dy * dy + dz * dz < maxSq) return waypoint;
        }
        return null;
    }

    public static boolean remove(int id) {
        boolean removed = WAYPOINTS.removeIf(waypoint -> waypoint.id == id);
        if (removed) save();
        return removed;
    }

    public static int clear() {
        int count = WAYPOINTS.size();
        WAYPOINTS.clear();
        nextId = 1;
        save();
        return count;
    }
}
