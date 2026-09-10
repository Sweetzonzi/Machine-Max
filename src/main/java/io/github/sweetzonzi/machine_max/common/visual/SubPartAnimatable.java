package io.github.sweetzonzi.machine_max.common.visual;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import lombok.Getter;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Map;

@Getter
public class SubPartAnimatable implements IAnimatable<SubPartAnimatable> {
    // 静态属性
    public final VariantAttr variantAttr;
    public final SubPartAttr attr;
    public final Level level;
    public final AnimController animController;
    public final ModelController modelController;
    public final ModelIndex modelIndex;
    // 渲染位姿
    public Transform transform = new Transform();
    public Transform oldTransform = null;

    public SubPartAnimatable(VariantAttr variantAttr, SubPartAttr attr, Level level) {
        this.variantAttr = variantAttr;
        this.attr = attr;
        this.level = level;
        this.modelIndex = new ModelIndex("part", variantAttr.getModel());
        this.modelController = new ModelController(this);
        this.animController = new AnimController(this);
        this.modelController.setTextureLocation(variantAttr.getTextureList().getFirst());
    }

    public SubPartAnimatable(SubPart subPart) {
        this(subPart.part.getVariant(), subPart.attr, subPart.level);
        setTransform(subPart.getTransform());
    }

    public void setTransform(Transform transform) {
        this.transform = transform.clone();
        this.oldTransform = transform.clone();
    }

    public void updateTransform(Transform transform) {
        if (this.oldTransform == null) {
            this.oldTransform = transform.clone();
        } else this.oldTransform = this.transform;
        this.transform = transform.clone();
    }

    public Map<String, OBone> getBones() {
        return attr.getBones(variantAttr);
    }

    @Override
    public SubPartAnimatable getAnimatable() {
        return this;
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
     * 获取渲染用位姿矩阵：把 poseStack 停在【刚体局部空间】。坐标系链如下：
     * <ol>
     *   <li>{@link #getWorldPositionMatrix} —— 刚体局部空间 → 世界空间（刚体原点位于质心）；</li>
     *   <li>右乘质心变换的逆 —— 把 poseStack 从刚体原点回退到 start_bone 空间的质心处
     *       （碰撞子形状、连接点 locator 都表达在 start_bone 空间）；</li>
     *   <li>再右乘 {@link SubPartAttr#getModelToStartBone} —— 把渲染器输出的
     *       【模型根空间】骨骼链换算到 start_bone 空间，使预览与实体渲染保持一致。</li>
     * </ol>
     *
     * @param number 插值系数，0-1
     * @return 渲染用位姿矩阵，常用于渲染
     */
    public Matrix4f getRenderWorldPositionMatrix(@NotNull Number number) {
        return getWorldPositionMatrix(number)
                .mul(SparkMathKt.toMatrix4f(getAttr().getMassCenterTransform().invert().toTransformMatrix()))
                .mul(getAttr().getModelToStartBone(variantAttr));
    }
}
