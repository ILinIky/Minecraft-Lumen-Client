package com.example.examplemod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Feste Aktionen, änderbar unter Optionen > Steuerung. Tasten zum Ein-/Ausschalten einzelner
 * Module (z.B. Freecam = F) werden im Client-Menü festgelegt.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ClientKeybinds {
    public static final String CATEGORY = "key.categories." + ExampleMod.MODID;

    public static KeyMapping MENU;
    public static KeyMapping WAYPOINTS;
    public static KeyMapping SAVE_WAYPOINT;
    public static KeyMapping LAST_TELEPORT;
    public static KeyMapping ZOOM;
    public static KeyMapping BLUEPRINTS;
    public static KeyMapping BLUEPRINT_LAYER_UP;
    public static KeyMapping BLUEPRINT_LAYER_DOWN;
    public static KeyMapping BLUEPRINT_INFO;
    public static KeyMapping BLUEPRINT_ACCEPT;
    public static KeyMapping BLUEPRINT_HIDE;
    public static KeyMapping BLUEPRINT_BUILD;
    public static KeyMapping CHEST_SEARCH;

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        MENU = createMapping("menu", GLFW.GLFW_KEY_X);
        WAYPOINTS = createMapping("waypoints", GLFW.GLFW_KEY_F6);
        SAVE_WAYPOINT = createMapping("save_waypoint", GLFW.GLFW_KEY_F7);
        LAST_TELEPORT = createMapping("last_teleport", GLFW.GLFW_KEY_F5);
        event.register(MENU);
        event.register(WAYPOINTS);
        event.register(SAVE_WAYPOINT);
        event.register(LAST_TELEPORT);
        ZOOM = createMapping("zoom", GLFW.GLFW_KEY_C);
        event.register(ZOOM);
        BLUEPRINTS = createMapping("blueprints", GLFW.GLFW_KEY_B);
        BLUEPRINT_LAYER_UP = createMapping("blueprint_layer_up", GLFW.GLFW_KEY_PAGE_UP);
        BLUEPRINT_LAYER_DOWN = createMapping("blueprint_layer_down", GLFW.GLFW_KEY_PAGE_DOWN);
        event.register(BLUEPRINTS);
        event.register(BLUEPRINT_LAYER_UP);
        event.register(BLUEPRINT_LAYER_DOWN);
        // Linkes Alt: liegt beim Laufen unter dem Daumen und ist in Vanilla frei.
        BLUEPRINT_INFO = createMapping("blueprint_info", GLFW.GLFW_KEY_LEFT_ALT);
        event.register(BLUEPRINT_INFO);
        // N: in Vanilla frei. Falsch gesetzten Bauplan-Block als gewollte Variante übernehmen.
        BLUEPRINT_ACCEPT = createMapping("blueprint_accept", GLFW.GLFW_KEY_N);
        event.register(BLUEPRINT_ACCEPT);
        BLUEPRINT_HIDE = createMapping("blueprint_hide", GLFW.GLFW_KEY_H);
        event.register(BLUEPRINT_HIDE);
        BLUEPRINT_BUILD = createMapping("blueprint_build", GLFW.GLFW_KEY_G);
        event.register(BLUEPRINT_BUILD);
        CHEST_SEARCH = createMapping("chest_search", GLFW.GLFW_KEY_K);
        event.register(CHEST_SEARCH);
    }

    private static KeyMapping createMapping(String name, int key) {
        return new KeyMapping("key." + ExampleMod.MODID + "." + name,
                KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, key, CATEGORY);
    }
}
