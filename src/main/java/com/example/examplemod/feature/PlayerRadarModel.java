package com.example.examplemod.feature;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Selection and spherical distance filter, independent of world rendering. */
public final class PlayerRadarModel {
    public record Candidate(UUID id, String name, double distanceSquared) {}
    public record Hit(UUID id, String name, double distance) {}
    public static List<Hit> nearby(Collection<Candidate> players, Set<UUID> selected, UUID self, double radius) {
        if (!Double.isFinite(radius) || radius < 0 || selected.isEmpty()) return List.of();
        double max = radius * radius;
        return players.stream().filter(p -> !p.id().equals(self) && selected.contains(p.id())
                        && Double.isFinite(p.distanceSquared()) && p.distanceSquared() >= 0 && p.distanceSquared() <= max)
                .map(p -> new Hit(p.id(), p.name(), Math.sqrt(p.distanceSquared())))
                .sorted(Comparator.comparingDouble(Hit::distance).thenComparing(Hit::name).thenComparing(p -> p.id().toString())).toList();
    }
    private PlayerRadarModel() {}
}
