package io.github.sweetzonzi.machine_max.common.visual;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.registry.MMDamageTypes;
import io.github.sweetzonzi.machine_max.common.vehicle.PartType;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.data.PartData;
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
            for (Map.Entry<String, SubPartAttr> entry : variantAttr.subParts.entrySet()) {
                String name = entry.getKey();
                SubPartAttr attr = entry.getValue();
                SubPartAnimatable subPart = new SubPartAnimatable(variantAttr, attr, level);
                if (rootSubPart == null) rootSubPart = subPart;
                subParts.put(name, subPart);
            }
        } else MachineMax.LOGGER.error("Variant {} not found for part {}", variant, partType.getRegistryKey());
    }

    public void setTransform(Transform transform) {
        Transform rootTransform = rootSubPart.getTransform().invert();
        rootSubPart.setTransform(transform);
        Transform subPartTransform;
        for (SubPartAnimatable subPart : subParts.values()) {
            if (subPart == rootSubPart) continue;
            subPartTransform = subPart.getTransform();
            MyMath.combine(subPartTransform, rootTransform, subPartTransform);
            MyMath.combine(subPartTransform, transform, subPartTransform);
            subPart.setTransform(subPartTransform);
        }
    }

    public void updateTransform(Transform transform) {
        Transform rootTransform = rootSubPart.getTransform().invert();
        rootSubPart.updateTransform(transform);
        Transform subPartTransform;
        for (SubPartAnimatable subPart : subParts.values()) {
            if (subPart == rootSubPart) continue;
            subPartTransform = subPart.getTransform();
            MyMath.combine(subPartTransform, rootTransform, subPartTransform);
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
