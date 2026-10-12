package com.example.examplemod.gui.hud;

import com.example.examplemod.feature.Hud;
import com.example.examplemod.feature.Waypoints;
import com.example.examplemod.gui.Theme;
import com.example.examplemod.gui.UiRender;
import com.example.examplemod.module.Module;
import com.example.examplemod.module.ModuleManager;
import com.example.examplemod.waypoint.Navigation;
import com.example.examplemod.waypoint.TeleportService;
import com.example.examplemod.waypoint.Waypoint;
import com.example.examplemod.waypoint.WaypointStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Die einzelnen HUD-Elemente. */
public final class HudElements {
    private HudElements() {}

    public static List<HudElement> create(Hud hud) {
        return List.of(new Watermark(hud), new Coordinates(hud), new Info(hud), new ModuleList(hud),
                new Compass(hud), new Armor(hud), new MinimapElement(ModuleManager.MINIMAP));
    }

    private static boolean shadow() { return !Theme.light; }

    private static void panel(GuiGraphics g, int x, int y, int w, int h) {
        UiRender.roundRect(g, x, y, w, h, 4, Theme.HUD_BG);
    }

    // ---- Logo + FPS ----
    static final class Watermark extends HudElement {
        private final Hud hud;

        Watermark(Hud hud) {
            super("watermark", "Logo", hud.watermark, 0, 0, 4, 4);
            this.hud = hud;
        }

        private String fpsText(Minecraft mc) { return hud.fps.get() ? mc.getFps() + " FPS" : null; }

        @Override
        public void measure(Minecraft mc, boolean editing) {
            String fps = fpsText(mc);
            w = mc.font.width(Theme.CLIENT_NAME) + 12 + (fps != null ? mc.font.width(fps) + 10 : 0);
            h = 15;
        }

        @Override
        public void render(GuiGraphics g, Minecraft mc, boolean editing, float delta) {
            Font font = mc.font;
            panel(g, x, y, w, h);
            UiRender.gradientH(g, x + 2, y, w - 4, 1, Theme.ACCENT, Theme.ACCENT_2);
            int cx = x + 6;
            String name = Theme.CLIENT_NAME;
            for (int i = 0; i < name.length(); i++) {
                String ch = String.valueOf(name.charAt(i));
                g.drawString(font, ch, cx, y + 4, Theme.accentWave(i * 0.06F), shadow());
                cx += font.width(ch);
            }
            String fps = fpsText(mc);
            if (fps != null) {
                g.fill(cx + 4, y + 3, cx + 5, y + 12, Theme.alpha(Theme.TEXT_MUTED, 0.5F));
                g.drawString(font, fps, cx + 9, y + 4, Theme.TEXT_DIM, shadow());
            }
        }
    }

    // ---- Koordinaten ----
    static final class Coordinates extends HudElement {
        private final Hud hud;
        private final List<String[]> lines = new ArrayList<>();
        private int labelW;

        Coordinates(Hud hud) {
            super("coordinates", "Koordinaten", hud.coordinates, 0, 0, 4, 23);
            this.hud = hud;
        }

        @Override
        public void measure(Minecraft mc, boolean editing) {
            lines.clear();
            LocalPlayer player = mc.player;
            if (player == null) { w = h = 0; return; }
            lines.add(new String[]{"XYZ", String.format(Locale.ROOT, "%.1f / %.0f / %.1f", player.getX(), player.getY(), player.getZ())});
            if (hud.netherCoordinates.get() && mc.level != null) {
                String dimension = mc.level.dimension().location().getPath();
                if (dimension.equals("overworld")) {
                    lines.add(new String[]{"Nether", String.format(Locale.ROOT, "%.0f / %.0f", player.getX() / 8, player.getZ() / 8)});
                } else if (dimension.equals("the_nether")) {
                    lines.add(new String[]{"Oberwelt", String.format(Locale.ROOT, "%.0f / %.0f", player.getX() * 8, player.getZ() * 8)});
                }
            }
            lines.add(new String[]{"Richtung", switch (player.getDirection()) {
                case NORTH -> "Nord (-Z)";
                case SOUTH -> "Süd (+Z)";
                case EAST -> "Ost (+X)";
                case WEST -> "West (-X)";
                default -> "-";
            }});
            labelW = 0;
            int valueW = 0;
            for (String[] line : lines) {
                labelW = Math.max(labelW, mc.font.width(line[0]));
                valueW = Math.max(valueW, mc.font.width(line[1]));
            }
            w = labelW + valueW + 18;
            h = lines.size() * 11 + 6;
        }

