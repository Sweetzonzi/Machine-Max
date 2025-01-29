package io.github.tt432.machinemax.common.registry;

import io.github.tt432.machinemax.MachineMax;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.NewRegistryEvent;

/**
 * 注册器注册
 * @author Sweetzonzi
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class MMRegistries {
    public static final ResourceKey<Registry<PartType>> PART_REGISTRY_KEY = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part_type"));
    @SubscribeEvent
    public static void registerRegistries(NewRegistryEvent event){
        event.register(PartType.PART_REGISTRY);
        event.register(PartPortType.PART_PORT_REGISTRY);
    }
}
