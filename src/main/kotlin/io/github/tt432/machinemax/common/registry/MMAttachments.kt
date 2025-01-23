package io.github.tt432.machinemax.common.registry

import io.github.tt432.machinemax.MachineMax
import io.github.tt432.machinemax.common.sloarphys.body.LivingEntityEyesightBody
import net.minecraft.client.Minecraft

object MMAttachments {
    @JvmStatic
    fun register() {}

    @JvmStatic
    val ENTITY_EYESIGHT = MachineMax.REGISTER.attachment<LivingEntityEyesightBody>()
        .id("entity_eyesight")
        .defaultValue { LivingEntityEyesightBody("entity_eyesight", Minecraft.getInstance().player) }
        .build()
}