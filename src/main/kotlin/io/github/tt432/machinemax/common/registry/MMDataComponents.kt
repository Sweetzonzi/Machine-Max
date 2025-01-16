package io.github.tt432.machinemax.common.registry

import io.github.tt432.machinemax.MachineMax
import io.github.tt432.machinemax.common.component.PartTypeComponent

object MMDataComponents {
    @JvmStatic
    fun register() {
    }

    //部件类型
    @JvmStatic
    val PART_TYPE = MachineMax.REGISTER.dataComponent<PartTypeComponent>()
        .id("part_type")
        .build {
            it.persistent(PartTypeComponent.CODEC)
                .networkSynchronized(PartTypeComponent.STREAM_CODEC)
        }
}