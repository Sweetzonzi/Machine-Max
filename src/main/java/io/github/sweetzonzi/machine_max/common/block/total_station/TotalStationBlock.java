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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TotalStationBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;

    public TotalStationBlock() {
        super(Properties.of().sound(SoundType.METAL).noOcclusion().lightLevel(p -> 5));
        registerDefaultState(
                stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(HALF, Half.BOTTOM)
        );
    }


    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Direction facing = ctx.getHorizontalDirection().getClockWise();

        BlockPos down = pos.below();
        BlockPos up = pos.above();

        if (!level.getBlockState(down).isSolid()) return null;
        if (!level.getBlockState(up).canBeReplaced(ctx)) return null;

        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(HALF, Half.BOTTOM);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, @NotNull ItemStack stack) {

        BlockPos up = pos.above();

        level.setBlock(up,
                state.setValue(HALF, Half.TOP),
                3);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos main = getMainPos(pos, state);

        for (BlockPos p : new BlockPos[]{
                main,
                main.above()
        }) {
            if (level.getBlockState(p).getBlock() == this) {
                level.destroyBlock(p, false);
            }
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (level.isClientSide) return;

        BlockPos main = getMainPos(pos, state);
        BlockPos down = main.below();

        if (!level.getBlockState(down).isSolid()) {
            for (BlockPos p : new BlockPos[]{
                    main,
                    main.above()
            }) {
                if (level.getBlockState(p).getBlock() == this) {
                    level.destroyBlock(p, true);
                }
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        } else {
            BlockPos mainPos = getMainPos(pos, state);
            MenuProvider menuprovider = this.getMenuProvider(level.getBlockState(mainPos), level, mainPos);
            if (menuprovider != null) {
                player.openMenu(menuprovider, mainPos);
            }
            return InteractionResult.CONSUME;
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return MapCodec.unit(this);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        if (state.getValue(HALF) == Half.BOTTOM) {
            return new TotalStationBlockEntity(pos, state);
        }
        return null;
    }

    @Override
    protected @NotNull RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    private BlockPos getMainPos(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == Half.TOP
                ? pos.below()
                : pos;
    }
}
