package io.github.sweetzonzi.machine_max.common.registry

import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.BasicSubsystemDynamicAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.BatterySubsystemAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.CarControllerSubsystemAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.EngineSubsystemAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.GearboxSubsystemAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.ItemStorageSubsystemAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.JointDriverSubsystemAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.WeaponControllerSubsystemAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.LauncherSubsystemAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.LightingSubsystemAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.MotorSubsystemAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.MotorbikeControllerSubsystemAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.ScriptableSubsystemAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.SeatSubsystemAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.TransmissionSubsystemAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.TurretDriverSubsystemAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.WheelDriverSubsystemAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.BasicSubsystemStaticAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.BatterySubsystemStaticAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CarControllerSubsystemStaticAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.EngineSubsystemStaticAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.GearboxSubsystemStaticAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.ItemStorageSubsystemStaticAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.JointDriverSubsystemStaticAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.WeaponControllerSubsystemStaticAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.LauncherSubsystemStaticAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.LightingSubsystemStaticAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.MotorbikeControllerSubsystemStaticAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.MotorSubsystemStaticAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.ScriptableSubsystemStaticAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.SeatSubsystemStaticAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.TransmissionSubsystemStaticAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.TurretDriverSubsystemStaticAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.WheelDriverSubsystemStaticAttr
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.RegisterEvent

object MMCodecs {
    private fun reg(event: RegisterEvent) {
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("basic")) { BasicSubsystemDynamicAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("seat")) { SeatSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("item_storage")) { ItemStorageSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("engine")) { EngineSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("motor")) { MotorSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("car_controller")) { CarControllerSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("motorbike_controller")) { MotorbikeControllerSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("gearbox")) { GearboxSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("transmission")) { TransmissionSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("joint_driver")) { JointDriverSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("wheel_driver")) { WheelDriverSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("turret_driver")) { TurretDriverSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("scriptable")) { ScriptableSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("battery")) { BatterySubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("lighting")) { LightingSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("launcher")) { LauncherSubsystemAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_ATTR_CODEC.key(), id("weapon_controller")) { WeaponControllerSubsystemAttr.CODEC}

        event.register(MMDataRegistries.SUBSYSTEM_STATIC_ATTR_CODEC.key(), id("basic")) { BasicSubsystemStaticAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_STATIC_ATTR_CODEC.key(), id("seat")) { SeatSubsystemStaticAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_STATIC_ATTR_CODEC.key(), id("item_storage")) { ItemStorageSubsystemStaticAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_STATIC_ATTR_CODEC.key(), id("engine")) { EngineSubsystemStaticAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_STATIC_ATTR_CODEC.key(), id("motor")) { MotorSubsystemStaticAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_STATIC_ATTR_CODEC.key(), id("car_controller")) { CarControllerSubsystemStaticAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_STATIC_ATTR_CODEC.key(), id("motorbike_controller")) { MotorbikeControllerSubsystemStaticAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_STATIC_ATTR_CODEC.key(), id("gearbox")) { GearboxSubsystemStaticAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_STATIC_ATTR_CODEC.key(), id("transmission")) { TransmissionSubsystemStaticAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_STATIC_ATTR_CODEC.key(), id("joint_driver")) { JointDriverSubsystemStaticAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_STATIC_ATTR_CODEC.key(), id("wheel_driver")) { WheelDriverSubsystemStaticAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_STATIC_ATTR_CODEC.key(), id("turret_driver")) { TurretDriverSubsystemStaticAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_STATIC_ATTR_CODEC.key(), id("scriptable")) { ScriptableSubsystemStaticAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_STATIC_ATTR_CODEC.key(), id("battery")) { BatterySubsystemStaticAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_STATIC_ATTR_CODEC.key(), id("lighting")) { LightingSubsystemStaticAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_STATIC_ATTR_CODEC.key(), id("launcher")) { LauncherSubsystemStaticAttr.CODEC}
        event.register(MMDataRegistries.SUBSYSTEM_STATIC_ATTR_CODEC.key(), id("weapon_controller")) { WeaponControllerSubsystemStaticAttr.CODEC}
    }

    private fun id(id: String) = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, id)

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::reg)
    }
}
