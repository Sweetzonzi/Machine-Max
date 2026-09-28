package io.github.sweetzonzi.machine_max.common.registry

import com.mojang.serialization.MapCodec
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.mech.projectile.component.effect.WorldEffect
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
    /** 战斗部 / 世界效果 Codec 注册表，按 JSON 中的 "type" 字段分派。 */
    @JvmStatic
    val WORLD_EFFECT_CODEC = MachineMax.REGISTER.registry<MapCodec<out WorldEffect>>("world_effect_codec") {
        it.sync(true).create()
    }
    @JvmStatic
    fun register() {}
}