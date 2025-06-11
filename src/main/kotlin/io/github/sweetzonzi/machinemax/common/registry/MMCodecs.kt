package io.github.sweetzonzi.machinemax.common.registry

import io.github.sweetzonzi.machinemax.MachineMax
import io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem.*
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.RegisterEvent

object MMCodecs {
    private fun reg(event: RegisterEvent) {
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("seat")) { SeatSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("resource_storage")) { ResourceStorageSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("engine")) { EngineSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("motor")) {MotorSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("car_controller")) { CarControllerSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("gearbox")) { GearboxSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("transmission")) { TransmissionSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("joint_driver")) { JointDriverSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("wheel_driver")) { WheelDriverSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("turret_driver")) { TurretDriverSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("scriptable")) { ScriptableSubsystemAttr.CODEC}
    }

    private fun id(id: String) = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, id)

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::reg)
    }
}