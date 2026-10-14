package com.example.examplemod.feature;

import com.example.examplemod.farm.*;
import com.example.examplemod.gui.FarmScreen;
import com.example.examplemod.gui.Notifications;
import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.Setting;
import com.example.examplemod.waypoint.Waypoint;
import com.example.examplemod.waypoint.WaypointStore;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Bounded client-only observation. No chunk loads, packets, planting or harvesting. */
public final class FarmTracker extends Module {
    private static final int RADIUS = 32, HEIGHT = 12, WIDTH = RADIUS * 2 + 1;
    private static final int DISCOVERY_BUDGET = 768, TRACK_BUDGET = 512, MAX_CROPS = 16384;
    private final ExecutorService analyzer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Lumen-Farmen-Erkennen"); t.setDaemon(true); return t;
    });
    public final Setting.Bool discovery, harvestNotice;
    public final Setting.Slider harvestThreshold;
    public final Setting.Slider areaGap;
    public record Suggestion(String dimension, List<FarmRecord.Plot> plots, Map<Integer, FarmAreas.Bounds> bounds) {
        public Suggestion(String dimension, List<FarmRecord.Plot> plots) {
            this(dimension, List.copyOf(plots), Map.copyOf(FarmAreas.bounds(plots)));
        }
    }
    private List<Suggestion> suggestions = List.of();
    private final Set<FarmRecord.Plot> ignored = new LinkedHashSet<>();
    private final Set<FarmRecord.Plot> found = new HashSet<>(), incomplete = new HashSet<>();
    private final Map<String, Set<Long>> observedChunks = new HashMap<>();
    private final Set<String> measured = new HashSet<>();
    private final Set<String> oversizedAreas = new HashSet<>();
    private final Map<String, Long> observedAt = new HashMap<>();
    private ClientLevel lastLevel;
    private BlockPos center;
    private int discoveryCursor, cooldown, generation;
    private boolean analyzing, overflow;
    private String scanMessage = "Umgebung wird untersucht …";
    private List<FarmRecord> scanFarms = List.of();
    private int farmCursor, plotCursor, ripe, planted, loaded;
    private final Set<Long> farmChunks = new HashSet<>();
    private int trackingCooldown;
    private boolean trackingInvalid;
    private FarmObservation observation, manualObservation;
    private FarmRecord manualFarm;
    private int manualCursor;
    private FarmRecord.Plot corner1, corner2;
    private String selectionDimension;
    private static String cropId(BlockState state) {
        return crop(state) ? net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString() : "";
    }
    public void corner(int number) {
        if (mc.player == null || mc.level == null) return;
        if (!dimension().equals(selectionDimension)) { corner1 = corner2 = null; selectionDimension = dimension(); }
        var p = new FarmRecord.Plot(mc.player.getBlockX(), (int) Math.ceil(mc.player.getY()), mc.player.getBlockZ());
        if (number == 1) corner1 = p; else corner2 = p;
        Notifications.info("Farmfläche", "Ecke " + number + ": " + p.x() + " / " + p.y() + " / " + p.z());
    }
    public String selectionText() {
        if (manualFarm != null) return "Fläche prüfen: " + (100L * manualCursor / manualFarm.area().volume()) + " %";
        return "Ecke 1: " + cornerText(corner1) + " · Ecke 2: " + cornerText(corner2);
    }
    private static String cornerText(FarmRecord.Plot p) { return p == null ? "offen" : p.x() + "/" + p.y() + "/" + p.z(); }
    public void clearSelection() { corner1 = corner2 = null; manualFarm = null; manualObservation = null; }
    public boolean saveSelection(String name) {
        if (!isEnabled() || !FarmStore.ready() || corner1 == null || corner2 == null || !dimension().equals(selectionDimension) || manualFarm != null) {
            Notifications.warning("Farmfläche", "Tracker aktivieren und zwei Ecken in derselben Dimension setzen"); return false;
        }
        var a = new FarmRecord.Area(Math.min(corner1.x(), corner2.x()), Math.min(corner1.y(), corner2.y()), Math.min(corner1.z(), corner2.z()),
                Math.max(corner1.x(), corner2.x()), Math.max(corner1.y(), corner2.y()), Math.max(corner1.z(), corner2.z()));
        if (!a.valid()) { Notifications.warning("Farmfläche", "Auswahl zu groß: maximal 65.536 Positionen"); return false; }
        var known = new LinkedHashMap<FarmRecord.Plot, String>();
        for (var f : FarmStore.all()) if (f.dimension().equals(dimension())) {
            if (f.plots().stream().anyMatch(a::contains) && !f.plots().stream().allMatch(a::contains)) {
                Notifications.warning("Farmfläche", "Vorhandene Farm nur teilweise enthalten. Auswahl vergrößern."); return false;
            }
            for (int i = 0; i < f.plots().size(); i++) if (a.contains(f.plots().get(i))) known.put(f.plots().get(i), f.crops().get(i));
        }
        if (known.size() > FarmRecord.MAX_PLOTS) { Notifications.warning("Farmfläche", "Maximal 4096 Pflanzplätze"); return false; }
        manualFarm = new FarmRecord(UUID.randomUUID().toString(), FarmRecord.cleanName(name), dimension(), List.copyOf(known.keySet()),
                0, 0, 0, -1, a, List.copyOf(known.values()), List.of());
        manualObservation = new FarmObservation(manualFarm); manualCursor = 0;
        generation++; analyzing = false; center = null;
        return true;
    }
    private void scanManual() {
        int budget = DISCOVERY_BUDGET;
        while (budget-- > 0 && manualCursor < manualFarm.area().volume()) {
            var p = manualFarm.area().at(manualCursor++);
            if (!loaded(p)) { Notifications.warning("Farmfläche", "Nicht vollständig geladen. Näher herangehen und erneut speichern."); manualFarm = null; return; }
            var state = mc.level.getBlockState(pos(p)); manualObservation.sample(p, cropId(state), ripe(state));
        }
        scanMessage = selectionText();
        if (manualCursor < manualFarm.area().volume()) return;
        var area = manualFarm.area();
        for (int x = area.minX() >> 4; x <= area.maxX() >> 4; x++) for (int z = area.minZ() >> 4; z <= area.maxZ() >> 4; z++)
            if (!mc.level.hasChunk(x, z)) { Notifications.warning("Farmfläche", "Bereich inzwischen entladen. Erneut speichern."); manualFarm = null; return; }
        if (manualObservation.overflow()) Notifications.warning("Farmfläche", "Mehr als 4096 Pflanzplätze; Auswahl verkleinern");
        else {
            var result = manualObservation.finish(manualFarm, System.currentTimeMillis());
            if (FarmStore.acceptManual(result)) {
                Notifications.success("Farmfläche gespeichert", result.name()); measured.clear();
                suggestions = suggestions.stream().filter(s -> !FarmStore.contains(s.dimension(), s.plots())).toList();
            } else Notifications.warning("Farmfläche", "Keine Pflanzen, überlappende Auswahl oder Farmlimit erreicht");
        }
        manualFarm = null; manualObservation = null;
    }

    public FarmTracker() {
        super("farm_tracker", "Farm Tracker", "Farmen entdecken, benennen und ihre Erntereife verfolgen", Category.WORLD);
        enabledByDefault();
        harvestNotice = add(new Setting.Bool("Erntehinweise", "Einmal je Farm melden; nach Ernte unter die Grenze erneut bereit", true));
        harvestThreshold = add(new Setting.Slider("Erntehinweis ab", "Anteil erntereifer Pflanzplätze; nur vollständig geladene Messungen", 100, 1, 100, 1, "%")
                .visibleWhen(harvestNotice::get));
        discovery = add(new Setting.Bool("Farmen vorschlagen", "Geladene Pflanzen im Umkreis von 32 Blöcken erkennen", true));
        areaGap = add(new Setting.Slider("Beet-Abstand", "Beete mit bis zu so vielen freien Blöcken verbinden; bestehende Bereiche bleiben erhalten", 6, 0, 16, 1, "Blöcke"));
        areaGap.onChange(() -> { reset(); FarmStore.consolidate(areaGap.getInt()); });
        add(new Setting.Action("Farm-Dashboard", "Farmen und Vorschläge ansehen", () -> mc.setScreen(new FarmScreen(mc.screen))));
    }
    public List<Suggestion> suggestions() { return suggestions; }
    public String scanMessage() { return scanMessage; }
    private String dimension() { return mc.level == null ? "" : mc.level.dimension().location().toString(); }
    public static boolean crop(BlockState state) { return state.getBlock() instanceof CropBlock || state.getBlock() instanceof NetherWartBlock; }
    public static boolean ripe(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) return crop.isMaxAge(state);
        return state.getBlock() instanceof NetherWartBlock && state.getValue(NetherWartBlock.AGE) == 3;
    }
    private static BlockPos pos(FarmRecord.Plot p) { return new BlockPos(p.x(), p.y(), p.z()); }
    private boolean loaded(FarmRecord.Plot p) { return mc.level.hasChunk(p.x() >> 4, p.z() >> 4); }
    public boolean current(FarmRecord farm) {
        if (!isEnabled() || mc.level == null || !farm.dimension().equals(dimension()) || farm.seen() == 0 || !measured.contains(farm.id())
                || System.currentTimeMillis() - observedAt.getOrDefault(farm.id(), 0L) > 10_000) return false;
        var chunks = observedChunks.get(farm.id());
        return chunks != null && chunks.stream().allMatch(key -> mc.level.hasChunk(ChunkPos.getX(key), ChunkPos.getZ(key)));
    }
    public String status(FarmRecord farm) {
        if (!isEnabled()) return "Pausiert · letzter Stand";
        if (!farm.dimension().equals(dimension())) return "Andere Dimension · letzter Stand";
        if (oversizedAreas.contains(farm.id())) return "Über 4096 Pflanzplätze · letzter Stand";
        if (!current(farm)) {
            if (farm.seen() == 0) return "Noch nicht vollständig geprüft";
            var chunks = observedChunks.get(farm.id());
            if (mc.level == null || !loaded(farm.position()) || (chunks != null && chunks.stream().anyMatch(key ->
                    !mc.level.hasChunk(ChunkPos.getX(key), ChunkPos.getZ(key))))) return "Nicht geladen · letzter Stand";
            return "Letzter Stand · wird erneut geprüft";
        }
        if (farm.ripe() == farm.plots().size()) return "Bereit zur Ernte";
        if (farm.planted() == 0) return "Abgeerntet / unbepflanzt";
        return farm.planted() < farm.plots().size() ? "Teilweise bepflanzt" : "Wächst";
    }
    @Override public void onTick() {
        if (mc.level == null || mc.player == null || !FarmStore.ready()) return;
        if (lastLevel != mc.level) { reset(); lastLevel = mc.level; FarmStore.consolidate(areaGap.getInt()); }
        track();
        if (manualFarm != null) scanManual();
        else if (discovery.get()) discover();
        else { suggestions = List.of(); scanMessage = "Farmvorschläge ausgeschaltet"; }
    }
    private void reset() {
        observation = null; manualFarm = null; manualObservation = null;
        generation++; analyzing = false; center = null; discoveryCursor = cooldown = 0;
        suggestions = List.of(); ignored.clear(); found.clear(); incomplete.clear();
        measured.clear(); oversizedAreas.clear(); observedChunks.clear(); observedAt.clear(); scanFarms = List.of(); trackingInvalid = false;
        farmCursor = plotCursor = ripe = planted = loaded = trackingCooldown = 0; farmChunks.clear();
    }
    @Override protected void onDisable() { reset(); }
    @Override public void onLeaveWorld() { reset(); lastLevel = null; clearSelection(); }

    private void track() {
        if (trackingCooldown > 0) { trackingCooldown--; return; }
        if (farmCursor >= scanFarms.size()) {
            scanFarms = FarmStore.all().stream().filter(f -> f.dimension().equals(dimension())).toList();
            farmCursor = 0;
            if (scanFarms.isEmpty()) { trackingCooldown = 20; return; }
        }
        int budget = TRACK_BUDGET;
        while (budget-- > 0 && farmCursor < scanFarms.size()) {
            FarmRecord farm = scanFarms.get(farmCursor);
            if (plotCursor == 0) observation = new FarmObservation(farm);
            FarmRecord.Plot p = farm.area() == null ? farm.plots().get(plotCursor) : farm.area().at(plotCursor);
            plotCursor++;
            farmChunks.add(ChunkPos.asLong(p.x() >> 4, p.z() >> 4));
            if (loaded(p)) {
                loaded++;
                BlockState state = mc.level.getBlockState(pos(p));
                observation.sample(p, cropId(state), ripe(state));
            }
            if (plotCursor == (farm.area() == null ? farm.plots().size() : farm.area().volume())) {
                if (observation.overflow()) oversizedAreas.add(farm.id());
                else oversizedAreas.remove(farm.id());
                FarmRecord actual = FarmStore.get(farm.id());
                boolean complete = !trackingInvalid && !observation.overflow() && loaded == plotCursor && farmChunks.stream().allMatch(key ->
                        mc.level.hasChunk(ChunkPos.getX(key), ChunkPos.getZ(key)));
                if (actual != null && actual.plots() == farm.plots() && complete) {
                    measured.add(farm.id()); observedChunks.put(farm.id(), Set.copyOf(farmChunks));
                    long now = System.currentTimeMillis();
                    observedAt.put(farm.id(), now);
                    var result = observation.finish(actual, now);
                    var notice = FarmHarvestNotice.evaluate(actual, result, harvestThreshold.getInt(), harvestNotice.get());
                    result = result.withHarvestNotified(notice.notified());
                    if (actual.ripe() != result.ripe() || actual.planted() != result.planted() || !actual.crops().equals(result.crops())
                            || !actual.beds().equals(result.beds()) || actual.plots() != result.plots() || actual.harvestNotified() != result.harvestNotified() || now - actual.seen() >= 30_000) {
                        if (FarmStore.observe(actual, result) && notice.announce())
                            Notifications.success("Erntebereit: " + result.name(), result.percent() + " % erntereif · Grenze " + harvestThreshold.getInt() + " %");
                    }
                } else measured.remove(farm.id());
                farmCursor++; plotCursor = ripe = planted = loaded = 0; farmChunks.clear(); trackingInvalid = false;
            }
        }
        if (farmCursor >= scanFarms.size()) trackingCooldown = 40;
    }

    private void discover() {
        if (analyzing || cooldown-- > 0) return;
        if (center == null || center.distSqr(mc.player.blockPosition()) > 16 * 16) {
            center = mc.player.blockPosition(); discoveryCursor = 0; found.clear(); incomplete.clear(); overflow = false;
        }
        int volume = WIDTH * WIDTH * (2 * HEIGHT + 1);
        int end = Math.min(volume, discoveryCursor + DISCOVERY_BUDGET);
        for (; discoveryCursor < end; discoveryCursor++) {
            int i = discoveryCursor;
            int x = center.getX() - RADIUS + i % WIDTH;
            int z = center.getZ() - RADIUS + (i / WIDTH) % WIDTH;
            int y = center.getY() - HEIGHT + i / (WIDTH * WIDTH);
            if (y < mc.level.getMinY() || y > mc.level.getMaxY() || !mc.level.hasChunk(x >> 4, z >> 4)) continue;
            if (!crop(mc.level.getBlockState(new BlockPos(x, y, z)))) continue;
            var p = new FarmRecord.Plot(x, y, z);
            if (found.size() >= MAX_CROPS) { overflow = true; continue; }
            found.add(p);
            if (Math.abs(x - center.getX()) >= RADIUS - areaGap.getInt() || Math.abs(z - center.getZ()) >= RADIUS - areaGap.getInt()
                    || !mc.level.hasChunk((x - 1) >> 4, (z - 1) >> 4) || !mc.level.hasChunk((x + 1) >> 4, (z + 1) >> 4)
                    || !mc.level.hasChunk((x - 1) >> 4, (z + 1) >> 4) || !mc.level.hasChunk((x + 1) >> 4, (z - 1) >> 4)) incomplete.add(p);
        }
        scanMessage = "Umgebung prüfen · " + (100 * discoveryCursor / volume) + " %";
        if (discoveryCursor < volume) return;
        if (overflow) { scanMessage = "Zu viele Pflanzen im Scanbereich"; center = null; cooldown = 100; return; }
        // Reject observations that became unavailable during this pass.
        for (var p : found) if (!loaded(p)) incomplete.add(p);
        Set<FarmRecord.Plot> snapshot = Set.copyOf(found);
        Set<FarmRecord.Plot> edges = Set.copyOf(incomplete);
        String dim = dimension(); int request = generation; int gap = areaGap.getInt();
        var manualAreas = FarmStore.all().stream().filter(f -> f.dimension().equals(dim) && f.area() != null).map(FarmRecord::area).toList();
        analyzing = true; scanMessage = "Zusammenhängende Flächen erkennen …";
        analyzer.execute(() -> {
            Set<FarmRecord.Plot> eligible = snapshot;
            if (!manualAreas.isEmpty()) {
                eligible = new HashSet<>(snapshot);
                eligible.removeIf(p -> {
                    for (var area : manualAreas) if (area.contains(p)) return true;
                    return false;
                });
            }
            List<List<FarmRecord.Plot>> areas = FarmDetection.components(eligible, edges, gap);
            mc.execute(() -> {
                if (request != generation) return;
                analyzing = false; center = null; cooldown = 100;
                if (!discovery.get()) return;
                suggestions = areas.stream().filter(area -> !FarmDetection.touchesKnown(area, ignored) && !FarmStore.contains(dim, area))
                        .limit(32).map(area -> new Suggestion(dim, area)).toList();
                scanMessage = suggestions.isEmpty() ? "Keine neuen Farmflächen in der Nähe" : suggestions.size() + " Farmvorschläge gefunden";
            });
        });
    }

    public void dismiss(Suggestion suggestion) {
        ignored.addAll(suggestion.plots());
        trimIgnored();
        suggestions = suggestions.stream().filter(s -> !s.dimension().equals(suggestion.dimension())
                || (!FarmDetection.touchesKnown(s.plots(), ignored) && !FarmStore.contains(s.dimension(), s.plots()))).toList();
    }
    public boolean confirm(Suggestion suggestion, String name) {
        if (!isEnabled() || mc.level == null || !dimension().equals(suggestion.dimension()) || !FarmStore.ready()) {
            Notifications.warning("Farm Tracker", "Tracker aktivieren und die Farm in ihrer Dimension laden"); return false;
        }
        if (FarmStore.contains(suggestion.dimension(), suggestion.plots())) {
            dismiss(suggestion);
            Notifications.warning("Farm Tracker", "Diese Fläche gehört bereits zu einer gespeicherten Farm"); return false;
        }
        int ready = 0;
        for (var p : suggestion.plots()) {
            if (!loaded(p) || !crop(mc.level.getBlockState(pos(p)))) {
                Notifications.warning("Farm Tracker", "Fläche hat sich geändert oder ist nicht geladen. Bitte erneut scannen lassen."); return false;
            }
            if (ripe(mc.level.getBlockState(pos(p)))) ready++;
        }
        var farm = new FarmRecord(UUID.randomUUID().toString(), FarmRecord.cleanName(name), suggestion.dimension(),
                suggestion.plots(), ready, suggestion.plots().size(), System.currentTimeMillis(), -1);
        if (!FarmStore.acceptArea(farm, areaGap.getInt())) { Notifications.warning("Farm Tracker", "Maximal 128 Farmen / 4096 Pflanzplätze pro Bereich"); return false; }
        suggestions = suggestions.stream().filter(s -> !FarmStore.contains(s.dimension(), s.plots())).toList();
        measured.clear();
        Notifications.success("Farmbereich gespeichert", "Pflanzplätze übernommen; Erntereife wird geprüft"); return true;
    }
    public void rename(FarmRecord farm, String name) {
        FarmRecord current = FarmStore.get(farm.id());
        if (current == null) return;
        FarmRecord renamed = current.renamed(name); FarmStore.put(renamed);
        Waypoint point = linkedWaypoint(current);
        if (point != null) { point.name = renamed.name(); WaypointStore.save(); }
    }
    public void navigate(FarmRecord farm) {
        FarmRecord current = FarmStore.get(farm.id());
        if (current == null) return;
        Waypoint point = linkedWaypoint(current);
        if (point == null) {
            var p = current.position();
            point = WaypointStore.add(current.name(), p.x() + 0.5, p.y(), p.z() + 0.5, 0, 0, current.dimension());
            WaypointStore.assignGroup(point, "Farmen");
            FarmStore.put(current.withWaypoint(point.id));
        }
        WaypointStore.setTarget(point);
        Notifications.success("Farm als Ziel gesetzt", current.name());
    }
    public void delete(FarmRecord farm) {
        FarmStore.remove(farm.id()); measured.remove(farm.id()); observedChunks.remove(farm.id()); observedAt.remove(farm.id());
        if (farm.dimension().equals(dimension())) ignored.addAll(farm.plots());
        trimIgnored();
        // A created waypoint remains an independent user waypoint.
    }
    @Override public String getHudInfo() { return FarmStore.all().size() + " Farmen"; }

    private static Waypoint linkedWaypoint(FarmRecord farm) {
        Waypoint point = WaypointStore.find(farm.waypointId());
        var p = farm.position();
        return point != null && farm.dimension().equals(point.dimension) && Math.abs(point.x - (p.x() + 0.5)) < 0.01
                && Math.abs(point.y - p.y()) < 0.01 && Math.abs(point.z - (p.z() + 0.5)) < 0.01 ? point : null;
    }
    private void trimIgnored() {
        var it = ignored.iterator();
        while (ignored.size() > 65536 && it.hasNext()) { it.next(); it.remove(); }
    }
    private static boolean intersectsChunk(FarmRecord.Area a, int x, int z) {
        return x >= (a.minX() >> 4) && x <= (a.maxX() >> 4) && z >= (a.minZ() >> 4) && z <= (a.maxZ() >> 4);
    }
    public void onChunkUnload(int x, int z) {
        long key = ChunkPos.asLong(x, z);
        if (manualFarm != null && intersectsChunk(manualFarm.area(), x, z)) {
            manualFarm = null; manualObservation = null;
            Notifications.warning("Farmfläche", "Chunk entladen. Auswahl bitte erneut speichern.");
        }
        if (farmCursor < scanFarms.size() && scanFarms.get(farmCursor).area() != null
                && intersectsChunk(scanFarms.get(farmCursor).area(), x, z)) trackingInvalid = true;
        measured.removeIf(id -> observedChunks.getOrDefault(id, Set.of()).contains(key));
        if (farmChunks.contains(key)) trackingInvalid = true;
        // Discard a discovery pass touching an unloaded chunk, including pending analysis results.
        if (center != null && Math.abs((x << 4) + 8 - center.getX()) <= RADIUS + 16
                && Math.abs((z << 4) + 8 - center.getZ()) <= RADIUS + 16) {
            generation++; analyzing = false; center = null; suggestions = List.of();
        }
    }
}
