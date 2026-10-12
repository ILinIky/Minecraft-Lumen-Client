package com.example.examplemod.feature;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.blueprint.Blueprint;
import com.example.examplemod.blueprint.BlueprintPlan;
import com.example.examplemod.blueprint.BlueprintReader;
import com.example.examplemod.gui.BlueprintScreen;
import com.example.examplemod.gui.Notifications;
import com.example.examplemod.module.Category;
import com.example.examplemod.module.ClientConfig;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.Setting;
import com.example.examplemod.render.BlueprintRenderer;
import com.example.examplemod.render.WorldRenderContext;
import com.example.examplemod.waypoint.WaypointStore;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.example.examplemod.gui.Theme;
import com.example.examplemod.render.Render3D;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
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
    private final ExecutorService loader = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Lumen-Bauplan-Laden");
        thread.setDaemon(true);
        return thread;
    });
    private final BlueprintRenderer renderer = new BlueprintRenderer();
    public final Setting.Slider opacity, range;
    public final Setting.Bool outlines;
    public final Setting.Bool blockInfo;
    private BlueprintPlan plan;
    private Path file;
    private Future<?> pending;
    private int generation;
    private boolean loading;
    private String message = "Eine .litematic-, .schem- oder Struktur-.nbt-Datei auswählen.";
    /** Zuletzt gespeicherte Platzierung; bei Änderung wird die Welt-Datei neu geschrieben. */
    private String savedSignature;

    /** Platzierung, die beim Wiederherstellen statt "vor dem Spieler" übernommen wird. */
    private record Placement(BlockPos origin, String dimension, int turns, boolean mirrored,
                             BlueprintPlan.Layers layers, int layer, boolean onlyMissing, boolean visible) {}

    public Blueprints() {
        super("blueprints", "Baupläne", "Transparente Blockvorschau, Ebenen und Materialbedarf", Category.WORLD);
        notPersistent();
        opacity = add(new Setting.Slider("Deckkraft", "Durchsichtige Vorschau mit echten Blocktexturen", 50, 15, 85, 5, "%"));
        range = add(new Setting.Slider("Sichtweite", "Vorschau nur in geladenen Chunks innerhalb dieser Entfernung", 48, 16, 96, 8, "Bl."));
        outlines = add(new Setting.Bool("Fehler markieren", "Blau: fehlt (nahe) · Rot: falscher Block · Gelb: Zustand · Violett: unbekannt", true));
        blockInfo = add(new Setting.Bool("Block-Info", "Beim Anvisieren eines noch nicht gebauten Blocks Name, Ausrichtung und Inventar anzeigen", true));
        add(new Setting.Action("Baupläne öffnen", "Datei laden, platzieren und Materialliste ansehen", () -> mc.setScreen(new BlueprintScreen(mc.screen))));
    }

    public Path directory() { return mc.gameDirectory.toPath().resolve("schematics"); }
    public BlueprintPlan plan() { return plan; }
    public boolean loading() { return loading; }
    public String message() { return message; }
    public BlueprintRenderer renderer() { return renderer; }

    public void load(Path path) { load(path, null); }

    private void load(Path path, Placement placement) {
        if (mc.level == null || mc.player == null) { message = "Bitte zuerst eine Welt betreten."; return; }
        if (pending != null) pending.cancel(true);
        int request = ++generation;
        String dimension = placement != null ? placement.dimension() : mc.level.dimension().location().toString();
        BlockPos anchor = placement != null ? placement.origin()
                : mc.player.blockPosition().relative(mc.player.getDirection(), 3);
        loading = true;
        message = "Lade " + path.getFileName() + " …";
        pending = loader.submit(() -> {
            try {
                Blueprint data = BlueprintReader.read(path);
                mc.execute(() -> {
                    if (request != generation) return;
                    loading = false;
                    if (mc.level == null || (placement == null && !mc.level.dimension().location().toString().equals(dimension))) {
                        message = "Welt oder Dimension gewechselt. Bitte erneut laden.";
                        return;
                    }
                    try {
                        BlueprintPlan loaded = new BlueprintPlan(data, anchor, dimension);
                        if (placement != null) {
                            loaded.setTransform(placement.turns(), placement.mirrored());
                            loaded.layers = placement.layers();
                            loaded.layer = Math.max(0, Math.min(data.sizeY() - 1, placement.layer()));
                            loaded.onlyMissing = placement.onlyMissing();
                        }
                        plan = loaded;
                        file = path;
                        renderer.clear();
                        setEnabled(placement == null || placement.visible(), false);
                        message = data.cells().size() + " Blöcke geladen · Platzierung prüfen";
                        if (placement == null) Notifications.success("Bauplan geladen", data.name());
                        else Notifications.info("Bauplan wiederhergestellt", data.name());
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

    /** Plan entladen und für diese Welt vergessen. */
    public void clear() {
        unload();
        message = "Bauplan entladen. Deine Welt bleibt unverändert.";
        deleteState();
    }

    private void unload() {
        generation++;
        if (pending != null) pending.cancel(true);
        loading = false;
        plan = null;
        file = null;
        savedSignature = null;
        renderer.clear();
        setEnabled(false, false);
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
                + "|" + plan.layers + plan.layer + plan.onlyMissing + isEnabled();
    }

    public void saveState() {
        Path target = stateFile();
        String signature = signature();
        if (target == null || signature == null || signature.equals(savedSignature)) return;
        BlockPos o = plan.origin();
        JsonObject json = new JsonObject();
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
        ClientConfig.writeAtomically(target, json.toString());
        savedSignature = signature;
    }

    private void deleteState() {
        Path target = stateFile();
        if (target == null) return;
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            ExampleMod.LOGGER.warn("Bauplan-Zustand konnte nicht gelöscht werden: {}", target, e);
        }
    }

    /** Beim Betreten einer Welt: den dort zuletzt genutzten Plan wieder laden. */
    public void restore() {
        Path target = stateFile();
        if (target == null || !Files.exists(target)) return;
        try (Reader reader = Files.newBufferedReader(target, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            Path path = Path.of(json.get("file").getAsString());
            if (!Files.exists(path)) {
                Notifications.warning("Bauplan", "Datei nicht mehr vorhanden: " + path.getFileName());
                deleteState();
                return;
            }
            BlueprintPlan.Layers layers = BlueprintPlan.Layers.valueOf(json.get("layers").getAsString());
            Placement placement = new Placement(
                    new BlockPos(json.get("x").getAsInt(), json.get("y").getAsInt(), json.get("z").getAsInt()),
                    json.get("dimension").getAsString(), json.get("turns").getAsInt(), json.get("mirrored").getAsBoolean(),
                    layers, json.get("layer").getAsInt(), json.get("onlyMissing").getAsBoolean(), json.get("visible").getAsBoolean());
            load(path, placement);
        } catch (IOException | RuntimeException failure) {
            ExampleMod.LOGGER.warn("Bauplan-Zustand ist ungültig: {}", target, failure);
        }
    }

    @Override
    protected void onEnable() {
        // Ohne geladenen Plan gibt es nichts anzuzeigen: stattdessen die Dateiauswahl öffnen.
        if (plan == null && !loading) {
            mc.execute(() -> {
                setEnabled(false, false);
                if (mc.player != null) mc.setScreen(new BlueprintScreen(mc.screen));
            });
        }
    }

    @Override protected void onDisable() { saveState(); }

    @Override public void onTick() {
        if (plan == null) return;
        plan.scan(mc.level);
        saveState();
    }

    @Override public void onWorldRender(WorldRenderContext context) {
        hover = null;
        if (plan == null || !plan.inDimension(mc.level)) return;
        renderer.render(plan, context, (float) opacity.get() / 100F, range.getInt(), outlines.get());
        if (!blockInfo.get() || mc.level == null) return;
        hover = findHover(context);
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
    public Hover hover() { return isEnabled() && plan != null && blockInfo.get() ? hover : null; }

    /**
     * Läuft Block für Block entlang der Blickrichtung (Amanatides-Woo) bis zum ersten echten Block.
     * Bereits korrekt gebaute Zellen werden übersprungen.
     */
    private Hover findHover(WorldRenderContext context) {
        Vec3 from = context.cameraPos();
        Vec3 look = Vec3.directionFromRotation(context.camera().getXRot(), context.camera().getYRot());
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
            if (index >= 0) {
                BlueprintPlan.Status status = plan.compare(mc.level, plan.blueprint.cells().get(index), pos);
                if (status != BlueprintPlan.Status.CORRECT && status != BlueprintPlan.Status.UNCHECKED) {
                    return new Hover(index, pos.immutable(), status);
                }
            }
            if (maxX < maxY && maxX < maxZ) { x += stepX; t = maxX; maxX += deltaX; }
            else if (maxY < maxZ) { y += stepY; t = maxY; maxY += deltaY; }
            else { z += stepZ; t = maxZ; maxZ += deltaZ; }
        }
        return null;
    }

    @Override public void onLeaveWorld() {
        saveState();
        unload();
        message = "Eine .litematic-, .schem- oder Struktur-.nbt-Datei auswählen.";
    }

    @Override public String getHudInfo() {
        if (plan == null) return "Kein Plan";
        String progress = plan.count(BlueprintPlan.Status.CORRECT) + "/" + plan.blueprint.cells().size();
        return plan.layers == BlueprintPlan.Layers.ALL ? progress : progress + " · E" + (plan.layer + 1);
    }
}
