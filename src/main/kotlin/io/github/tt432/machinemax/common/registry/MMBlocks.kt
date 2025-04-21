package io.github.tt432.machinemax.common.registry

import io.github.tt432.machinemax.MachineMax
import io.github.tt432.machinemax.common.block.road.RoadBaseBlock

object MMBlocks {
    @JvmStatic
    fun register() {}
    //路基方块
    @JvmStatic
    val ROAD_BASE_BLOCK = MachineMax.REGISTER.block<RoadBaseBlock>()
        .id("road_base_block")
        .bound (::RoadBaseBlock)
        .build()
}