package io.github.sweetzonzi.machinemax.common.registry;

import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.vehicle.PartType;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

/**
 * 注册器注册
 *
 * @author Sweetzonzi
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class MMRegistries {

    @SubscribeEvent//数据包注册
    public static void registerDataPackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(
                PartType.PART_REGISTRY_KEY,
                PartType.CODEC,
                PartType.CODEC
        );
    }

    public static RegistryAccess getRegistryAccess(Level level) {
        return level.registryAccess();
    }

}
