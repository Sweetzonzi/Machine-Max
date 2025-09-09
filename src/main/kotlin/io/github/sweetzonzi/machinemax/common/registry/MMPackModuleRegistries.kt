package io.github.sweetzonzi.machinemax.common.registry

import cn.solarmoon.spark_core.event.SparkPackageReaderRegisterEvent
import io.github.sweetzonzi.machinemax.common.resource.modules.*
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import net.neoforged.neoforge.common.NeoForge

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
object MMPackModuleRegistries {
    fun reg(event: SparkPackageReaderRegisterEvent) {
        event.register(PartModule())
        event.register(BlueprintModule())
        event.register(HudModule())
    }

    @SubscribeEvent
    @JvmStatic
    fun regReloadListener(event: RegisterClientReloadListenersEvent) {
        //注册reload监听器以确保原版进行reload时重新注入外部包内容
    }

    @JvmStatic
    fun register() {
        NeoForge.EVENT_BUS.addListener(::reg)
    }
}