package com.example.examplemod.gui.hud;

import com.example.examplemod.feature.EntityEsp;
import com.example.examplemod.feature.Minimap;
import com.example.examplemod.gui.Theme;
import com.example.examplemod.gui.UiRender;
import com.example.examplemod.render.MinimapData;
import com.example.examplemod.waypoint.Navigation;
import com.example.examplemod.waypoint.TeleportService;
import com.example.examplemod.waypoint.Waypoint;
import com.example.examplemod.waypoint.WaypointStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** Die Minimap als HUD-Element. Sichtbar, solange das Modul "Minimap" an ist. */
public final class MinimapElement extends HudElement {
    private static final int BORDER = 3;
    private static final int COORDS_H = 12;

    private final Minimap minimap;
    // Pro Bild berechnet, damit project() ohne Parameterflut auskommt.
    private double px, pz;
    private float scale, sin, cos;
    private int centerX, centerY, half;
    private final float[] projected = new float[2];

    MinimapElement(Minimap minimap) {
        super("minimap", "Minimap", null, 0, 0, 4, 112);
        this.minimap = minimap;
    }

    @Override
    public boolean isVisible() { return minimap.isEnabled(); }

    @Override
    public void toggleVisible() { minimap.toggle(); }

    @Override
    public void measure(Minecraft mc, boolean editing) {
        if (mc.player == null) { w = h = 0; return; }
        int mapSize = minimap.size.getInt();
        w = mapSize + BORDER * 2;
        h = mapSize + BORDER * 2 + (minimap.coordinates.get() ? COORDS_H : 0);
    }

    @Override
    public void render(GuiGraphics g, Minecraft mc, boolean editing, float delta) {
        LocalPlayer player = mc.player;
        if (player == null) return;
        Font font = mc.font;
        int mapSize = minimap.size.getInt();
        int mx = x + BORDER, my = y + BORDER;
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 position = player.getPosition(partial);
        float yaw = player.getViewYRot(partial);
        boolean rotating = minimap.rotate.get();

        px = position.x;
        pz = position.z;
        half = mapSize / 2;
        centerX = mx + half;
        centerY = my + half;
        scale = mapSize / (2.0F * minimap.radius());
        float angle = rotating ? (float) Math.toRadians(180 - yaw) : 0;
        sin = (float) Math.sin(angle);
        cos = (float) Math.cos(angle);

        UiRender.roundRect(g, x, y, w, h, 4, Theme.HUD_BG);
        g.fill(mx, my, mx + mapSize, my + mapSize, 0xFF0C0D12);

        MinimapData data = minimap.data();
        if (data.ready()) {
            int n = data.size();
            g.enableScissor(mx, my, mx + mapSize, my + mapSize);
            g.pose().pushMatrix();
            g.pose().translate(centerX, centerY);
            g.pose().rotate(angle);
            g.pose().scale(scale, scale);
            g.pose().translate((float) (data.originX() - px), (float) (data.originZ() - pz));
            g.blit(RenderPipelines.GUI_TEXTURED, MinimapData.TEXTURE, 0, 0, 0, 0, n, n, n, n, n, n, 0xFFFFFFFF);
            g.pose().popMatrix();
            g.disableScissor();
        } else {
            String text = "Karte lädt…";
            g.drawString(font, text, centerX - font.width(text) / 2, centerY - 4, Theme.TEXT_MUTED, false);
        }

        if (minimap.mobs.get() || minimap.players.get()) drawEntities(g, mc, player);
        if (minimap.waypoints.get()) drawWaypoints(g, font);

        // Spielerpfeil in der Mitte
        g.pose().pushMatrix();
        g.pose().translate(centerX, centerY);
        if (!rotating) g.pose().rotate((float) Math.toRadians(yaw + 180));
        g.drawString(font, "▲", -font.width("▲") / 2, -4, 0xFFFFFFFF, true);
        g.pose().popMatrix();

        // Norden am Rand
        float nx = sin, ny = -cos;
        float edge = (half - 5) / Math.max(Math.abs(nx), Math.abs(ny));
        int northX = Math.round(centerX + nx * edge), northY = Math.round(centerY + ny * edge);
        UiRender.roundRect(g, northX - 4, northY - 5, 9, 10, 3, 0xC0000000);
        g.drawString(font, "N", northX - 2, northY - 4, Theme.ACCENT_2, false);

        // Rahmen in Akzentfarbe
        UiRender.gradientH(g, mx, my, mapSize, 1, Theme.ACCENT, Theme.ACCENT_2);
        if (data.caveView()) {
            g.drawString(font, "Höhle", mx + 3, my + mapSize - 10, Theme.alpha(Theme.TEXT_DIM, 0.9F), true);
        }

        if (minimap.coordinates.get()) {
            String coords = String.format(Locale.ROOT, "%d, %d, %d", player.getBlockX(), player.getBlockY(), player.getBlockZ());
            g.drawString(font, coords, x + (w - font.width(coords)) / 2, my + mapSize + 3, Theme.TEXT, !Theme.light);
        }
    }

