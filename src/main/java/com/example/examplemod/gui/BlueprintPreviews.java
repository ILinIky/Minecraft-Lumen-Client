package com.example.examplemod.gui;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.blueprint.Blueprint;
import com.example.examplemod.blueprint.BlueprintPreview;
import com.example.examplemod.blueprint.BlueprintReader;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Vorschaubilder für Baupläne. Bevorzugt das in der Datei gespeicherte Bild (Litematica),
 * sonst eine berechnete isometrische Ansicht. Erzeugt im Hintergrund, angezeigt als Textur.
 * Alle Methoden außer {@link #prepare} laufen auf dem Haupt-Thread.
 */
public final class BlueprintPreviews {
    private static final int RENDER_SIZE = 128;
    private static final int MAX_ENTRIES = 48;
    private static final int UNKNOWN_COLOR = 0xFFC084FC;
    /** Glas und andere Blöcke ohne Kartenfarbe: hell und durchsichtig. */
    private static final int CLEAR_COLOR = 0x70C8DCF0;

    /** Ergebnis für eine Datei; Felder werden vom Hintergrund-Thread einmal gesetzt. */
    public static final class Entry {
        private final long modified;
        private volatile boolean done;
        private volatile int[] pixels;
        private volatile int pixelSize;
        private volatile boolean fromFile;
        private volatile String name, details, error;
        private DynamicTexture texture;
        private ResourceLocation id;

        private Entry(long modified) { this.modified = modified; }

        public boolean done() { return done; }
        public boolean fromFile() { return fromFile; }
        public String name() { return name; }
        /** z.B. "12 × 8 × 15 · 1.234 Blöcke", oder der Fehler. */
        public String details() { return error != null ? error : details; }
        public boolean failed() { return error != null; }
    }

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Lumen-Bauplan-Vorschau");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private static final List<Entry> RELEASE = new ArrayList<>();
    private static final Map<Path, Entry> CACHE = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Path, Entry> eldest) {
            if (size() <= MAX_ENTRIES) return false;
            RELEASE.add(eldest.getValue());
            return true;
        }
    };
    private static int nextId;

    private BlueprintPreviews() {}

    private static Path key(Path file) { return file.toAbsolutePath().normalize(); }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Vorschau einer Datei; wird beim ersten Aufruf im Hintergrund erzeugt. */
    public static Entry get(Path file) {
        Path key = key(file);
        Entry entry = CACHE.get(key);
        if (entry == null) {
            Entry created = new Entry(modified(file));
            CACHE.put(key, created);
            WORKER.submit(() -> {
                try {
                    fill(created, BlueprintReader.read(file));
                } catch (Exception failure) {
                    String message = failure.getMessage();
                    created.error = message == null || message.isBlank() ? "Datei nicht lesbar" : message;
                    created.done = true;
                }
            });
            entry = created;
        }
        releasePending();
        return entry;
    }

    /** Nach "Neu einlesen": geänderte Dateien neu berechnen. */
    public static void invalidateChanged() {
        CACHE.entrySet().removeIf(e -> {
            boolean stale = e.getValue().modified != modified(e.getKey());
            if (stale) RELEASE.add(e.getValue());
            return stale;
        });
        releasePending();
    }

    /**
     * Vom Bauplan-Lader (Hintergrund-Thread): das gerade gelesene Bild übernehmen, damit die Datei
     * nicht ein zweites Mal gelesen wird.
     */
    public static void prepare(Path file, Blueprint blueprint) {
        Entry entry = new Entry(modified(file));
        try {
            fill(entry, blueprint);
        } catch (RuntimeException failure) {
            ExampleMod.LOGGER.warn("Bauplan-Vorschau konnte nicht erzeugt werden", failure);
            return;
        }
        Minecraft.getInstance().execute(() -> {
            Entry old = CACHE.put(key(file), entry);
            if (old != null) RELEASE.add(old);
            releasePending();
        });
    }

    private static void fill(Entry entry, Blueprint blueprint) {
        entry.name = blueprint.name();
        entry.details = String.format(Locale.GERMANY, "%d × %d × %d · %,d Blöcke",
                blueprint.sizeX(), blueprint.sizeY(), blueprint.sizeZ(), blueprint.cells().size());
        int[] image = blueprint.previewImage();
        if (image != null) {
            entry.pixels = image;
            entry.pixelSize = (int) Math.round(Math.sqrt(image.length));
            entry.fromFile = true;
        } else {
            entry.pixels = BlueprintPreview.render(blueprint, colors(blueprint), RENDER_SIZE);
            entry.pixelSize = RENDER_SIZE;
        }
        entry.done = true;
    }

    /** Berechnete Ansicht (schräg von oben), z.B. als Vorschaubild in selbst gespeicherten Bauplänen. */
    public static int[] render(Blueprint blueprint) {
        return BlueprintPreview.render(blueprint, colors(blueprint), RENDER_SIZE);
    }

    /** Kartenfarbe je Paletteneintrag (Registries sind nach dem Start unveränderlich, daher threadsicher lesbar). */
    private static int[] colors(Blueprint blueprint) {
        int[] colors = new int[blueprint.palette().size()];
        for (int i = 0; i < colors.length; i++) {
            Blueprint.State spec = blueprint.palette().get(i);
            ResourceLocation id = ResourceLocation.tryParse(spec.name());
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
                colors[i] = UNKNOWN_COLOR;
                continue;
            }
            try {
                BlockState state = BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
                if (state.is(Blocks.WATER)) { colors[i] = 0xB03F76E4; continue; }
                MapColor color = state.getMapColor(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                colors[i] = color == MapColor.NONE ? CLEAR_COLOR : color.calculateARGBColor(MapColor.Brightness.HIGH);
            } catch (RuntimeException failure) {
                colors[i] = UNKNOWN_COLOR;
            }
        }
        return colors;
    }

    /**
     * Zeichnet die Vorschau in ein Quadrat. Während der Berechnung erscheint ein Platzhalter,
     * bei Fehlern ein Warnsymbol.
     */
    public static void draw(GuiGraphics g, Path file, int x, int y, int size) {
        UiRender.roundRect(g, x, y, size, size, 3, Theme.lerp(Theme.FIELD, 0xFF000000, 0.15F));
        if (file == null) return;
        Entry entry = get(file);
        var font = Minecraft.getInstance().font;
        if (!entry.done) {
            String dots = ".".repeat((int) (System.currentTimeMillis() / 300 % 3) + 1);
            g.drawString(font, dots, x + (size - font.width(dots)) / 2, y + size / 2 - 4, Theme.TEXT_MUTED, false);
            return;
        }
        if (entry.failed() || entry.pixels == null) {
            g.drawString(font, "!", x + (size - font.width("!")) / 2, y + size / 2 - 4, Theme.DANGER, false);
            return;
        }
        if (entry.texture == null) upload(entry);
        int n = entry.pixelSize;
        g.blit(RenderPipelines.GUI_TEXTURED, entry.id, x, y, 0, 0, size, size, n, n, n, n, 0xFFFFFFFF);
    }

    private static void upload(Entry entry) {
        int n = entry.pixelSize;
        NativeImage image = new NativeImage(n, n, false);
        int[] pixels = entry.pixels;
        for (int py = 0; py < n; py++) {
            for (int px = 0; px < n; px++) image.setPixel(px, py, pixels[py * n + px]);
        }
        entry.id = ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "blueprint_preview/" + nextId++);
        entry.texture = new DynamicTexture(() -> "examplemod blueprint preview", image);
        Minecraft.getInstance().getTextureManager().register(entry.id, entry.texture);
    }

    private static void releasePending() {
        if (RELEASE.isEmpty()) return;
        for (Entry entry : RELEASE) {
            if (entry.id != null) Minecraft.getInstance().getTextureManager().release(entry.id);
            entry.texture = null;
        }
        RELEASE.clear();
    }
}
