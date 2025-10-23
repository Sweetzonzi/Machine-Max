package io.github.sweetzonzi.machine_max.common.registry

import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.block.fabricator.FabricatorBlockEntity
import io.github.sweetzonzi.machine_max.common.block.road.RoadBaseBlockEntity

object MMBlockEntitiesOld {
    @JvmStatic
    fun register() {}
    @JvmStatic
    val ROAD_BASE_BLOCK_ENTITY = MachineMax.REGISTER.blockEntityType {
        id = "road_base"
        factory = ::RoadBaseBlockEntity
        validBlocks {
            +MMBlocks.ROAD_BASE_BLOCK.get()
        }
    }
    @JvmStatic
    val FABRICATOR_BLOCK_ENTITY = MachineMax.REGISTER.blockEntityType{
        id = "fabricator"
        factory = ::FabricatorBlockEntity
        validBlocks {
            +MMBlocks.FABRICATOR_BLOCK.get()
        }
    }
}