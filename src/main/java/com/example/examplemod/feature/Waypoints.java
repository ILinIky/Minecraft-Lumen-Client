package com.example.examplemod.feature;

import com.example.examplemod.gui.WaypointScreen;
import com.example.examplemod.module.Category;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.Setting;
import com.example.examplemod.render.Render3D;
import com.example.examplemod.render.WorldRenderContext;
import com.example.examplemod.waypoint.Navigation;
import com.example.examplemod.waypoint.Waypoint;
import com.example.examplemod.waypoint.WaypointStore;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** Zeigt gespeicherte Wegpunkte als Marker in der Welt. Teleportieren geht über das Wegpunkt-Menü. */
public class Waypoints extends Module {
    private final Setting.Bool labels;
    private final Setting.Bool beams;
    private final Setting.Slider maxDistance;
    public final Setting.Bool saveDeaths;
    public final Setting.Slider keepDeaths;
    public final Setting.Slider arrivalRadius;
    public final Setting.Bool clearOnArrival;
    public final Setting.Bool arrivalSound;

    public Waypoints() {
        super("waypoints", "Wegpunkte", "Gespeicherte Orte in der Welt anzeigen und teleportieren", Category.WORLD);
        enabledByDefault();
        labels = add(new Setting.Bool("Beschriftung", "Name und Entfernung anzeigen", true));
        beams = add(new Setting.Bool("Lichtstrahl", "Senkrechte Linie am Wegpunkt", true));
        maxDistance = add(new Setting.Slider("Max. Entfernung", "Weiter entfernte Wegpunkte ausblenden", 5000, 100, 30000, 100, "Bl."));
        saveDeaths = add(new Setting.Bool("Todespunkte speichern", "Beim Tod automatisch einen Wegpunkt anlegen und als Kompass-Ziel setzen", true));
        keepDeaths = add(new Setting.Slider("Max. Todespunkte", "Ältere Todespunkte werden automatisch gelöscht", 3, 1, 10, 1, "").visibleWhen(saveDeaths::get));
        arrivalRadius = add(new Setting.Slider("Ankunftsradius", "Ab dieser Entfernung gilt das Kompass-Ziel als erreicht", 5, 2, 30, 1, "Bl."));
        clearOnArrival = add(new Setting.Bool("Ziel bei Ankunft entfernen", "Kompass-Ziel nach dem Erreichen zurücksetzen", true));
        arrivalSound = add(new Setting.Bool("Ankunftston", "Ton beim Erreichen des Ziels oder Portal-Punkts", true));
        add(new Setting.Action("Wegpunkte öffnen", "Liste mit Teleport, Bearbeiten und Löschen", () ->
                mc.setScreen(new WaypointScreen(mc.screen))));
    }

    @Override
    public boolean showInModuleList() { return false; }

    @Override
    public void onWorldRender(WorldRenderContext context) {
        if (mc.level == null || mc.player == null) return;
        String dimension = mc.level.dimension().location().toString();
        double maxSq = maxDistance.get() * maxDistance.get();
        Vec3 playerPos = mc.player.position();
        Waypoint target = WaypointStore.target();
        Navigation.Point portal = Navigation.point(target, dimension);
        if (portal != null && !portal.portal()) portal = null;
        VertexConsumer lines = Render3D.lines(context);
        boolean drewLabels = false;
        for (Waypoint waypoint : WaypointStore.all()) {
            if (!dimension.equals(waypoint.dimension)) continue;
            Vec3 pos = new Vec3(waypoint.x, waypoint.y, waypoint.z);
            double distSq = pos.distanceToSqr(playerPos);
            if (distSq > maxSq && waypoint != target) continue;
            int color = waypoint.color();
            if (beams.get()) {
                Render3D.worldLine(context, lines, pos, pos.add(0, 256, 0), (color & 0xFFFFFF) | 0x90000000);
                Render3D.box(context, lines, new AABB(pos.x - 0.3, pos.y, pos.z - 0.3,
                        pos.x + 0.3, pos.y + 0.6, pos.z + 0.3), color);
            }
            if (labels.get()) drewLabels = true;
        }
        // Ziel in der anderen Dimension: Strahl am umgerechneten Portal-Punkt auf Spielerhöhe.
        Vec3 portalPos = portal == null ? null : new Vec3(portal.x(), playerPos.y, portal.z());
        if (portalPos != null) {
            Render3D.worldLine(context, lines, portalPos.add(0, -64, 0), portalPos.add(0, 256, 0),
                    (target.color() & 0xFFFFFF) | 0x90000000);
            if (labels.get()) drewLabels = true;
        }
        Render3D.flush(context);

        if (!drewLabels) return;
        for (Waypoint waypoint : WaypointStore.all()) {
            if (!dimension.equals(waypoint.dimension)) continue;
            Vec3 pos = new Vec3(waypoint.x, waypoint.y, waypoint.z);
            double distSq = pos.distanceToSqr(playerPos);
            if (distSq > maxSq && waypoint != target) continue;
            String text = waypoint.icon() + " " + waypoint.displayName() + "  " + formatDistance(Math.sqrt(distSq));
            Render3D.label(context, pos.add(0, 1.4, 0), text, waypoint.color(), 0.025F);
        }
        if (portalPos != null) {
            String text = "⇄ " + target.displayName() + " (" + Navigation.portalLabel(dimension) + ")  "
                    + formatDistance(Navigation.distance(portal, playerPos.x, playerPos.y, playerPos.z));
            Render3D.label(context, portalPos.add(0, 1.4, 0), text, target.color(), 0.025F);
        }
        context.buffers().endBatch();
    }

    public static String formatDistance(double blocks) {
        return blocks >= 1000 ? String.format(Locale.ROOT, "%.1fk m", blocks / 1000.0)
                : String.format(Locale.ROOT, "%.0f m", blocks);
    }
}
