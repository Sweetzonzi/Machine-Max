package io.github.sweetzonzi.machine_max.common.registry

import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.mech.projectile.component.effect.ExplosionWorldEffect
import io.github.sweetzonzi.machine_max.common.mech.projectile.component.guidance.PurePursuitGuidance
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.RegisterEvent

object MMCodecs {
    private fun reg(event: RegisterEvent) {
        event.register(MMDataRegistries.WORLD_EFFECT_CODEC.key(), id("blast")) { ExplosionWorldEffect.CODEC }

        event.register(MMDataRegistries.GUIDANCE_LAW_CODEC.key(), id("pure_pursuit")) { PurePursuitGuidance.CODEC }
    }

    private fun id(id: String) = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, id)

    @JvmStatic
    fun register(bus: IEventBus) {
        bus.addListener(::reg)
    }
}
