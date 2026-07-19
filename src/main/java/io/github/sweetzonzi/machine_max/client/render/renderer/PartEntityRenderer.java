package io.github.sweetzonzi.machine_max.client.render.renderer;

import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelInstance;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.model.origin.OCube;
import cn.solarmoon.spark_core.animation.renderer.GeoEntityRenderer;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.util.RenderTypeUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.client.MMClientConfig;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.BlueprintData;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
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
import org.joml.Matrix4f;

import java.awt.*;
import java.util.Map;

public class PartEntityRenderer extends GeoEntityRenderer<MMPartEntity> {

    /** 复用的可变 BlockPos，避免 render() 每帧分配 */
    private final BlockPos.MutableBlockPos reusableBlockPos = new BlockPos.MutableBlockPos();
    /** 复用的变换矩阵，循环内每骨骼覆写，渲染单线程安全 */
    private final Matrix4f reusableTransform = new Matrix4f();

    /** 内构查看模式 — SubPart 耐久度着色（alpha 128） */
    private static final int INSPECT_COLOR_FULL      = new Color(200, 200, 200, 128).getRGB();  // 灰 — 满耐久
    private static final int INSPECT_COLOR_YELLOW    = new Color(128, 128, 0, 128).getRGB();    // 黄
    private static final int INSPECT_COLOR_ORANGE    = new Color(128, 64, 0, 128).getRGB();     // 橙
    private static final int INSPECT_COLOR_DARK_RED  = new Color(128, 0, 0, 128).getRGB();      // 暗红
    private static final int INSPECT_COLOR_NEAR_BLACK = new Color(32, 0, 0, 128).getRGB();     // 近黑
    private static final int INSPECT_COLOR_BLACK     = new Color(0, 0, 0, 128).getRGB();        // 黑 — 已毁坏

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
        var pos = entity.subPart.transform.getTranslation();
        reusableBlockPos.set((int) pos.x, (int) pos.y, (int) pos.z);
        int blockLight = this.getBlockLightLevel(entity, reusableBlockPos);
        int skyLight = this.getSkyLightLevel(entity, reusableBlockPos);
        
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
        if (entity.subPart.tickCount >= 15) {
            renderNormal(entity, modelInstance, useWireframe, assemblingProgress,
                    blockLight, skyLight, overlay, poseStack, bufferSource, partialTick);
        } else {
            renderFadeIn(entity, modelInstance, useWireframe, assemblingProgress,
                    blockLight, skyLight, overlay, poseStack, bufferSource, partialTick);
        }

