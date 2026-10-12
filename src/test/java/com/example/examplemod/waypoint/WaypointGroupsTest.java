package com.example.examplemod.waypoint;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Ohne gestarteten Minecraft-Client ausführbare Persistenz- und Exportprüfungen. */
public final class WaypointGroupsTest {
    private static final Gson GSON = new Gson();
    private static int assertions;

    public static void main(String[] args) throws Exception {
        legacyAndRoundTrip();
        groupsAndFilters();
        exports();
        System.out.println("WaypointGroupsTest: " + assertions + " assertions passed");
    }

    private static void legacyAndRoundTrip() {
        Waypoint legacy = GSON.fromJson("{\"id\":7,\"x\":8123.25,\"y\":64,\"z\":-12000.5,"
                + "\"name\":\"Alte Basis\",\"dimension\":\"minecraft:overworld\"}", Waypoint.class);
        check(legacy.group == null && legacy.groupName().isEmpty(), "Alte Dateien bleiben ohne Gruppe");
        check(legacy.groupLabel().equals("Ohne Gruppe"), "Beschriftung alter Wegpunkte");
        check(legacy.id == 7 && legacy.x == 8123.25 && legacy.z == -12000.5, "Alte Koordinaten bleiben erhalten");
        legacy.group = "Dörfer & Städte";
        legacy.icon = "⌂";
        legacy.color = 0xFF34D399;
        Waypoint restored = GSON.fromJson(GSON.toJson(legacy), Waypoint.class);
        check(restored.group.equals(legacy.group), "Eigene Gruppe überlebt das Speichern und Laden");
        check(restored.icon.equals(legacy.icon) && restored.color == legacy.color, "Darstellung bleibt erhalten");
        restored.group = null;
        check(GSON.fromJson(GSON.toJson(restored), Waypoint.class).groupName().isEmpty(), "Gruppe kann entfernt werden");
    }

    private static void groupsAndFilters() {
        Waypoint base = waypoint(1, " basen ");
        Waypoint village = waypoint(2, "Dörfer");
        Waypoint duplicate = waypoint(3, "dÖrfer");
        Waypoint ungrouped = waypoint(4, null);
        List<Waypoint> points = List.of(base, village, duplicate, ungrouped);
        List<String> groups = WaypointGroups.names(points);
        check(groups.size() == 4 && groups.containsAll(WaypointGroups.DEFAULTS), "Standardgruppen und eigene Gruppe ohne Duplikate");
        check(WaypointGroups.canonical(" BASEN ", points).equals("Basen"), "Einheitliche Schreibweise der Standardgruppen");
        check(WaypointGroups.canonical("dÖRFER", points).equals("Dörfer"), "Einheitliche Schreibweise eigener Gruppen");
        check(WaypointGroups.canonical("  Neue   Orte  ", points).equals("Neue Orte"), "Neue Namen werden bereinigt");
        check(WaypointGroups.normalize("§aFarmen\n Nord§").equals("Farmen Nord"), "Keine Formatierung oder Steuerzeichen");
        String longName = "🌍".repeat(40);
        String shortened = WaypointGroups.normalize(longName);
        check(shortened.equals("🌍".repeat(32)), "Längenlimit zerstört keine Unicode-Zeichen");
        check(points.stream().filter(w -> WaypointGroups.matches(w, null)).count() == 4, "Alle Gruppen");
        check(points.stream().filter(w -> WaypointGroups.matches(w, "")).toList().equals(List.of(ungrouped)), "Nur ohne Gruppe");
        check(points.stream().filter(w -> WaypointGroups.matches(w, " DÖRFER ")).count() == 2, "Gruppenfilter ignoriert Schreibweise");
        check(points.stream().noneMatch(w -> WaypointGroups.matches(w, "Unbekannt")), "Unbekannte Gruppe liefert keine Treffer");
        Waypoint namedLikeUngrouped = waypoint(5, "Ohne Gruppe");
        check(!WaypointGroups.matches(namedLikeUngrouped, ""), "Eigener Name verwechselt sich nicht mit leerer Gruppe");
    }

