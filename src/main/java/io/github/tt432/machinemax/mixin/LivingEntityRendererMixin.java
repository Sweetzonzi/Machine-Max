package io.github.tt432.machinemax.mixin;

import cn.solarmoon.spark_core.physics.SparkMathKt;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.tt432.machinemax.common.vehicle.subsystem.SeatSubsystem;
import io.github.tt432.machinemax.mixin_interface.ILivingEntityMixin;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin<T extends LivingEntity> {

    @Inject(method = "setupRotations", at = @At("TAIL"))
    public void setupRotations(T entity, PoseStack poseStack, float bob, float yBodyRot, float partialTick, float scale, CallbackInfo ci) {
        if (((ILivingEntityMixin) entity).machine_Max$getRidingSubsystem() instanceof SeatSubsystem seatSubsystem) {
            var rot = SparkMathKt.toQuaternionf(seatSubsystem.seatLocator.subPart.body.tickTransform.getRotation());
            var oldRot = SparkMathKt.toQuaternionf(seatSubsystem.seatLocator.subPart.body.lastTickTransform.getRotation());
            var actualRot = oldRot.slerp(rot, partialTick);
            poseStack.mulPose(actualRot);
        }
    }

    @Inject(method = "render*", at = @At("HEAD"), cancellable = true)
    public void render(T entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource multiBufferSource, int light, CallbackInfo ci) {
        if (((ILivingEntityMixin) entity).machine_Max$getRidingSubsystem() instanceof SeatSubsystem seatSubsystem) {
            //根据座椅部件的设置，取消实体模型的渲染
            //According to the seat subsystem settings, cancel the rendering of the entity model
            if (!seatSubsystem.attr.renderPassenger) ci.cancel();
        }
    }
}
