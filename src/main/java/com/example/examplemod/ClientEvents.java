package com.example.examplemod;

import com.example.examplemod.feature.Freecam;
import com.example.examplemod.feature.Fullbright;
import com.example.examplemod.feature.NoFall;
import com.example.examplemod.chest.ChestStore;
import com.example.examplemod.gui.BlueprintScreen;
import com.example.examplemod.gui.ChestSearchScreen;
import com.example.examplemod.gui.ClickGuiScreen;
import com.example.examplemod.gui.Notifications;
import com.example.examplemod.gui.WaypointEditScreen;
import com.example.examplemod.gui.WaypointScreen;
import com.example.examplemod.mixin.ClientInputAccessor;
import com.example.examplemod.module.ModuleManager;
import com.example.examplemod.waypoint.Navigation;
import com.example.examplemod.waypoint.TeleportService;
import com.example.examplemod.waypoint.Waypoint;
import com.example.examplemod.waypoint.WaypointStore;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public class ClientEvents {

    /** Vor Minecrafts eigener Tastenverarbeitung: Mittelklick auf einen Bauplan-Block abfangen. */
    @SubscribeEvent
    public static void onClientTickStart(TickEvent.ClientTickEvent.Pre event) {
        if (ModuleManager.isReady() && ModuleManager.BLUEPRINTS != null) ModuleManager.BLUEPRINTS.handlePickKey();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        if (!ModuleManager.isReady()) return;
        Minecraft mc = Minecraft.getInstance();

        TeleportService.onClientTick();

        while (ClientKeybinds.MENU.consumeClick()) {
            if (mc.screen == null) mc.setScreen(new ClickGuiScreen(null));
        }
        while (ClientKeybinds.WAYPOINTS.consumeClick()) {
            if (mc.screen == null && mc.player != null) mc.setScreen(new WaypointScreen(null));
        }
        while (ClientKeybinds.SAVE_WAYPOINT.consumeClick()) {
            if (mc.screen == null && mc.player != null) mc.setScreen(WaypointEditScreen.forCurrentPosition(null));
        }
        while (ClientKeybinds.LAST_TELEPORT.consumeClick()) {
            if (mc.player != null) TeleportService.teleportLast();
        }
        while (ClientKeybinds.BLUEPRINTS.consumeClick()) {
            if (mc.screen == null && mc.player != null) mc.setScreen(new BlueprintScreen(null));
        }
        while (ClientKeybinds.BLUEPRINT_LAYER_UP.consumeClick()) {
            if (mc.player != null) ModuleManager.BLUEPRINTS.stepLayer(1);
        }
        while (ClientKeybinds.BLUEPRINT_LAYER_DOWN.consumeClick()) {
            if (mc.player != null) ModuleManager.BLUEPRINTS.stepLayer(-1);
        }
        boolean chestSearch = ModuleManager.CHEST_MEMORY != null && ModuleManager.CHEST_MEMORY.consumeSearchRequest();
        while (ClientKeybinds.CHEST_SEARCH.consumeClick()) chestSearch = true;
        if (chestSearch && mc.screen == null && mc.player != null) mc.setScreen(new ChestSearchScreen(null, ""));
        while (ClientKeybinds.BLUEPRINT_INFO.consumeClick()) {
            if (mc.player != null && mc.screen == null) ModuleManager.BLUEPRINTS.onInfoKey();
        }

        if (mc.player != null && mc.level != null) {
            ModuleManager.onTick();
            Fullbright.update();
            checkDeath(mc);
            Navigation.onClientTick(mc);
            ChestStore.tick();
        } else {
            wasDead = false;
        }
    }

    private static boolean wasDead;

    /** Beim Tod einen Wegpunkt anlegen und als Kompass-Ziel setzen. */
    private static void checkDeath(Minecraft mc) {
        boolean dead = mc.player.isDeadOrDying();
        if (dead && !wasDead && ModuleManager.WAYPOINTS.saveDeaths.get() && WaypointStore.isLoaded()) {
            Waypoint death = WaypointStore.addDeath(mc.player.getX(), mc.player.getY(), mc.player.getZ(),
                    TeleportService.currentDimension(), ModuleManager.WAYPOINTS.keepDeaths.getInt());
            WaypointStore.setTarget(death);
            Notifications.warning("Todespunkt gespeichert", death.displayName() + " · Kompass zeigt dorthin");
        }
        wasDead = dead;
    }

    /** Kisten-Gedächtnis: merken, welcher Behälter gleich geöffnet wird. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() && ModuleManager.CHEST_MEMORY != null && ModuleManager.CHEST_MEMORY.isEnabled()) {
            ModuleManager.CHEST_MEMORY.onInteract(event.getPos());
        }
    }

    /** Blockumrandung in Akzentfarbe statt der Vanilla-Linie. */
    @SubscribeEvent
    public static boolean onBlockHighlight(RenderHighlightEvent.Block event) {
        return ModuleManager.BLOCK_OUTLINE != null && ModuleManager.BLOCK_OUTLINE.render(event);
    }

    /** Tooltips: Haltbarkeit, Sättigung und Entfernen der Shulker-Textliste. */
    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        if (ModuleManager.TOOLTIPS != null) ModuleManager.TOOLTIPS.addLines(event);
    }

    /** Tooltips: Shulker-Inhalt als Raster. */
    @SubscribeEvent
    public static void onGatherTooltip(RenderTooltipEvent.GatherComponents event) {
        if (ModuleManager.TOOLTIPS != null) ModuleManager.TOOLTIPS.addPreview(event);
    }

    /** Zoom: Sichtfeld verkleinern, solange die Zoom-Taste gehalten wird. */
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (ModuleManager.ZOOM == null) return;
        double factor = ModuleManager.ZOOM.updateFactor();
        if (factor > 1.0001) event.setFOV((float) (event.getFOV() / factor));
    }

    /** Zoom: Mausrad ändert die Stärke statt den Hotbar-Platz. */
    @SubscribeEvent
    public static boolean onMouseScroll(InputEvent.MouseScrollingEvent event) {
        return ModuleManager.ZOOM != null && ModuleManager.ZOOM.onScroll(event.getDeltaY());
    }

    /** Modul-Tasten aus dem Client-Menü (nicht zusammen mit F3-Kombinationen). */
    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;
        if (InputConstants.isKeyDown(mc.getWindow().getWindow(), GLFW.GLFW_KEY_F3)) return;
        ModuleManager.onKeyPressed(event.getKey());
    }

    /** Freecam: Spieler bleibt stehen, während WASD die Kamera bewegt. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!Freecam.active()) return;
        ClientInput input = event.getInput();
        input.keyPresses = Input.EMPTY;
        ((ClientInputAccessor) input).examplemod$setMoveVector(Vec2.ZERO);
    }

    /** Freecam: kein Abbauen/Benutzen an der unsichtbaren Spielerposition. */
    @SubscribeEvent
    public static boolean onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        return Freecam.active();
    }

    /** NoFall im Einzelspieler: Fallschaden direkt auf dem integrierten Server verhindern. */
    @SubscribeEvent
    public static boolean onLivingFall(LivingFallEvent event) {
        return event.getEntity() instanceof ServerPlayer player && NoFall.protects(player.getUUID());
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        TeleportService.reset();
        WaypointStore.onJoin();
        ChestStore.onJoin();
        if (ModuleManager.BLUEPRINTS != null) ModuleManager.BLUEPRINTS.restore();
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        TeleportService.reset();
        ModuleManager.onLeaveWorld();
        ChestStore.onLeave();
        WaypointStore.onLeave();
    }
}
