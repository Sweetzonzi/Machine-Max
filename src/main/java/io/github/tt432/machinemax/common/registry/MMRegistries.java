package io.github.tt432.machinemax.common.registry;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.PartType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;

/**
 * 注册器注册
 *
 * @author Sweetzonzi
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class MMRegistries {


    @SubscribeEvent//注册器注册
    public static void registerRegistries(NewRegistryEvent event) {
//        event.register(PartType.PART_REGISTRY);
        event.register(PartPortType.PART_PORT_REGISTRY);
    }

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
        else return Minecraft.getInstance().level.registryAccess();
    }
}
