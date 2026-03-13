package io.github.sweetzonzi.machine_max.common.visual;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.render.renderable.ModelAnimatable;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.PartType;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.data.PartData;
import io.github.sweetzonzi.machine_max.common.vehicle.data.SubPartData;
import io.github.sweetzonzi.machine_max.common.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Getter
public class VehicleAnimatable implements IAnimatable<VehicleAnimatable> {
    public final Level level;
    public final ModelIndex modelIndex = new ModelIndex("part", ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "empty"));
    public final AnimController animController = new AnimController(this);
    public final ModelController modelController = new ModelController(this);
    public final Map<Integer, SubPartAnimatable> subParts = new HashMap<>();
    public Transform transform = new Transform();

    public VehicleAnimatable(VehicleCore vehicle) {
        this(vehicle.level, new VehicleData(vehicle));
    }

    public VehicleAnimatable(Level level, VehicleData vehicleData) {
        this.level = level;
        this.update(vehicleData);
    }

    public void update(VehicleCore vehicle) {
        this.update(new VehicleData(vehicle));
    }

    public void update(VehicleData vehicleData) {
        subParts.clear();
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

    public void setTransform(Transform transform) {
        this.transform = transform.clone();
        //TODO: 基于质心发生的变化，改变所有SubPartAnimatable的位姿
    }

    public void updateTransform(Transform transform) {
        this.transform = transform.clone();
        //TODO: 基于质心发生的变化，更新所有SubPartAnimatable的位姿
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