        @Override
        public void render(GuiGraphics g, Minecraft mc, boolean editing, float delta) {
            panel(g, x, y, w, h);
            int ly = y + 4;
            for (String[] line : lines) {
                g.drawString(mc.font, line[0], x + 6, ly, Theme.ACCENT_2, shadow());
                g.drawString(mc.font, line[1], x + 12 + labelW, ly, Theme.TEXT, shadow());
                ly += 11;
            }
        }
    }

    // ---- Ping, Uhrzeit, CPS ----
    static final class Info extends HudElement {
        private final Hud hud;
        private final List<String[]> lines = new ArrayList<>();
        private final List<Integer> colors = new ArrayList<>();
        private int labelW;

        Info(Hud hud) {
            super("info", "Infos", hud.info, 0, 0, 4, 69);
            this.hud = hud;
        }

        @Override
        public void measure(Minecraft mc, boolean editing) {
            lines.clear();
            colors.clear();
            if (mc.player == null || mc.level == null) { w = h = 0; return; }
            if (hud.ping.get()) {
                PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
                int ping = info == null ? 0 : info.getLatency();
                lines.add(new String[]{"Ping", mc.hasSingleplayerServer() ? "lokal" : ping + " ms"});
                colors.add(ping < 80 ? Theme.SUCCESS : ping < 180 ? Theme.WARNING : Theme.DANGER);
            }
            if (hud.time.get()) {
                long day = mc.level.getDayTime() % 24000L;
                int hours = (int) ((day / 1000 + 6) % 24);
                int minutes = (int) (day % 1000 * 60 / 1000);
                lines.add(new String[]{"Zeit", String.format(Locale.ROOT, "%02d:%02d", hours, minutes)});
                colors.add(day >= 13000 && day < 23000 ? Theme.INFO : Theme.WARNING);
            }
            if (hud.cps.get()) {
                lines.add(new String[]{"CPS", Hud.cps(0) + " | " + Hud.cps(1)});
                colors.add(Theme.TEXT);
            }
            if (lines.isEmpty()) { w = h = 0; return; }
            labelW = 0;
            int valueW = 0;
            for (String[] line : lines) {
                labelW = Math.max(labelW, mc.font.width(line[0]));
                valueW = Math.max(valueW, mc.font.width(line[1]));
            }
            w = labelW + valueW + 18;
            h = lines.size() * 11 + 6;
        }

        @Override
        public void render(GuiGraphics g, Minecraft mc, boolean editing, float delta) {
            panel(g, x, y, w, h);
            int ly = y + 4;
            for (int i = 0; i < lines.size(); i++) {
                g.drawString(mc.font, lines.get(i)[0], x + 6, ly, Theme.ACCENT_2, shadow());
                g.drawString(mc.font, lines.get(i)[1], x + 12 + labelW, ly, colors.get(i), shadow());
                ly += 11;
            }
        }
    }

    // ---- Aktive Module ----
    static final class ModuleList extends HudElement {
        private final List<Module> shown = new ArrayList<>();
        private final List<Integer> widths = new ArrayList<>();
        private boolean sample;

        ModuleList(Hud hud) {
            super("modules", "Modulliste", hud.moduleList, 1, 0, 4, 4);
        }

        private static String label(Module module) {
            String info = module.getHudInfo();
            return info == null ? module.getName() : module.getName() + " " + info;
        }

        @Override
        protected int extraOffsetY(Minecraft mc, boolean editing) {
            // Oben rechts nicht über die Vanilla-Effektsymbole zeichnen.
            if (editing || anchorX < 1 || anchorY > 0 || offsetY > 52 || mc.player == null) return 0;
            boolean beneficial = false, harmful = false;
            for (MobEffectInstance effect : mc.player.getActiveEffects()) {
                if (!effect.showIcon()) continue;
                if (effect.getEffect().value().isBeneficial()) beneficial = true; else harmful = true;
            }
            return (beneficial ? 25 : 0) + (harmful ? 26 : 0);
        }

