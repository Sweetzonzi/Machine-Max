package io.github.sweetzonzi.machinemax.common.registry

import io.github.sweetzonzi.machinemax.MachineMax
import io.github.sweetzonzi.machinemax.common.vehicle.data.VehicleData
import io.github.sweetzonzi.machinemax.common.attachment.LivingEntityEyesightAttachment
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.common.util.NeoForgeExtraCodecs

object MMAttachments {
    @JvmStatic
    fun register() {
    }
    //实体视线，用于获取看着的载具、部件、对接口等
    @JvmStatic
    val ENTITY_EYESIGHT = MachineMax.REGISTER.attachment<LivingEntityEyesightAttachment>()
        .id("entity_eyesight")
        .defaultValue {
            LivingEntityEyesightAttachment(Minecraft.getInstance().player)
        }
        .build()
    //世界的载具列表，用于保存、加载和管理世界中的载具
    @JvmStatic
    val LEVEL_VEHICLES = MachineMax.REGISTER.attachment<MutableSet<VehicleData>>()
        .id("level_vehicles")
        .defaultValue { mutableSetOf() }
        .serializer { it.serialize(NeoForgeExtraCodecs.setOf(VehicleData.CODEC)) }
        .build()
}