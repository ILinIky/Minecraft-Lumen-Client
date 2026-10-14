package com.example.examplemod.blueprint;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import java.nio.file.*;
import java.util.*;

public final class BlueprintSavedStatesTest {
    private static int checks;
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static JsonObject state(Path file, int x, int hidden) {
        JsonObject state = new JsonObject(); state.addProperty("version", 2); state.addProperty("file", file.toString());
        state.addProperty("x", x); state.addProperty("turns", 3); state.addProperty("layer", 4);
        JsonArray cells = new JsonArray(); cells.add(hidden); state.add("ignored", cells);
        JsonObject swaps = new JsonObject(); swaps.addProperty("minecraft:stone", "minecraft:oak_planks"); state.add("replacements", swaps);
        return state;
    }
    public static void main(String[] args) throws Exception {
        Path dir = Files.createTempDirectory("lumen-blueprint-states-");
        List<Path> files = new ArrayList<>(); List<Path> folders = new ArrayList<>();
        try {
            Path world = dir.resolve("world-a.json"), otherWorld = dir.resolve("world-b.json");
            Path a = dir.resolve("castle.nbt"), b = dir.resolve("barn.nbt");
            Path savedA = BlueprintSavedStates.archive(world, a), savedB = BlueprintSavedStates.archive(world, b);
            check(!savedA.equals(savedB), "Different blueprint files have independent workspaces");
            check(!savedA.equals(BlueprintSavedStates.archive(otherWorld, a)), "Same blueprint in another world remains independent");
            check(savedA.equals(BlueprintSavedStates.archive(world, dir.resolve("sub/../castle.nbt"))), "Normalized path reuses workspace");
            check(!savedA.equals(BlueprintSavedStates.archive(world, dir.resolve("other/castle.nbt"))), "Equal filenames in different folders never collide");
            Files.createDirectories(savedA.getParent()); folders.add(savedA.getParent());
            Files.writeString(savedA, state(a, 123, 7).toString()); files.add(savedA);
            Files.writeString(savedB, state(b, 456, 19).toString()); files.add(savedB);
            var returnToA = BlueprintSavedStates.read(savedA);
            check(returnToA.get("x").getAsInt() == 123 && BlueprintSavedStates.cells(returnToA, "ignored")[0] == 7,
                    "A to B to A restores original location and hidden block");
            check(BlueprintSavedStates.read(savedB).get("x").getAsInt() == 456, "Switching does not overwrite other location");
            check(returnToA.get("turns").getAsInt() == 3 && returnToA.get("layer").getAsInt() == 4, "Rotation and layer survive persistence");
            check(returnToA.getAsJsonObject("replacements").get("minecraft:stone").getAsString().equals("minecraft:oak_planks"), "Material replacements survive switching");
            var legacy = state(a, 10, 1); legacy.remove("version"); legacy.remove("ignored");
            Files.writeString(world, legacy.toString()); files.add(world);
            check(BlueprintSavedStates.read(world) != null && BlueprintSavedStates.cells(legacy, "ignored").length == 0,
                    "Former single-plan format is readable and has no hidden blocks");
            Files.writeString(a, "original"); files.add(a);
            String hash = BlueprintSavedStates.fingerprint(a);
            check(BlueprintSavedStates.sameContents(hash, BlueprintSavedStates.fingerprint(a)), "Unchanged source keeps modifications");
            Files.writeString(a, "replacement");
            check(!BlueprintSavedStates.sameContents(hash, BlueprintSavedStates.fingerprint(a)), "Changed source rejects old index-based edits");
            check(BlueprintSavedStates.sameContents(null, hash), "Legacy state without fingerprint can migrate");
            var reset = state(a, 123, 7); reset.add("ignored", new JsonArray()); reset.add("replacements", new JsonObject());
            Files.writeString(savedA, reset.toString());
            check(BlueprintSavedStates.cells(BlueprintSavedStates.read(savedA), "ignored").length == 0, "Reset persists empty edit mask");
            check(BlueprintSavedStates.read(savedB).get("x").getAsInt() == 456, "Reset leaves other blueprint state intact");
            Files.writeString(savedA, "broken json");
            try { BlueprintSavedStates.read(savedA); throw new AssertionError("Corruption accepted"); }
            catch (RuntimeException expected) { checks++; }
            check(Files.readString(savedA).equals("broken json"), "Reading damaged state never overwrites it");
            check(BlueprintSavedStates.read(dir.resolve("missing.json")) == null, "Unseen blueprint starts fresh");
            System.out.println("BlueprintSavedStatesTest: " + checks + " checks passed");
        } finally {
            for (Path file : files) Files.deleteIfExists(file);
            for (Path folder : folders) Files.deleteIfExists(folder);
            Files.deleteIfExists(dir);
        }
    }
}
