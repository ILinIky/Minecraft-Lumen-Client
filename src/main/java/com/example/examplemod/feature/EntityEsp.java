package com.example.examplemod.feature;

import com.example.examplemod.gui.EntityFilterScreen;
import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.ModuleManager;
import com.example.examplemod.module.Setting;
import com.example.examplemod.render.Render3D;
import com.example.examplemod.render.WorldRenderContext;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;

/** Zeigt Spieler, Monster, Tiere und Items als Rahmen durch Wände, mit Lebensbalken und Tracern. */
public class EntityEsp extends Module {
    public static final int PLAYER_COLOR = 0xFF60A5FA;
    public static final int MONSTER_COLOR = 0xFFF87171;
    public static final int ANIMAL_COLOR = 0xFF34D399;
    public static final int ITEM_COLOR = 0xFFFBBF24;

    private final Setting.Bool players;
    private final Setting.Mode monsters;
    private final Setting.Mode animals;
    private final Setting.Bool items;
    private final Setting.Slider radius;
    private final Setting.Bool tracers;
    private final Setting.Bool healthBars;

    public final Set<EntityType<?>> selectedMonsters = new LinkedHashSet<>();
    public final Set<EntityType<?>> selectedAnimals = new LinkedHashSet<>();
    public final Set<String> selectedPlayers = new LinkedHashSet<>();

    private int visibleCount;

    public EntityEsp() {
        super("entity_esp", "Entity-ESP", "Spieler, Monster und Tiere durch Wände sehen", Category.RENDER);
        players = add(new Setting.Bool("Spieler", "Andere Spieler markieren", true));
        monsters = add(new Setting.Mode("Monster", "Alle, nur Auswahl oder keine", "Alle", "Alle", "Auswahl", "Aus"));
        animals = add(new Setting.Mode("Tiere", "Alle, nur Auswahl oder keine", "Aus", "Alle", "Auswahl", "Aus"));
        items = add(new Setting.Bool("Items", "Herumliegende Items markieren", false));
        radius = add(new Setting.Slider("Radius", "Maximale Entfernung", 64, 16, 256, 8, "Bl."));
        tracers = add(new Setting.Bool("Tracer", "Linien von der Bildschirmmitte", false));
        healthBars = add(new Setting.Bool("Lebensbalken", "Leben über dem Rahmen anzeigen", true));
        add(new Setting.Action("Filter bearbeiten", "Bestimmte Mobs und Spieler auswählen", () ->
                mc.setScreen(new EntityFilterScreen(mc.screen, this))));
        selectedMonsters.addAll(Set.of(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.CREEPER,
                EntityType.SPIDER, EntityType.ENDERMAN));
    }

    public Setting.Mode monsterMode() { return monsters; }
    public Setting.Mode animalMode() { return animals; }
    public Setting.Bool playersEnabled() { return players; }

    public void filterChanged() { ModuleManager.markDirty(); }

    @Override
    public String getHudInfo() { return String.valueOf(visibleCount); }

    /** Farbe für eine Entity oder 0, wenn sie nicht angezeigt werden soll. */
    private int colorFor(Entity entity) {
        if (entity == mc.player || entity.isRemoved() || entity instanceof ArmorStand) return 0;
        if (entity instanceof Player player) {
            if (!players.get() || player.isSpectator()) return 0;
            return selectedPlayers.isEmpty() || selectedPlayers.contains(player.getGameProfile().getName()) ? PLAYER_COLOR : 0;
        }
        if (entity instanceof ItemEntity) return items.get() ? ITEM_COLOR : 0;
        if (!(entity instanceof LivingEntity)) return 0;
        EntityType<?> type = entity.getType();
        if (isMonster(entity)) {
            return matches(monsters, selectedMonsters, type) ? MONSTER_COLOR : 0;
        }
        return matches(animals, selectedAnimals, type) ? ANIMAL_COLOR : 0;
    }

    public static boolean isMonster(Entity entity) {
        return entity instanceof Enemy || entity.getType().getCategory() == MobCategory.MONSTER;
    }

    private static boolean matches(Setting.Mode mode, Set<EntityType<?>> selection, EntityType<?> type) {
        return mode.is("Alle") || (mode.is("Auswahl") && selection.contains(type));
    }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        if (mc.level == null || mc.player == null) return;
        double maxDistSq = radius.get() * radius.get();
        float partial = context.partialTick();
        VertexConsumer lines = Render3D.lines(context);
        int count = 0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            int color = colorFor(entity);
            if (color == 0 || entity.distanceToSqr(mc.player) > maxDistSq) continue;
            count++;
            Vec3 lerped = entity.getPosition(partial);
            AABB box = entity.getBoundingBox().move(lerped.subtract(entity.position())).inflate(0.05);
            Render3D.box(context, lines, box, color);
            if (healthBars.get() && entity instanceof LivingEntity living) {
                healthBar(context, lines, living, box);
            }
            if (tracers.get()) {
                Render3D.tracer(context, lines, new Vec3(lerped.x, box.minY + box.getYsize() * 0.5, lerped.z), color & 0xC0FFFFFF);
            }
        }
        visibleCount = count;
        Render3D.flush(context);
    }

    /** Waagerechter Balken über der Entity, immer zur Kamera ausgerichtet. */
    private void healthBar(WorldRenderContext context, VertexConsumer lines, LivingEntity living, AABB box) {
        float max = living.getMaxHealth();
        if (max <= 0) return;
        float progress = Math.max(0, Math.min(1, living.getHealth() / max));
        double yaw = Math.toRadians(context.camera().getYRot());
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        double half = Math.max(0.35, box.getXsize() * 0.6);
        Vec3 center = new Vec3((box.minX + box.maxX) / 2, box.maxY + 0.25, (box.minZ + box.maxZ) / 2);
        Vec3 start = center.subtract(right.scale(half));
        Vec3 end = center.add(right.scale(half));
        Render3D.worldLine(context, lines, start, end, 0xC0101010);
        int color = progress > 0.5F ? 0xFF34D399 : progress > 0.25F ? 0xFFFBBF24 : 0xFFF87171;
        Render3D.worldLine(context, lines, start, start.add(right.scale(half * 2 * progress)), color);
    }

    @Override
    public void saveExtra(JsonObject json) {
        json.add("monsters", BlockLists.entitiesToJson(selectedMonsters));
        json.add("animals", BlockLists.entitiesToJson(selectedAnimals));
        JsonArray names = new JsonArray();
        selectedPlayers.forEach(names::add);
        json.add("players", names);
    }

    @Override
    public void loadExtra(JsonObject json) {
        if (json.has("monsters")) {
            selectedMonsters.clear();
            selectedMonsters.addAll(BlockLists.entitiesFromJson(json.getAsJsonArray("monsters")));
        }
        if (json.has("animals")) {
            selectedAnimals.clear();
            selectedAnimals.addAll(BlockLists.entitiesFromJson(json.getAsJsonArray("animals")));
        }
        if (json.has("players")) {
            selectedPlayers.clear();
            for (JsonElement element : json.getAsJsonArray("players")) selectedPlayers.add(element.getAsString());
        }
    }
}
