package io.github.tt432.machinemax.common.registry

import cn.solarmoon.spark_core.SparkCore
import io.github.tt432.machinemax.MachineMax
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.RegisterEvent

object MMCodecs {
    private fun reg(event: RegisterEvent) {
//        event.register(MMDataRegistries.SUBSYSTEM_DATA_CODEC.resourceType(), id("engine")) {EngineSybsystem.CODEC}
    }

    private fun id(id: String) = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, id)

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::reg)
    }
}