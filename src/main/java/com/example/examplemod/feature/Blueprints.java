package com.example.examplemod.feature;

import com.example.examplemod.ClientKeybinds;
import com.example.examplemod.ExampleMod;
import com.example.examplemod.blueprint.Blueprint;
import com.example.examplemod.blueprint.BlueprintPlan;
import com.example.examplemod.blueprint.BlueprintSavedStates;
import com.example.examplemod.blueprint.BlueprintCapture;
import com.example.examplemod.blueprint.BlueprintReader;
import com.example.examplemod.blueprint.BlueprintWriter;
import com.example.examplemod.gui.BlueprintPreviews;
import com.example.examplemod.gui.BlueprintScreen;
import com.example.examplemod.gui.Notifications;
import com.example.examplemod.module.Category;
import com.example.examplemod.module.ClientConfig;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.ModuleManager;
import com.example.examplemod.module.Setting;
import com.example.examplemod.render.BlueprintRenderer;
import com.example.examplemod.render.WorldRenderContext;
import com.example.examplemod.waypoint.WaypointStore;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.example.examplemod.gui.Theme;
import com.example.examplemod.render.Render3D;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.SharedConstants;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Baupläne (.litematic, .schem, Struktur-.nbt): laden → platzieren und drehen → Materialliste → nachbauen.
 * Rein clientseitig; die Welt wird nie verändert. Der zuletzt geladene Plan wird pro Welt bzw. Server
 * in config/examplemod/blueprints gemerkt und beim nächsten Betreten wiederhergestellt.
 */
public final class Blueprints extends Module {
    private final java.util.concurrent.ThreadPoolExecutor loader = new java.util.concurrent.ThreadPoolExecutor(
            1, 1, 10, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.LinkedBlockingQueue<>(),
            r -> new Thread(r, "Lumen-Bauplan-Laden"));
    { loader.allowCoreThreadTimeOut(true); }
    private final BlueprintRenderer renderer = new BlueprintRenderer();
    public final Setting.Slider opacity, range;
    /** Linien an Blöcken: Aus, Nur Fehler (rot/gelb/violett) oder Alles (zusätzlich blau = fehlt). */
    public final Setting.Mode markers;
    public static final String MARKERS_OFF = "Aus", MARKERS_ERRORS = "Nur Fehler", MARKERS_ALL = "Alles";
    public final Setting.Mode blockInfo;
    public final Setting.Mode infoPosition;
    public final Setting.Bool pickBlock;
    public final Setting.Bool autoLayer;
    public final Setting.Bool lazyBuild;
    public final Setting.Mode lazyMode, lazyTempo;
    public final LazyBuilder lazyBuilder = new LazyBuilder(this);
    public static final String POSITION_TOP = "Oben mittig", POSITION_CROSSHAIR = "Am Fadenkreuz";
    /** Ob die Blockumrandung von hier eingeschaltet wurde und beim Ausschalten wieder aus soll. */
    private boolean outlineEnabledByUs;
    private BlueprintPlan plan;
    private Blueprint sourceBlueprint;
    private final java.util.Map<String, String> replacements = new java.util.TreeMap<>();
    private Path file;
    private Future<?> pending;
    private int generation;
    private boolean loading;
    private String message = "Eine .litematic-, .schem- oder Struktur-.nbt-Datei auswählen.";
    /** Zuletzt gespeicherte Platzierung; bei Änderung wird die Welt-Datei neu geschrieben. */
    private String savedSignature;
    private String sourceHash;
    private final java.util.ArrayDeque<Integer> hiddenUndo = new java.util.ArrayDeque<>();

    /** Platzierung, die beim Wiederherstellen statt "vor dem Spieler" übernommen wird. */
    private record Placement(BlockPos origin, String dimension, int turns, boolean mirrored,
                             BlueprintPlan.Layers layers, int layer, boolean onlyMissing, boolean visible, int[] accepted,
                             int[] ignored, String sourceHash, java.util.Map<String, String> replacements) {}

    public Blueprints() {
        super("blueprints", "Baupläne", "Transparente Blockvorschau, Ebenen und Materialbedarf", Category.WORLD);
        notPersistent();
        lazyBuild = add(new Setting.Bool("Lazy-Bauen", "Taste umschalten: erreichbare Bauplan-Blöcke nacheinander setzen", false));
        lazyMode = add(new Setting.Mode("Lazy-Bereich", "Nur im Blickfeld oder alle erreichbaren Stellen der gewählten Ebene",
                "Im Blickfeld", "Im Blickfeld", "Aktuelle Ebene"));
        lazyTempo = add(new Setting.Mode("Lazy-Tempo", "Maximal 2 / 3,3 / 5 Blöcke pro Sekunde, jeweils mit Serverbestätigung",
                "Normal", "Ruhig", "Normal", "Zügig"));
        autoLayer = add(new Setting.Bool("Automatischer Ebenenwechsel",
                "Fertige Ebene nach 1,5 Sekunden erneut prüfen und zur nächsten wechseln", false));
        opacity = add(new Setting.Slider("Deckkraft", "Durchsichtige Vorschau mit echten Blocktexturen", 50, 15, 100, 5, "%"));
        range = add(new Setting.Slider("Sichtweite", "Vorschau nur in geladenen Chunks innerhalb dieser Entfernung", 48, 16, 96, 8, "Bl."));
        markers = add(new Setting.Mode("Markierungen",
                "Nur Fehler: rot = falscher Block, gelb = Ausrichtung, violett = unbekannt · Alles: zusätzlich blau = fehlt",
                MARKERS_ERRORS, MARKERS_OFF, MARKERS_ERRORS, MARKERS_ALL));
        blockInfo = add(new Setting.Mode("Block-Info",
                "Name, Ausrichtung und Inventar des anvisierten, noch nicht gebauten Blocks. Taste im Bauplan-Fenster (Platzierung) frei wählbar, Standard: linkes Alt",
                INFO_HOLD, INFO_HOLD, INFO_TOGGLE, INFO_ALWAYS, INFO_OFF).onChange(() -> infoToggled = false));
        infoPosition = add(new Setting.Mode("Block-Info-Position", "Wo der Tooltip der Block-Info erscheint",
                POSITION_TOP, POSITION_TOP, POSITION_CROSSHAIR));
        pickBlock = add(new Setting.Bool("Mittelklick wählt Bauplan-Block",
                "Mausrad-Klick auf einen noch nicht gebauten Block nimmt das passende Item aus dem Inventar in die Hand, wie Block auswählen im Überlebensmodus",
                true));
        add(new Setting.Action("Baupläne öffnen", "Datei laden, platzieren und Materialliste ansehen", () -> mc.setScreen(new BlueprintScreen(mc.screen))));
    }

