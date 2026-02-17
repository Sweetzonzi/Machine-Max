package io.github.sweetzonzi.machine_max.client;

import com.jme3.system.JmeSystem;
import com.jme3.system.Platform;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.input.CameraController;
import io.github.sweetzonzi.machine_max.client.input.NativeKeyListener;
import io.github.sweetzonzi.machine_max.common.registry.MMVisualEffects;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
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
        NativeKeyListener.setUp();
        MachineMax.LOGGER.debug("Client setup complete");
    }
}