        poseStack.popPose();
        poseStack.popPose();
    }

    /* ==================== 常规渲染（tickCount >= 15） ==================== */

    /**
     * 常规渲染：处理销毁着色、内构查看覆盖，然后按 wireframe 状态分派到纹理或线框渲染。
     */
    private void renderNormal(MMPartEntity entity, ModelInstance modelInstance,
                              boolean useWireframe, float assemblingProgress,
                              int blockLight, int skyLight, int overlay,
                              PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        int color = Color.WHITE.getRGB();

        if (entity.subPart.isDestroyed()) {
            int alpha = entity.subPart.getDestroyTime() < 20 ? 255 * entity.subPart.getDestroyTime() / 20 : 255;
            if (MMClientConfig.getRenderDestroyBlackening()) {
                color = new Color(64, 64, 64, alpha).getRGB();
            } else {
                color = new Color(255, 255, 255, alpha).getRGB();
            }
        }

        // 内构查看模式：透明度渲染
        if (VehicleInspectorRenderer.inspecting && isPlayerRidingSubPart(entity.subPart)) {
            color = new Color(255, 255, 255, 64).getRGB();
        }

        int light = LightTexture.pack(blockLight, skyLight);
        var bones = entity.subPart.getBones();

        if (!useWireframe) {
            renderTextured(bones, modelInstance, false, light, overlay, color,
                    poseStack, bufferSource, partialTick, getTextureLocation(entity));
        } else {
            renderWireframe(bones, modelInstance, assemblingProgress, false, light, overlay, color,
                    poseStack, bufferSource, partialTick, getTextureLocation(entity));
        }
    }

    /**
     * 整体纹理渲染：遍历骨骼用 ModelRenderHelperKt 渲染。
     */
    private void renderTextured(Map<String, OBone> bones, ModelInstance modelInstance,
                                boolean inspecting, int light, int overlay, int color,
                                PoseStack poseStack, MultiBufferSource bufferSource,
                                float partialTick, ResourceLocation texture) {
        for (OBone bone : bones.values()) {
            boolean ysmGlow = bone.getName().toLowerCase().startsWith("ysmglow");
            ModelRenderHelperKt.render(
                    bone,
                    modelInstance.getPose(),
                    poseStack,
                    inspecting
                            ? bufferSource.getBuffer(RenderType.entityTranslucent(texture))
                            : ysmGlow
                            ? bufferSource.getBuffer(RenderType.eyes(texture))
                            : bufferSource.getBuffer(RenderType.entityTranslucent(texture)),
                    ysmGlow && !inspecting
                            ? Brightness.FULL_BRIGHT.pack()
                            : light,
                    overlay,
                    color,
                    partialTick,
                    false
            );
        }
    }

    /**
     * 线框模式渲染：按组装进度逐立方体渲染，已完成用纹理、未完成用线框。
     */
    private void renderWireframe(Map<String, OBone> bones, ModelInstance modelInstance,
                                 float assemblingProgress, boolean inspecting,
                                 int light, int overlay, int color,
                                 PoseStack poseStack, MultiBufferSource bufferSource,
                                 float partialTick, ResourceLocation texture) {
        int cubeCount = 0;
        for (OBone bone : bones.values()) {
            cubeCount += bone.getCubes().size();
        }
        float i = 0f;
        for (OBone bone : bones.values()) {
            bone.applyTransformWithParents(modelInstance.getPose(), reusableTransform, partialTick);
            poseStack.pushPose();
            poseStack.mulPose(reusableTransform);
            for (OCube cube : bone.getCubes()) {
                if (i / cubeCount >= assemblingProgress) {
                    cube.renderVertexes(poseStack,
                            bufferSource.getBuffer(RenderType.lines()),
                            light, overlay, color, false);
                } else {
                    cube.renderVertexes(poseStack,
                            bufferSource.getBuffer(inspecting
                                    ? RenderType.entityTranslucent(texture)
                                    : getCutoutOrTranslucentType(texture)),
                            light, overlay, color, false);
                }
                i++;
            }
            poseStack.popPose();
        }
    }

    /* ==================== 淡入渲染（tickCount < 15） ==================== */

    /**
     * 刚放置时的淡入效果：波动光晕 + 逐块随机颜色。
     */
    private void renderFadeIn(MMPartEntity entity, ModelInstance modelInstance,
                              boolean useWireframe, float assemblingProgress,
                              int blockLight, int skyLight, int overlay,
                              PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        int color = Color.WHITE.getRGB();
        float progress = (entity.subPart.tickCount + partialTick) / 15.0f;
        int alpha = (int) (progress * progress * 255);
        blockLight = (int) ((1 - progress) * 15 + progress * blockLight);
        skyLight = (int) ((1 - progress) * 15 + progress * skyLight);
        int light = LightTexture.pack(blockLight, skyLight);
        var bones = entity.subPart.getBones();

        // 波动效果
        poseStack.pushPose();
        poseStack.scale(1.3f, 1.3f, 1.3f);
        for (OBone bone : bones.values()) {
            ModelRenderHelperKt.render(
                    bone,
                    modelInstance.getPose(),
                    poseStack,
                    bufferSource.getBuffer(RenderTypeUtil.pureEffect(partialTick,
                            (float) (15f * Math.sqrt((17.0 - entity.subPart.tickCount - partialTick) / 17)))),
                    light, overlay, color, partialTick, false
            );
        }
        poseStack.popPose();

        // 逐块渲染，附带随机颜色
        int cubeCount = 0;
        for (OBone bone : bones.values()) {
            cubeCount += bone.getCubes().size();
        }
        float i = 0f;
        for (OBone bone : bones.values()) {
            bone.applyTransformWithParents(modelInstance.getPose(), reusableTransform, partialTick);
            poseStack.pushPose();
            poseStack.mulPose(reusableTransform);
            for (OCube cube : bone.getCubes()) {
                if (!useWireframe || i / cubeCount < assemblingProgress) {
                    Color rgb = new Color(Color.HSBtoRGB((float) Math.random(), 1 - progress * progress, 1));
                    color = new Color(rgb.getRed(), rgb.getGreen(), rgb.getBlue(), alpha).getRGB();
                    cube.renderVertexes(poseStack,
                            bufferSource.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity))),
                            light, overlay, color, false);
                } else {
                    cube.renderVertexes(poseStack,
                            bufferSource.getBuffer(RenderType.lines()),
                            light, overlay, color, false);
                }
                i++;
            }
            poseStack.popPose();
        }
    }

    private static RenderType getCutoutOrTranslucentType(ResourceLocation texture) {
        return MMClientConfig.getRenderForceTranslucentParts()
                ? RenderType.entityTranslucent(texture)
                : RenderType.entityCutout(texture);
    }

    /**
     * 检查当前客户端玩家是否乘坐了该 SubPart 所属的载具。
     * 用于内构查看模式判断——只有玩家乘坐载具的零件才需要半透明+耐久着色。
     */
    private static boolean isPlayerRidingSubPart(SubPart subPart) {
        var player = Minecraft.getInstance().player;
        if (player == null) return false;
        if (!(player instanceof IEntityMixin mixin)) return false;
        if (!(mixin.machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat)) return false;
        if (!(seat.owner instanceof SubPart ownerSubPart)) return false;
        return ownerSubPart.part.assembly == subPart.part.assembly;
    }

    /**
     * 根据 SubPart 耐久度返回内构查看模式下的着色常量。
     * 灰(满耐久) → 黄 → 橙 → 暗红 → 黑(毁坏)，alpha 固定 128。
     * 与子系统耐久配色（绿→红）形成视觉区分。
     */
    private static int getInspectColor(float durability, float maxDurability) {
        float ratio = maxDurability > 0 ? Math.clamp(durability / maxDurability, 0f, 1f) : 1f;
        if (ratio >= 1.0f)      return INSPECT_COLOR_FULL;
        else if (ratio >= 0.75f) return INSPECT_COLOR_YELLOW;
        else if (ratio >= 0.5f)  return INSPECT_COLOR_ORANGE;
        else if (ratio >= 0.25f) return INSPECT_COLOR_DARK_RED;
        else if (ratio > 0)      return INSPECT_COLOR_NEAR_BLACK;
        else                     return INSPECT_COLOR_BLACK;
    }
}
