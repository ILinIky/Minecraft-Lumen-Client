package com.example.examplemod.module;

import com.example.examplemod.ExampleMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Speichert Modul-Zustände, Einstellungen und Tasten in config/examplemod/client.json. */
public final class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ClientConfig() {}

    public static Path directory() {
        return FMLPaths.CONFIGDIR.get().resolve(ExampleMod.MODID);
    }

    private static Path file() {
        return directory().resolve("client.json");
    }

    /** Gespeicherte Profile: Name → Moduldaten (ohne Tasten). */
    static final java.util.Map<String, JsonObject> PROFILES = new java.util.LinkedHashMap<>();

    static void load(List<Module> modules) {
        Path file = file();
        JsonObject modulesJson = new JsonObject();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                if (root.has("modules")) modulesJson = root.getAsJsonObject("modules");
                if (root.has("profiles")) {
                    for (var entry : root.getAsJsonObject("profiles").entrySet()) {
                        if (entry.getValue().isJsonObject()) PROFILES.put(entry.getKey(), entry.getValue().getAsJsonObject());
                    }
                }
            } catch (IOException | RuntimeException e) {
                ExampleMod.LOGGER.error("Client-Konfiguration konnte nicht gelesen werden: {}", file, e);
            }
        }
        for (Module module : modules) {
            boolean enabled = module.isDefaultEnabled();
            JsonObject json = modulesJson.has(module.getId()) ? modulesJson.getAsJsonObject(module.getId()) : null;
            if (json != null) {
                loadModule(module, json, true);
                if (json.has("enabled")) enabled = json.get("enabled").getAsBoolean();
            }
            if (enabled && module.isPersistent()) module.restoreEnabled(true);
        }
    }

    /** Übernimmt Einstellungen (und optional die Taste) eines Moduls aus JSON. */
    static void loadModule(Module module, JsonObject json, boolean withKey) {
        try {
            if (withKey && json.has("key")) module.setKey(json.get("key").getAsInt());
            JsonObject settings = json.has("settings") ? json.getAsJsonObject("settings") : new JsonObject();
            for (Setting setting : module.getSettings()) {
                JsonElement value = settings.get(setting.getName());
                if (value != null) setting.load(value);
            }
            module.loadExtra(json);
        } catch (RuntimeException e) {
            ExampleMod.LOGGER.error("Einstellungen für Modul {} sind ungültig", module.getId(), e);
        }
    }

    static JsonObject serializeModule(Module module, boolean withKey) {
        JsonObject json = new JsonObject();
        json.addProperty("enabled", withKey ? module.isPersistent() && module.isEnabled() : module.isEnabled());
        if (withKey) json.addProperty("key", module.getKey());
        JsonObject settings = new JsonObject();
        for (Setting setting : module.getSettings()) {
            if (!(setting instanceof Setting.Action)) settings.add(setting.getName(), setting.save());
        }
        json.add("settings", settings);
        module.saveExtra(json);
        return json;
    }

    static void save(List<Module> modules) {
        JsonObject modulesJson = new JsonObject();
        for (Module module : modules) modulesJson.add(module.getId(), serializeModule(module, true));
        JsonObject profiles = new JsonObject();
        PROFILES.forEach(profiles::add);
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.add("modules", modulesJson);
        root.add("profiles", profiles);
        writeAtomically(file(), GSON.toJson(root));
    }

    /** Schreibt zuerst in eine temporäre Datei, damit ein Absturz keine halbe Datei hinterlässt. */
    public static void writeAtomically(Path target, String content) {
        try {
            Files.createDirectories(target.getParent());
            Path temp = target.resolveSibling(target.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                writer.write(content);
            }
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            ExampleMod.LOGGER.error("Datei konnte nicht gespeichert werden: {}", target, e);
        }
    }
}
