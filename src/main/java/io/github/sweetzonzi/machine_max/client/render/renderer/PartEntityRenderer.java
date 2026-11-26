package io.github.sweetzonzi.machine_max.client.render.renderer;

import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelInstance;
import cn.solarmoon.spark_core.animation.model.ModelPose;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.animation.model.origin.OCube;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.util.RenderTypeUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.client.renderer.entity.EntityRenderer;
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

public class PartEntityRenderer extends EntityRenderer<MMPartEntity> {

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
        if (entity.subPart != null) return entity.subPart.part.getModelController().getTextureLocation();
        else return BlueprintData.EMPTY;
    }

    @Override
    public boolean shouldRender(MMPartEntity entity, Frustum camera, double camX, double camY, double camZ) {
        if (entity.subPart != null) {
            if (!VehicleRenderDistance.isEntityRenderOwner(
                    entity.getX() - camX,
                    entity.getY() - camY,
                    entity.getZ() - camZ)) {
                return false;
            } else {
                // 剔除盒：普通部件取完整刚体 AABB；履带主动轮并入轮组 AABB 外扩后的履带范围
                AABB aabb = TrackRenderer.cullingAabb(entity.subPart, entity.getBoundingBoxForCulling());
                return camera.isVisible(aabb);
            }
        } else return false;
    }

    @Override
    public void render(@NotNull MMPartEntity entity, float entityYaw, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight) {
        if (entity.subPart == null) return;
        ModelController modelController = entity.subPart.part.getModelController();
        ModelInstance modelInstance = modelController.getModel();
        if (modelInstance == null) return;

        float assemblingProgress = entity.subPart.part instanceof io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart regular ? regular.getAssemblingProgress() : 1f;
        float functionalThreshold = entity.subPart.part instanceof io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart regular ? regular.type.functionalThreshold : 0f;
        boolean useWireframe = entity.subPart.part.isAssembling() && assemblingProgress < functionalThreshold;
        var worldMatrix = entity.subPart.getRenderWorldPositionMatrix(partialTick);
        var pos = entity.subPart.transform.getTranslation();
        reusableBlockPos.set((int) pos.x, (int) (pos.y + entity.getBbHeight()), (int) pos.z);
        int blockLight = this.getBlockLightLevel(entity, reusableBlockPos);
        int skyLight = this.getSkyLightLevel(entity, reusableBlockPos);
        
        poseStack.pushPose();//开始渲染
        var entityPos = entity.getPosition(partialTick);
        poseStack.translate(-entityPos.x, -entityPos.y, -entityPos.z); // 抵消实体位置带来的渲染位置偏移
        // 履带由主动轮实体一并绘制：走实体管线，履带才能继承世界光照并进入 Iris 的阴影 pass。
        // 此处姿态栈已把世界原点映射到相机相对原点，renderTrack 内部直接使用世界坐标。
        TrackRenderer.renderTrack(entity.subPart, poseStack, bufferSource, partialTick,
                LightTexture.pack(blockLight, skyLight), TrackRenderer.TrackRenderMode.MODEL);
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

        // 内构查看模式保留半透明；普通模型统一使用 cutout。
        boolean inspecting = VehicleInspectorRenderer.inspecting && isPlayerRidingSubPart(entity.subPart);
        if (inspecting) {
            color = new Color(255, 255, 255, 64).getRGB();
        }

        int light = LightTexture.pack(blockLight, skyLight);
        var bones = entity.subPart.getBones();

        if (!useWireframe) {
            renderTextured(bones, modelInstance, inspecting, light, overlay, color,
                    poseStack, bufferSource, partialTick, getTextureLocation(entity));
            PartWearRenderer.render(entity.subPart.part, bones, modelInstance.getPose(), partialTick,
                    poseStack, bufferSource, light, overlay, color, inspecting);
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
            // 发光骨骼由模型命名约定在解析期判定（见 Spark-Core OBone#shouldGlow）
            boolean glow = bone.getShouldGlow();
            ModelRenderHelperKt.render(
                    bone,
                    modelInstance.getPose(),
                    poseStack,
                    inspecting
                            ? bufferSource.getBuffer(RenderType.entityTranslucent(texture))
                            : glow
                            ? bufferSource.getBuffer(RenderType.eyes(texture))
                            : bufferSource.getBuffer(RenderType.entityCutout(texture)),
                    glow && !inspecting
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
            // applyTransformWithParents 内部为累乘（ma.mul），复用矩阵必须每次重置为单位阵，否则变换逐骨骼累积
            reusableTransform.identity();
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

    /* ==================== 线框工具 ==================== */

    /**
     * 把一组骨骼的立方体按 {@link RenderType#lines()} 描边。
     *
     * <p>未装配部件的线框与履带的缺节共用这一条路径：两者都是「设计在位、材料没成形」，
     * 表现语言相同。调用方负责把 {@code poseStack} 摆到该节的位置——本方法只叠加每根骨骼
     * 自身的父链变换。骨骼没有立方体（纯网格）时该骨骼不产生描边，调用方应改用程序化外形。</p>
     *
     * <p>{@code bone.applyTransformWithParents} 内部是累乘，因此每根骨骼都从单位阵重新开始，
     * 否则变换会逐骨骼累积。</p>
     *
     * @param bones       该节要描边的骨骼
     * @param pose        该模型的静态姿态
     * @param partialTick 帧间插值系数
     * @param poseStack   已定位到该节的姿态栈
     * @param packedLight 打包光照
     * @param color       ARGB 顶点色
     */
    public static void renderBonesAsLines(Iterable<OBone> bones, ModelPose pose, float partialTick,
                                          PoseStack poseStack, MultiBufferSource bufferSource,
                                          int packedLight, int color) {
        VertexConsumer lines = bufferSource.getBuffer(RenderType.lines());
        Matrix4f transform = new Matrix4f();
        for (OBone bone : bones) {
            if (bone.getCubes().isEmpty()) continue;
            transform.identity();
            bone.applyTransformWithParents(pose, transform, partialTick);
            poseStack.pushPose();
            poseStack.mulPose(transform);
            for (OCube cube : bone.getCubes()) {
                cube.renderVertexes(poseStack, lines, packedLight, OverlayTexture.NO_OVERLAY, color, false);
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
            // applyTransformWithParents 内部为累乘（ma.mul），复用矩阵必须每次重置为单位阵，否则变换逐骨骼累积
            reusableTransform.identity();
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
     * 子系统命中框另用连续的灰白→暖色渐变表示耐久。
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
