package io.github.sweetzonzi.machine_max.common.visual;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Transform;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.render.renderable.ModelAnimatable;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.vehicle.data.VehicleData;
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
    public final VehicleData vehicleData;
    public final ModelIndex modelIndex = new ModelIndex("part", ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "empty"));
    public final AnimController animController = new AnimController(this);
    public final ModelController modelController = new ModelController(this);

    public VehicleAnimatable(VehicleCore vehicle) {
        this(vehicle.level, new VehicleData(vehicle));
    }

    public VehicleAnimatable(Level level, VehicleData vehicleData) {
        this.level = level;
        this.vehicleData = vehicleData;
    }

    public void setTransform(Transform transform) {

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
        return new Matrix4f();
    }
}
