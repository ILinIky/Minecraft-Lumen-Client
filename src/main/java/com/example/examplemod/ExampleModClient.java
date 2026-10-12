package com.example.examplemod;

import com.example.examplemod.gui.ClickGuiScreen;
import com.example.examplemod.gui.ContainerPreview;
import com.example.examplemod.module.ModuleManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ExampleModClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Module erst auf dem Hauptthread anlegen, dort sind Registries und Optionen sicher verfügbar.
        event.enqueueWork(ModuleManager::init);
        ModList.get().getModContainerById(ExampleMod.MODID).ifPresent(container ->
                container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class, () ->
                        new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new ClickGuiScreen(parent))));
    }

    @SubscribeEvent
    public static void onRegisterTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(ContainerPreview.class, ContainerPreview.Client::new);
    }
}
