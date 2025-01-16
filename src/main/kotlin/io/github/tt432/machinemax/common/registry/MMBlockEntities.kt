package io.github.tt432.machinemax.common.registry

import io.github.tt432.machinemax.MachineMax
import io.github.tt432.machinemax.common.block.road.RoadBaseBlockEntity
import net.minecraft.world.level.block.Block

object MMBlockEntities {
    @JvmStatic
    fun register() {}
    @JvmStatic
    val ROAD_BASE_BLOCK_ENTITY = MachineMax.REGISTER.blockentity<RoadBaseBlockEntity>()
        .id("road_base_block_entity")
        .bound(::RoadBaseBlockEntity)
        .validBlocks { (arrayOf(MMBlocks.ROAD_BASE_BLOCK.get())) }
        .build()
}