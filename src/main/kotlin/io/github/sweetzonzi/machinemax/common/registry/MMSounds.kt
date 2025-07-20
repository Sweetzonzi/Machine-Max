package io.github.sweetzonzi.machinemax.common.registry

import io.github.sweetzonzi.machinemax.MachineMax

object MMSounds {
    @JvmStatic
    fun register() {
    }

    @JvmStatic
    val PART_PAINTED = MachineMax.REGISTER.sound().id("item.part_painted").build()

    @JvmStatic
    val CUSTOM_SOUND = MachineMax.REGISTER.sound().id("custom_sound").build()
}