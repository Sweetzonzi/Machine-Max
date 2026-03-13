package io.github.sweetzonzi.machine_max.common.visual;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import jme3utilities.math.MyMath;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.PartType;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.data.PartData;
import io.github.sweetzonzi.machine_max.common.vehicle.data.SubPartData;
import io.github.sweetzonzi.machine_max.common.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * 载具动画体，用于表示整个载具及其所有零件的渲染状态。
 * 
 * <p>设计思想：</p>
 * <ul>
 *   <li>所有{@link SubPartAnimatable}使用绝对世界坐标存储，确保渲染一致性</li>
 *   <li>载具质心变换时，计算相对变换差值并应用到所有子零件</li>
 *   <li>插值由每个{@link SubPartAnimatable}自行处理（通过其{@code oldTransform}字段）</li>
 *   <li>变换更新公式：Δ = newTransform × currentTransform⁻¹，应用于所有子零件</li>
 *   <li>参考{@link PartAnimatable}的实现模式，使用{@link MyMath#combine}进行变换组合</li>
 * </ul>
 * 
 * <p>使用场景：</p>
 * <ul>
 *   <li>载具蓝图/装配体在GUI中的3D预览（当未提供图标时）</li>
 *   <li>玩家保存的载具设计展示</li>
 *   <li>世界中的载具放置预览（通过PartAssemblyRenderer）</li>
 * </ul>
 * 
 * <p>注意：与{@link PartAnimatable}不同，本类不设{@code rootSubPart}概念，
 * 所有子零件直接相对于载具质心进行变换。</p>
 * 
 * <p>缩放属性：支持模型缩尺比（scale属性），例如scale=35代表1:35比例，
 * 渲染时需要将模型缩小至原尺寸的1/35。缩放由渲染器在{@code poseStack}中应用。</p>
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
    private final Transform delta = new Transform();
    private float scale; //TODO: 动态scale

    public VehicleAnimatable(VehicleCore vehicle) {
        this(vehicle.level, new VehicleData(vehicle), 1.0f);
    }

    public VehicleAnimatable(Level level, VehicleData vehicleData) {
        this(level, vehicleData, 1.0f);
    }

    public VehicleAnimatable(Level level, VehicleData vehicleData, float scale) {
        this.level = level;
        this.scale = scale;
        this.update(vehicleData);
    }

    public void update(VehicleCore vehicle) {
        this.update(new VehicleData(vehicle));
    }

    public void update(VehicleData vehicleData) {
        subParts.clear();
        this.vehicleData = vehicleData;
        for (PartData partData : vehicleData.parts.values()) {
            PartType type = level.isClientSide()
                    ? MMDynamicRes.PART_TYPES.get(partData.registryKey)
                    : MMDynamicRes.SERVER_PART_TYPES.get(partData.registryKey);
            if (type == null) continue;
            String variantName = partData.variant;
            VariantAttr variant = type.getVariant(variantName);
            if (variant == null) continue;
            for (Map.Entry<String, SubPartData> entry : partData.subParts.entrySet()) {
                String name = entry.getKey();
                SubPartData subPartData = entry.getValue();
                SubPartAttr attr = variant.subParts.get(name);
                int id = subPartData.id;
                SubPartAnimatable subPartAnimatable = new SubPartAnimatable(variant, attr, level);
                // 设置位姿并保存零件动画体对象
                subPartAnimatable.setTransform(subPartData.getPosRotVelVel().toTransform());
                subParts.put(id, subPartAnimatable);
            }
        }
        this.transform= new Transform(PhysicsHelperKt.toBVector3f(vehicleData.pos), Quaternion.IDENTITY);
    }

    /**
     * 设置载具质心变换，并同步更新所有子零件的绝对坐标。
     * 
     * <p>计算相对变换 Δ = newTransform × currentTransform⁻¹，
     * 然后将Δ应用到每个{@link SubPartAnimatable}的当前变换上。</p>
     * 
     * <p>实现参考{@link PartAnimatable#setTransform}，使用{@link MyMath#combine}
     * 进行变换组合。所有子零件保持绝对世界坐标的同步更新。</p>
     * 
     * @param transform 新的载具质心变换（世界坐标）
     */
    public void setTransform(Transform transform) {
        Transform currentTransform = this.transform;
        this.transform = transform.clone();
        
        // 计算相对变换：Δ = newTransform × currentTransform⁻¹
        Transform inverseCurrent = currentTransform.invert();
        MyMath.combine(transform, inverseCurrent, delta);
        
        // 将相对变换应用到所有子零件（保持绝对坐标同步）
        for (SubPartAnimatable subPart : subParts.values()) {
            Transform subPartTransform = subPart.getTransform();
            Transform newSubPartTransform = new Transform();
            MyMath.combine(subPartTransform, delta, newSubPartTransform);
            subPart.setTransform(newSubPartTransform);
        }
    }

    /**
     * 更新载具质心变换（支持插值），同步更新子零件。
     * 
     * <p>与{@link #setTransform}逻辑相同，但使用{@link SubPartAnimatable#updateTransform}
     * 以支持动画系统的插值需求。每个子零件将维护其{@code oldTransform}用于平滑过渡。</p>
     * 
     * @param transform 新的载具质心变换（世界坐标）
     */
    public void updateTransform(Transform transform) {
        Transform currentTransform = this.transform;
        this.transform = transform.clone();
        
        // 计算相对变换：Δ = newTransform × currentTransform⁻¹
        Transform inverseCurrent = currentTransform.invert();
        MyMath.combine(transform, inverseCurrent, delta);
        
        // 将相对变换应用到所有子零件，支持插值
        for (SubPartAnimatable subPart : subParts.values()) {
            Transform subPartTransform = subPart.getTransform();
            Transform newSubPartTransform = new Transform();
            MyMath.combine(subPartTransform, delta, newSubPartTransform);
            subPart.updateTransform(newSubPartTransform);
        }
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
        //返回载具质心
        return SparkMathKt.toMatrix4f(transform.toTransformMatrix());
    }
}