    public Path directory() { return mc.gameDirectory.toPath().resolve("schematics"); }
    public BlueprintPlan plan() { return plan; }
    /** Datei des geladenen Plans, sonst {@code null}. */
    public Path file() { return file; }
    public boolean loading() { return loading; }
    public String message() { return message; }
    public BlueprintRenderer renderer() { return renderer; }

    public void load(Path path) { saveState(); load(path.toAbsolutePath().normalize(), null); }

    private void load(Path path, Placement initialPlacement) {
        if (mc.level == null || mc.player == null) { message = "Bitte zuerst eine Welt betreten."; return; }
        if (pending != null) pending.cancel(true);
        int request = ++generation;
        lazyBuilder.cancel();
        Path worldState = stateFile();
        String fallbackDimension = mc.level.dimension().location().toString();
        BlockPos fallbackAnchor = mc.player.blockPosition().relative(mc.player.getDirection(), 3);
        loading = true;
        message = "Lade " + path.getFileName() + " …";
        pending = loader.submit(() -> {
            try {
                Placement placement = initialPlacement;
                if (placement == null && worldState != null) {
                    // Migrate the former single active state before another file can replace it.
                    JsonObject last = BlueprintSavedStates.read(worldState);
                    if (last != null) {
                        Path oldArchive = BlueprintSavedStates.archive(worldState, Path.of(last.get("file").getAsString()));
                        if (!Files.exists(oldArchive)) ClientConfig.writeAtomically(oldArchive, last.toString());
                    }
                    JsonObject saved = BlueprintSavedStates.read(BlueprintSavedStates.archive(worldState, path));
                    if (saved != null) {
                        if (!BlueprintSavedStates.identity(path).equals(BlueprintSavedStates.identity(Path.of(saved.get("file").getAsString()))))
                            throw new IOException("Gespeicherter Zustand gehört zu einer anderen Bauplandatei");
                        placement = placement(saved);
                    }
                }
                Blueprint data = BlueprintReader.read(path);
                String fingerprint = BlueprintSavedStates.fingerprint(path);
                boolean changedFile = placement != null && !BlueprintSavedStates.sameContents(placement.sourceHash(), fingerprint);
                if (changedFile && worldState != null) {
                    Path previous = BlueprintSavedStates.archive(worldState, path);
                    if (Files.exists(previous)) Files.copy(previous, previous.resolveSibling(previous.getFileName()
                            + ".before-source-change-" + java.util.UUID.randomUUID() + ".bak"));
                }
                Placement restoredPlacement = placement;
                String dimension = placement == null ? fallbackDimension : placement.dimension();
                BlockPos anchor = placement == null ? fallbackAnchor : placement.origin();
                BlueprintPreviews.prepare(path, data);
                mc.execute(() -> {
                    if (request != generation) return;
                    loading = false;
                    if (mc.level == null || (restoredPlacement == null && !mc.level.dimension().location().toString().equals(dimension))) {
                        message = "Welt oder Dimension gewechselt. Bitte erneut laden.";
                        return;
                    }
                    try {
                        var restored = restoredPlacement == null || changedFile ? java.util.Map.<String, String>of() : restoredPlacement.replacements();
                        Blueprint replaced = restored.isEmpty() ? data : com.example.examplemod.blueprint.BlueprintMaterials.remap(data, restored, BlueprintPlan::replacementState);
                        BlueprintPlan loaded = new BlueprintPlan(replaced, anchor, dimension);
                        if (restoredPlacement != null) {
                            loaded.setTransform(restoredPlacement.turns(), restoredPlacement.mirrored());
                            loaded.layers = restoredPlacement.layers();
                            loaded.layer = Math.max(0, Math.min(data.sizeY() - 1, restoredPlacement.layer()));
                            loaded.onlyMissing = restoredPlacement.onlyMissing();
                            if (!changedFile) {
                                loaded.restoreAccepted(restoredPlacement.accepted());
                                loaded.restoreIgnored(restoredPlacement.ignored());
                            }
                        }
                        saveState();
                        plan = loaded;
                        focusItem = null;
                        sourceBlueprint = data;
                        replacements.clear();
                        replacements.putAll(restored);
                        file = path; sourceHash = fingerprint; savedSignature = null; hiddenUndo.clear();
                        renderer.clear();
                        setEnabled(restoredPlacement == null || restoredPlacement.visible(), false);
                        message = data.cells().size() + " Blöcke geladen · Platzierung prüfen";
                        if (restoredPlacement == null) Notifications.success("Bauplan geladen", data.name());
                        else Notifications.info("Bauplan wiederhergestellt", data.name());
                        if (changedFile) Notifications.warning("Bauplandatei geändert", "Platzierung behalten; Anpassungen zur Sicherheit zurückgesetzt");
                        saveState();
                    } catch (RuntimeException failure) { failed(failure); }
                });
            } catch (Exception failure) {
                mc.execute(() -> { if (request == generation) { loading = false; failed(failure); } });
            }
        });
    }

    private void failed(Exception failure) {
        message = failure.getMessage() == null ? "Bauplan konnte nicht geladen werden." : failure.getMessage();
        ExampleMod.LOGGER.warn("Bauplan konnte nicht geladen werden", failure);
        Notifications.error("Bauplan", message);
    }

    /** Unload the active plan; keep each file's saved workspace. */
    public void clear() {
        saveState();
        unload();
        message = "Bauplan entladen. Deine Welt bleibt unverändert.";
        deleteState();
    }

