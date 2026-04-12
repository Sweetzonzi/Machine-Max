package io.github.sweetzonzi.machine_max.common.registry

import cn.solarmoon.spark_core.event.SparkPackageReaderRegisterEvent
import io.github.sweetzonzi.machine_max.common.resource.modules.*
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent

object MMPackModuleRegistries {
    fun reg(event: SparkPackageReaderRegisterEvent) {
        event.register(ColorModule())//自定义色板
        event.register(HudModule())//自定义HUD元素
        event.register(TooltipModule())//自定义描述信息
        event.register(SubsystemModule())//子系统型号
        event.register(ConnectorModule())//连接点类型
        event.register(MaterialModule())//材料类型
        event.register(PartModule())//自定义部件
        event.register(TemplateModule())//预装配结构体模板
        event.register(BlueprintModule())//自定义蓝图
        event.register(AssemblyModule())//自定义装配体
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