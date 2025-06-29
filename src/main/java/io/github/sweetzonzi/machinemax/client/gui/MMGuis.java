package io.github.sweetzonzi.machinemax.client.gui;

import io.github.sweetzonzi.machinemax.MachineMax;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import static io.github.sweetzonzi.machinemax.common.registry.MMMenus.FABRICATING_MENU;
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class MMGuis {
    @SubscribeEvent
    public static void registerHud(RegisterGuiLayersEvent event){
        event.registerAboveAll(id("custom_hud"), new CustomHud());
    }

    private static ResourceLocation id(String path){
        return ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, path);
    }

    @SubscribeEvent
    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(FABRICATING_MENU.get(), FabricatingScreen::new);
    }
}
