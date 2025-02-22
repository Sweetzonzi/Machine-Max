package io.github.tt432.machinemax.common.block.road;

import com.mojang.serialization.MapCodec;
import io.github.tt432.machinemax.common.registry.MMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class RoadBaseBlock extends BaseEntityBlock {

    public RoadBaseBlock() {
        super(Properties.of().sound(SoundType.STONE));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return null;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RoadBaseBlockEntity(pos, state);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof RoadBaseBlockEntity)
            ((RoadBaseBlockEntity) blockEntity).unloadHeightfield();//先销毁道路几何体再移除方块
        //TODO: 发包通知客户端移除
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return createTickerHelper(blockEntityType, MMBlockEntities.getROAD_BASE_BLOCK_ENTITY().get(), RoadBaseBlockEntity::tick);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

//    @Override//修改的是碰撞箱
//    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
//        return Shapes.box(0,0,0,1,1.5,1);
//    }
//
//    @Override
//    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
//        return Shapes.box(0,0,0,1,1.5,1);
//    }
//
//    @Override//修改的是描边
//    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
//        return super.getShape(state, level, pos, context);
//    }
}
