package io.github.sweetzonzi.machine_max.client.render.renderer;

import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.MMClientConfig;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Brightness;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 远程载具渲染器 — 通过 {@link RenderLevelStageEvent} 代理渲染超出实体渲染距离的远处载具。
 * <p>在 {@link RenderLevelStageEvent.Stage#AFTER_LEVEL} 阶段执行（所有渲染完成后的最后阶段，
 * 此时雾效已处理完毕，在出现雾时自动切换为该渲染器，
 * 遍历 {@link ObjectManager#clientAllVehicles} 中的所有 {@link SubPart}，
 * 跳过：已移除的、在原版实体渲染距离内的（由 {@link io.github.sweetzonzi.machine_max.client.render.renderer.PartEntityRenderer} 渲染）、
 * 超出最大渲染距离（20倍实体渲染距离）的；销毁倒计时中的（isDestroyed）仍渲染黑化/淡出效果。</p>
 * <p>使用简化渲染：{@link RenderType#entityCutout} + 与近距离载具渲染（{@link PartEntityRenderer}）一致的光照机制
 * （按 SubPart 世界位置采样方块光/天空光）；车灯（ysmGlow）骨骼与近距离一致，用 {@link RenderType#eyes} + 全亮发光；
 * 销毁倒计时中按近距离同样逻辑黑化/淡出。不处理淡入、受击闪白、线框/组装进度等状态。</p>
 */
public class DistantVehicleRenderer extends VisualEffectRenderer {

    private final Map<UUID, Integer> partUuid$LightValueCache = new ConcurrentHashMap<>();

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
        Camera camera = event.getCamera();

        Vec3 camPos = camera.getPosition();
        PoseStack poseStack = event.getPoseStack();
        // AFTER_LEVEL 的 PoseStack 为空，相机旋转在此矩阵中，需传入渲染方法
        Matrix4f modelViewMatrix = event.getModelViewMatrix();

        for (VehicleCore vehicle : ObjectManager.clientAllVehicles.values()) {;

            for (Part part : vehicle.partMap.values()) {
                for (SubPart subPart : part.subParts.values()) {
                    try {
                        Matrix4f worldMatrix = subPart.getRenderWorldPositionMatrix(partialTick);
                        Vector3f subPartPos = new Vector3f(worldMatrix.m30(), worldMatrix.m31(), worldMatrix.m32());
                        double dx = subPartPos.x() - camPos.x;
                        double dy = subPartPos.y() - camPos.y;
                        double dz = subPartPos.z() - camPos.z;
                        double distSqr = dx * dx + dy * dy + dz * dz;

                        // 原版设置渲染距离内的由老管线（PartEntityRenderer）接管，阈值 = 当前渲染距离（方块）
                        if (!FMLLoader.getDist().isClient()) return;
                        float renderDistance = Minecraft.getInstance().gameRenderer.getRenderDistance();
                        if (distSqr < (double) renderDistance * 30) continue;

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
        var modelController = subPart.getModelController();
        var modelInstance = modelController.getModel();
        if (modelInstance == null) return;

        poseStack.pushPose();
        // AFTER_LEVEL 的 PoseStack 为空，需手动乘入相机旋转矩阵，
        // 否则渲染位置不随视角转动。
        poseStack.mulPose(modelViewMatrix);
        // 抵消摄像机位置，使 worldMatrix 中的世界坐标正确映射到屏幕
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        // 应用 SubPart 的世界位姿（含平移和旋转）
        poseStack.mulPose(worldMatrix);

        // 亮度：与近距离载具渲染（PartEntityRenderer）相同机制 — 按 SubPart 世界位置采样方块光/天空光后打包
        Vector3f subPartPos = new Vector3f(worldMatrix.m30(), worldMatrix.m31(), worldMatrix.m32());
        Level level = Minecraft.getInstance().level;
        BlockPos blockPos = BlockPos.containing(subPartPos.x, subPartPos.y, subPartPos.z);
        int blockLight = level.getBrightness(LightLayer.BLOCK, blockPos);
        int skyLight = level.getBrightness(LightLayer.SKY, blockPos);
        int light = LightTexture.pack(blockLight, skyLight);
        if (light != 0) {
            partUuid$LightValueCache.put(subPart.getPart().getUuid(), light);
        } else {
            Integer cachedLight = partUuid$LightValueCache.get(subPart.getPart().getUuid());
            if (cachedLight != null) {
                light = cachedLight;
            } else return;
        }
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
        RenderType renderType = RenderType.entityCutout(texture);
        for (OBone bone : bones.values()) {
            // 车灯（ysmGlow）骨骼：与近距离渲染（PartEntityRenderer#renderTextured）相同，用发光着色器 + 全亮
            ModelRenderHelperKt.render(
                    bone,
                    modelInstance.getPose(),
                    poseStack,
                    bufferSource.getBuffer(renderType),
                    light,
                    overlay,
                    color,
                    partialTick,
                    false
            );
        }
        poseStack.popPose();
    }
}