    private static void exports() throws Exception {
        Path baseDirectory = Path.of(".build-tmp", "waypoint-group-tests");
        Files.createDirectories(baseDirectory);
        Path temporary = Files.createTempDirectory(baseDirectory, "run-").toAbsolutePath();
        try {
            Waypoint farm = waypoint(11, "Farmen");
            farm.name = "Kürbisfarm <Nord>";
            farm.x = -8000.75;
            farm.y = 112;
            farm.z = 24000.125;
            farm.yaw = 45.5F;
            farm.pitch = -12.25F;
            farm.dimension = "minecraft:the_nether";
            farm.created = 1720000000000L;
            farm.icon = "⚒";
            farm.color = 0xFFFBBF24;
            Waypoint base = waypoint(12, "Basen");
            List<Waypoint> selection = List.of(farm, base).stream()
                    .filter(w -> WaypointGroups.matches(w, "Farmen")).toList();
            Path directory = temporary.resolve("exports");
            Path first = WaypointExport.write(directory, selection, "Farmen");
            String original = Files.readString(first);
            JsonObject root = JsonParser.parseString(original).getAsJsonObject();
            check(root.get("format").getAsString().equals("lumen-waypoints")
                    && root.get("version").getAsInt() == 1, "Exportformat ist versioniert");
            check(root.get("group").getAsString().equals("Farmen"), "Export nennt den Gruppenfilter");
            check(root.getAsJsonArray("waypoints").size() == 1, "Nur ausgewählte Wegpunkte werden exportiert");
            Waypoint restored = GSON.fromJson(root.getAsJsonArray("waypoints").get(0), Waypoint.class);
            check(GSON.toJson(restored).equals(GSON.toJson(farm)), "Export bewahrt sämtliche Wegpunktdaten");
            check(root.keySet().equals(java.util.Set.of("format", "version", "exportedAt", "group", "waypoints")),
                    "Export enthält keine Serveradresse oder lokalen Weltpfade");
            Path second = WaypointExport.write(directory, selection, "Farmen");
            check(!first.equals(second) && Files.readString(first).equals(original), "Wiederholter Export überschreibt nichts");
            Path all = WaypointExport.write(directory, List.of(farm, base), null);
            JsonObject allData = JsonParser.parseString(Files.readString(all)).getAsJsonObject();
            check(!allData.has("group") && allData.getAsJsonArray("waypoints").size() == 2, "Alle Gruppen exportieren");
            Path suspicious = WaypointExport.write(directory, selection, "../../outside");
            check(suspicious.getParent().equals(directory) && suspicious.getFileName().toString().endsWith(".json"),
                    "Gruppennamen können den Exportordner nicht verlassen");
            try (var entries = Files.list(directory)) {
                check(entries.noneMatch(path -> path.toString().endsWith(".tmp")), "Keine unvollständigen Dateien nach Export");
            }
            Path empty = temporary.resolve("empty");
            try {
                WaypointExport.write(empty, List.of(), null);
                throw new AssertionError("Leerer Export muss abgelehnt werden");
            } catch (IllegalArgumentException expected) {
                check(!Files.exists(empty), "Leerer Export erzeugt keine Datei");
            }
            Path blocked = temporary.resolve("file-instead-of-directory");
            Files.writeString(blocked, "unverändert");
            try {
                WaypointExport.write(blocked, selection, "Farmen");
                throw new AssertionError("Schreibfehler muss gemeldet werden");
            } catch (IOException expected) {
                check(Files.readString(blocked).equals("unverändert"), "Schreibfehler verändert vorhandene Datei nicht");
            }
        } finally {
            try (var entries = Files.walk(temporary)) {
                for (Path path : entries.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static Waypoint waypoint(int id, String group) {
        Waypoint waypoint = new Waypoint();
        waypoint.id = id;
        waypoint.group = group;
        return waypoint;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
}
