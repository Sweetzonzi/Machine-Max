package io.github.sweetzonzi.machine_max.common.registry

import com.mojang.serialization.MapCodec
import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.mech.projectile.component.effect.WorldEffect
import io.github.sweetzonzi.machine_max.common.mech.projectile.component.guidance.GuidanceLaw

object MMDataRegistries {
    /** 战斗部 / 世界效果 Codec 注册表，按 JSON 中的 "type" 字段分派。 */
    @JvmStatic
    val WORLD_EFFECT_CODEC = MachineMax.REGISTER.registry<MapCodec<out WorldEffect>>("world_effect_codec") {
        it.sync(true).create()
    }
    /** 制导律 Codec 注册表，按 JSON 中的 "type" 字段分派。 */
    @JvmStatic
    val GUIDANCE_LAW_CODEC = MachineMax.REGISTER.registry<MapCodec<out GuidanceLaw>>("guidance_law_codec") {
        it.sync(true).create()
    }
    @JvmStatic
    fun register() {}
}
