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

    /**
     * 在 LivingEntityRenderer#render 开头注入，无论如何都 push 2 次并做变换。<br>
     * 与 {@link #onRenderEnd} 的 pop 2 次严格配对，确保 PoseStack 深度平衡。<br>
     * 当座椅配置了不渲染乘客（renderPassenger=false）时，通过 scale(0) 隐藏模型，<br>
     * 而不是用 ci.cancel() 跳过方法体——后者会破坏方法体内部的 push/pop 配对及 NeoForge 事件。
     */
    @Inject(method = "render*", at = @At("HEAD"))
    public void onRenderStart(T entity, float yaw, float partialTicks, PoseStack poseStack,
                              MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (((IEntityMixin) entity).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seatSubsystem) {
            // 保存当前变换状态（始终 push 2 次，与 TAIL 的 pop 2 次配对）
            poseStack.pushPose();
            poseStack.translate(0, 0.5f, 0);// 移动旋转枢轴以避免大倾角时的错位
            poseStack.pushPose();

            if (seatSubsystem.attr.staticAttribute.renderPassenger) {
                // 正常渲染乘客：应用载具的旋转和缩放变换
                var actualRot = seatSubsystem.getOwner().getSubPart().getLerpedLocatorWorldTransform(
                        seatSubsystem.attr.locator, partialTicks).getRotation();
                poseStack.mulPose(SparkMathKt.toQuaternionf(actualRot));

                Vector3f passengerScale = seatSubsystem.attr.staticAttribute.passengerScale.toVector3f();
                poseStack.scale(passengerScale.x(), passengerScale.y(), passengerScale.z());
            } else {
                // 不渲染乘客：缩放为 0 使模型不可见，但方法体正常执行，确保所有 push/pop 配对
                poseStack.scale(0, 0, 0);
            }

            poseStack.translate(0, -0.5f, 0);// 复位枢轴点
        }
    }

    /**
     * 在 LivingEntityRenderer#render 末尾注入，恢复 onRenderStart 推入的 2 层变换。<br>
     * 守卫条件与 onRenderStart 一致，确保 push/pop 严格配对。
     */
    @Inject(method = "render*", at = @At("TAIL"))
    public void onRenderEnd(T entity, float yaw, float partialTicks, PoseStack poseStack,
                            MultiBufferSource buffer, int light, CallbackInfo ci) {
        if (((IEntityMixin) entity).machine_Max$getControllingSubsystem() instanceof SeatSubsystem) {
            // 恢复变换状态（始终 pop 2 次，与 HEAD 的 push 2 次配对）
            poseStack.popPose();
            poseStack.popPose();
        }
    }

}
