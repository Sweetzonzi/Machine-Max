package io.github.sweetzonzi.machine_max.common.block.research_table;

import cn.solarmoon.spark_core.animation.IBlockEntityAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.mojang.math.Axis;
import io.github.sweetzonzi.machine_max.common.menu.BlueprintResearchMenu;
import io.github.sweetzonzi.machine_max.common.registry.MMBlockEntities;
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

/**
 * 蓝图研发台 BlockEntity
 * 当前职责：
 * - 作为 MenuProvider
 * - 承载未来扩展点（团队科研 / 能量 / 插件）
 * 当前版本：
 * - 不存储任何数据
 * - 不进行 tick
 */
public class ResearchTableBlockEntity extends BlockEntity implements MenuProvider, IBlockEntityAnimatable<ResearchTableBlockEntity> {

    public final AnimController animController = new AnimController(this);
    public final ModelController modelController = new ModelController(this);

    public ResearchTableBlockEntity(BlockPos pos, BlockState state) {
        super(MMBlockEntities.getRESEARCH_TABLE_BLOCK_ENTITY().get(), pos, state);
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.empty();
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(
            int id,
            @NotNull Inventory inventory,
            @NotNull Player player
    ) {
        return new BlueprintResearchMenu(id, inventory);
    }

    @Override
    public ResearchTableBlockEntity getAnimatable() {
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
