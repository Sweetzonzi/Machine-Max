package io.github.sweetzonzi.machine_max.common.block.research_table;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ResearchTableBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    public enum Part implements StringRepresentable {
        MAIN,
        EXTENSION;

        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }
    }

    public ResearchTableBlock() {
        super(Properties.of().sound(SoundType.WOOD).noOcclusion().lightLevel(p -> 13));
        registerDefaultState(
                stateDefinition.any()
                        .setValue(FACING, Direction.NORTH)
                        .setValue(HALF, Half.BOTTOM)
                        .setValue(PART, Part.MAIN)
        );
    }

    @Override
    protected @NotNull MapCodec<? extends BaseEntityBlock> codec() {
        return MapCodec.unit(this);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, BlockState state) {
        if (state.getValue(PART) == Part.MAIN
                && state.getValue(HALF) == Half.BOTTOM) {
            return new ResearchTableBlockEntity(pos, state);
        }
        return null;
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Direction facing = ctx.getHorizontalDirection();

        BlockPos ext = pos.relative(facing.getClockWise());
        BlockPos up = pos.above();
        BlockPos upExt = up.relative(facing.getClockWise());

        if (!level.getBlockState(ext).canBeReplaced(ctx)) return null;
        if (!level.getBlockState(up).canBeReplaced(ctx)) return null;
        if (!level.getBlockState(upExt).canBeReplaced(ctx)) return null;

        return defaultBlockState()
                .setValue(FACING, facing)
                .setValue(HALF, Half.BOTTOM)
                .setValue(PART, Part.MAIN);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, @NotNull ItemStack stack) {

        Direction facing = state.getValue(FACING);

        BlockPos ext = pos.relative(facing.getClockWise());
        BlockPos up = pos.above();
        BlockPos upExt = up.relative(facing.getClockWise());

        level.setBlock(ext,
                state.setValue(PART, Part.EXTENSION),
                3);

        level.setBlock(up,
                state.setValue(HALF, Half.TOP),
                3);

        level.setBlock(upExt,
                state.setValue(HALF, Half.TOP).setValue(PART, Part.EXTENSION),
                3);
    }


    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos,
                                        BlockState state, Player player) {

        BlockPos main = getMainPos(pos, state);

        for (BlockPos p : new BlockPos[]{
                main,
                main.relative(state.getValue(FACING).getClockWise()),
                main.above(),
                main.above().relative(state.getValue(FACING).getClockWise())
        }) {
            if (level.getBlockState(p).getBlock() == this) {
                level.destroyBlock(p, false);
            }
        }

        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockPos mainPos = getMainPos(pos, state);
        BlockEntity be = level.getBlockEntity(mainPos);

        if (be instanceof ResearchTableBlockEntity researchTable) {
            player.openMenu(researchTable);
            return InteractionResult.CONSUME;
        }

        return InteractionResult.PASS;
    }

    @Override
    protected @NotNull VoxelShape getCollisionShape(BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        if (state.getValue(HALF) == Half.TOP) {
            return Shapes.empty();
        }
        return super.getCollisionShape(state, level, pos, context);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    private BlockPos getMainPos(BlockPos pos, BlockState state) {
        BlockPos base = state.getValue(HALF) == Half.TOP
                ? pos.below()
                : pos;

        return state.getValue(PART) == Part.MAIN
                ? base
                : base.relative(state.getValue(FACING).getClockWise().getOpposite());
    }

    /**
     * * BlockState 注册
     */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, PART);
    }
}
