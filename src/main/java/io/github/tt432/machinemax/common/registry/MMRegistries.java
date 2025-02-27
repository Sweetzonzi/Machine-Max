package io.github.tt432.machinemax.common.registry;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.PartType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.level.ServerLevel;
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
        if (level instanceof ServerLevel) return level.registryAccess();
        else return Minecraft.getInstance().getConnection().registryAccess();
    }

}
