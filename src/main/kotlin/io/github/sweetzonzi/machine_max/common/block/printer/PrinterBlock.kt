package cn.solarmoon.spark_core.printer

import cn.solarmoon.spark_core.SparkCore
import net.minecraft.core.BlockPos
import net.minecraft.util.RandomSource
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.VoxelShape

class PrinterBlock(properties: Properties): Block(properties), EntityBlock {

    companion object {
        val SHAPE = box(0.0, 0.0, 0.0, 16.0, 16.0, 16.0)
    }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape {
        return SHAPE
    }

    override fun <T : BlockEntity> getTicker(
        level: Level,
        state: BlockState,
        blockEntityType: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        return BlockEntityTicker<T> { level, pos, state, be -> (be as PrinterBlockEntity).tick(level, pos, state) }.takeIf { blockEntityType == PrinterRegister.PRINTER_BE.get() }
    }

    override fun newBlockEntity(
        pos: BlockPos,
        state: BlockState
    ): BlockEntity {
        return PrinterBlockEntity(pos, state)
    }

}