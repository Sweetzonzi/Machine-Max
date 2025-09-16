package io.github.sweetzonzi.machine_max.common.registry

import cn.solarmoon.spark_core.event.SparkPackageReaderRegisterEvent
import io.github.sweetzonzi.machine_max.common.resource.modules.*
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent
import net.neoforged.neoforge.common.NeoForge

object MMPackModuleRegistries {
    fun reg(event: SparkPackageReaderRegisterEvent) {
        event.register(PartModule())//自定义部件
        event.register(BlueprintModule())//自定义蓝图
        event.register(HudModule())//自定义HUD元素
        event.register(BlueprintInfoModule())//自定义蓝图描述信息
        event.register(ColorModule())//自定义色板
    }

    @JvmStatic
    fun regReloadListener(event: RegisterClientReloadListenersEvent) {
        //注册reload监听器以确保原版进行reload时重新注入外部包内容
    }

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(MMPackModuleRegistries::reg)
        bus.addListener(MMPackModuleRegistries::regReloadListener)
    }
}