package io.github.sweetzonzi.machine_max.client.render.gui;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.render.gui.hud.CustomHud;
import io.github.sweetzonzi.machine_max.client.render.gui.hud.InteractHud;
import io.github.sweetzonzi.machine_max.client.render.gui.hud.ResearchPointHud;
import io.github.sweetzonzi.machine_max.client.render.gui.hud3d.AssemblyHud3D;
import io.github.sweetzonzi.machine_max.client.render.gui.screen.BlueprintResearchScreen;
import io.github.sweetzonzi.machine_max.client.render.gui.screen.FabricatingScreen;
import io.github.sweetzonzi.machine_max.client.render.gui.screen.ItemStorageSubsystemScreen;
import io.github.sweetzonzi.machine_max.client.render.gui.screen.VehicleNamingScreen;
import io.github.sweetzonzi.machine_max.client.render.renderer.Hud3DRenderer;
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
        event.registerAboveAll(id("research_point_hud"), new ResearchPointHud());
//        event.registerAboveAll(id("assembly_hud"), new AssemblyHud());
        Hud3DRenderer.register(new AssemblyHud3D());
    }

    private static ResourceLocation id(String path){
        return ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, path);
    }

    @SubscribeEvent
    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(FABRICATING_MENU.get(), FabricatingScreen::new);
        event.register(BLUEPRINT_RESEARCH_MENU.get(), BlueprintResearchScreen::new);
        event.register(VEHICLE_NAMING_MENU.get(), VehicleNamingScreen::new);
        event.register(ITEM_STORAGE_SUBSYSTEM_MENU.get(), ItemStorageSubsystemScreen::new);
    }
}
