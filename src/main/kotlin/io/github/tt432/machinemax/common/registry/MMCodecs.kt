package io.github.tt432.machinemax.common.registry

import io.github.tt432.machinemax.MachineMax
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.ResourceStorageSubsystemAttr
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.SeatSubsystemAttr
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.RegisterEvent

object MMCodecs {
    private fun reg(event: RegisterEvent) {
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("seat")) { SeatSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("resource_storage")) { ResourceStorageSubsystemAttr.CODEC}
    }

    private fun id(id: String) = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, id)

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::reg)
    }
}