package io.github.sweetzonzi.machinemax.common.registry

import cn.solarmoon.spark_core.event.SparkPackageReaderRegisterEvent
import io.github.sweetzonzi.machinemax.common.resource.modules.PartModule
import io.github.sweetzonzi.machinemax.common.resource.modules.TextureModule
import net.neoforged.neoforge.common.NeoForge

object MMPackModuleRegistries {
    fun reg(event: SparkPackageReaderRegisterEvent) {
        event.register(TextureModule())
        event.register(PartModule())
    }

    @JvmStatic
    fun register() {
        NeoForge.EVENT_BUS.addListener(::reg)
    }
}