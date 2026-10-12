package com.example.examplemod.waypoint;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class WaypointExport {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private WaypointExport() {}

    public static Path write(Path directory, List<Waypoint> waypoints, String group) throws IOException {
        if (waypoints.isEmpty()) throw new IllegalArgumentException("Keine Wegpunkte zum Exportieren");
        JsonObject root = new JsonObject();
        root.addProperty("format", "lumen-waypoints");
        root.addProperty("version", 1);
        root.addProperty("exportedAt", Instant.now().toString());
        if (group != null) root.addProperty("group", WaypointGroups.normalize(group));
        root.add("waypoints", GSON.toJsonTree(waypoints));
        Files.createDirectories(directory);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        // Ausschließlich generierter Dateiname; Gruppennamen werden nie zu Pfaden.
        Path temporary = Files.createTempFile(directory, "wegpunkte-" + stamp + "-", ".tmp");
        Path target = temporary.resolveSibling(temporary.getFileName().toString().replaceFirst("\\.tmp$", ".json"));
        try {
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(temporary, target); // Ohne REPLACE_EXISTING: vorhandene Exporte niemals überschreiben.
            return target;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