    /** Weltposition → Bildschirm; liefert false, wenn der Punkt außerhalb liegt (dann an den Rand geklemmt). */
    private boolean project(double wx, double wz, int inset) {
        float dx = (float) (wx - px) * scale, dz = (float) (wz - pz) * scale;
        float sx = dx * cos - dz * sin, sy = dx * sin + dz * cos;
        float limit = half - inset;
        float max = Math.max(Math.abs(sx), Math.abs(sy));
        boolean inside = max <= limit;
        if (!inside) {
            sx *= limit / max;
            sy *= limit / max;
        }
        projected[0] = centerX + sx;
        projected[1] = centerY + sy;
        return inside;
    }

    private void drawEntities(GuiGraphics g, Minecraft mc, LocalPlayer self) {
        if (mc.level == null) return;
        double reach = minimap.radius() * 1.5;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == self || !(entity instanceof LivingEntity) || entity.isInvisible()) continue;
            if (Math.abs(entity.getX() - px) > reach || Math.abs(entity.getZ() - pz) > reach) continue;
            int color;
            if (entity instanceof Player) {
                if (!minimap.players.get()) continue;
                color = EntityEsp.PLAYER_COLOR;
            } else {
                if (!minimap.mobs.get()) continue;
                color = entity instanceof Enemy ? EntityEsp.MONSTER_COLOR : EntityEsp.ANIMAL_COLOR;
            }
            if (!project(entity.getX(), entity.getZ(), 2)) continue;
            int ex = Math.round(projected[0]), ey = Math.round(projected[1]);
            boolean player = entity instanceof Player;
            int r = player ? 2 : 1;
            g.fill(ex - r - 1, ey - r - 1, ex + r + 1, ey + r + 1, 0xC0000000);
            g.fill(ex - r, ey - r, ex + r, ey + r, color);
        }
    }

    private void drawWaypoints(GuiGraphics g, Font font) {
        String dimension = TeleportService.currentDimension();
        Waypoint target = WaypointStore.target();
        for (Waypoint waypoint : WaypointStore.all()) {
            if (waypoint == target || !dimension.equals(waypoint.dimension)) continue;
            boolean inside = project(waypoint.x, waypoint.z, 5);
            marker(g, font, waypoint.icon(), waypoint.color(), inside ? 1F : 0.6F, false);
        }
        // Ziel zuletzt (obenauf), in der anderen Dimension am umgerechneten Portal-Punkt.
        Navigation.Point point = Navigation.point(target, dimension);
        if (point != null) {
            project(point.x(), point.z(), 6);
            marker(g, font, point.portal() ? "⇄" : target.icon(), target.color(), 1F, true);
        }
    }

    private void marker(GuiGraphics g, Font font, String icon, int color, float alpha, boolean highlight) {
        int cx = Math.round(projected[0]), cy = Math.round(projected[1]);
        if (highlight) UiRender.roundRect(g, cx - 6, cy - 6, 13, 13, 6, Theme.alpha(Theme.ACCENT, 0.85F));
        UiRender.roundRect(g, cx - 5, cy - 5, 11, 11, 5, Theme.alpha(0xD0000000, alpha));
        g.drawString(font, icon, cx - font.width(icon) / 2 + 1, cy - 3, Theme.alpha(color | 0xFF000000, alpha), false);
    }
}
