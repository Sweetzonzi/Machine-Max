package io.github.sweetzonzi.machine_max.client.renderable;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Transform;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.common.visual.AnimatableParams;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VehicleAnimatable implements ITickableRenderable {
    public final Map<UUID, ModelAnimatable> parts = HashMap.newHashMap(4);
    public final VehicleCore vehicle;

    public Transform transform = new Transform();
    public Transform lastTransform = new Transform();

    public VehicleAnimatable(VehicleCore vehicle) {
        this.vehicle = vehicle;
        for(Map.Entry<UUID, Part> entry : vehicle.partMap.entrySet()){
            UUID uuid = entry.getKey();
            Part part = entry.getValue();
            AnimatableParams partParams = new AnimatableParams(
                    part.type.variants.get(part.variant),
                    part.type.animation,
                    part.type.textures.getFirst()
            );
            partParams.setTransform(PhysicsBodyExtensionKt.stateOf(part.rootSubPart.body).getTransform().clone());
            parts.put(uuid, new ModelAnimatable(partParams));
        }
        create();
    }

    public VehicleAnimatable(VehicleData vehicleData) {
        this(new VehicleCore(Minecraft.getInstance().level, vehicleData, true));
    }

    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTick) {
        poseStack.pushPose();
        Vector3f offset = getOffset(partialTick);
        poseStack.translate(offset.x, offset.y, offset.z);
        poseStack.pushPose();
        poseStack.mulPose(getQuaternion(partialTick));
        Vector3f scale = getScale(partialTick);
        poseStack.scale(scale.x, scale.y, scale.z);
        for (ModelAnimatable modelAnimatable : parts.values()) {
            modelAnimatable.render(poseStack, bufferSource, partialTick);
        }
        poseStack.popPose();
        poseStack.popPose();
    }

    @Override
    public void animTick() {
        if (vehicle != null) {
            // 获取并设置载具所有部件的位置姿态
            for (Map.Entry<UUID, Part> entry : vehicle.partMap.entrySet()) {
                UUID uuid = entry.getKey();
                Part part = entry.getValue();
                Transform transform = PhysicsBodyExtensionKt.stateOf(part.rootSubPart.body).getTransform().clone();
                transform.setTranslation(transform.getTranslation().subtract(PhysicsHelperKt.toBVector3f(vehicle.getPosition())));
                parts.get(uuid).params.setTransform(transform);
                //TODO: 连接点的关节姿态molang存入渲染对象的属性中
            }
        }
    }

    @Override
    public void physicsTick() {

    }
    private Vec3 getOffset() {
        return SparkMathKt.toVec3(getTransform(1).getTranslation());
    }

    public void setOffset(Vec3 offset){
        this.transform.setTranslation(PhysicsHelperKt.toBVector3f(offset));
    }

    private Vec3 getRotation() {
        return SparkMathKt.toVec3(SparkMathKt.toQuaternionf(getTransform(1).getRotation()).getEulerAnglesXYZ(new Vector3f()));
    }

    public void setQuaternion(Quaternionf quaternion){
        this.transform.setRotation(SparkMathKt.toBQuaternion(quaternion));
    }

    private Vec3 getScale() {
        return SparkMathKt.toVec3(getTransform(1).getScale());
    }

    public void setScale(Vec3 scale){
        this.transform.setScale(PhysicsHelperKt.toBVector3f(scale));
    }

    public Vector3f getOffset(float partialTick) {
        return SparkMathKt.toVector3f(getTransform(partialTick).getTranslation());
    }

    public Vector3f getRotation(float partialTick) {
        return SparkMathKt.toQuaternionf(getTransform(partialTick).getRotation()).getEulerAnglesXYZ(new Vector3f());
    }

    public Quaternionf getQuaternion(float partialTick) {
        return SparkMathKt.toQuaternionf(getTransform(partialTick).getRotation());
    }

    public Vector3f getScale(float partialTick) {
        return SparkMathKt.toVector3f(getTransform(partialTick).getScale());
    }

    public void setTransform(Transform transform) {
        this.lastTransform = this.transform;
        this.transform = transform;
    }

    public Transform getTransform(float partialTick) {
        return SparkMathKt.lerp(lastTransform, transform, partialTick);
    }
}
