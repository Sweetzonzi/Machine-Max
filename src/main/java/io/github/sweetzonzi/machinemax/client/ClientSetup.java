package io.github.sweetzonzi.machinemax.client;

import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.client.input.CameraController;
import io.github.sweetzonzi.machinemax.common.registry.MMVisualEffects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = MachineMax.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void init(FMLClientSetupEvent event) {
        CameraController.init();
        MMVisualEffects.init();
        MachineMax.LOGGER.debug("Client setup complete");
    }
}
