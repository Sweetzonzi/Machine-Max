package io.github.sweetzonzi.machine_max.common.registry

import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.vehicle.data.VehicleData
import io.github.sweetzonzi.machine_max.common.attachment.LivingEntityEyesightAttachment
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.common.util.NeoForgeExtraCodecs

object MMAttachments {
    @JvmStatic
    fun register() {
    }

    //实体视线，用于获取看着的载具、部件、对接口等
    @JvmStatic
    val ENTITY_EYESIGHT = MachineMax.REGISTER.attachment {
        id = "entity_eyesight"
        factory = { _ -> LivingEntityEyesightAttachment(Minecraft.getInstance().player) }
    }

    //世界的载具列表，用于保存、加载和管理世界中的载具
    @JvmStatic
    val LEVEL_VEHICLES = MachineMax.REGISTER.attachment {
        id = "level_vehicles"
        factory = { _ -> mutableSetOf<VehicleData>()}
        serializer = NeoForgeExtraCodecs.setOf(VehicleData.CODEC)
    }
}