package io.github.sweetzonzi.machine_max.client.render.renderer;

import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelInstance;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.model.origin.OCube;
import cn.solarmoon.spark_core.animation.renderer.GeoEntityRenderer;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.util.RenderTypeUtil;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.client.MMClientConfig;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.vehicle.data.BlueprintData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Brightness;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.awt.*;

public class PartEntityRenderer extends GeoEntityRenderer<MMPartEntity> {

    protected PartEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull MMPartEntity entity) {
        if (entity.subPart != null) return entity.subPart.getModelController().getTextureLocation();
        else return BlueprintData.EMPTY;
    }

    @Override
    public boolean shouldRender(MMPartEntity entity, Frustum camera, double camX, double camY, double camZ) {
        if (entity.subPart != null) {
            float renderDistance = Minecraft.getInstance().gameRenderer.getRenderDistance();
            double sqrDist = entity.distanceToSqr(camX, entity.getY(), camZ);
            if (sqrDist > renderDistance * renderDistance) {
                return false;
            } else {
                AABB aabb = entity.getBoundingBox();
                return camera.isVisible(aabb);
            }
        } else return false;
    }

    @Override
    public void render(@NotNull MMPartEntity entity, float entityYaw, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight) {
        if (entity.subPart == null) return;
        ModelController modelController = entity.subPart.getModelController();
        ModelInstance modelInstance = modelController.getModel();
        if (modelInstance == null) return;
        float assemblingProgress = entity.subPart.part.getAssemblingProgress();
        float functionalThreshold = entity.subPart.part.type.getFunctionalThreshold();
        boolean useWireframe = entity.subPart.part.shouldRenderWireframe() && assemblingProgress < functionalThreshold;
        var worldMatrix = entity.subPart.getRenderWorldPositionMatrix(partialTick);
        int color = Color.WHITE.getRGB();
        var pos = entity.subPart.transform.getTranslation();
        BlockPos blockpos = new BlockPos((int) pos.x, (int) pos.y, (int) pos.z);
        int blockLight = this.getBlockLightLevel(entity, blockpos);
        int skyLight = this.getSkyLightLevel(entity, blockpos);
        poseStack.pushPose();//开始渲染
        var entityPos = entity.getPosition(partialTick);
        poseStack.translate(-entityPos.x, -entityPos.y, -entityPos.z); // 抵消实体位置带来的渲染位置偏移
        poseStack.pushPose();
        poseStack.mulPose(worldMatrix); // 直接使用刚体位姿
        int overlay = OverlayTexture.NO_OVERLAY;
        // 受击闪烁效果
        if (MMClientConfig.getRenderHitWhitening() && entity.subPart.hurtTime > 0) {
            overlay = OverlayTexture.pack(Math.min(entity.subPart.hurtTime, 15), 10);
        }
        // 常规渲染
        if (entity.subPart.tickCount >= 15) {
            if (entity.subPart.isDestroyed()) {
                int alpha = entity.subPart.getDestroyTime() < 20 ? 255 * entity.subPart.getDestroyTime() / 20 : 255;
                if (MMClientConfig.getRenderDestroyBlackening()) {
                    color = new Color(64, 64, 64, alpha).getRGB();
                } else {
                    color = new Color(255, 255, 255, alpha).getRGB();
                }
            }
            int light = LightTexture.pack(blockLight, skyLight);
            var bones = entity.subPart.getBones();
            if (!useWireframe) {
                // 整体渲染
                for (OBone bone : bones.values()) {
                    boolean ysmGlow = bone.getName().toLowerCase().startsWith("ysmglow");
                    ModelRenderHelperKt.render(
                            bone,
                            modelInstance.getPose(),
                            poseStack,
                            ysmGlow && ! entity.subPart.isDestroyed()
                                    ? bufferSource.getBuffer(RenderType.eyes(getTextureLocation(entity)))
                                    : entity.subPart.getDestroyTime() >= 20
                                    ? bufferSource.getBuffer(getCutoutOrTranslucentType(getTextureLocation(entity)))
                                    : bufferSource.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity))),
                            ysmGlow && ! entity.subPart.isDestroyed()
                                    ? Brightness.FULL_BRIGHT.pack()
                                    : light,
                            overlay,
                            color,
                            partialTick,
                            false
                    );
                }
            } else { // 线框模式：按组装进度渲染，未完成部分显示线框
                int cubeCount = 0;
                for (OBone bone : bones.values()) {
                    cubeCount += bone.getCubes().size();
                }
                float i = 0f;
                for (OBone bone : bones.values()) {
                    Matrix4f transform = new Matrix4f();
                    bone.applyTransformWithParents(modelInstance.getPose(), transform, partialTick);
                    poseStack.pushPose();
                    poseStack.mulPose(transform);
                    for (OCube cube : bone.getCubes()) {
                        if (i / cubeCount >= assemblingProgress) {
                            cube.renderVertexes(
                                    poseStack,
                                    bufferSource.getBuffer(RenderType.lines()),
                                    light,
                                    overlay,
                                    color,
                                    false
                            );
                        } else {
                            cube.renderVertexes(
                                    poseStack,
                                    bufferSource.getBuffer(getCutoutOrTranslucentType(getTextureLocation(entity))),
                                    light,
                                    overlay,
                                    color,
                                    false
                            );
                        }
                        i++;
                    }
                    poseStack.popPose();
                }
            }
        } else {
            // 刚刚放置时的淡入效果
            float progress = (entity.subPart.tickCount + partialTick) / 15.0f;
            // 计算 alpha 值，progress 从 0 到 1，alpha 从 0 到 255
            int alpha = (int) (progress * progress * 255);
            blockLight = (int) ((1 - progress) * 15 + progress * blockLight);
            skyLight = (int) ((1 - progress) * 15 + progress * skyLight);
            int light = LightTexture.pack(blockLight, skyLight);
            var bones = entity.subPart.getBones();
            int cubeCount = 0;
            for (OBone bone : bones.values()) {
                cubeCount += bone.getCubes().size();
            }
            // 波动效果
            poseStack.pushPose();
            poseStack.scale(1.3f, 1.3f, 1.3f);
            for (OBone bone : bones.values()) {
                ModelRenderHelperKt.render(
                        bone,
                        modelInstance.getPose(),
                        poseStack,
                        bufferSource.getBuffer(RenderTypeUtil.pureEffect(partialTick, (float) (15f * Math.sqrt((17.0 - entity.subPart.tickCount - partialTick) / 17)))),
                        light,
                        overlay,
                        color,
                        partialTick,
                        false
                );
            }
            poseStack.popPose();
            // 按块渲染，附带随机颜色
            float i = 0f;
            for (OBone bone : bones.values()) {
                Matrix4f transform = new Matrix4f();
                bone.applyTransformWithParents(modelInstance.getPose(), transform, partialTick);
                poseStack.pushPose();
                poseStack.mulPose(transform);
                for (OCube cube : bone.getCubes()) {
                    if (!useWireframe || i / cubeCount < assemblingProgress) {
                        // 将 HSB 转换为 RGB
                        Color rgb = new Color(Color.HSBtoRGB((float) Math.random(), 1 - progress * progress, 1));
                        // 创建新的颜色对象，包含 alpha 值
                        color = new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), alpha).getRGB();
                        cube.renderVertexes(
                                poseStack,
                                bufferSource.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity))),
                                light,
                                overlay,
                                color,
                                false
                        );
                    } else {
                        cube.renderVertexes(
                                poseStack,
                                bufferSource.getBuffer(RenderType.lines()),
                                light,
                                overlay,
                                color,
                                false
                        );
                    }
                    i++;
                }
                poseStack.popPose();
            }
        }
        poseStack.popPose();
        poseStack.popPose();//结束渲染
    }

    private static RenderType getCutoutOrTranslucentType(ResourceLocation texture) {
        return MMClientConfig.getRenderForceTranslucentParts()
                ? RenderType.entityTranslucent(texture)
                : RenderType.entityCutout(texture);
    }
}
