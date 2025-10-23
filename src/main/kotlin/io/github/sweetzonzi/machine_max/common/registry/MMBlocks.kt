package io.github.sweetzonzi.machine_max.common.registry

import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.block.fabricator.FabricatorBlock
import io.github.sweetzonzi.machine_max.common.block.road.RoadBaseBlock

object MMBlocks {
    @JvmStatic
    fun register() {}
    //路基方块
    @JvmStatic
    val ROAD_BASE_BLOCK = MachineMax.REGISTER.block {
        id= "road_base"
        factory = ::RoadBaseBlock
    }
    //制造台方块
    @JvmStatic
    val FABRICATOR_BLOCK = MachineMax.REGISTER.block{
        id= "fabricator"
        factory = ::FabricatorBlock
    }
}