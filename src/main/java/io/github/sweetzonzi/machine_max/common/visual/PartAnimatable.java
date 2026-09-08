package io.github.sweetzonzi.machine_max.common.visual;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.OModel;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.PartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.PartData;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import jme3utilities.math.MyMath;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

@Getter
public class PartAnimatable implements IAnimatable<PartAnimatable> {
    public final PartType partType;
    public final Level level;
    public final Map<String, SubPartAnimatable> subParts = new HashMap<>();
    public VariantAttr variantAttr;
    public SubPartAnimatable rootSubPart;
    public final ModelIndex modelIndex = new ModelIndex("part", ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "empty"));
    public final AnimController animController = new AnimController(this);
    public final ModelController modelController = new ModelController(this);

    public PartAnimatable(Level level, PartType partType, String variant) {
        this.level = level;
        this.partType = partType;
        this.setVariant(variant);
    }

    public PartAnimatable(Level level, PartData partData) {
        this(level,
                level.isClientSide() ?
                        MMDynamicRes.PART_TYPES.get(partData.getRegistryKey()) :
                        MMDynamicRes.SERVER_PART_TYPES.get(partData.getRegistryKey()),
                partData.getVariant());
    }

    public void setVariant(String variant) {
        VariantAttr variantAttr = partType.getVariant(variant);
        if (variantAttr != null && this.variantAttr != variantAttr) {
            this.variantAttr = variantAttr;
            subParts.clear();
            rootSubPart = null;
            // 加载模型，用于还原各子部件在模型空间中的相对质心位置
            OModel model = OModel.getOrEmpty(new ModelIndex("part", variantAttr.getModel()));
            // 与实体 Part 共用同一根子部件判定（质量最大者），保证布放/预览锚点一致
            String rootName = variantAttr.getRootSubPartName();
            SubPartAttr rootAttr = rootName == null ? null : variantAttr.subParts.get(rootName);
            Vector3f rootMassCenter = rootAttr == null ? new Vector3f() : rootAttr.computeGlobalMassCenter(model);
            for (Map.Entry<String, SubPartAttr> entry : variantAttr.subParts.entrySet()) {
                String name = entry.getKey();
                SubPartAttr attr = entry.getValue();
                SubPartAnimatable subPart = new SubPartAnimatable(variantAttr, attr, level);
                if (name.equals(rootName)) {
                    // 根子部件保持单位变换（质心位于模型原点），由 setTransform/updateTransform 赋予绝对位姿
                    rootSubPart = subPart;
                } else {
                    // 其余子部件按“相对根的质心偏移”摆放，
                    // 与 Part.positionSubPartsForInternalAttach 的布放算法保持一致
                    subPart.setTransform(new Transform(
                            attr.computeGlobalMassCenter(model).subtract(rootMassCenter), Quaternion.IDENTITY));
                }
                subParts.put(name, subPart);
            }
        } else MachineMax.LOGGER.error("Variant {} not found for part {}", variant, partType.getRegistryKey());
    }

    /**
     * 以 {@link #rootSubPart} 为锚点设置整体位姿（不做插值）。
     *
     * @param transform 锚点子部件的目标质心位姿（世界坐标）
     */
    public void setTransform(Transform transform) {
        setTransform(transform, rootSubPart);
    }

    /**
     * 以指定子部件为锚点设置整体位姿（不做插值）。
     * <p>锚点的质心被放置到 {@code transform}，其余子部件施加同一刚性增量以保持相对关系。</p>
     *
     * @param transform 目标质心位姿（世界坐标）
     * @param anchor    锚点子部件，为 null 时回退到 {@link #rootSubPart}
     */
    public void setTransform(Transform transform, @Nullable SubPartAnimatable anchor) {
        if (anchor == null) anchor = rootSubPart;
        if (anchor == null) return;
        Transform anchorInverse = anchor.getTransform().invert();
        anchor.setTransform(transform);
        for (SubPartAnimatable subPart : subParts.values()) {
            if (subPart == anchor) continue;
            // 必须先克隆：combine 会就地修改，若直接传入 subPart.getTransform()，
            // 随后的 updateTransform 会把 oldTransform 指到已被改写的对象上，导致失去插值（抖动）
            Transform subPartTransform = subPart.getTransform().clone();
            MyMath.combine(subPartTransform, anchorInverse, subPartTransform);
            MyMath.combine(subPartTransform, transform, subPartTransform);
            subPart.setTransform(subPartTransform);
        }
    }

    /**
     * 以 {@link #rootSubPart} 为锚点更新整体位姿（支持插值）。
     *
     * @param transform 锚点子部件的目标质心位姿（世界坐标）
     */
    public void updateTransform(Transform transform) {
        updateTransformAnchored(transform, rootSubPart);
    }

    /**
     * 以指定名称的子部件为锚点更新整体位姿（支持插值）。
     * <p>用于连接点吸附预览：服务端 {@code AbstractConnector#adjustTransform} 锚定的是
     * 待安装连接点所属的 SubPart，因此预览也必须以该子部件为绝对锚点。</p>
     *
     * @param transform         目标质心位姿（世界坐标）
     * @param anchorSubPartName 锚点子部件名称；为空或不存在时回退到 {@link #rootSubPart}
     */
    public void updateTransform(Transform transform, @Nullable String anchorSubPartName) {
        SubPartAnimatable anchor = anchorSubPartName == null ? null : subParts.get(anchorSubPartName);
        updateTransformAnchored(transform, anchor == null ? rootSubPart : anchor);
    }

    /**
     * 以指定子部件为锚点更新整体位姿（支持插值）。
     * <p>锚点的质心被放置到 {@code transform}，其余子部件施加同一刚性增量以保持相对关系。</p>
     *
     * @param transform 目标质心位姿（世界坐标）
     * @param anchor    锚点子部件，为 null 时不做任何更新
     */
    private void updateTransformAnchored(Transform transform, @Nullable SubPartAnimatable anchor) {
        if (anchor == null) return;
        Transform anchorInverse = anchor.getTransform().invert();
        anchor.updateTransform(transform);
        for (SubPartAnimatable subPart : subParts.values()) {
            if (subPart == anchor) continue;
            // 必须先克隆：combine 会就地修改，若直接传入 subPart.getTransform()，
            // 随后的 updateTransform 会把 oldTransform 指到已被改写的对象上，导致失去插值（抖动）
            Transform subPartTransform = subPart.getTransform().clone();
            MyMath.combine(subPartTransform, anchorInverse, subPartTransform);
            MyMath.combine(subPartTransform, transform, subPartTransform);
            subPart.updateTransform(subPartTransform);
        }
    }

    @Override
    public PartAnimatable getAnimatable() {
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
        return rootSubPart == null ? new Matrix4f() : rootSubPart.getWorldPositionMatrix(number);
    }

    public @NotNull Matrix4f getRenderWorldPositionMatrix(@NotNull Number number) {
        return rootSubPart == null ? new Matrix4f() : rootSubPart.getRenderWorldPositionMatrix(number);
    }
}
