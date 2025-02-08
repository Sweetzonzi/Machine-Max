package io.github.tt432.machinemax.common.phys.thread;

import cn.solarmoon.spark_core.event.PhysLevelRegisterEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import static io.github.tt432.machinemax.MachineMax.MOD_ID;

@EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class PhysThreadApplier {

    @SubscribeEvent//注册物理引擎线程
    private static void registerPhysThread(PhysLevelRegisterEvent event) {
        var level = event.getLevel();
        if (!level.isClientSide)
            event.register(new MMServerPhysLevel(ResourceLocation.fromNamespaceAndPath(MOD_ID, "main"), "MachineMax Phys Thread - Server", (ServerLevel) level, 20, false));
        else
            event.register(new MMClientPhysLevel(ResourceLocation.fromNamespaceAndPath(MOD_ID, "main"), "MachineMax Phys Thread - Client", (ClientLevel) level, 20, false));
    }

    @SubscribeEvent//TODO:根据主线程同步物理引擎线程时间
    public static void physThreadSynchronize(ServerTickEvent.Pre event) {

    }


}
