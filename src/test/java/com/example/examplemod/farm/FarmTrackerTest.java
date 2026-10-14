package com.example.examplemod.farm;

import com.google.gson.Gson;
import java.util.*;

public final class FarmTrackerTest {
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static FarmRecord.Plot p(int x, int y, int z) { return new FarmRecord.Plot(x, y, z); }
    private static Set<Set<FarmRecord.Plot>> reference(Set<FarmRecord.Plot> input, Set<FarmRecord.Plot> incomplete, int gap) {
        var left = new HashSet<>(input);
        Set<Set<FarmRecord.Plot>> result = new HashSet<>();
        while (!left.isEmpty()) {
            var seed = left.iterator().next(); left.remove(seed);
            var queue = new ArrayDeque<FarmRecord.Plot>(); queue.add(seed);
            Set<FarmRecord.Plot> area = new HashSet<>();
            while (!queue.isEmpty()) {
                var p = queue.removeFirst(); area.add(p);
                var iterator = left.iterator();
                while (iterator.hasNext()) {
                    var other = iterator.next();
                    if (other.y() == p.y() && Math.abs(other.x() - p.x()) <= gap + 1 && Math.abs(other.z() - p.z()) <= gap + 1) {
                        iterator.remove(); queue.add(other);
                    }
                }
            }
            if (Collections.disjoint(area, incomplete)) result.add(area);
        }
        return result;
    }
    public static void main(String[] args) {
        var random = new Random(12893);
        for (int trial = 0; trial < 24; trial++) {
            var input = new HashSet<FarmRecord.Plot>();
            for (int i = 0; i < 180; i++) input.add(p(random.nextInt(101) - 50, 63 + random.nextInt(3), random.nextInt(101) - 50));
            var edge = Set.of(input.iterator().next());
            int gap = trial % 17;
            var actual = new HashSet<Set<FarmRecord.Plot>>();
            FarmDetection.components(input, edge, gap).forEach(group -> actual.add(Set.copyOf(group)));
            check(actual.equals(reference(input, edge, gap)), "Spatial buckets match reference connectivity, height and incomplete boundaries at gap " + gap);
        }
        var selection = new FarmRecord.Area(-10, 64, -10, 10, 64, 10);
        check(selection.valid() && selection.volume() == 441, "Manual area includes both corners and wide gaps");
        check(selection.at(0).equals(p(-10, 64, -10)) && selection.at(440).equals(p(10, 64, 10)), "Bounded area enumeration reaches exact corners");
        check(!new FarmRecord.Area(0, 64, 0, 1000, 64, 1000).valid(), "Reject excessive manual scan volume");
        check(!new FarmRecord.Area(-30_000_000, -2048, -30_000_000, 30_000_000, 2048, 30_000_000).valid(), "Reject huge spans before volume overflow");
        var seed = new FarmRecord("manual", "Gemüse", "overworld", List.of(p(-10, 64, -10), p(10, 64, 10)),
                0, 0, 0, 3, selection, List.of("minecraft:wheat", "minecraft:carrots"), List.of());
        var observe = new FarmObservation(seed);
        observe.sample(p(-10, 64, -10), "minecraft:wheat", true);
        observe.sample(p(10, 64, 10), "minecraft:carrots", false);
        observe.sample(p(0, 64, 0), "minecraft:wheat", false);
        var measured = observe.finish(seed, 100);
        check(measured.valid() && measured.plots().size() == 3 && measured.percent() == 33, "New bed inside selection joins tracked crops; paths excluded");
        check(measured.beds().stream().anyMatch(b -> b.crop().equals("minecraft:wheat") && b.percent() == 50), "Wheat percentage uses wheat plots only");
        check(measured.beds().stream().anyMatch(b -> b.crop().equals("minecraft:carrots") && b.percent() == 0), "Carrot percentage independent");
        var harvested = new FarmObservation(measured).finish(measured, 200);
        check(harvested.valid() && harvested.planted() == 0 && harvested.beds().size() == 2 && harvested.beds().stream().allMatch(b -> b.percent() == 0),
                "Harvest retains crop types and denominators with zero ripe plants");
        var replanted = new FarmObservation(harvested);
        replanted.sample(p(-10, 64, -10), "minecraft:potatoes", true);
        var changedCrop = replanted.finish(harvested, 300);
        check(changedCrop.beds().stream().anyMatch(b -> b.crop().equals("minecraft:potatoes") && b.percent() == 100), "Crop rotation updates plant category");
        var roundTrip = new Gson().fromJson(new Gson().toJson(measured.renamed("Beete").withWaypoint(9)), FarmRecord.class);
        check(roundTrip.valid() && roundTrip.area().equals(selection) && roundTrip.beds().equals(measured.beds()) && roundTrip.crops().equals(measured.crops()),
                "Manual bounds and per-crop last-known values survive save/reload and rename");
        var legacyJson = new Gson().toJsonTree(seed).getAsJsonObject(); legacyJson.remove("area"); legacyJson.remove("crops"); legacyJson.remove("beds");
        var legacy = new Gson().fromJson(legacyJson, FarmRecord.class);
        check(legacy.valid() && legacy.area() == null && legacy.beds().isEmpty() && legacy.crops().size() == 2, "Old farm JSON loads with unknown plant categories");
        check(FarmAreas.merge(List.of(seed, new FarmRecord("outside", "Nebenan", "overworld", List.of(p(11, 64, 10)), 0, 0, 0, -1)), 16).size() == 2,
                "Automatic merging never widens explicitly selected area");
        var beds = Set.of(p(0, 64, 0), p(7, 64, 0), p(14, 64, 0));
        check(FarmDetection.components(beds, Set.of(), 6).size() == 1, "Different beds connect transitively across six empty blocks");
        check(FarmDetection.components(beds, Set.of(), 5).size() == 3, "Distance setting is respected");
        check(FarmDetection.components(beds, Set.of(p(14, 64, 0)), 6).isEmpty(), "Incomplete connected bed blocks the entire proposal");
        FarmRecord a = new FarmRecord("a", "Hauptfarm", "overworld", List.of(p(0, 64, 0), p(20, 64, 20)), 2, 2, 100, 17);
        FarmRecord b = new FarmRecord("b", "Innen", "overworld", List.of(p(10, 64, 10)), 1, 1, 100, -1);
        var merged = FarmAreas.merge(List.of(a, b), 0);
        check(merged.size() == 1 && merged.getFirst().plots().size() == 3, "Enclosing area owns disconnected interior beds");
        check(merged.getFirst().name().equals("Hauptfarm") && merged.getFirst().waypointId() == 17 && merged.getFirst().id().equals("a"), "Preserve original identity and waypoint");
        check(merged.getFirst().seen() == 0 && merged.getFirst().ripe() == 0, "Union invalidates old measurement");
        check(FarmAreas.merge(merged, 0).equals(merged), "Area merge is idempotent");
        FarmRecord upper = new FarmRecord("c", "Oben", "overworld", List.of(p(10, 65, 10)), 1, 1, 100, -1);
        FarmRecord nether = new FarmRecord("d", "Nether", "nether", b.plots(), 1, 1, 100, -1);
        check(FarmAreas.merge(List.of(a, upper, nether), 16).size() == 3, "Height and dimension remain separate");
        check(FarmAreas.merge(List.of(a, a), 6).getFirst().plots().size() == 2, "Repeated acceptance never doubles planting positions");
        check(merged.getFirst().observed(3, 3, 200).percent() == 100, "Paths inside enclosing rectangle do not count as missing crops");
        var far = new FarmRecord("far", "Fern", "overworld", List.of(p(28, 64, 20)), 1, 1, 100, -1);
        check(FarmAreas.merge(List.of(a, far), 6).size() == 2, "Seven empty blocks exceed default gap");
        check(FarmAreas.merge(List.of(a, far), 7).size() == 1, "Larger configured gap joins same area");
        var patch = new HashSet<FarmRecord.Plot>();
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) if (x != 4 || z != 4) patch.add(p(x, 64, z));
        var groups = FarmDetection.components(patch, Set.of());
        check(groups.size() == 1 && groups.get(0).size() == 80, "Irrigated field with central hole is one connected farm");
        check(FarmDetection.components(patch, Set.of(p(0, 64, 0))).isEmpty(), "Do not propose farm cut off by scan/chunk edge");
        var separate = new HashSet<>(patch);
        separate.add(p(20, 64, 20)); separate.add(p(0, 65, 0));
        check(FarmDetection.components(separate, Set.of()).size() == 3, "Separate heights and distant plots stay separate");
        check(FarmDetection.components(Set.of(p(-1, 64, -1), p(0, 64, 0)), Set.of()).size() == 1, "Diagonal neighbors across negative coordinates connect");
        check(FarmDetection.components(Set.of(), Set.of()).isEmpty(), "Empty scan");
        var oversized = new HashSet<FarmRecord.Plot>();
        for (int x = 0; x <= FarmRecord.MAX_PLOTS; x++) oversized.add(p(x, 64, 0));
        check(FarmDetection.components(oversized, Set.of()).isEmpty(), "Oversized farm is not silently truncated");
        oversized.remove(p(FarmRecord.MAX_PLOTS, 64, 0));
        var overflowing = new FarmObservation(new FarmRecord("large", "Large", "overworld", List.copyOf(oversized), 0, 0, 0, -1));
        overflowing.sample(p(5000, 64, 0), "minecraft:wheat", true);
        check(overflowing.overflow(), "Oversized observed area is flagged without growing memory indefinitely");
        var maximum = new FarmRecord("max", "Max", "overworld", List.copyOf(oversized), 0, 0, 0, -1);
        var extra = new FarmRecord("extra", "Extra", "overworld", List.of(p(4096, 64, 0)), 0, 0, 0, -1);
        try { FarmAreas.merge(List.of(maximum, extra), 0); throw new AssertionError("Oversized union accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        check(FarmDetection.components(oversized, Set.of()).get(0).size() == FarmRecord.MAX_PLOTS, "Maximum size accepted without recursion");
        FarmRecord farm = new FarmRecord("id", "Weizen", "minecraft:overworld", groups.get(0), 60, 80, 1000, -1);
        check(farm.valid() && farm.percent() == 75, "Harvest percentage uses saved area");
        Set<FarmRecord.Plot> known = Set.copyOf(farm.plots());
        check(FarmDetection.touchesKnown(List.of(p(0, 64, 0)), known), "Exact position cannot be saved twice");
        check(FarmDetection.touchesKnown(List.of(p(9, 64, 3)), known), "Newly planted neighboring row belongs to known farm");
        check(FarmDetection.touchesKnown(List.of(p(9, 64, 9)), known), "Diagonal extension is also the same component");
        check(!FarmDetection.touchesKnown(List.of(p(0, 65, 0)), known), "Stacked farms remain independent");
        check(!FarmDetection.touchesKnown(List.of(p(10, 64, 3)), known), "Separated neighboring farms remain independent");
        FarmRecord duplicate = new FarmRecord("duplicate", "Anderer Name", farm.dimension(), List.of(p(0, 64, 0), p(-1, 64, 0)), 2, 2, 2000, 42);
        var repaired = FarmDetection.deduplicate(List.of(farm.withWaypoint(17), duplicate));
        check(repaired.size() == 1 && repaired.get(0).plots().size() == 81, "Merge partial overlap without losing any area");
        check(repaired.get(0).id().equals(farm.id()) && repaired.get(0).name().equals(farm.name()) && repaired.get(0).waypointId() == 17, "Keep original identity/name/navigation");
        check(repaired.get(0).seen() == 0 && repaired.get(0).ripe() == 0, "Merged area needs fresh measurement");
        check(FarmDetection.deduplicate(repaired).equals(repaired), "Repair is idempotent");
        check(FarmDetection.deduplicate(List.of(farm, farm)).size() == 1, "Repeated same ID and area repairs safely");
        FarmRecord otherDimension = new FarmRecord("nether", "Nether", "minecraft:the_nether", farm.plots(), 60, 80, 1000, -1);
        check(FarmDetection.deduplicate(List.of(farm, otherDimension)).size() == 2, "Same coordinates in different dimensions are not duplicates");
        FarmRecord adjacent = new FarmRecord("adjacent", "Separat benannt", farm.dimension(), List.of(p(9, 64, 0)), 1, 1, 1000, -1);
        check(FarmDetection.deduplicate(List.of(farm, adjacent)).size() == 2, "Do not merge already named adjacent farms without overlap");
        FarmRecord bridge = new FarmRecord("bridge", "Brücke", farm.dimension(), List.of(p(8, 64, 0), p(9, 64, 0)), 2, 2, 1000, -1);
        check(FarmDetection.deduplicate(List.of(farm, adjacent, bridge)).size() == 1, "Transitive overlap repairs all duplicates");
        check(farm.observed(0, 0, 2000).percent() == 0, "Harvested/empty positions are not ripe");
        check(farm.observed(40, 40, 2000).percent() == 50, "Half planted/ripe does not mean ready");
        check(farm.observed(80, 80, 2000).percent() == 100, "Entire planted farm ripe");
        check(farm.ripe() == 60 && farm.seen() == 1000, "Snapshots stay immutable for background save and unloaded display");
        check(!farm.observed(81, 80, 2000).valid(), "Reject invalid maturity counts");
        check(farm.renamed("  Karotten\n ").name().equals("Karotten"), "Clean individual name");
        check(farm.renamed(" ").name().equals("Meine Farm"), "Empty name fallback");
        Gson gson = new Gson();
        FarmRecord loaded = gson.fromJson(gson.toJson(farm.withWaypoint(17)), FarmRecord.class);
        check(loaded.valid() && loaded.waypointId() == 17 && loaded.plots().equals(farm.plots()), "JSON round trip preserves area and waypoint");
        check(loaded.dimension().equals("minecraft:overworld") && loaded.name().equals("Weizen") && loaded.seen() == 1000, "JSON round trip preserves metadata and last-known state");
        try { loaded.plots().clear(); throw new AssertionError("Mutable loaded snapshot"); }
        catch (UnsupportedOperationException expected) { checks++; }
        var many = new HashSet<FarmRecord.Plot>();
        for (int i = 0; i < 16384; i++) many.add(p((i % 128) * 3, 64, (i / 128) * 3));
        long start = System.nanoTime();
        check(FarmDetection.components(many, Set.of()).size() == many.size(), "Sparse maximum scan remains linear and keeps distinct plots");
        System.out.println("Farm detection benchmark: 16384 isolated plots in " + (System.nanoTime() - start) / 1_000_000 + " ms (background)");
        System.out.println("FarmTrackerTest: " + checks + " checks passed");
    }
}