    private void unload() {
        lazyBuilder.cancel();
        generation++;
        if (pending != null) pending.cancel(true);
        loading = false;
        plan = null;
        focusItem = null;
        sourceBlueprint = null;
        replacements.clear();
        file = null; sourceHash = null; hiddenUndo.clear();
        savedSignature = null;
        renderer.clear();
        // Eine laufende Bereichsauswahl bleibt sichtbar, auch ohne geladenen Plan.
        setEnabled(corner1 != null || corner2 != null, false);
    }

    /**
     * Kopiert eine hineingezogene Datei in den Bauplan-Ordner (ohne Vorhandenes zu überschreiben),
     * damit sie in der Liste bleibt und nach einem Neustart wiederhergestellt werden kann.
     */
    public Path importFile(Path source) {
        try {
            Path folder = directory();
            Files.createDirectories(folder);
            if (source.toAbsolutePath().normalize().getParent().equals(folder.toAbsolutePath().normalize())) return source;
            String name = source.getFileName().toString();
            Path target = folder.resolve(name);
            int dot = name.lastIndexOf('.');
            for (int n = 2; Files.exists(target); n++) {
                if (Files.size(target) == Files.size(source) && Files.mismatch(target, source) == -1) return target;
                target = folder.resolve(name.substring(0, dot) + " (" + n + ")" + name.substring(dot));
            }
            Files.copy(source, target);
            return target;
        } catch (IOException | RuntimeException failure) {
            ExampleMod.LOGGER.warn("Bauplan konnte nicht in den Ordner kopiert werden: {}", source, failure);
            return source;
        }
    }

    // ---- Darstellung der Vorschau ----

    private BlueprintRenderer.Style renderStyle() {
        BlueprintRenderer.Markers lines = markers.is(MARKERS_OFF) ? BlueprintRenderer.Markers.OFF
                : markers.is(MARKERS_ALL) ? BlueprintRenderer.Markers.ALL : BlueprintRenderer.Markers.ERRORS;
        return new BlueprintRenderer.Style((float) opacity.get() / 100F, lines);
    }

    // ---- Aufnehmen: eigenen Bauplan aus der Welt speichern ----

    private BlockPos corner1, corner2;
    private String selectionDimension;
    private boolean saving;

    public BlockPos corner(int which) { return which == 1 ? corner1 : corner2; }
    public boolean hasSelection() { return corner1 != null && corner2 != null; }
    public boolean saving() { return saving; }

