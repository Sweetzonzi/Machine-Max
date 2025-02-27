package io.github.tt432.machinemax.common.registry

import com.mojang.serialization.MapCodec
import io.github.tt432.machinemax.MachineMax
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.AbstractSubSystemAttr

object MMDataRegistries {
    @JvmStatic
    val SUBSYSTEM_DATA_CODEC = MachineMax.REGISTER.registry<MapCodec<out AbstractSubSystemAttr>>()
        .id("subsystem_codec")
        .build { it.sync(true).create() }

    @JvmStatic
    fun register() {
    }
}