package io.github.tt432.machinemax.common.registry;

import io.github.tt432.machinemax.MachineMax;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.NewRegistryEvent;

/**
 * 注册器注册
 * @author Sweetzonzi
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class MMRegistries {
    @SubscribeEvent
    public static void registerRegistries(NewRegistryEvent event){
        event.register(PartRegistry.PART_REGISTRY);
    }
}
