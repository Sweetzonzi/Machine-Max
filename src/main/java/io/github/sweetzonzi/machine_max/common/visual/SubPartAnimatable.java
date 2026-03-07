package io.github.sweetzonzi.machine_max.common.visual;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.VariantAttr;
import lombok.Getter;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Map;

@Getter
public class SubPartAnimatable implements IAnimatable<SubPartAttr> {
    // 静态属性
    public final VariantAttr variantAttr;
    public final SubPartAttr attr;
    public final Level level;
    public final AnimController animController;
    public final ModelController modelController;
    public final ModelIndex modelIndex;
    // 渲染位姿
    public Transform transform = new Transform();
    public Transform oldTransform = new Transform();

    public SubPartAnimatable(VariantAttr variantAttr, SubPartAttr attr, Level level) {
        this.variantAttr = variantAttr;
        this.attr = attr;
        this.level = level;
        this.modelController = new ModelController(this);
        this.animController = new AnimController(this);
        this.modelIndex = new ModelIndex("part", variantAttr.getModel());
    }

    public SubPartAnimatable(SubPart subPart) {
        this(subPart.part.getVariant(), subPart.attr, subPart.level);
        this.transform = subPart.getTransform().clone();
        this.oldTransform = subPart.getTransform().clone();
    }

    @Override
    public SubPartAttr getAnimatable() {
        return this.attr;
    }

    @Override
    public @Nullable Level getAnimLevel() {
        return this.level;
    }

    @Override
    public @NotNull ModelIndex getDefaultModelIndex() {
        return modelIndex;
    }

    @Override
    public @NotNull Map<String, Object> getVariables() {
        return Map.of();
    }

    /**
     * 获取质心在世界坐标系下的位姿变换
     * @param number 插值系数，0-1
     * @return 质心在世界坐标系下的位姿变换， 常用于游戏机制
     */
    @Override
    public @NotNull Matrix4f getWorldPositionMatrix(@NotNull Number number) {
        return SparkMathKt.toMatrix4f(SparkMathKt.lerp(oldTransform, transform, number.floatValue()).toTransformMatrix());
    }

    /**
     * 获取模型坐标原点在世界坐标系下的位姿变换
     * @param number 插值系数，0-1
     * @return 模型坐标原点在世界坐标系下的位姿变换，常用于渲染
     */
    public Matrix4f getRenderWorldPositionMatrix(@NotNull Number number) {
        return getWorldPositionMatrix(number).mul(SparkMathKt.toMatrix4f(getAttr().getMassCenterTransform().toTransformMatrix()));
    }
}
