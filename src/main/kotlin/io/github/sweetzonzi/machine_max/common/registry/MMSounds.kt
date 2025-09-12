package io.github.sweetzonzi.machine_max.common.registry

import io.github.sweetzonzi.machine_max.MachineMax

object MMSounds {
    @JvmStatic
    fun register() {
    }

    @JvmStatic
    val PART_PAINTED = MachineMax.REGISTER.sound().id("item.part_painted").build()

    @JvmStatic
    val CUSTOM_SOUND = MachineMax.REGISTER.sound().id("custom_sound").build()
}