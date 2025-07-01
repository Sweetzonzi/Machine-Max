package io.github.sweetzonzi.machinemax.common.registry

import io.github.sweetzonzi.machinemax.MachineMax
import io.github.sweetzonzi.machinemax.common.block.FabricatorBlock
import io.github.sweetzonzi.machinemax.common.block.road.RoadBaseBlock

object MMBlocks {
    @JvmStatic
    fun register() {}
    //路基方块
    @JvmStatic
    val ROAD_BASE_BLOCK = MachineMax.REGISTER.block<RoadBaseBlock>()
        .id("road_base")
        .bound (::RoadBaseBlock)
        .build()
    //制造台方块
    @JvmStatic
    val FABRICATOR_BLOCK = MachineMax.REGISTER.block<FabricatorBlock>()
        .id("fabricator")
        .bound (::FabricatorBlock)
        .build()
}