package io.github.tt432.machinemax.client.gui;

import io.github.tt432.machinemax.MachineMax;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public class MMGuiHandler {
    @SubscribeEvent
    public static void registerHud(RegisterGuiLayersEvent event){
        event.registerAboveAll(id("assembly"), new AssemblyHud());
        event.registerAboveAll(id("compass"), new CompassHud());
    }

    private static ResourceLocation id(String path){
        return ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, path);
    }
}
