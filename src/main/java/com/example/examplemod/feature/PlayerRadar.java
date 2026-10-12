package com.example.examplemod.feature;

import com.example.examplemod.gui.Notifications;
import com.example.examplemod.gui.PlayerRadarScreen;
import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.ModuleManager;
import com.example.examplemod.module.Setting;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;

/** A saved watchlist; online status alone never implies a known position. */
public final class PlayerRadar extends Module {
    public static final int MAX_SELECTED = 256;
    public final Setting.Slider radius, maxRows;
    private final Map<UUID, String> selected = new LinkedHashMap<>();
    private List<PlayerRadarModel.Hit> nearby = List.of();
    private int ticks;
    private Object lastLevel;

    public PlayerRadar() {
        super("player_radar", "Spieler-Radar", "Ausgewählte Spieler im Radius rechts mit Entfernung anzeigen", Category.RENDER);
        radius = add(new Setting.Slider("Radius", "Gerade Entfernung einschließlich Höhenunterschied", 64, 8, 256, 8, "Bl.").onChange(this::refresh));
        maxRows = add(new Setting.Slider("Zeilen", "Maximale Anzahl sichtbarer Spieler; nächste zuerst", 8, 3, 16, 1, ""));
        add(new Setting.Action("Spieler auswählen", "Spieler aus der Online-Liste auswählen; Auswahl wird gespeichert",
                () -> mc.setScreen(new PlayerRadarScreen(mc.screen))));
    }
    public Map<UUID, String> selected() { return Collections.unmodifiableMap(selected); }
    public List<PlayerRadarModel.Hit> nearby() { return isEnabled() && mc.level == lastLevel ? nearby : List.of(); }
    public void togglePlayer(UUID id, String name) {
        if (selected.containsKey(id)) selected.remove(id);
        else {
            if (selected.size() >= MAX_SELECTED) { Notifications.warning("Spieler-Radar", "Maximal 256 Spieler auswählbar"); return; }
            selected.put(id, name);
        }
        refresh(); ModuleManager.markDirty();
    }
    public void clearPlayers() { selected.clear(); nearby = List.of(); ModuleManager.markDirty(); }
    private void refresh() {
        lastLevel = mc.level;
        if (!isEnabled() || mc.player == null || mc.level == null) { nearby = List.of(); return; }
        List<PlayerRadarModel.Candidate> candidates = new ArrayList<>();
        // The client world contains only entities whose positions the server actually sent.
        for (var player : mc.level.players()) {
            if (player.isRemoved() || !player.isAlive()) continue;
            candidates.add(new PlayerRadarModel.Candidate(player.getUUID(), player.getGameProfile().getName(), player.distanceToSqr(mc.player)));
        }
        nearby = PlayerRadarModel.nearby(candidates, selected.keySet(), mc.player.getUUID(), radius.get());
    }
    @Override public void onTick() { if (lastLevel != mc.level || ++ticks % 5 == 0) refresh(); }
    @Override protected void onEnable() { refresh(); }
    @Override protected void onDisable() { nearby = List.of(); }
    @Override public void onLeaveWorld() { nearby = List.of(); lastLevel = null; ticks = 0; }
    @Override public String getHudInfo() { return nearby().size() + " im Radius"; }
    @Override public void saveExtra(JsonObject json) {
        JsonArray array = new JsonArray();
        selected.forEach((id, name) -> { JsonObject player = new JsonObject(); player.addProperty("uuid", id.toString()); player.addProperty("name", name); array.add(player); });
        json.add("players", array);
    }
    @Override public void loadExtra(JsonObject json) {
        selected.clear(); nearby = List.of();
        if (!json.has("players") || !json.get("players").isJsonArray()) return;
        for (var value : json.getAsJsonArray("players")) {
            if (selected.size() >= MAX_SELECTED) break;
            try {
                var player = value.getAsJsonObject(); String name = player.get("name").getAsString();
                if (!name.isBlank() && name.length() <= 64) selected.put(UUID.fromString(player.get("uuid").getAsString()), name);
            } catch (RuntimeException ignored) { }
        }
    }
}
