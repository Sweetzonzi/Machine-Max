package io.github.sweetzonzi.machine_max.common.registry

import io.github.sweetzonzi.machine_max.MachineMax

object MMSounds {
    @JvmStatic
    fun register() {
    }

    @JvmStatic
    val CUSTOM_SOUND = MachineMax.REGISTER.soundEvent {
        id = "custom_sound"
    }
}