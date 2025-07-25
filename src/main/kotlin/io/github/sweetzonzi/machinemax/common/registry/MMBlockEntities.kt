package io.github.sweetzonzi.machinemax.common.registry

import io.github.sweetzonzi.machinemax.MachineMax
import io.github.sweetzonzi.machinemax.common.block.FabricatorBlockEntity
import io.github.sweetzonzi.machinemax.common.block.road.RoadBaseBlockEntity

object MMBlockEntities {
    @JvmStatic
    fun register() {}
    @JvmStatic
    val ROAD_BASE_BLOCK_ENTITY = MachineMax.REGISTER.blockentity<RoadBaseBlockEntity>()
        .id("road_base_block_entity")
        .bound(::RoadBaseBlockEntity)
        .validBlocks { (arrayOf(MMBlocks.ROAD_BASE_BLOCK.get())) }
        .build()
    @JvmStatic
    val FABRICATOR_BLOCK_ENTITY = MachineMax.REGISTER.blockentity<FabricatorBlockEntity>()
        .id("fabricator_block_entity")
        .bound(::FabricatorBlockEntity)
        .validBlocks { (arrayOf(MMBlocks.FABRICATOR_BLOCK.get())) }
        .build()
}