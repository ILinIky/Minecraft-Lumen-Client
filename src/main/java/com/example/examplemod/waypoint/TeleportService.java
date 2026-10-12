package com.example.examplemod.waypoint;

import com.example.examplemod.ExampleMod;
import com.example.examplemod.feature.Freecam;
import com.example.examplemod.feature.Waypoints;
import com.example.examplemod.gui.Notifications;
import com.example.examplemod.module.ModuleManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Set;
import java.util.UUID;

/**
 * Einmaliger Teleport mit anschließender Beobachtung. Keine Positionssperre, kein
 * Sendecache-Eingriff und keine automatischen Wiederholungen bei Server-Korrekturen.
 * Im Einzelspieler führt ausschließlich der integrierte Server den Teleport aus.
 */
public final class TeleportService {
    public enum Method {
        SINGLEPLAYER("Einzelspieler"),
        CLIENT_ONLY("Client-Versuch"),
        UNAVAILABLE("Nicht möglich");

        public final String label;
        Method(String label) { this.label = label; }
    }

    private record Location(String dimension, double x, double y, double z, float yaw, float pitch) {
        Vec3 position() { return new Vec3(x, y, z); }
    }

    private static final double ARRIVAL_DISTANCE = 1.5;
    private static final long COOLDOWN_NANOS = 1_000_000_000L;
    private static Waypoint lastTarget;
    private static Location back;
    private static Pending pending;
    private static long nextAllowedAttempt;
    private static long sequence;

    private static final class Pending {
        final long id = ++sequence;
        final Location before;
        final Location target;
        final String label;
        final Method method;
        final LocalPlayer player;
        final ClientLevel level;
        final ClientPacketListener connection;
        final TeleportAttempt progress;
        // Wird auch vom integrierten Server gelesen; keine alten Aufträge nach Abbruch ausführen.
        volatile boolean cancelled;
        boolean appliedOnServer;

        Pending(Minecraft mc, Location before, Location target, String label, Method method) {
            this.before = before;
            this.target = target;
            this.label = label;
            this.method = method;
            player = mc.player;
            level = mc.level;
            connection = player.connection;
            progress = new TeleportAttempt(method == Method.SINGLEPLAYER);
        }
    }

    private TeleportService() {}

