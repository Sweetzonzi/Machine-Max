package io.github.sweetzonzi.machine_max.common.registry

import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.block.FabricatorBlockEntity
import io.github.sweetzonzi.machine_max.common.block.road.RoadBaseBlockEntity

object MMBlockEntities {
    @JvmStatic
    fun register() {}
    @JvmStatic
    val ROAD_BASE_BLOCK_ENTITY = MachineMax.REGISTER.blockentity<RoadBaseBlockEntity>()
        .id("road_base")
        .bound(::RoadBaseBlockEntity)
        .validBlocks { (arrayOf(MMBlocks.ROAD_BASE_BLOCK.get())) }
        .build()
    @JvmStatic
    val FABRICATOR_BLOCK_ENTITY = MachineMax.REGISTER.blockentity<FabricatorBlockEntity>()
        .id("fabricator")
        .bound(::FabricatorBlockEntity)
        .validBlocks { (arrayOf(MMBlocks.FABRICATOR_BLOCK.get())) }
        .build()
}