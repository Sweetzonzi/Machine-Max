package io.github.sweetzonzi.machine_max.common.registry

import com.mojang.serialization.MapCodec
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AbstractSubsystemAttr
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractSubsystemStaticAttr

object MMDataRegistries {
    @JvmStatic
    val SUBSYSTEM_ATTR_CODEC = MachineMax.REGISTER.registry<MapCodec<out AbstractSubsystemAttr>>("subsystem_attr_codec") {
        it.sync(true).create()
    }
    @JvmStatic
    val SUBSYSTEM_STATIC_ATTR_CODEC = MachineMax.REGISTER.registry<MapCodec<out AbstractSubsystemStaticAttr>>("subsystem_static_attr_codec") {
        it.sync(true).create()
    }
    @JvmStatic
    fun register() {}
}