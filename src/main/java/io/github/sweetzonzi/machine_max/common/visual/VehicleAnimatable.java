package io.github.sweetzonzi.machine_max.common.visual;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehiclePlacementService;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.RegularPartData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.SubPartData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * 载具的纯渲染动画体，供物品模型与世界放置预览共用。
 * 整车位姿由接地锚点和参考根朝向描述；各子零件独立保存绝对位姿和上一 tick 的插值端点。
 */
@Getter
public class VehicleAnimatable implements IAnimatable<VehicleAnimatable> {
    public final Level level;
    public final ModelIndex modelIndex = new ModelIndex("part", ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "empty"));
    public final AnimController animController = new AnimController(this);
    public final ModelController modelController = new ModelController(this);
    public final Map<Integer, SubPartAnimatable> subParts = new HashMap<>();
    public VehicleData vehicleData;
    public Transform transform = new Transform();
    private VehiclePlacementService placement;
    private float scale; //TODO: 动态scale

    /**
     * 从载具快照创建动画体，初始接地锚点和参考根朝向取展示坐标系的原点和单位旋转。
     *
     * @param vehicle 提供数据快照和世界的载具
     */
    public VehicleAnimatable(VehicleCore vehicle) {
        this(vehicle.level, new VehicleData(vehicle), 1.0f);
    }

    /**
     * 创建无缩放的载具动画体。
     *
     * @param level 解析部件定义的世界
     * @param vehicleData 源子零件及其位姿；在动画体生命周期内保持不变
     */
    public VehicleAnimatable(Level level, VehicleData vehicleData) {
        this(level, vehicleData, 1.0f);
    }

    /**
     * 创建载具动画体，初始接地锚点和参考根朝向取展示坐标系的原点和单位旋转。
     *
     * @param level 解析部件定义的世界
     * @param vehicleData 源子零件及其位姿；在动画体生命周期内保持不变
     * @param scale 模型显示倍率，仅供渲染器使用，不影响碰撞边界或放置锚点
     */
    public VehicleAnimatable(Level level, VehicleData vehicleData, float scale) {
        this.level = level;
        this.scale = scale;
        this.update(vehicleData);
    }

    /**
     * 用新的载具快照重建子零件动画体与放置几何（主线程）。
     *
     * @param vehicle 提供新数据快照的载具
     */
    public void update(VehicleCore vehicle) {
        this.update(new VehicleData(vehicle));
    }

    /**
     * 更换源数据与放置几何，在当前展示位姿上重建全部子零件（主线程）。
     *
     * @param vehicleData 新的载具数据，包含各子零件的源位姿
     */
    public void update(VehicleData vehicleData) {
        placement = new VehiclePlacementService(level, vehicleData);
        subParts.clear();
        this.vehicleData = vehicleData;
        for (RegularPartData partData : vehicleData.parts.values()) {
            RegularPartType type = RegularPartType.get(level, partData.registryKey);
            VariantAttr variant = type.getVariant(partData.variant);
            for (Map.Entry<String, SubPartData> entry : partData.subParts.entrySet()) {
                SubPartAttr attr = variant.subParts.get(entry.getKey());
                subParts.put(entry.getValue().id, new SubPartAnimatable(variant, attr, level));
            }
        }
        setTransform(transform);
    }

    /**
     * 立即设置放置位姿，将所有子零件的两个插值端点同时对齐（主线程）。
     *
     * @param transform 平移为目标接地锚点：XZ 对齐参考根局部 AABB 底心的投影，Y 为接地高度；
     *                  旋转为参考根子零件的目标朝向；缩放不参与计算
     */
    public void setTransform(Transform transform) {
        applyTransform(SparkMathKt.toVec3(transform.getTranslation()), transform.getRotation(), false);
    }

    /**
     * 更新放置位姿，各子零件保留上一个 tick 的位姿用于渲染插值（主线程）。
     *
     * @param transform 平移为目标接地锚点：XZ 对齐参考根局部 AABB 底心的投影，Y 为接地高度；
     *                  旋转为参考根子零件的目标朝向；缩放不参与计算
     */
    public void updateTransform(Transform transform) {
        applyTransform(SparkMathKt.toVec3(transform.getTranslation()), transform.getRotation(), true);
    }

    private void applyTransform(Vec3 groundAnchor, Quaternion rootRotation, boolean interpolate) {
        // 每次从源数据出发，避免移动或旋转时在上一帧的绝对位姿上累积误差。
        placement.forEachPlacedSubPart(groundAnchor, rootRotation, (pose, id) -> {
            SubPartAnimatable subPart = subParts.get(id);
            if (interpolate) subPart.updateTransform(pose);
            else subPart.setTransform(pose);
        });
        this.transform = new Transform(PhysicsHelperKt.toBVector3f(groundAnchor), rootRotation);
    }

    /**
     * 将参考根对齐到展示坐标轴，把该朝向下的碰撞盒几何中心移到展示坐标原点。
     *
     * @param fitToUnitSize 为 {@code true} 时按碰撞盒平均边长自动缩放，为 {@code false} 时保留模型倍率
     */
    public void centerForItemDisplay(boolean fitToUnitSize) {
        AABB bounds = placement.boundsAtOrigin(Quaternion.IDENTITY);
        setTransform(new Transform(PhysicsHelperKt.toBVector3f(bounds.getCenter().scale(-1)), Quaternion.IDENTITY));
        if (fitToUnitSize) scale = (float) (1.0 / bounds.getSize());
    }
    @Override
    public VehicleAnimatable getAnimatable() {
        return this;
    }

    @Override
    public @Nullable Level getAnimLevel() {
        return level;
    }

    @Override
    public @NotNull ModelIndex getDefaultModelIndex() {
        return modelIndex;
    }

    @Override
    public @NotNull Map<String, Object> getVariables() {
        return Map.of();
    }

    @Override
    public @NotNull Matrix4f getWorldPositionMatrix(@NotNull Number number) {
        // 动画体原点是接地锚点，子零件矩阵独立保存绝对位姿。
        return SparkMathKt.toMatrix4f(transform.toTransformMatrix());
    }
}
