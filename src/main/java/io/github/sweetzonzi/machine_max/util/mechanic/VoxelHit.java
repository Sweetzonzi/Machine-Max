package io.github.sweetzonzi.machine_max.util.mechanic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 一次体素遍历产生的方块命中条目。
 *
 * @param pos         命中的方块坐标
 * @param hitFraction 命中点在本次遍历段上的参数（0=段起点，1=段终点）
 * @param state       命中时的方块状态
 */
public record VoxelHit(BlockPos pos, float hitFraction, BlockState state) {
}
