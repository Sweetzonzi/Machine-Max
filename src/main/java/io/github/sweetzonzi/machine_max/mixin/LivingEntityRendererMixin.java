package io.github.sweetzonzi.machine_max.mixin;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修正座椅乘客模型在座椅定位器旋转后的渲染朝向。
 * 朝向换算只作用于模型渲染，不回写玩家实体旋转，避免改变交互与投射物的瞄准方向。
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity> {
    /** 当前 render 调用对应的乘客；用于避免把座椅朝向修正应用到同一渲染器绘制的其他实体。 */
    @Unique
    private T machine_Max$seatRenderEntity;

    /** 当前乘客本帧实际应用到 PoseStack 的座椅定位器旋转。 */
    @Unique
    private Quaternionf machine_Max$seatRenderRotation;

    /** 调用原版旋转流程，供下面的 @Redirect 在替换 yaw 后继续执行。 */
    @Shadow
    protected abstract void setupRotations(T entity, PoseStack poseStack, float bob, float yBodyRot,
                                           float partialTick, float scale);

    /**
     * 在原版应用身体 yaw 前，将世界身体朝向换算为当前座椅定位器坐标系下的 yaw。
     * 只替换传给渲染旋转流程的参数；玩家实体的 yaw、头部相对身体的动画角度和瞄准数据均不修改。
     *
     * @param renderer 原版方法调用所属的渲染器实例（Mixin 重定向签名所需）
     * @param entity 正在渲染的生物实体
     * @param poseStack 当前模型使用的姿态矩阵栈
     * @param bob 原版身体摆动角度
     * @param yBodyRot 插值后的世界身体偏航角
     * @param partialTick 当前渲染插值进度
     * @param scale 原版模型缩放参数
     */
    @Redirect(
            method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/LivingEntityRenderer;setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V"
            )
    )
    private void machine_Max$redirectSetupRotations(LivingEntityRenderer<?, ?> renderer, T entity,
                                                     PoseStack poseStack, float bob, float yBodyRot,
                                                     float partialTick, float scale) {
        float renderBodyYaw = yBodyRot;
        if (entity == machine_Max$seatRenderEntity && machine_Max$seatRenderRotation != null) {
            renderBodyYaw = machine_Max$toSeatLocalBodyYaw(yBodyRot, machine_Max$seatRenderRotation);
        }
        setupRotations(entity, poseStack, bob, renderBodyYaw, partialTick, scale);
    }

    /**
     * 将世界水平朝向投回座椅局部水平面，再解出原版旋转函数需要的局部 yaw。
     * 定位器可以包含 pitch、roll 或倒置姿态，因此不能只用 locator 的 Y 角做相减。
     * 若目标方向与座椅局部 up 轴平行，其水平投影退化且 yaw 无法表示，此时采用局部零角作为稳定回退。
     *
     * @param worldBodyYaw 插值后的世界身体偏航角，单位为度
     * @param seatRotation 本次渲染实际应用的定位器世界旋转
     * @return 座椅局部坐标系中的身体偏航角，单位为度
     */
    @Unique
    private static float machine_Max$toSeatLocalBodyYaw(float worldBodyYaw, Quaternionf seatRotation) {
        float yawRadians = (float) Math.toRadians(worldBodyYaw);
        Vector3f localForward = new Vector3f(
                -(float) Math.sin(yawRadians), 0.0F, (float) Math.cos(yawRadians));
        new Quaternionf(seatRotation).invert().transform(localForward);

        if (localForward.x * localForward.x + localForward.z * localForward.z < 1.0E-8F) {
            // 目标方向与座椅局部 up 轴平行，单独的 yaw 无法表达该朝向。
            return 0.0F;
        }
        return Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-localForward.x, localForward.z)));
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
            machine_Max$seatRenderEntity = entity;
            machine_Max$seatRenderRotation = null;

            if (seatSubsystem.attr.staticAttribute.renderPassenger) {
                // 正常渲染乘客：应用载具的旋转和缩放变换
                var actualRot = seatSubsystem.getOwner().getSubPart().getLerpedLocatorWorldTransform(
                        seatSubsystem.attr.locator, partialTicks).getRotation();
                Quaternionf renderRotation = SparkMathKt.toQuaternionf(actualRot);
                machine_Max$seatRenderRotation = new Quaternionf(renderRotation);
                poseStack.mulPose(renderRotation);

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
            if (machine_Max$seatRenderEntity == entity) {
                machine_Max$seatRenderEntity = null;
                machine_Max$seatRenderRotation = null;
            }
        }
    }

}
