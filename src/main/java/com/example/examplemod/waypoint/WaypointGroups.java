package com.example.examplemod.waypoint;

import java.util.List;
import java.util.TreeSet;

/** Gruppen bleiben Teil des bisherigen Wegpunkt-Formats; keine separate Registrierung nötig. */
public final class WaypointGroups {
    public static final int MAX_NAME = 32;
    public static final List<String> DEFAULTS = List.of("Basen", "Farmen", "Portale");

    private WaypointGroups() {}

    public static String normalize(String name) {
        if (name == null) return "";
        String cleaned = name.replaceAll("§.", "").replaceAll("[\\p{Cntrl}§]", " ")
                .strip().replaceAll("\\s+", " ");
        int count = cleaned.codePointCount(0, cleaned.length());
        return count <= MAX_NAME ? cleaned : cleaned.substring(0, cleaned.offsetByCodePoints(0, MAX_NAME));
    }

    public static List<String> names(List<Waypoint> waypoints) {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(DEFAULTS);
        for (Waypoint waypoint : waypoints) {
            String group = waypoint.groupName();
            if (!group.isEmpty()) names.add(group);
        }
        return List.copyOf(names);
    }

    public static String canonical(String name, List<Waypoint> waypoints) {
        String cleaned = normalize(name);
        for (String existing : names(waypoints)) {
            if (existing.equalsIgnoreCase(cleaned)) return existing;
        }
        return cleaned;
    }

    /** null = alle Gruppen; leer = nur Wegpunkte ohne Gruppe. */
    public static boolean matches(Waypoint waypoint, String filter) {
        return filter == null || waypoint.groupName().equalsIgnoreCase(normalize(filter));
    }
}
