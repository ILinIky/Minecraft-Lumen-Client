package com.example.examplemod.waypoint;

import com.example.examplemod.feature.Waypoints;
import com.example.examplemod.gui.Notifications;
import com.example.examplemod.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

/**
 * Navigation zum Kompass-Ziel. Liegt das Ziel in der Oberwelt und man ist im Nether (oder
 * umgekehrt), wird zum umgerechneten Portal-Punkt geführt (Faktor 8). Beim Erreichen gibt es
 * eine Meldung; danach wird das Ziel auf Wunsch entfernt.
 */
public final class Navigation {
    /**
     * Zielpunkt in der aktuellen Dimension.
     *
     * @param portal true, wenn es der umgerechnete Portal-Punkt eines Ziels in der anderen Dimension ist
     */
    public record Point(double x, double y, double z, boolean portal) {}

    private static int lastKey = -1;
    /** Erst melden, nachdem man einmal außerhalb des Radius war (Ziel nicht am eigenen Standort setzen). */
    private static boolean armed;

    private Navigation() {}

    public static boolean isOverworld(String dimension) { return dimension != null && dimension.endsWith("overworld"); }
    public static boolean isNether(String dimension) { return dimension != null && dimension.endsWith("the_nether"); }

    /** Punkt, zu dem in {@code dimension} geführt wird, oder {@code null}, wenn das Ziel von hier nicht erreichbar ist. */
    public static Point point(Waypoint target, String dimension) {
        if (target == null || dimension == null) return null;
        if (dimension.equals(target.dimension)) return new Point(target.x, target.y, target.z, false);
        if (isNether(dimension) && isOverworld(target.dimension)) return new Point(target.x / 8, target.y, target.z / 8, true);
        if (isOverworld(dimension) && isNether(target.dimension)) return new Point(target.x * 8, target.y, target.z * 8, true);
        return null;
    }

    /** Aktueller Zielpunkt für den Spieler, oder {@code null}. */
    public static Point current() {
        return point(WaypointStore.target(), TeleportService.currentDimension());
    }

    /** Text für den Portal-Punkt, z.B. "Portal bei 120 / -48". */
    public static String portalHint(Point point) {
        return String.format(Locale.ROOT, "Portal bei %.0f / %.0f", point.x(), point.z());
    }

    public static String portalLabel(String dimension) {
        return isNether(dimension) ? "über Nether" : "Portal-Punkt";
    }

    /** Abstand zum Zielpunkt; beim Portal-Punkt zählt nur die Waagerechte. */
    public static double distance(Point point, double x, double y, double z) {
        double dx = point.x() - x, dz = point.z() - z;
        double dy = point.portal() ? 0 : point.y() - y;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static void onClientTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        Waypoint target = WaypointStore.target();
        Waypoints settings = ModuleManager.WAYPOINTS;
        if (player == null || target == null || settings == null) {
            lastKey = -1;
            return;
        }
        Point point = point(target, TeleportService.currentDimension());
        if (point == null) return;

        double radius = settings.arrivalRadius.get();
        double distance = distance(point, player.getX(), player.getY(), player.getZ());
        boolean inside = distance <= radius;
        int key = target.id * 2 + (point.portal() ? 1 : 0);
        if (key != lastKey) {
            lastKey = key;
            armed = !inside;
            return;
        }
        if (!inside) {
            if (distance > radius + 2) armed = true;
            return;
        }
        if (!armed) return;
        armed = false;

        if (settings.arrivalSound.get()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2F, 0.8F));
        }
        if (point.portal()) {
            Notifications.info("Portal-Punkt erreicht", "Hier Portal bauen → " + target.displayName());
        } else {
            Notifications.success("Ziel erreicht", target.icon() + " " + target.displayName());
            if (settings.clearOnArrival.get()) {
                WaypointStore.setTarget(null);
                lastKey = -1;
            }
        }
    }
}