        @Override
        public void measure(Minecraft mc, boolean editing) {
            shown.clear();
            widths.clear();
            Font font = mc.font;
            float delta = HudRenderer.frameDelta();
            for (Module module : ModuleManager.all()) {
                if (!module.showInModuleList()) continue;
                module.hudAnimation = UiRender.approach(module.hudAnimation, module.isEnabled() ? 1 : 0, delta, 5);
                if (module.hudAnimation > 0.01F) shown.add(module);
            }
            shown.sort(Comparator.comparingInt((Module m) -> font.width(label(m))).reversed());
            sample = shown.isEmpty() && editing;
            if (sample) {
                w = font.width("Beispiel-Modul") + 10;
                h = 12;
                return;
            }
            w = 0;
            h = 0;
            for (Module module : shown) {
                int width = font.width(label(module)) + 10;
                widths.add(width);
                w = Math.max(w, width);
                h += Math.round(12 * UiRender.ease(module.hudAnimation));
            }
        }

        @Override
        public void render(GuiGraphics g, Minecraft mc, boolean editing, float delta) {
            Font font = mc.font;
            if (sample) {
                g.fill(x, y, x + w, y + h, Theme.HUD_BG);
                g.drawString(font, "Beispiel-Modul", x + 5, y + 2, Theme.TEXT_DIM, shadow());
                return;
            }
            boolean right = alignRight();
            int rowY = y;
            for (int i = 0; i < shown.size(); i++) {
                Module module = shown.get(i);
                float anim = UiRender.ease(module.hudAnimation);
                int width = widths.get(i);
                int rowH = Math.round(12 * anim);
                if (rowH <= 0) continue;
                int slide = Math.round(width * (1 - anim));
                int rx = right ? x + w - width + slide : x - slide;
                g.fill(rx, rowY, rx + width, rowY + rowH, Theme.alpha(Theme.HUD_BG, anim));
                int accent = Theme.alpha(Theme.accentWave(i * 0.08F), anim);
                if (right) g.fill(rx + width - 2, rowY, rx + width, rowY + rowH, accent);
                else g.fill(rx, rowY, rx + 2, rowY + rowH, accent);
                if (rowH >= 10) {
                    int tx = right ? rx + 4 : rx + 6;
                    g.drawString(font, module.getName(), tx, rowY + 2, Theme.alpha(Theme.TEXT, anim), shadow());
                    String info = module.getHudInfo();
                    if (info != null) {
                        g.drawString(font, info, tx + font.width(module.getName()) + 4, rowY + 2,
                                Theme.alpha(Theme.TEXT_MUTED, anim), shadow());
                    }
                }
                rowY += rowH;
            }
        }
    }

    // ---- Kompass zum Ziel-Wegpunkt ----
    static final class Compass extends HudElement {
        private Waypoint target;
        private String line1, line2, line3;
        private boolean unreachable;
        private float relativeYaw;

        Compass(Hud hud) {
            super("compass", "Kompass", hud.compass, 0.5F, 0, 0, 4);
        }

        @Override
        public void measure(Minecraft mc, boolean editing) {
            target = WaypointStore.target();
            LocalPlayer player = mc.player;
            if (player == null || (target == null && !editing)) { w = h = 0; return; }
            line3 = null;
            if (target == null) {
                line1 = "◆ Kein Ziel";
                line2 = "Ziel im Wegpunkt-Menü wählen";
                unreachable = false;
                relativeYaw = 0;
            } else {
                line1 = target.icon() + " " + target.displayName();
                String dimension = TeleportService.currentDimension();
                Navigation.Point point = Navigation.point(target, dimension);
                unreachable = point == null;
                if (unreachable) {
                    line2 = "im " + target.dimensionName();
                } else {
                    double dx = point.x() - player.getX(), dz = point.z() - player.getZ();
                    double dy = point.y() - player.getY();
                    float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    relativeYaw = Mth.wrapDegrees(targetYaw - player.getYRot());
                    String distance = Waypoints.formatDistance(Navigation.distance(point, player.getX(), player.getY(), player.getZ()));
                    if (point.portal()) {
                        line2 = Navigation.portalLabel(dimension) + " · " + distance;
                        line3 = Navigation.portalHint(point);
                    } else {
                        String height = Math.abs(dy) < 2 ? "" : (dy > 0 ? "  ↑" : "  ↓") + (int) Math.abs(dy);
                        line2 = distance + height;
                    }
                }
            }
            int textW = Math.max(mc.font.width(line1), mc.font.width(line2));
            if (line3 != null) textW = Math.max(textW, mc.font.width(line3));
            w = 30 + textW + 6;
            h = line3 == null ? 26 : 37;
        }

