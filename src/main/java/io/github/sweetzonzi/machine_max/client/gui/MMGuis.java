package io.github.sweetzonzi.machine_max.client.gui;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.gui.hud.AssemblyHud;
import io.github.sweetzonzi.machine_max.client.gui.hud.AssemblyHud3D;
import io.github.sweetzonzi.machine_max.client.gui.hud.CustomHud;
import io.github.sweetzonzi.machine_max.client.gui.hud.InteractHud;
import io.github.sweetzonzi.machine_max.client.gui.screen.FabricatingScreen;
import io.github.sweetzonzi.machine_max.client.gui.screen.ItemStorageSubsystemScreen;
import io.github.sweetzonzi.machine_max.client.gui.screen.VehicleNamingScreen;
import io.github.sweetzonzi.machine_max.client.renderer.Hud3DRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

import static io.github.sweetzonzi.machine_max.common.registry.MMMenus.*;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class MMGuis {
    @SubscribeEvent
    public static void registerHud(RegisterGuiLayersEvent event){
        event.registerAboveAll(id("custom_hud"), new CustomHud());
        event.registerAboveAll(id("interact_hud"), new InteractHud());
        event.registerAboveAll(id("assembly_hud"), new AssemblyHud());
        Hud3DRenderer.register(new AssemblyHud3D());
    }

    private static ResourceLocation id(String path){
        return ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, path);
    }

    @SubscribeEvent
    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(FABRICATING_MENU.get(), FabricatingScreen::new);
        event.register(VEHICLE_NAMING_MENU.get(), VehicleNamingScreen::new);
        event.register(ITEM_STORAGE_SUBSYSTEM_MENU.get(), ItemStorageSubsystemScreen::new);
    }
}
