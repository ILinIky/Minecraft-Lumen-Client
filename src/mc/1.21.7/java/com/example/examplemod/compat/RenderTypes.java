package com.example.examplemod.compat;

import com.example.examplemod.ExampleMod;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Alle eigenen Render-Zustände an einer Stelle (Minecraft 1.21.7). Diese API ändert sich bei
 * Minecraft-Updates am häufigsten (1.21.5: RenderPipeline, 1.21.6: GUI-Umbau), daher liegt sie im
 * versionsabhängigen Ordner src/mc/&lt;Version&gt;/. Der übrige Code nutzt nur die Felder und Methoden hier.
 *
 * <p>Erbt von RenderStateShard, weil einige Bausteine (z.B. LineStateShard) dort geschützt sind.
 */
public final class RenderTypes extends RenderStateShard {
    private RenderTypes() { super("examplemod_render_types", () -> {}, () -> {}); }

    // ---- Linien durch Wände (ESP, Tracer, Wegpunkte) ----

    private static final RenderPipeline LINES_THROUGH_WALLS = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "pipeline/esp_lines"))
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withDepthWrite(false)
            .build();

    // MAIN_TARGET statt ITEM_ENTITY_TARGET: wir zeichnen nach dem Zusammensetzen der Fabulous-Puffer.
    public static final RenderType ESP_LINES = RenderType.create("examplemod_esp_lines", 1536, LINES_THROUGH_WALLS,
            RenderType.CompositeState.builder()
                    .setLineState(DEFAULT_LINE)
                    .setLayeringState(NO_LAYERING)
                    .setOutputState(MAIN_TARGET)
                    .createCompositeState(false));

    // ---- Linien, die von Blöcken verdeckt werden (Lichtlevel, Chunk-Grenzen, Bauplan-Fehler) ----

    private static final RenderPipeline LINES_DEPTH = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "pipeline/world_lines"))
            .withDepthWrite(false)
            .build();

    public static final RenderType WORLD_LINES = RenderType.create("examplemod_world_lines", 1536, LINES_DEPTH,
            RenderType.CompositeState.builder()
                    .setLineState(DEFAULT_LINE)
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setOutputState(MAIN_TARGET)
                    .createCompositeState(false));

    // ---- Halbtransparente Flächen (Blockumrandung-Füllung, Bauplan-Körper) ----

    public static RenderType filledQuads() { return RenderType.debugQuads(); }

    // ---- Blockumrandung mit wählbarer Linienstärke ----

    private static final Map<Integer, RenderType> OUTLINES = new HashMap<>();

    /** Linien in Pixeln (Schritte von 0.5), wie die Vanilla-Umrandung mit Tiefentest. */
    public static RenderType outline(double pixels) {
        int key = Math.max(2, (int) Math.round(pixels * 2));
        return OUTLINES.computeIfAbsent(key, k -> {
            // Genau 1.0 gilt bei Vanilla als "Standardbreite" und würde ignoriert.
            double width = k == 2 ? 1.01 : k / 2.0;
            return RenderType.create("examplemod_block_outline_" + k, 1536, RenderPipelines.LINES,
                    RenderType.CompositeState.builder()
                            .setLineState(new LineStateShard(OptionalDouble.of(width)))
                            .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                            .setOutputState(ITEM_ENTITY_TARGET)
                            .createCompositeState(false));
        });
    }

    // ---- Bauplan-Vorschau: echte Blocktexturen, halbtransparent ----

    private static final RenderPipeline BLUEPRINT_PIPELINE = RenderPipeline.builder(RenderPipelines.TERRAIN_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, "pipeline/blueprint"))
            .withBlend(BlendFunction.TRANSLUCENT).withDepthWrite(false)
            .withShaderDefine("ALPHA_CUTOUT", 0.01F).build();

    public static final RenderType BLUEPRINT_GHOST = RenderType.create("examplemod_blueprint", 2 * 1024 * 1024, false, true,
            BLUEPRINT_PIPELINE, RenderType.CompositeState.builder().setTextureState(BLOCK_SHEET_MIPPED)
                    .setLightmapState(LIGHTMAP).setOutputState(MAIN_TARGET)
                    .createCompositeState(false));
}
