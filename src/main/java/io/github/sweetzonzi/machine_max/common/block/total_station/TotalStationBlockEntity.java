package io.github.sweetzonzi.machine_max.common.block.total_station;

import cn.solarmoon.spark_core.animation.IBlockEntityAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.mojang.math.Axis;
import io.github.sweetzonzi.machine_max.common.menu.BlueprintResearchMenu;
import io.github.sweetzonzi.machine_max.common.registry.MMBlockEntities;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

@Getter
public class TotalStationBlockEntity extends BlockEntity implements IBlockEntityAnimatable<TotalStationBlockEntity> {

    public final AnimController animController = new AnimController(this);
    public final ModelController modelController = new ModelController(this);
    private final Map<String, Object> variables = HashMap.newHashMap(1);

    public TotalStationBlockEntity(BlockPos pos, BlockState state) {
        super(MMBlockEntities.getTOTAL_STATION_BLOCK_ENTITY().get(), pos, state);
    }

    @Override
    public TotalStationBlockEntity getAnimatable() {
        return this;
    }

    @NotNull
    @Override
    public AnimController getAnimController() {
        return this.animController;
    }

    @NotNull
    @Override
    public ModelController getModelController() {
        return this.modelController;
    }

    @NotNull
    @Override
    public Matrix4f getWorldPositionMatrix(@NotNull Number partialTicks) {
        Quaternionf rotation = Axis.YP.rotationDegrees(-getBlockState().getOptionalValue(BlockStateProperties.HORIZONTAL_FACING).orElse(Direction.NORTH).toYRot());
        return new Matrix4f()
                .translate(SparkMathKt.toVector3f(getBlockPos()).add(0.5f, 0.0f, 0.5f))
                .rotate(rotation);
    }
}
