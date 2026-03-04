package io.github.sweetzonzi.machine_max.common.registry

import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.attachment.BlueprintAttachment
import io.github.sweetzonzi.machine_max.common.attachment.ControlPreferenceAttachment
import io.github.sweetzonzi.machine_max.common.attachment.LivingEntityEyesightAttachment
import io.github.sweetzonzi.machine_max.common.attachment.VehicleAssemblyAttachment
import io.github.sweetzonzi.machine_max.common.vehicle.data.VehicleData
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.common.util.NeoForgeExtraCodecs

object MMAttachments {
    @JvmStatic
    fun register() {
    }

    //实体视线，用于获取看着的载具、部件、连接点等
    @JvmStatic
    val ENTITY_EYESIGHT = MachineMax.REGISTER.attachment {
        id = "entity_eyesight"
        factory = { _ -> LivingEntityEyesightAttachment(Minecraft.getInstance().player) }
    }


    //实体组装部件时的缓存信息，如持有的部件类型，变体类型，选中的连接点等
    @JvmStatic
    val VEHICLE_ASSEMBLY = MachineMax.REGISTER.attachment {
        id = "vehicle_assembly_cache"
        factory = { _ -> VehicleAssemblyAttachment(Minecraft.getInstance().player) }
    }

    //实体的载具控制偏好
    @JvmStatic
    val CONTROL_PREFERENCE = MachineMax.REGISTER.attachment {
        id = "control_preference"
        factory = { _ -> ControlPreferenceAttachment() }
        copyOnDeath = true
        serializer = ControlPreferenceAttachment.CODEC
    }

    //实体保存的蓝图与研发点
    @JvmStatic
    val BLUEPRINT = MachineMax.REGISTER.attachment {
        id = "blueprint"
        factory = { _ -> BlueprintAttachment(0) }
        copyOnDeath = true
        serializer = BlueprintAttachment.CODEC
    }

    //世界的载具列表，用于保存、加载和管理世界中的载具
    @JvmStatic
    val LEVEL_VEHICLES = MachineMax.REGISTER.attachment {
        id = "level_vehicles"
        factory = { _ -> mutableSetOf<VehicleData>() }
        serializer = NeoForgeExtraCodecs.setOf(VehicleData.CODEC)
    }
}