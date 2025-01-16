package io.github.tt432.machinemax.common.sloarphys.thread;

import cn.solarmoon.spark_core.event.PhysLevelRegisterEvent;
import io.github.tt432.machinemax.MachineMax;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import static io.github.tt432.machinemax.MachineMax.MOD_ID;

public class PhysThreadApplier {
    @EventBusSubscriber(modid = MOD_ID, bus = EventBusSubscriber.Bus.GAME)
    public static class events {

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

        @SubscribeEvent//加载区块时创建或加载地形碰撞箱
        public static void phyThreadMapUpdate(ChunkEvent.Load event) {
//            TerrainBuilder.build(event.getChunk());
            //TODO:根据区块变化更新区块储存的Trimesh数据，以免无限创建新几何体导致内存溢出
            //TODO:优化碰撞时间效率
            //TODO:优化内存占用
            //似乎没有必要了，现场创建Box的效果也不错
        }

        @SubscribeEvent//卸载区块时销毁地形碰撞箱，保存构造数据
        public static void phyThreadMapUnload(ChunkEvent.Unload event) {
            //TODO:随区块卸载销毁Trimesh几何体，但保留构造数据
        }
    }

}