    /** Anvisierter Block oder {@code null}. */
    public BlockPos aimedBlock() {
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) return hit.getBlockPos();
        return null;
    }

    public void setCorner(int which, BlockPos pos) {
        if (mc.level == null || pos == null) return;
        String dimension = mc.level.dimension().location().toString();
        if (selectionDimension != null && !selectionDimension.equals(dimension)) corner1 = corner2 = null;
        selectionDimension = dimension;
        if (which == 1) corner1 = pos.immutable(); else corner2 = pos.immutable();
        if (!isEnabled()) setEnabled(true, false);
        String text = "Ecke " + which + ": " + pos.getX() + " / " + pos.getY() + " / " + pos.getZ();
        Notifications.info("Bauplan-Aufnahme", hasSelection() ? text + " · " + selectionSize() : text);
    }

    /** z.B. "12 × 8 × 15 (1.440 Positionen)". */
    public String selectionSize() {
        if (!hasSelection()) return "";
        int sx = Math.abs(corner1.getX() - corner2.getX()) + 1;
        int sy = Math.abs(corner1.getY() - corner2.getY()) + 1;
        int sz = Math.abs(corner1.getZ() - corner2.getZ()) + 1;
        return String.format(java.util.Locale.GERMANY, "%d × %d × %d (%,d Positionen)", sx, sy, sz, BlueprintCapture.volume(corner1, corner2));
    }

    public boolean selectionTooLarge() { return hasSelection() && BlueprintCapture.volume(corner1, corner2) > BlueprintCapture.MAX_VOLUME; }

    public void clearSelection() {
        corner1 = corner2 = null;
        selectionDimension = null;
        if (plan == null && !loading) setEnabled(false, false);
    }

    /**
     * Liest den Bereich (Haupt-Thread, braucht die Welt) und schreibt die Datei im Hintergrund.
     * Vorhandene Dateien werden nie überschrieben.
     */
    public void saveSelection(String rawName, BlueprintWriter.Format format) {
        if (!hasSelection() || mc.level == null || mc.player == null) {
            Notifications.warning("Bauplan-Aufnahme", "Zuerst beide Ecken setzen");
            return;
        }
        if (!mc.level.dimension().location().toString().equals(selectionDimension)) {
            Notifications.warning("Bauplan-Aufnahme", "Die Auswahl liegt in einer anderen Dimension");
            return;
        }
        if (saving) return;
        String name = cleanName(rawName);
        Blueprint captured;
        try {
            captured = BlueprintCapture.capture(mc.level, corner1, corner2, name);
        } catch (IOException failure) {
            message = failure.getMessage();
            Notifications.error("Bauplan-Aufnahme", message);
            return;
        }
        String author = mc.player.getName().getString();
        int dataVersion = SharedConstants.getCurrentVersion().dataVersion().version();
        Path target = uniqueFile(name, format);
        saving = true;
        message = "Speichere " + target.getFileName() + " …";
        loader.submit(() -> {
            try {
                // Litematica-Dateien bekommen gleich ein Vorschaubild mit.
                Blueprint withPreview = format == BlueprintWriter.Format.LITEMATIC
                        ? new Blueprint(captured.name(), target.getFileName().toString(), captured.sizeX(), captured.sizeY(),
                        captured.sizeZ(), captured.palette(), captured.cells(), captured.warnings(), BlueprintPreviews.render(captured))
                        : captured;
                BlueprintWriter.write(target, BlueprintWriter.toNbt(withPreview, format, author, dataVersion));
                BlueprintPreviews.prepare(target, withPreview);
                mc.execute(() -> {
                    saving = false;
                    message = "Gespeichert: " + target.getFileName();
                    Notifications.success("Bauplan gespeichert", target.getFileName() + " · " + captured.cells().size() + " Blöcke");
                });
            } catch (Exception failure) {
                mc.execute(() -> {
                    saving = false;
                    failed(failure);
                });
            }
        });
    }

    private Path uniqueFile(String name, BlueprintWriter.Format format) {
        Path target = directory().resolve(name + format.extension);
        for (int n = 2; Files.exists(target); n++) target = directory().resolve(name + " (" + n + ")" + format.extension);
        return target;
    }

    /** Für Windows erlaubter Dateiname; leer → "Bauplan 2026-10-12 14-30". */
    private static String cleanName(String raw) {
        String name = raw == null ? "" : raw.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (name.length() > 64) name = name.substring(0, 64).trim();
        if (name.isEmpty() || name.startsWith(".")) {
            name = "Bauplan " + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm"));
        }
        return name;
    }

    private void renderSelection(WorldRenderContext context) {
        if ((corner1 == null && corner2 == null) || mc.level == null
                || !mc.level.dimension().location().toString().equals(selectionDimension)) return;
        VertexConsumer lines = Render3D.lines(context);
        if (corner1 != null) Render3D.box(context, lines, new AABB(corner1).inflate(0.03), Theme.ACCENT);
        if (corner2 != null) Render3D.box(context, lines, new AABB(corner2).inflate(0.03), Theme.ACCENT_2);
        AABB area = null;
        if (hasSelection()) {
            area = new AABB(corner1).minmax(new AABB(corner2));
            Render3D.box(context, lines, area.inflate(0.01), Theme.alpha(Theme.ACCENT_2, 0.85F));
        }
        Render3D.flush(context);
        if (area != null) {
            Render3D.label(context, new Vec3(area.getCenter().x, area.maxY + 0.6, area.getCenter().z),
                    "Auswahl · " + selectionSize(), selectionTooLarge() ? Theme.DANGER : Theme.ACCENT_2, 0.025F);
            context.buffers().endBatch();
        }
    }

    // ---- Ebenen per Taste (Bild auf / Bild ab) ----

    /** Erster Druck: Ebene auf Höhe des Spielers. Über die oberste bzw. unterste Ebene hinaus: alle Ebenen. */
    public void stepLayer(int direction) {
        if (plan == null) {
            Notifications.info("Baupläne", "Kein Bauplan geladen");
            return;
        }
        int height = plan.blueprint.sizeY();
        if (plan.layers == BlueprintPlan.Layers.ALL) {
            plan.layers = BlueprintPlan.Layers.SINGLE;
            int relative = mc.player == null ? 0 : mc.player.getBlockY() - plan.origin().getY();
            plan.layer = Math.max(0, Math.min(height - 1, relative));
        } else {
            int next = plan.layer + direction;
            if (next < 0 || next >= height) {
                plan.layers = BlueprintPlan.Layers.ALL;
                Notifications.info("Bauplan-Ebene", "Alle Ebenen");
                return;
            }
            plan.layer = next;
        }
        if (!isEnabled()) setEnabled(true, false);
        Notifications.info("Bauplan-Ebene", "Ebene " + (plan.layer + 1) + " / " + height + " · Y " + (plan.origin().getY() + plan.layer));
    }

    // ---- Merken pro Welt ----

    private Path stateFile() {
        String key = WaypointStore.worldKey();
        if (key == null) return null;
        return ClientConfig.directory().resolve("blueprints").resolve(key.replaceAll("[^a-zA-Z0-9.-]", "_") + ".json");
    }

    private String signature() {
        if (plan == null || file == null) return null;
        BlockPos o = plan.origin();
        return file + "|" + o.getX() + "," + o.getY() + "," + o.getZ() + "|" + plan.turns() + plan.mirrored()
                + "|" + plan.layers + plan.layer + plan.onlyMissing + isEnabled() + "|" + plan.acceptedVersion() + "|" + replacements;
    }

    public void saveState() {
        Path target = stateFile();
        String signature = signature();
        if (target == null || signature == null || signature.equals(savedSignature)) return;
        BlockPos o = plan.origin();
        JsonObject json = new JsonObject();
        json.addProperty("version", 2); json.addProperty("sourceHash", sourceHash);
        json.addProperty("file", file.toAbsolutePath().toString());
        json.addProperty("dimension", plan.dimension);
        json.addProperty("x", o.getX());
        json.addProperty("y", o.getY());
        json.addProperty("z", o.getZ());
        json.addProperty("turns", plan.turns());
        json.addProperty("mirrored", plan.mirrored());
        json.addProperty("layers", plan.layers.name());
        json.addProperty("layer", plan.layer);
        json.addProperty("onlyMissing", plan.onlyMissing);
        json.addProperty("visible", isEnabled());
        com.google.gson.JsonArray accepted = new com.google.gson.JsonArray();
        for (int cell : plan.acceptedCells()) accepted.add(cell);
        json.add("accepted", accepted);
        com.google.gson.JsonArray ignored = new com.google.gson.JsonArray();
        for (int cell : plan.ignoredCells()) ignored.add(cell);
        json.add("ignored", ignored);
        JsonObject swaps = new JsonObject();
        replacements.forEach(swaps::addProperty);
        json.add("replacements", swaps);
        Path archive = BlueprintSavedStates.archive(target, file);
        String snapshot = json.toString();
        loader.execute(() -> {
            ClientConfig.writeAtomically(archive, snapshot);
            ClientConfig.writeAtomically(target, snapshot);
        });
        savedSignature = signature;
    }

    private void deleteState() {
        Path target = stateFile();
        if (target == null) return;
        loader.execute(() -> {
            try { Files.deleteIfExists(target); }
            catch (IOException e) { ExampleMod.LOGGER.warn("Aktiver Bauplan konnte nicht entladen werden", e); }
        });
    }

    private static Placement placement(JsonObject json) {
        java.util.Map<String, String> swaps = new java.util.TreeMap<>();
        if (json.has("replacements")) json.getAsJsonObject("replacements").entrySet().forEach(e -> swaps.put(e.getKey(), e.getValue().getAsString()));
        return new Placement(new BlockPos(json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt()),
                json.get("dimension").getAsString(), json.get("turns").getAsInt(), json.get("mirrored").getAsBoolean(),
                BlueprintPlan.Layers.valueOf(json.get("layers").getAsString()), json.get("layer").getAsInt(),
                json.get("onlyMissing").getAsBoolean(), json.get("visible").getAsBoolean(),
                BlueprintSavedStates.cells(json, "accepted"), BlueprintSavedStates.cells(json, "ignored"),
                json.has("sourceHash") && !json.get("sourceHash").isJsonNull() ? json.get("sourceHash").getAsString() : null, swaps);
    }

    /** Reads after queued saves, including a rapid leave/rejoin of the same world. */
    public void restore() {
        Path target = stateFile();
        if (target == null) return;
        int request = ++generation;
        loader.execute(() -> {
            try {
                JsonObject json = BlueprintSavedStates.read(target);
                if (json == null) return;
                Path path = Path.of(json.get("file").getAsString());
                Placement saved = placement(json);
                mc.execute(() -> {
                    if (request != generation || mc.level == null) return;
                    if (!Files.exists(path)) {
                        Notifications.warning("Bauplan", "Datei nicht mehr vorhanden: " + path.getFileName()); return;
                    }
                    load(path, saved);
                });
            } catch (IOException | RuntimeException failure) {
                ExampleMod.LOGGER.warn("Bauplan-Zustand ist ungültig: {}", target, failure);
                mc.execute(() -> { if (request == generation) Notifications.warning("Bauplan", "Speicherstand nicht lesbar; Datei bleibt erhalten"); });
            }
        });
    }

    public Blueprint sourceBlueprint() { return sourceBlueprint; }
    public java.util.Map<String, String> replacements() { return java.util.Collections.unmodifiableMap(replacements); }
    public boolean canReplace(String source, String target) {
        if (sourceBlueprint == null) return false;
        boolean found = false;
        for (var state : sourceBlueprint.palette()) if (state.name().equals(source)) {
            found = true;
            if (!BlueprintPlan.replacementCompatible(state, target)) return false;
        }
        return found;
    }
    public void replaceMaterial(String source, String target) {
        if (plan == null || sourceBlueprint == null || loading) return;
        var next = new java.util.TreeMap<>(replacements);
        if (source.equals(target)) next.remove(source); else next.put(source, target);
        applyReplacements(next);
    }
    /** A displayed material may represent several original block types (e.g. standing/wall variants). */
    public void replaceDisplayedMaterial(BlueprintPlan expectedPlan, Item material, String target) {
        if (plan != expectedPlan || sourceBlueprint == null || loading) {
            Notifications.warning("Material ersetzen", "Der Bauplan hat sich geändert. Bitte das Material erneut wählen.");
            return;
        }
        var next = new java.util.TreeMap<>(replacements);
        for (int i = 0; i < sourceBlueprint.palette().size(); i++) {
            if (!plan.paletteUsed(i) || plan.materialForPalette(i).item != material) continue;
            String source = sourceBlueprint.palette().get(i).name();
            if (source.equals(target)) next.remove(source); else next.put(source, target);
        }
        applyReplacements(next);
    }
    /** Ob dieses Item ein Material des geladenen Bauplans ist (dann lässt es sich ersetzen). */
    public boolean usesMaterial(Item item) {
        return plan != null && item != Items.AIR && plan.materials.stream().anyMatch(m -> m.item == item);
    }

    /** Ersatzblock auswählen und im ganzen Bauplan einsetzen; danach zurück zu {@code parent}. */
    public void openReplace(net.minecraft.client.gui.screens.Screen parent, Item material) {
        BlueprintPlan expected = plan;
        if (expected == null) return;
        mc.setScreen(new com.example.examplemod.gui.ItemPickerScreen(parent,
                "Ersatz für " + new ItemStack(material).getHoverName().getString(),
                item -> item instanceof net.minecraft.world.item.BlockItem,
                item -> replaceDisplayedMaterial(expected, material,
                        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(((net.minecraft.world.item.BlockItem) item).getBlock()).toString())));
    }

    public void resetMaterials() { if (plan != null && !replacements.isEmpty()) applyReplacements(java.util.Map.of()); }
    private void applyReplacements(java.util.Map<String, String> next) {
        try {
            Blueprint data = com.example.examplemod.blueprint.BlueprintMaterials.remap(sourceBlueprint, next, BlueprintPlan::replacementState);
            BlueprintPlan updated = new BlueprintPlan(data, plan);
            updated.setTransform(plan.turns(), plan.mirrored());
            updated.layers = plan.layers; updated.layer = plan.layer; updated.onlyMissing = plan.onlyMissing;
            // Accepted deviations on changed materials must be checked again; other exceptions survive.
            int[] accepted = java.util.Arrays.stream(plan.acceptedCells()).filter(i -> {
                int state = sourceBlueprint.cells().get(i).state();
                return plan.blueprint.palette().get(state).equals(data.palette().get(state));
            }).toArray();
            updated.restoreAccepted(accepted);
            updated.restoreIgnored(plan.ignoredCells());
            plan = updated;
            replacements.clear(); replacements.putAll(next);
            renderer.clear(); hover = null; savedSignature = null;
            saveState();
            if (ModuleManager.MATERIAL_LIST != null) ModuleManager.MATERIAL_LIST.refresh();
            Notifications.success("Bauplan-Material", "Vorschau und Bedarf aktualisiert · Originaldatei bleibt erhalten");
        } catch (RuntimeException failure) { failed(failure); }
    }

    @Override
    protected void onEnable() {
        // Blockumrandung zeigt, wo der nächste Block landet; nur einschalten, wenn sie aus war.
        Module outline = ModuleManager.BLOCK_OUTLINE;
        if (outline != null && !outline.isEnabled()) {
            outline.setEnabled(true, false);
            outlineEnabledByUs = true;
        }
        // Ohne geladenen Plan gibt es nichts anzuzeigen: stattdessen die Dateiauswahl öffnen.
        if (plan == null && !loading && corner1 == null && corner2 == null) {
            mc.execute(() -> {
                setEnabled(false, false);
                if (mc.player != null) mc.setScreen(new BlueprintScreen(mc.screen));
            });
        }
    }

    @Override protected void onDisable() {
        lazyBuilder.cancel();
        if (plan != null) plan.resetLayerProgress();
        saveState();
        if (outlineEnabledByUs && ModuleManager.BLOCK_OUTLINE != null) ModuleManager.BLOCK_OUTLINE.setEnabled(false, false);
        outlineEnabledByUs = false;
    }

    @Override public void onTick() {
        lazyBuilder.tick(plan);
        if (plan == null) return;
        int before = plan.layer;
        plan.scan(mc.level, autoLayer.get() && !lazyBuilder.waiting());
        if (plan.layer != before) Notifications.success("Ebene fertig", "Weiter mit Ebene " + (plan.layer + 1));
        saveState();
    }

    @Override public void onWorldRender(WorldRenderContext context) {
        renderSelection(context);
        hover = null;
        if (plan == null || !plan.inDimension(mc.level)) return;
        syncFocus();
        renderer.render(plan, context, renderStyle(), range.getInt());
        lazyBuilder.render(context);
        // Der anvisierte Geisterblock ist immer umrandet; den Tooltip gibt es nur mit Block-Info.
        if (mc.level == null) return;
        hover = findHover(context.cameraPos(), Vec3.directionFromRotation(context.camera().getXRot(), context.camera().getYRot()));
        if (hover != null) {
            VertexConsumer lines = Render3D.lines(context);
            Render3D.box(context, lines, new AABB(hover.pos()).inflate(0.02), Theme.ACCENT_2);
            Render3D.flush(context);
        }
    }

    // ---- Block-Info: anvisierter, noch nicht gebauter Block ----

    /** Anvisierte Bauplan-Zelle mit ihrem aktuellen Status. */
    public record Hover(int index, BlockPos pos, BlueprintPlan.Status status) {}
    private static final double HOVER_DISTANCE = 24;
    private Hover hover;

    /** Nur gültig, solange die Vorschau läuft und Block-Info an ist. */
    public Hover hover() { return isEnabled() && plan != null && infoVisible() ? hover : null; }

    public static final String INFO_HOLD = "Taste halten";
    public static final String INFO_TOGGLE = "Taste umschalten";
    public static final String INFO_ALWAYS = "Immer";
    public static final String INFO_OFF = "Aus";
    /** Zustand für "Taste umschalten"; wird beim Wechsel der Einstellung zurückgesetzt. */
    private boolean infoToggled;

    /** Ob der Tooltip gerade gezeigt werden soll: abhängig von der Einstellung und der Info-Taste. */
    public boolean infoVisible() {
        return switch (blockInfo.get()) {
            case INFO_ALWAYS -> true;
            case INFO_HOLD -> ClientKeybinds.BLUEPRINT_INFO != null && ClientKeybinds.BLUEPRINT_INFO.isDown();
            case INFO_TOGGLE -> infoToggled;
            default -> false;
        };
    }

    /** Druck auf die Info-Taste (aus ClientEvents); wirkt nur bei "Taste umschalten". */
    public void onInfoKey() {
        if (!blockInfo.is(INFO_TOGGLE) || plan == null) return;
        infoToggled = !infoToggled;
        Notifications.info("Block-Info", infoToggled ? "Eingeblendet" : "Ausgeblendet");
    }

    /** Für das Bauplan-Fenster: aktuelle Einstellung samt Taste, z.B. "Taste halten (Links Alt)". */
    public String infoLabel() {
        if (blockInfo.is(INFO_ALWAYS) || blockInfo.is(INFO_OFF) || ClientKeybinds.BLUEPRINT_INFO == null) return blockInfo.get();
        return blockInfo.get() + " (" + ClientKeybinds.BLUEPRINT_INFO.getTranslatedKeyMessage().getString() + ")";
    }

    /**
     * Läuft Block für Block entlang der Blickrichtung (Amanatides-Woo) bis zum ersten echten Block.
     * Bereits korrekt gebaute Zellen werden übersprungen.
     */
    private Hover findHover(Vec3 from, Vec3 look) {
        return findHover(from, look, false);
    }
    private Hover findHover(Vec3 from, Vec3 look, boolean includeBuilt) {
        double limit = Math.min(HOVER_DISTANCE, range.get());
        Vec3 to = from.add(look.scale(limit));
        BlockHitResult real = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        if (real.getType() == HitResult.Type.BLOCK) limit = from.distanceTo(real.getLocation()) + 1.0E-3;

        int x = Mth.floor(from.x), y = Mth.floor(from.y), z = Mth.floor(from.z);
        int stepX = look.x > 0 ? 1 : -1, stepY = look.y > 0 ? 1 : -1, stepZ = look.z > 0 ? 1 : -1;
        double deltaX = look.x == 0 ? Double.MAX_VALUE : Math.abs(1 / look.x);
        double deltaY = look.y == 0 ? Double.MAX_VALUE : Math.abs(1 / look.y);
        double deltaZ = look.z == 0 ? Double.MAX_VALUE : Math.abs(1 / look.z);
        double maxX = look.x == 0 ? Double.MAX_VALUE : (stepX > 0 ? x + 1 - from.x : from.x - x) * deltaX;
        double maxY = look.y == 0 ? Double.MAX_VALUE : (stepY > 0 ? y + 1 - from.y : from.y - y) * deltaY;
        double maxZ = look.z == 0 ? Double.MAX_VALUE : (stepZ > 0 ? z + 1 - from.z : from.z - z) * deltaZ;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double t = 0;
        for (int steps = 0; steps < 128 && t <= limit; steps++) {
            pos.set(x, y, z);
            int index = plan.cellAt(pos);
            if (index >= 0 && !plan.ignored(index) && plan.focused(plan.blueprint.cells().get(index))) {
                BlueprintPlan.Status status = plan.compare(mc.level, index, pos);
                if ((includeBuilt || status != BlueprintPlan.Status.CORRECT) && status != BlueprintPlan.Status.UNCHECKED) {
                    return new Hover(index, pos.immutable(), status);
                }
            }
            if (maxX < maxY && maxX < maxZ) { x += stepX; t = maxX; maxX += deltaX; }
            else if (maxY < maxZ) { y += stepY; t = maxY; maxY += deltaY; }
            else { z += stepZ; t = maxZ; maxZ += deltaZ; }
        }
        return null;
    }

    public void onHideKey() {
        if (!isEnabled() || loading || plan == null || mc.player == null || mc.level == null || !plan.inDimension(mc.level)) return;
        Hover target = findHover(mc.player.getEyePosition(), mc.player.getViewVector(1F), true);
        if (target == null) { Notifications.info("Bauplan bearbeiten", "Zuerst einen sichtbaren Bauplan-Block anvisieren"); return; }
        lazyBuilder.cancel();
        plan.setIgnored(mc.level, target.index(), true);
        hiddenUndo.addLast(target.index());
        renderer.clear(); hover = null; saveState();
        if (ModuleManager.MATERIAL_LIST != null) ModuleManager.MATERIAL_LIST.refresh();
        Notifications.success("Bauplan-Block ausgeblendet", "Zählt als erledigt · im Bauplan-Menü rückgängig machen");
    }
    public boolean canUndoHidden() { return !hiddenUndo.isEmpty(); }
    public void undoHidden() {
        if (plan == null || loading || hiddenUndo.isEmpty()) return;
        lazyBuilder.cancel();
        plan.setIgnored(mc.level, hiddenUndo.removeLast(), false);
        renderer.clear(); hover = null; saveState();
        if (ModuleManager.MATERIAL_LIST != null) ModuleManager.MATERIAL_LIST.refresh();
    }
    /** Restore original blocks and materials while retaining the chosen placement and layer. */
    public void restoreOriginal() {
        if (plan == null || sourceBlueprint == null || loading) return;
        lazyBuilder.cancel();
        BlueprintPlan original = new BlueprintPlan(sourceBlueprint, plan);
        original.setTransform(plan.turns(), plan.mirrored());
        original.layers = plan.layers; original.layer = plan.layer; original.onlyMissing = plan.onlyMissing;
        plan = original; replacements.clear(); hiddenUndo.clear(); focusItem = null;
        renderer.clear(); hover = null; savedSignature = null; saveState();
        if (ModuleManager.MATERIAL_LIST != null) ModuleManager.MATERIAL_LIST.refresh();
        Notifications.success("Original wiederhergestellt", "Ausgeblendete Blöcke, Abweichungen und Materialtausch zurückgesetzt");
    }

    // ---- Abweichung übernehmen: gewollte Variante statt Fehler ----

    /**
     * Taste "Abweichung übernehmen" (aus ClientEvents): Zielt der Spieler auf einen falsch gesetzten Block,
     * zählt diese Stelle ab jetzt als richtig. Auf einer schon übernommenen Stelle wird es zurückgenommen.
     */
    public void onAcceptKey() {
        if (plan == null || mc.player == null || mc.level == null) {
            Notifications.info("Bauplan", "Kein Bauplan geladen");
            return;
        }
        if (!plan.inDimension(mc.level)) return;
        Vec3 from = mc.player.getEyePosition();
        Vec3 to = from.add(mc.player.getViewVector(1F).scale(Math.min(HOVER_DISTANCE, range.get())));
        BlockHitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        int index = hit.getType() == HitResult.Type.BLOCK ? plan.cellAt(hit.getBlockPos()) : -1;
        if (index < 0) {
            Notifications.info("Abweichung übernehmen", "Zuerst einen falsch gesetzten Bauplan-Block anvisieren");
            return;
        }
        BlockPos pos = hit.getBlockPos();
        String actual = mc.level.getBlockState(pos).getBlock().getName().getString();
        if (plan.accepted(index)) {
            plan.setAccepted(mc.level, index, false);
            Notifications.info("Abweichung zurückgenommen", actual + " wird wieder geprüft");
            return;
        }
        BlueprintPlan.Status status = plan.compare(mc.level, index, pos);
        switch (status) {
            case WRONG_BLOCK, WRONG_STATE, UNSUPPORTED -> {
                plan.setAccepted(mc.level, index, true);
                BlockState expected = plan.state(plan.blueprint.cells().get(index));
                String text = status == BlueprintPlan.Status.WRONG_STATE || expected == null
                        ? actual + " so übernommen" : actual + " statt " + expected.getBlock().getName().getString();
                Notifications.success("Abweichung übernommen", text);
            }
            case CORRECT -> Notifications.info("Abweichung übernehmen", "Dieser Block ist schon richtig");
            default -> Notifications.info("Abweichung übernehmen", "Hier steht noch kein Block");
        }
    }

    /** Alle übernommenen Abweichungen verwerfen; die Stellen werden wieder normal geprüft. */
    public void clearAccepted() {
        if (plan == null) return;
        int count = plan.acceptedCount();
        plan.clearAccepted();
        if (count > 0) Notifications.info("Bauplan", count + " Abweichungen zurückgesetzt");
    }

    /** Für das Bauplan-Fenster, z.B. "N". */
    public String acceptKeyName() {
        return ClientKeybinds.BLUEPRINT_ACCEPT == null ? "-" : ClientKeybinds.BLUEPRINT_ACCEPT.getTranslatedKeyMessage().getString();
    }

    // ---- Materialfilter: nur ein Material in der Vorschau ----

    /** Gewähltes Item; überlebt einen Materialtausch, solange es im Plan vorkommt. */
    private Item focusItem;

    /** Aus dem Bauplan-Fenster: dasselbe Material nochmal oder {@code null} zeigt wieder alle. */
    public void focusMaterial(BlueprintPlan.Material material) {
        if (plan == null) return;
        boolean off = material == null || plan.focus == material;
        focus(off || material.item == Items.AIR ? null : material.item, off ? null : material);
    }

    /** Aus der Sammelliste: nur dieses Item; {@code null} zeigt wieder alle. */
    public void focusItem(Item item) { focus(item, item == null ? null : materialFor(item)); }

    /**
     * Gemeinsamer Filter für Bauplan und Sammelliste: Die Vorschau zeigt nur dieses Material (über alle
     * Ebenen, jede offene Stelle umrandet), die Sammelliste nur diesen Eintrag.
     */
    private void focus(Item item, BlueprintPlan.Material material) {
        focusItem = item;
        if (plan != null) plan.focus = material;
        // Damit man sieht, wohin das Material gehört: Vorschau einschalten.
        if (material != null && !isEnabled()) setEnabled(true, false);
        if (ModuleManager.MATERIAL_LIST != null) ModuleManager.MATERIAL_LIST.solo(item);
        String name = material != null ? material.name : item != null ? new ItemStack(item).getHoverName().getString() : null;
        if (name == null) Notifications.info("Bauplan", "Wieder alles angezeigt");
        else if (material != null) Notifications.info("Nur " + name, "Im Bauplan umrandet, auf allen Ebenen");
        else Notifications.info("Sammelliste", "Nur " + name);
    }

    private BlueprintPlan.Material materialFor(Item item) {
        if (plan == null || item == Items.AIR) return null;
        for (BlueprintPlan.Material material : plan.materials) if (material.item == item) return material;
        return null;
    }

    /** Nach einem Materialtausch ist der Plan neu: gewähltes Material wieder zuordnen. */
    private void syncFocus() {
        if (plan != null && focusItem != null && (plan.focus == null || plan.focus.item != focusItem)) plan.focus = materialFor(focusItem);
    }

    // ---- Mittelklick: Item des anvisierten Bauplan-Blocks in die Hand nehmen ----

    private static final int MAIN_INVENTORY = 36;
    private static final int HOTBAR = 9;

    /**
     * Vor der Vanilla-Tastenverarbeitung (aus ClientEvents): Zielt der Spieler auf einen noch nicht gebauten
     * Bauplan-Block, übernimmt Lumen den Mittelklick. Sonst bleibt der Klick für Minecraft liegen, z.B.
     * wenn ein echter Block näher ist.
     */
    public void handlePickKey() {
        if (!pickBlock.get() || !isEnabled() || plan == null || mc.screen != null || mc.player == null
                || mc.level == null || mc.gameMode == null || mc.player.isSpectator() || Freecam.active()
                || !plan.inDimension(mc.level)) return;
        Hover target = findHover(mc.player.getEyePosition(), mc.player.getViewVector(1F));
        if (target == null || target.status() != BlueprintPlan.Status.MISSING) return;
        boolean clicked = false;
        while (mc.options.keyPickItem.consumeClick()) clicked = true;
        if (clicked) pick(target);
    }

    /**
     * Wie Block auswählen im Überlebensmodus: liegt das Item in der Hotbar, wird dieser Platz gewählt, sonst
     * wird es in die Hotbar getauscht (wie Zahlentaste im Inventar). Es wird nie ein Item erzeugt.
     */
    private void pick(Hover target) {
        Blueprint.Cell cell = plan.blueprint.cells().get(target.index());
        BlockState expected = plan.state(cell);
        if (expected == null) return;
        Item item = BlueprintPlan.materialItem(expected);
        if (item == Items.AIR) {
            Notifications.info("Bauplan", expected.getBlock().getName().getString() + " lässt sich nicht auswählen");
            return;
        }
        takeIntoHand(item, plan.material(cell));
    }

    /**
     * Item aus dem Inventar in die Hand nehmen (Mittelklick und Materialliste). Es wird nie ein Item erzeugt.
     * {@code material} nur für die Meldung "noch offen", darf {@code null} sein.
     */
    public void takeIntoHand(Item item, BlueprintPlan.Material material) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || item == Items.AIR) return;
        Inventory inventory = player.getInventory();
        int slot = findSlot(inventory, item);
        if (slot < 0) {
            String text = new ItemStack(item).getHoverName().getString() + " nicht im Inventar";
            if (material == null) Notifications.warning("Nicht im Inventar", text);
            else Notifications.warning("Bauplan", text + " · " + material.remaining() + " noch offen");
            return;
        }
        if (slot < HOTBAR) {
            inventory.setSelectedSlot(slot);
            return;
        }
        // Tauschen geht nur, wenn kein anderes Menü offen ist und nichts am Mauszeiger hängt.
        if (player.containerMenu != player.inventoryMenu || !player.inventoryMenu.getCarried().isEmpty()) return;
        int hotbar = suitableHotbarSlot(inventory);
        // Inventarplätze 9–35 haben im Spieler-Menü dieselbe Nummer; SWAP mit Knopf = Hotbar-Platz.
        mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, slot, hotbar, ClickType.SWAP, player);
        inventory.setSelectedSlot(hotbar);
    }

    /** Erst ein unverändertes Item wie bei Vanilla, sonst eines mit z.B. eigenem Namen. Hotbar zuerst. */
    private static int findSlot(Inventory inventory, Item item) {
        ItemStack plain = new ItemStack(item);
        int fallback = -1;
        for (int i = 0; i < MAIN_INVENTORY; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty() || !stack.is(item)) continue;
            if (ItemStack.isSameItemSameComponents(plain, stack)) return i;
            if (fallback < 0) fallback = i;
        }
        return fallback;
    }

    /** Dieselbe Regel wie Minecraft: freier Platz ab dem gewählten, sonst einer ohne Verzauberung, sonst der gewählte. */
    private static int suitableHotbarSlot(Inventory inventory) {
        int selected = inventory.getSelectedSlot();
        for (int i = 0; i < HOTBAR; i++) {
            int slot = (selected + i) % HOTBAR;
            if (inventory.getItem(slot).isEmpty()) return slot;
        }
        for (int i = 0; i < HOTBAR; i++) {
            int slot = (selected + i) % HOTBAR;
            if (!inventory.getItem(slot).isEnchanted()) return slot;
        }
        return selected;
    }

    @Override public void onLeaveWorld() {
        corner1 = corner2 = null;
        selectionDimension = null;
        saving = false;
        saveState();
        unload();
        message = "Eine .litematic-, .schem- oder Struktur-.nbt-Datei auswählen.";
    }

    @Override public String getHudInfo() {
        if (plan == null) return corner1 != null || corner2 != null ? "Aufnahme" : "Kein Plan";
        String progress = plan.count(BlueprintPlan.Status.CORRECT) + "/" + plan.blueprint.cells().size();
        if (plan.layers != BlueprintPlan.Layers.ALL) progress += " · E" + (plan.layer + 1);
        return plan.focus == null ? progress : progress + " · Filter";
    }
}
