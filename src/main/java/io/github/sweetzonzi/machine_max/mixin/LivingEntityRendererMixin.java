package io.github.sweetzonzi.machine_max.mixin;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
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
        if (((IEntityMixin) entity).machine_Max$getControllingSubsystem() instanceof SeatSubsystem) {
//            poseStack.mulPose(Axis.YP.rotationDegrees(90));
        }
    }

    @Inject(method = "render*", at = @At("HEAD"))
    public void onRenderStart(T entity, float yaw, float partialTicks, PoseStack poseStack,
                              MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (((IEntityMixin) entity).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seatSubsystem) {
            // 保存当前变换状态
            poseStack.pushPose();
            poseStack.translate(0, 0.5f, 0);//移动旋转枢轴以避免大倾角时的错位
            poseStack.pushPose();

            // 应用载具的旋转和指定的缩放变换
            var actualRot = seatSubsystem.getOwner().getSubPart().getLerpedLocatorWorldTransform(
                    seatSubsystem.attr.locator, partialTicks).getRotation();
            poseStack.mulPose(SparkMathKt.toQuaternionf(actualRot));

            Vector3f passengerScale = seatSubsystem.attr.staticAttribute.passengerScale.toVector3f();
            poseStack.scale(passengerScale.x(), passengerScale.y(), passengerScale.z());

            poseStack.translate(0, -0.5f, 0);//复位枢轴点
        }
    }

    @Inject(method = "render*", at = @At("TAIL"))
    public void onRenderEnd(T entity, float yaw, float partialTicks, PoseStack poseStack,
                            MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (((IEntityMixin) entity).machine_Max$getControllingSubsystem() instanceof SeatSubsystem) {
            // 恢复变换状态
            poseStack.popPose();
            poseStack.popPose();
        }
    }

    @Inject(method = "render*", at = @At("HEAD"), cancellable = true)
    public void render(T entity, float yaw, float partialTicks, PoseStack poseStack, MultiBufferSource multiBufferSource, int light, CallbackInfo ci) {
        if (((IEntityMixin) entity).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seatSubsystem) {
            //根据座椅部件的设置，取消实体模型的渲染
            //According to the seat subsystem settings, cancel the rendering of the entity model
            if (!seatSubsystem.attr.staticAttribute.renderPassenger) ci.cancel();
        }
    }

}
