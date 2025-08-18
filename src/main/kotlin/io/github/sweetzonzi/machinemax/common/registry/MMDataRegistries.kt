package io.github.sweetzonzi.machinemax.common.registry

import com.mojang.serialization.MapCodec
import io.github.sweetzonzi.machinemax.MachineMax
import io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem.AbstractSubsystemAttr
import kotlin.reflect.KClass

object MMDataRegistries {
    @JvmStatic
    val SUBSYSTEM_ATTR_CODEC = MachineMax.REGISTER.registry<MapCodec<out AbstractSubsystemAttr>>()
        .id("subsystem_attr_codec")
        .valueType(MapCodec::class as KClass<out MapCodec<out AbstractSubsystemAttr>>)
        .build { it.sync(true).create() }
    @JvmStatic
    fun register() {}
}