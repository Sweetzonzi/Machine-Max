package io.github.sweetzonzi.machine_max.common.block.total_station;

import com.mojang.serialization.MapCodec;
import io.github.sweetzonzi.machine_max.common.block.research_table.ResearchTableBlock;
import io.github.sweetzonzi.machine_max.common.block.research_table.ResearchTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TotalStationBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public TotalStationBlock() {
        super(Properties.of().sound(SoundType.METAL).noOcclusion().lightLevel(p -> 5));
        registerDefaultState(
                stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
        );
    }


    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Direction facing = ctx.getHorizontalDirection().getClockWise();

        BlockPos up = pos.above();

        if (!level.getBlockState(up).canBeReplaced(ctx)) return null;

        return defaultBlockState()
                .setValue(FACING, facing);
    }

//    @Override
//    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
//                            @Nullable LivingEntity placer, @NotNull ItemStack stack) {
//
//        Direction facing = state.getValue(FACING);
//
//        BlockPos ext = pos.relative(facing.getClockWise());
//        BlockPos up = pos.above();
//        BlockPos upExt = up.relative(facing.getClockWise());
//
//        level.setBlock(ext,
//                state.setValue(PART, ResearchTableBlock.Part.EXTENSION),
//                3);
//
//        level.setBlock(up,
//                state.setValue(HALF, Half.TOP),
//                3);
//
//        level.setBlock(upExt,
//                state.setValue(HALF, Half.TOP).setValue(PART, ResearchTableBlock.Part.EXTENSION),
//                3);
//    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            MenuProvider menuprovider = this.getMenuProvider(state, level, pos);
            if (menuprovider != null) {
                player.openMenu(menuprovider, pos);
            }
            return InteractionResult.CONSUME;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return null;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new TotalStationBlockEntity(pos, state);
    }

    @Override
    protected @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }
}
