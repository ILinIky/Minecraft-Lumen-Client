package com.example.examplemod;

import com.example.examplemod.blueprint.BlueprintWriter;
import com.example.examplemod.gui.BlueprintScreen;
import com.example.examplemod.module.ModuleManager;
import net.minecraft.core.BlockPos;
import com.example.examplemod.gui.ChestSearchScreen;
import com.example.examplemod.gui.Notifications;
import com.example.examplemod.gui.WaypointScreen;
import com.example.examplemod.waypoint.TeleportService;
import com.example.examplemod.waypoint.Waypoint;
import com.example.examplemod.waypoint.WaypointStore;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Chat-Befehle für Wegpunkte. Die bisherigen Befehle bleiben erhalten:
 * /tpmanager <id|name>, /tpmanager delete <id>, /tpmanager back, /tpname <name>, /tpadd <x> <y> <z> [name], /wp
 * Dazu /wp ziel <id|name> und /wp ziel aus für das Kompass-Ziel, /bauplan [ecke1|ecke2|speichern <Name>]
 * und /kiste [Suche].
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ClientCommandHandler {

    @SubscribeEvent
    public static void registerCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("tpmanager")
                .then(Commands.literal("delete")
                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                .executes(context -> delete(IntegerArgumentType.getInteger(context, "id")))))
                .then(Commands.literal("back").executes(context -> TeleportService.teleportBack() ? 1 : 0))
                .then(Commands.literal("list").executes(context -> openMenu()))
                .then(Commands.argument("target", StringArgumentType.greedyString())
                        .suggests((context, builder) -> {
                            for (Waypoint waypoint : WaypointStore.all()) builder.suggest(String.valueOf(waypoint.id));
                            return builder.buildFuture();
                        })
                        .executes(context -> teleport(StringArgumentType.getString(context, "target")))));

        dispatcher.register(Commands.literal("wp").executes(context -> openMenu())
                .then(Commands.literal("ziel")
                        .then(Commands.literal("aus").executes(context -> clearTarget()))
                        .then(Commands.argument("waypoint", StringArgumentType.greedyString())
                                .suggests((context, builder) -> {
                                    for (Waypoint waypoint : WaypointStore.all()) builder.suggest(waypoint.displayName());
                                    return builder.buildFuture();
                                })
                                .executes(context -> setTarget(StringArgumentType.getString(context, "waypoint"))))));

        dispatcher.register(Commands.literal("bauplan").executes(context -> {
                    Minecraft mc = Minecraft.getInstance();
                    mc.schedule(() -> mc.setScreen(new BlueprintScreen(null)));
                    return 1;
                })
                .then(Commands.literal("ecke1").executes(context -> blueprintCorner(1)))
                .then(Commands.literal("ecke2").executes(context -> blueprintCorner(2)))
                .then(Commands.literal("speichern")
                        .executes(context -> saveBlueprint(""))
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .executes(context -> saveBlueprint(StringArgumentType.getString(context, "name"))))));

        dispatcher.register(Commands.literal("kiste")
                .executes(context -> openChestSearch(""))
                .then(Commands.argument("suche", StringArgumentType.greedyString())
                        .executes(context -> openChestSearch(StringArgumentType.getString(context, "suche")))));

        dispatcher.register(Commands.literal("tpname")
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(context -> saveHere(StringArgumentType.getString(context, "name")))));

        RequiredArgumentBuilder<CommandSourceStack, Double> z = Commands.argument("z", DoubleArgumentType.doubleArg())
                .executes(context -> addCoordinates(context, null))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(context -> addCoordinates(context, StringArgumentType.getString(context, "name"))));
        dispatcher.register(Commands.literal("tpadd")
                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg()).then(z))));
    }

    /** Ecke der Bauplan-Aufnahme: anvisierter Block, sonst die eigene Position. */
    private static int blueprintCorner(int which) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ModuleManager.BLUEPRINTS == null) return 0;
        BlockPos aimed = ModuleManager.BLUEPRINTS.aimedBlock();
        ModuleManager.BLUEPRINTS.setCorner(which, aimed != null ? aimed : mc.player.blockPosition());
        return 1;
    }

    private static int saveBlueprint(String name) {
        if (ModuleManager.BLUEPRINTS == null) return 0;
        ModuleManager.BLUEPRINTS.saveSelection(name, BlueprintWriter.Format.LITEMATIC);
        return 1;
    }

    private static int openChestSearch(String query) {
        Minecraft mc = Minecraft.getInstance();
        mc.schedule(() -> mc.setScreen(new ChestSearchScreen(null, query)));
        return 1;
    }

    private static int openMenu() {
        Minecraft mc = Minecraft.getInstance();
        // Nach dem Befehl schließt sich der Chat; das Menü daher erst im nächsten Tick öffnen.
        mc.schedule(() -> mc.setScreen(new WaypointScreen(null)));
        return 1;
    }

    private static int teleport(String target) {
        Waypoint waypoint = WaypointStore.find(target);
        if (waypoint == null) {
            Notifications.error("Teleport", "Wegpunkt \"" + target + "\" nicht gefunden");
            return 0;
        }
        return TeleportService.teleport(waypoint) ? 1 : 0;
    }

    private static int setTarget(String name) {
        Waypoint waypoint = WaypointStore.find(name);
        if (waypoint == null) {
            Notifications.error("Kompass", "Wegpunkt \"" + name + "\" nicht gefunden");
            return 0;
        }
        WaypointStore.setTarget(waypoint);
        Notifications.info("Kompass", "Ziel: " + waypoint.displayName());
        return 1;
    }

    private static int clearTarget() {
        WaypointStore.setTarget(null);
        Notifications.info("Kompass", "Ziel entfernt");
        return 1;
    }

    private static int delete(int id) {
        Waypoint waypoint = WaypointStore.find(id);
        if (waypoint == null || !WaypointStore.remove(id)) {
            Notifications.error("Wegpunkte", "#" + id + " nicht gefunden");
            return 0;
        }
        Notifications.info("Wegpunkt gelöscht", waypoint.displayName());
        return 1;
    }

    private static int saveHere(String name) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0;
        String trimmed = name.trim();
        if (trimmed.length() > 32) {
            Notifications.error("Wegpunkte", "Name zu lang (max. 32 Zeichen)");
            return 0;
        }
        Waypoint waypoint = WaypointStore.add(trimmed.equalsIgnoreCase("skip") || trimmed.isEmpty() ? null : trimmed,
                player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot(),
                TeleportService.currentDimension());
        Notifications.success("Wegpunkt gespeichert", waypoint.displayName());
        return 1;
    }

    private static int addCoordinates(CommandContext<CommandSourceStack> context, String name) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return 0;
        if (name != null && name.length() > 32) {
            Notifications.error("Wegpunkte", "Name zu lang (max. 32 Zeichen)");
            return 0;
        }
        Waypoint waypoint = WaypointStore.add(name,
                DoubleArgumentType.getDouble(context, "x"),
                DoubleArgumentType.getDouble(context, "y"),
                DoubleArgumentType.getDouble(context, "z"),
                player.getYRot(), player.getXRot(), TeleportService.currentDimension());
        Notifications.success("Wegpunkt gespeichert", waypoint.displayName());
        return 1;
    }
}