        @Override
        public void render(GuiGraphics g, Minecraft mc, boolean editing, float delta) {
            panel(g, x, y, w, h);
            int color = target != null ? target.color() : Theme.ACCENT;
            int cx = x + 14, cy = y + 13;
            UiRender.roundRect(g, cx - 10, cy - 10, 20, 20, 10, Theme.alpha(color, 0.18F));
            if (unreachable) {
                g.drawString(mc.font, "✕", cx - 3, cy - 4, Theme.TEXT_MUTED, false);
            } else {
                g.pose().pushMatrix();
                g.pose().translate(cx, cy);
                g.pose().rotate((float) Math.toRadians(relativeYaw));
                g.pose().scale(1.6F, 1.6F);
                g.drawString(mc.font, "▲", -mc.font.width("▲") / 2, -4, color, false);
                g.pose().popMatrix();
            }
            g.drawString(mc.font, line1, x + 30, y + 4, Theme.TEXT, shadow());
            g.drawString(mc.font, line2, x + 30, y + 15, Theme.TEXT_DIM, shadow());
            if (line3 != null) g.drawString(mc.font, line3, x + 30, y + 26, Theme.ACCENT_2, shadow());
        }
    }

    // ---- Rüstung und Werkzeug mit Haltbarkeit ----
    static final class Armor extends HudElement {
        private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
        private final List<ItemStack> items = new ArrayList<>();
        private int textW;

        Armor(Hud hud) {
            super("armor", "Rüstung", hud.armor, 1, 1, 4, 4);
        }

        @Override
        public void measure(Minecraft mc, boolean editing) {
            items.clear();
            LocalPlayer player = mc.player;
            if (player == null) { w = h = 0; return; }
            for (EquipmentSlot slot : SLOTS) {
                ItemStack stack = player.getItemBySlot(slot);
                if (stack.isEmpty()) continue;
                boolean hand = slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND;
                if (hand && !stack.isDamageableItem()) continue;
                items.add(stack);
            }
            if (items.isEmpty() && editing) items.add(new ItemStack(Items.DIAMOND_CHESTPLATE));
            if (items.isEmpty()) { w = h = 0; return; }
            textW = 0;
            for (ItemStack stack : items) textW = Math.max(textW, mc.font.width(durabilityText(stack)));
            w = 16 + 10 + textW;
            h = items.size() * 17 + 2;
        }

        private static String durabilityText(ItemStack stack) {
            if (!stack.isDamageableItem()) return stack.getCount() > 1 ? String.valueOf(stack.getCount()) : "";
            return String.valueOf(stack.getMaxDamage() - stack.getDamageValue());
        }

        private static int durabilityColor(ItemStack stack) {
            if (!stack.isDamageableItem()) return Theme.TEXT;
            float ratio = 1 - stack.getDamageValue() / (float) stack.getMaxDamage();
            return ratio > 0.5F ? Theme.SUCCESS : ratio > 0.2F ? Theme.WARNING : Theme.DANGER;
        }

        @Override
        public void render(GuiGraphics g, Minecraft mc, boolean editing, float delta) {
            panel(g, x, y, w, h);
            boolean right = alignRight();
            int rowY = y + 1;
            for (ItemStack stack : items) {
                int itemX = right ? x + w - 18 : x + 2;
                g.renderItem(stack, itemX, rowY);
                String text = durabilityText(stack);
                int tx = right ? itemX - 4 - mc.font.width(text) : itemX + 20;
                g.drawString(mc.font, text, tx, rowY + 4, durabilityColor(stack), shadow());
                rowY += 17;
            }
        }
    }
}
