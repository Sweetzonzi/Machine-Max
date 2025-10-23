package io.github.sweetzonzi.machine_max.common.registry

import com.mojang.serialization.MapCodec
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.AbstractSubsystemAttr

object MMDataRegistries {
    @JvmStatic
    val SUBSYSTEM_ATTR_CODEC = MachineMax.REGISTER.registry<MapCodec<out AbstractSubsystemAttr>>("subsystem_attr_codec") {
        it.sync(true).create()
    }
    @JvmStatic
    fun register() {}
}