package com.example.examplemod.feature;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PlayerRadarTest {
    private static int assertions;
    private static void check(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    public static void main(String[] args) {
        UUID self = new UUID(0,1), near = new UUID(0,2), edge = new UUID(0,3), far = new UUID(0,4), other = new UUID(0,5);
        var players = List.of(new PlayerRadarModel.Candidate(self, "Self", 0), new PlayerRadarModel.Candidate(edge,"Edge",4096),
                new PlayerRadarModel.Candidate(near,"Near",25), new PlayerRadarModel.Candidate(far,"Far",4096.01), new PlayerRadarModel.Candidate(other,"Other",1));
        var selected = Set.of(self,near,edge,far);
        var hits = PlayerRadarModel.nearby(players, selected, self, 64);
        check(hits.size() == 2, "Only selected in-range players; self excluded");
        check(hits.get(0).id().equals(near) && hits.get(0).distance() == 5, "Nearest first and distance in blocks");
        check(hits.get(1).id().equals(edge) && hits.get(1).distance() == 64, "Radius boundary included");
        check(PlayerRadarModel.nearby(players, Set.of(), self, 64).isEmpty(), "Empty selection means nobody");
        check(PlayerRadarModel.nearby(List.of(), selected, self, 64).isEmpty(), "Unloaded or disconnected players disappear");
        check(PlayerRadarModel.nearby(players, selected, self, 4).isEmpty(), "Radius changes take effect");
        var renamed = List.of(new PlayerRadarModel.Candidate(near,"Renamed",25));
        check(PlayerRadarModel.nearby(renamed, selected, self, 64).getFirst().name().equals("Renamed"), "UUID selection survives renamed players");
        var invalid = List.of(new PlayerRadarModel.Candidate(near,"Invalid",Double.NaN),new PlayerRadarModel.Candidate(edge,"Invalid",-1));
        check(PlayerRadarModel.nearby(invalid, selected, self, 64).isEmpty(), "Invalid coordinates rejected");
        check(PlayerRadarModel.nearby(players, selected, self, Double.NaN).isEmpty(), "Invalid radius rejected");
        System.out.println("PlayerRadarTest: " + assertions + " assertions passed");
    }
}
