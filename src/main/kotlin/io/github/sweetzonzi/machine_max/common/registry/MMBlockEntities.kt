package io.github.sweetzonzi.machine_max.common.registry

import io.github.sweetzonzi.machine_max.MachineMax
import io.github.sweetzonzi.machine_max.common.block.fabricator.FabricatorBlockEntity
import io.github.sweetzonzi.machine_max.common.block.research_table.ResearchTableBlockEntity
import io.github.sweetzonzi.machine_max.common.block.road.RoadBaseBlockEntity
import io.github.sweetzonzi.machine_max.common.block.total_station.TotalStationBlockEntity

object MMBlockEntities {
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
    @JvmStatic
    val RESEARCH_TABLE_BLOCK_ENTITY = MachineMax.REGISTER.blockEntityType{
        id = "research_table"
        factory = ::ResearchTableBlockEntity
        validBlocks {
            +MMBlocks.RESEARCH_TABLE_BLOCK.get()
        }
    }
    @JvmStatic
    val TOTAL_STATION_BLOCK_ENTITY = MachineMax.REGISTER.blockEntityType{
        id = "total_station"
        factory = ::TotalStationBlockEntity
        validBlocks {
            +MMBlocks.TOTAL_STATION_BLOCK.get()
        }
    }
}