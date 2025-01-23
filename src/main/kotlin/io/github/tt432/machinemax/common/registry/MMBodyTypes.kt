package io.github.tt432.machinemax.common.registry

import io.github.tt432.machinemax.MachineMax

object MMBodyTypes {
    @JvmStatic
    fun register() {
    }

    @JvmStatic
    val PART = MachineMax.REGISTER.bodyType()
        .id("part")
        .build()

    @JvmStatic
    val BLOCK_BOUNDING_BOX = MachineMax.REGISTER.bodyType()
        .id("block_bounding_box")
        .build()

    @JvmStatic
    val LIVING_ENTITY_EYESIGHT = MachineMax.REGISTER.bodyType()
        .id("living_entity_eyesight")
        .build()

    @JvmStatic
    val PART_SLOT_ATTACH_POINT = MachineMax.REGISTER.bodyType()
        .id("part_slot_attach_point")
        .build()
}