    public static Method methodFor(String targetDimension) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.player.isDeadOrDying()) return Method.UNAVAILABLE;
        if (mc.getSingleplayerServer() != null) return Method.SINGLEPLAYER;
        return currentDimension().equals(targetDimension) ? Method.CLIENT_ONLY : Method.UNAVAILABLE;
    }

    public static String currentDimension() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? "" : mc.level.dimension().location().toString();
    }

    public static boolean hasLast() { return lastTarget != null; }
    public static boolean hasBack() { return back != null; }

    public static boolean teleport(Waypoint waypoint) {
        if (waypoint == null) return false;
        boolean started = teleportTo(new Location(waypoint.dimension, waypoint.x, waypoint.y, waypoint.z,
                waypoint.yaw, waypoint.pitch), waypoint.displayName());
        if (started) lastTarget = waypoint;
        return started;
    }

    public static boolean teleportLast() {
        if (lastTarget == null) {
            Notifications.warning("Teleport", "Noch kein Wegpunkt angesteuert");
            return false;
        }
        Waypoint saved = WaypointStore.find(lastTarget.id);
        return teleport(saved != null ? saved : lastTarget);
    }

    public static boolean teleportBack() {
        if (back == null) {
            Notifications.warning("Teleport", "Keine vorherige Position");
            return false;
        }
        return teleportTo(back, "vorherige Position");
    }

    private static boolean teleportTo(Location target, String label) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || player.isDeadOrDying()) return false;
        if (pending != null) {
            Notifications.info("Teleport", "Vorheriger Versuch wird noch ausgewertet");
            return false;
        }
        long now = System.nanoTime();
        if (nextAllowedAttempt != 0 && now - nextAllowedAttempt < 0) {
            Notifications.info("Teleport", "Bitte kurz warten");
            return false;
        }
        if (target.dimension == null || ResourceLocation.tryParse(target.dimension) == null
                || !Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z)
                || Math.abs(target.x) > 30_000_000 || Math.abs(target.z) > 30_000_000 || Math.abs(target.y) > 20_000_000
                || !Float.isFinite(target.yaw) || !Float.isFinite(target.pitch)) {
            Notifications.error("Teleport nicht möglich", "Ungültiges Ziel");
            return false;
        }
        Method method = methodFor(target.dimension);
        if (method == Method.UNAVAILABLE) {
            Notifications.warning("Teleport", "Ziel liegt im " + Waypoint.dimensionName(target.dimension));
            return false;
        }
        if (player.isPassenger() && method == Method.CLIENT_ONLY) {
            Notifications.warning("Teleport", "Bitte zuerst vom Fahrzeug absteigen");
            return false;
        }
        double distance = player.position().distanceTo(target.position());
        if (currentDimension().equals(target.dimension) && distance < 1) {
            Notifications.info("Teleport", "Du bist bereits bei " + label);
            return false;
        }

        Location before = new Location(currentDimension(), player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
        Pending request = new Pending(mc, before, target, label, method);
        if (!request.progress.begin()) return false;
        if (Freecam.active()) ModuleManager.FREECAM.setEnabled(false);
        pending = request;
        nextAllowedAttempt = now + COOLDOWN_NANOS;
        ExampleMod.LOGGER.info("[Teleport #{}] single attempt: method={}, distance={}", request.id, method, Math.round(distance));
        if (method == Method.SINGLEPLAYER) {
            teleportOnIntegratedServer(mc, request);
        } else {
            // Genau ein lokales Umsetzen. Vanilla versendet normale Bewegung; keine eigenen Pakete.
            player.setPos(target.x, target.y, target.z);
            player.setYRot(target.yaw);
            player.setXRot(target.pitch);
            player.setYHeadRot(target.yaw);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
        }
        Notifications.info("Teleport gestartet", label + " · " + Waypoints.formatDistance(distance));
        return true;
    }

    /** Beobachtet nur; schreibt weder Position, Blickrichtung noch Bewegung zurück. */
    public static void onClientTick() {
        Pending request = pending;
        if (request == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            finishIfReady(request, request.progress.tick(false, false));
            return;
        }
        boolean wrongSession = player.connection != request.connection;
        boolean remoteWorldChanged = request.method == Method.CLIENT_ONLY
                && (player != request.player || mc.level != request.level);
        if (wrongSession || remoteWorldChanged || player.isDeadOrDying() || Freecam.active()) {
            finishIfReady(request, request.progress.cancel());
            return;
        }
        boolean atTarget = atTarget(player, request.target);
        finishIfReady(request, request.progress.tick(atTarget, atTarget && targetChunksLoaded(mc.level, request.target)));
    }

    /** Vanilla hat das Paket bereits angewendet und bestätigt. Hier niemals erneut teleportieren. */
    public static void onServerPosition(ClientPacketListener connection) {
        Pending request = pending;
        LocalPlayer player = Minecraft.getInstance().player;
        if (request == null || player == null || connection != request.connection || player.connection != connection) return;
        if (request.method == Method.CLIENT_ONLY
                && (player != request.player || Minecraft.getInstance().level != request.level)) {
            finishIfReady(request, request.progress.cancel());
            return;
        }
        boolean arrived = atTarget(player, request.target);
        ExampleMod.LOGGER.info("[Teleport #{}] server position: atTarget={}, elapsedTicks={}",
                request.id, arrived, request.progress.elapsedTicks());
        // Vor Ausführung des integrierten Server-Auftrags kann noch eine ältere Position eintreffen.
        if (request.method == Method.SINGLEPLAYER && !request.appliedOnServer && !arrived) return;
        finishIfReady(request, request.progress.serverPosition(arrived));
    }

    private static boolean atTarget(LocalPlayer player, Location target) {
        return currentDimension().equals(target.dimension)
                && player.position().distanceToSqr(target.position()) <= ARRIVAL_DISTANCE * ARRIVAL_DISTANCE;
    }

    private static boolean targetChunksLoaded(ClientLevel level, Location target) {
        for (int x = SectionPos.blockToSectionCoord(target.x - 0.5); x <= SectionPos.blockToSectionCoord(target.x + 0.5); x++) {
            for (int z = SectionPos.blockToSectionCoord(target.z - 0.5); z <= SectionPos.blockToSectionCoord(target.z + 0.5); z++) {
                if (!level.getChunkSource().hasChunk(x, z)) return false;
            }
        }
        return true;
    }

    private static void finishIfReady(Pending request, TeleportAttempt.Result result) {
        if (pending != request || result == TeleportAttempt.Result.WAITING) return;
        pending = null;
        request.cancelled = true;
        ExampleMod.LOGGER.info("[Teleport #{}] finished: result={}, elapsedTicks={}; no automatic retry",
                request.id, result, request.progress.elapsedTicks());
        switch (result) {
            case CONFIRMED -> {
                back = request.before;
                Notifications.success("Teleport bestätigt", request.label);
            }
            case UNCONFIRMED -> {
                back = request.before;
                Notifications.info("Teleport unbestätigt", "Clientposition erreicht; keine Serverbestätigung");
            }
            case REJECTED -> Notifications.warning("Teleport abgelehnt", "Serverposition übernommen; Versuch beendet");
            case TIMED_OUT -> Notifications.warning("Teleport beendet", "Ziel nicht bestätigt; Zeitlimit erreicht");
            case CANCELLED -> Notifications.info("Teleport beendet", "Spieler oder Welt hat sich geändert");
            default -> { }
        }
    }

    private static void teleportOnIntegratedServer(Minecraft mc, Pending request) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) {
            finishIfReady(request, request.progress.cancel());
            return;
        }
        UUID playerId = request.player.getUUID();
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(request.target.dimension));
        server.execute(() -> {
            if (request.cancelled) return;
            boolean applied = false;
            try {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                ServerLevel level = server.getLevel(key);
                if (player != null && level != null) {
                    Location target = request.target;
                    player.stopRiding();
                    applied = player.teleportTo(level, target.x, target.y, target.z, Set.of(), target.yaw, target.pitch, true);
                    if (applied) {
                        player.setDeltaMovement(Vec3.ZERO);
                        player.resetFallDistance();
                    }
                }
            } catch (RuntimeException failure) {
                ExampleMod.LOGGER.error("[Teleport #{}] integrated server failed", request.id, failure);
            }
            boolean success = applied;
            mc.execute(() -> {
                if (pending != request || request.cancelled) return;
                request.appliedOnServer = success;
                finishIfReady(request, request.progress.serverPosition(success));
            });
        });
    }

    public static void reset() {
        if (pending != null) {
            pending.cancelled = true;
            pending.progress.cancel();
            pending = null;
        }
        nextAllowedAttempt = 0;
        lastTarget = null;
        back = null;
    }
}
