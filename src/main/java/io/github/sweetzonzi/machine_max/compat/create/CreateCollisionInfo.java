package io.github.sweetzonzi.machine_max.compat.create;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public record CreateCollisionInfo(@Nullable BlockPos blockPos, @Nullable BlockState blockState, boolean create) {
}

