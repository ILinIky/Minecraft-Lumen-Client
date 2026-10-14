package com.example.examplemod.blueprint;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
import java.util.Locale;

/** File identity and JSON reading for the existing per-world blueprint state. No Minecraft dependency. */
public final class BlueprintSavedStates {
    private BlueprintSavedStates() {}
    private static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static String identity(Path source) {
        String name = source.toAbsolutePath().normalize().toString();
        if (File.separatorChar == '\\') name = name.toLowerCase(Locale.ROOT);
        return HexFormat.of().formatHex(digest().digest(name.getBytes(StandardCharsets.UTF_8)));
    }
    public static Path archive(Path worldState, Path source) {
        return worldState.resolveSibling(worldState.getFileName() + ".plans").resolve(identity(source) + ".json");
    }
    public static JsonObject read(Path state) throws IOException {
        if (!Files.exists(state)) return null;
        if (Files.size(state) > 32_000_000) throw new IOException("Gespeicherter Bauplan-Zustand ist zu groß");
        try (Reader reader = Files.newBufferedReader(state, StandardCharsets.UTF_8)) {
            JsonObject result = JsonParser.parseReader(reader).getAsJsonObject();
            if (!result.has("file")) throw new IOException("Bauplandatei im Speicherstand fehlt");
            if (result.has("version") && result.get("version").getAsInt() != 2)
                throw new IOException("Unbekannte Version des Bauplan-Speicherstands");
            return result;
        }
    }
    public static int[] cells(JsonObject json, String field) {
        if (!json.has(field)) return new int[0];
        var array = json.getAsJsonArray(field);
        if (array.size() > 2_000_000) throw new IllegalArgumentException("Zu viele Bauplan-Anpassungen");
        int[] result = new int[array.size()];
        for (int i = 0; i < result.length; i++) result[i] = array.get(i).getAsInt();
        return result;
    }
    public static String fingerprint(Path source) throws IOException {
        MessageDigest digest = digest();
        try (var input = new DigestInputStream(Files.newInputStream(source), digest)) {
            input.transferTo(OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    public static boolean sameContents(String saved, String current) {
        return saved == null || saved.isEmpty() || saved.equals(current);
    }
}
