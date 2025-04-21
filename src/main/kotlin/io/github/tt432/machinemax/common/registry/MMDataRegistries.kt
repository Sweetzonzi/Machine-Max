package io.github.tt432.machinemax.common.registry

import com.mojang.serialization.MapCodec
import io.github.tt432.machinemax.MachineMax
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.AbstractSubsystemAttr
import io.github.tt432.machinemax.common.vehicle.data.subsystem.AbstractSubsystemData

object MMDataRegistries {
    @JvmStatic
    val SUBSYSTEM_ATTR_CODEC = MachineMax.REGISTER.registry<MapCodec<out AbstractSubsystemAttr>>()
        .id("subsystem_attr_codec")
        .build { it.sync(true).create() }

    @JvmStatic
    val SUBSYSTEM_DATA_CODEC = MachineMax.REGISTER.registry<MapCodec<out AbstractSubsystemData>>()
        .id("subsystem_data_codec")
        .build { it.sync(true).create() }

    @JvmStatic
    fun register() {}
}