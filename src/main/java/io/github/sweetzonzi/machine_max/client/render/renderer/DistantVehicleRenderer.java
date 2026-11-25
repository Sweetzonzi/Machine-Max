package io.github.sweetzonzi.machine_max.client.render.renderer;

import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.MMClientConfig;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Brightness;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.awt.*;

/** 在 AFTER_LEVEL 阶段绘制交接距离之外的客户端载具，并为缺失实体代理的零件提供兜底。 */
public class DistantVehicleRenderer extends VisualEffectRenderer {

    @Override
    public void tick() {
        // 无需 tick 逻辑
    }

    @Override
    public void physTick(@NotNull PhysicsLevel physicsLevel) {
        // 无需物理 tick 逻辑
    }

    @Override
    public @NotNull RenderLevelStageEvent.Stage getRenderStage() {
        return RenderLevelStageEvent.Stage.AFTER_LEVEL;
    }

    @Override
    public void render(@NotNull RenderLevelStageEvent event, @NotNull MultiBufferSource bufferSource, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null) return;

        Camera camera = event.getCamera();

        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        // AFTER_LEVEL 的 PoseStack 为空，相机旋转在此矩阵中，需传入渲染方法
        Matrix4f modelViewMatrix = event.getModelViewMatrix();

        for (VehicleCore vehicle : ObjectManager.clientAllVehicles.values()) {
            if (vehicle.level != level) continue;

            for (RegularPart part : vehicle.partMap.values()) {
                for (SubPart subPart : part.subParts.values()) {
                    try {
                        Matrix4f worldMatrix = subPart.getRenderWorldPositionMatrix(partialTick);
                        Vec3 renderPosition = subPart.getPresentationPosition();
                        double dx = renderPosition.x - camPos.x;
                        double dy = renderPosition.y - camPos.y;
                        double dz = renderPosition.z - camPos.z;

                        var entity = subPart.entity;
                        boolean hasEntityProxy = entity != null && !entity.isRemoved() && entity.level() == level;
                        if (hasEntityProxy && VehicleRenderDistance.isEntityRenderOwner(dx, dy, dz)) {
                            continue;
                        }
                        renderSubPart(subPart, worldMatrix, camPos, modelViewMatrix, poseStack, bufferSource, partialTick);
                    } catch (Exception e) {
                        MachineMax.LOGGER.warn("远程载具渲染失败: subPart={}", subPart.name, e);
                    }
                }
            }
        }
    }

    private void renderSubPart(SubPart subPart, Matrix4f worldMatrix, Vec3 camPos,
                               Matrix4f modelViewMatrix, PoseStack poseStack,
                               MultiBufferSource bufferSource, float partialTick) {
        var modelController = subPart.part.getModelController();
        var modelInstance = modelController.getModel();
        if (modelInstance == null) return;

        // 远距零件不依赖所在区块的光照数据，只保留天空光，不叠加方块光。
        int light = LightTexture.FULL_SKY;

        poseStack.pushPose();
        // AFTER_LEVEL 的 PoseStack 为空，需手动乘入相机旋转矩阵，
        // 否则渲染位置不随视角转动。
        poseStack.mulPose(modelViewMatrix);
        // 抵消摄像机位置，使 worldMatrix 中的世界坐标正确映射到屏幕
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        // 履带同理：主动轮实体一并绘制，姿态栈此时已把世界原点映射到相机相对原点
        TrackRenderer.renderTrack(subPart, poseStack, bufferSource, partialTick, light,
                TrackRenderer.TrackRenderMode.PROCEDURAL);
        // 应用 SubPart 的世界位姿（含平移和旋转）
        poseStack.mulPose(worldMatrix);

        // 销毁黑化/淡出：与近距离渲染（PartEntityRenderer#renderNormal）一致，按销毁倒计时计算 alpha，
        // 配置开启时颜色渐变为黑(64,64,64)，否则保持白色仅淡出
        int color = Color.WHITE.getRGB();
        if (subPart.isDestroyed()) {
            int alpha = subPart.getDestroyTime() < 20 ? 255 * subPart.getDestroyTime() / 20 : 255;
            if (MMClientConfig.getRenderDestroyBlackening()) {
                color = new Color(64, 64, 64, alpha).getRGB();
            } else {
                color = new Color(255, 255, 255, alpha).getRGB();
            }
        }
        int overlay = OverlayTexture.NO_OVERLAY;

        var bones = subPart.getBones();
        ResourceLocation texture = modelController.getTextureLocation();
        for (OBone bone : bones.values()) {
            boolean glow = bone.getShouldGlow();
            ModelRenderHelperKt.render(
                    bone,
                    modelInstance.getPose(),
                    poseStack,
                    bufferSource.getBuffer(glow ? RenderType.eyes(texture) : RenderType.entityCutout(texture)),
                    glow ? Brightness.FULL_BRIGHT.pack() : light,
                    overlay,
                    color,
                    partialTick,
                    false
            );
        }
        poseStack.popPose();
    }
}
