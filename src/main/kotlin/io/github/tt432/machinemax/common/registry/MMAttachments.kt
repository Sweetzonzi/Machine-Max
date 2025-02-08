package io.github.tt432.machinemax.common.registry

import io.github.tt432.machinemax.MachineMax
import io.github.tt432.machinemax.common.vehicle.data.VehicleData
import io.github.tt432.machinemax.common.phys.body.LivingEntityEyesightBody
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.common.util.NeoForgeExtraCodecs

object MMAttachments {
    @JvmStatic
    fun register() {
    }

    @JvmStatic
    val ENTITY_EYESIGHT = MachineMax.REGISTER.attachment<LivingEntityEyesightBody>()
        .id("entity_eyesight")
        .defaultValue { LivingEntityEyesightBody("entity_eyesight", Minecraft.getInstance().player) }
        .build()

    @JvmStatic
    val LEVEL_VEHICLES = MachineMax.REGISTER.attachment<MutableSet<VehicleData>>()
        .id("level_vehicles")
        .defaultValue { mutableSetOf() }
        .serializer { it.serialize(NeoForgeExtraCodecs.setOf(VehicleData.CODEC)) }
        .build()
}