package io.github.sweetzonzi.machinemax.common.registry

import com.mojang.serialization.MapCodec
import io.github.sweetzonzi.machinemax.MachineMax
import io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem.AbstractSubsystemAttr

object MMDataRegistries {
    @JvmStatic
    val SUBSYSTEM_ATTR_CODEC = MachineMax.REGISTER.registry<MapCodec<out AbstractSubsystemAttr>>()
        .id("subsystem_attr_codec")
        .build { it.sync(true).create() }
    @JvmStatic
    fun register() {}
}