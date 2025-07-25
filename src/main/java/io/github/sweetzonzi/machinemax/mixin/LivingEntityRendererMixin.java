package io.github.sweetzonzi.machinemax.mixin;

import cn.solarmoon.spark_core.physics.SparkMathKt;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machinemax.mixin_interface.IEntityMixin;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin<T extends LivingEntity> {

    @Inject(method = "setupRotations", at = @At("TAIL"))
    public void setupRotations(T entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale, CallbackInfo ci) {
        if (((IEntityMixin) entity).machine_Max$getRidingSubsystem() instanceof SeatSubsystem seatSubsystem) {
            var actualRot = seatSubsystem.getPart().getLerpedLocatorWorldTransform(seatSubsystem.attr.locator, partialTick).getRotation();
            poseStack.mulPose(SparkMathKt.toQuaternionf(actualRot));
            Vector3f passengerScale = seatSubsystem.attr.passengerScale.toVector3f();
            poseStack.scale(passengerScale.x(), passengerScale.y(), passengerScale.z());
        }
    }

    @Inject(method = "render*", at = @At("HEAD"), cancellable = true)
    public void render(T entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource multiBufferSource, int light, CallbackInfo ci) {
        if (((IEntityMixin) entity).machine_Max$getRidingSubsystem() instanceof SeatSubsystem seatSubsystem) {
            //根据座椅部件的设置，取消实体模型的渲染
            //According to the seat subsystem settings, cancel the rendering of the entity model
            if (!seatSubsystem.attr.renderPassenger) ci.cancel();
        }
    }

//    @ModifyVariable(
//            method = "render*",
//            at = @At(value = "STORE"),
//            ordinal = 0
//    )
//    private boolean modifyShouldSit(boolean original) {
//        // 修改 shouldSit 的值
//        return true;
//    }
}
