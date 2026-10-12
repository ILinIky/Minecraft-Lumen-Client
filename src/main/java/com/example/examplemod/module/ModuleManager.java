package com.example.examplemod.module;

import com.example.examplemod.feature.AutoDrop;
import com.example.examplemod.feature.AutoSprint;
import com.example.examplemod.feature.BlockEsp;
import com.example.examplemod.feature.BlockOutline;
import com.example.examplemod.feature.Blueprints;
import com.example.examplemod.feature.ChestMemory;
import com.example.examplemod.feature.ChunkBorders;
import com.example.examplemod.feature.Design;
import com.example.examplemod.feature.EntityEsp;
import com.example.examplemod.feature.Flight;
import com.example.examplemod.feature.Freecam;
import com.example.examplemod.feature.Fullbright;
import com.example.examplemod.feature.Hud;
import com.example.examplemod.feature.Invisibility;
import com.example.examplemod.feature.LightLevel;
import com.example.examplemod.feature.Minimap;
import com.example.examplemod.feature.NoFall;
import com.example.examplemod.feature.Tooltips;
import com.example.examplemod.feature.Waypoints;
import com.example.examplemod.feature.XRay;
import com.example.examplemod.feature.Zoom;
import com.example.examplemod.gui.Notifications;
import com.google.gson.JsonObject;
import com.example.examplemod.render.WorldRenderContext;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ModuleManager {
    private static final List<Module> MODULES = new ArrayList<>();
    private static boolean initialized;
    private static boolean dirty;
    private static int saveCooldown;

    public static XRay XRAY;
    public static BlockEsp BLOCK_ESP;
    public static EntityEsp ENTITY_ESP;
    public static Fullbright FULLBRIGHT;
    public static Hud HUD;
    public static Flight FLIGHT;
    public static NoFall NO_FALL;
    public static Freecam FREECAM;
    public static Invisibility INVISIBILITY;
    public static AutoDrop AUTO_DROP;
    public static ChestMemory CHEST_MEMORY;
    public static Waypoints WAYPOINTS;
    public static LightLevel LIGHT_LEVEL;
    public static ChunkBorders CHUNK_BORDERS;
    public static Zoom ZOOM;
    public static AutoSprint AUTO_SPRINT;
    public static Design DESIGN;
    public static BlockOutline BLOCK_OUTLINE;
    public static Tooltips TOOLTIPS;
    public static Minimap MINIMAP;
    public static Blueprints BLUEPRINTS;

    private ModuleManager() {}

    public static void init() {
        if (initialized) return;
        initialized = true;
        XRAY = register(new XRay());
        BLOCK_ESP = register(new BlockEsp());
        ENTITY_ESP = register(new EntityEsp());
        FULLBRIGHT = register(new Fullbright());
        LIGHT_LEVEL = register(new LightLevel());
        CHUNK_BORDERS = register(new ChunkBorders());
        BLOCK_OUTLINE = register(new BlockOutline());
        TOOLTIPS = register(new Tooltips());
        ZOOM = register(new Zoom());
        FLIGHT = register(new Flight());
        NO_FALL = register(new NoFall());
        AUTO_SPRINT = register(new AutoSprint());
        FREECAM = register(new Freecam());
        INVISIBILITY = register(new Invisibility());
        AUTO_DROP = register(new AutoDrop());
        WAYPOINTS = register(new Waypoints());
        BLUEPRINTS = register(new Blueprints());
        CHEST_MEMORY = register(new ChestMemory());
        // Vor dem HUD, weil dessen Elemente die Minimap brauchen.
        MINIMAP = register(new Minimap());
        HUD = register(new Hud());
        DESIGN = register(new Design());
        ClientConfig.load(MODULES);
        dirty = false;
    }

    // ---- Profile: gespeicherte Sätze aus An/Aus-Zuständen und Einstellungen ----

    public static List<String> profileNames() {
        return new ArrayList<>(ClientConfig.PROFILES.keySet());
    }

    /** Anzahl der Module, die das Profil einschaltet. */
    public static int profileActiveCount(String name) {
        JsonObject profile = ClientConfig.PROFILES.get(name);
        if (profile == null) return 0;
        int count = 0;
        for (String id : profile.keySet()) {
            JsonObject json = profile.getAsJsonObject(id);
            Module module = byId(id);
            if (module != null && module.hasToggle() && module.showInModuleList()
                    && json.has("enabled") && json.get("enabled").getAsBoolean()) count++;
        }
        return count;
    }

    public static void saveProfile(String name) {
        JsonObject profile = new JsonObject();
        for (Module module : MODULES) {
            if (module == FREECAM) continue;
            profile.add(module.getId(), ClientConfig.serializeModule(module, false));
        }
        ClientConfig.PROFILES.put(name, profile);
        saveNow();
    }

    public static boolean applyProfile(String name) {
        JsonObject profile = ClientConfig.PROFILES.get(name);
        if (profile == null) return false;
        for (Module module : MODULES) {
            if (module == FREECAM || !profile.has(module.getId())) continue;
            JsonObject json = profile.getAsJsonObject(module.getId());
            ClientConfig.loadModule(module, json, false);
            if (module.hasToggle() && json.has("enabled")) module.setEnabled(json.get("enabled").getAsBoolean(), false);
        }
        saveNow();
        Notifications.success("Profil geladen", name);
        return true;
    }

    public static void deleteProfile(String name) {
        ClientConfig.PROFILES.remove(name);
        saveNow();
    }

    public static Module byId(String id) {
        for (Module module : MODULES) {
            if (module.getId().equals(id)) return module;
        }
        return null;
    }

    private static <M extends Module> M register(M module) {
        MODULES.add(module);
        return module;
    }

    public static boolean isReady() { return initialized; }

    public static List<Module> all() { return Collections.unmodifiableList(MODULES); }

    public static List<Module> byCategory(Category category) {
        List<Module> result = new ArrayList<>();
        for (Module module : MODULES) {
            if (module.getCategory() == category) result.add(module);
        }
        return result;
    }

    public static void markDirty() {
        dirty = true;
        saveCooldown = 40;
    }

    public static void saveNow() {
        if (!initialized) return;
        ClientConfig.save(MODULES);
        dirty = false;
    }

    public static void onTick() {
        if (!initialized) return;
        for (Module module : MODULES) {
            if (module.isEnabled()) module.onTick();
        }
        if (dirty && --saveCooldown <= 0) saveNow();
    }

    public static void onWorldRender(WorldRenderContext context) {
        if (!initialized) return;
        for (Module module : MODULES) {
            if (module.isEnabled()) module.onWorldRender(context);
        }
    }

    public static void onLeaveWorld() {
        if (!initialized) return;
        for (Module module : MODULES) module.onLeaveWorld();
        saveNow();
    }

    /** Schaltet Module um, deren Taste gedrückt wurde (nur ohne offenes Menü). */
    public static void onKeyPressed(int key) {
        if (!initialized || key == GLFW.GLFW_KEY_UNKNOWN) return;
        for (Module module : MODULES) {
            if (module.getKey() == key) module.toggle();
        }
    }
}
