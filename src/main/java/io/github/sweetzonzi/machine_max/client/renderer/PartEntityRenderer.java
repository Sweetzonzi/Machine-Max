package io.github.sweetzonzi.machine_max.client.renderer;

import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelInstance;
import cn.solarmoon.spark_core.animation.renderer.GeoEntityRenderer;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.util.RenderTypeUtil;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.vehicle.data.BlueprintData;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
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
        var worldMatrix = entity.subPart.getWorldPositionMatrix(partialTick);
        Color color = Color.WHITE;
        if (entity.subPart.isDestroyed()) {
            color = new Color(64, 64, 64, entity.subPart.getDestroyTime() < 20 ? 255 * entity.subPart.getDestroyTime() / 20 : 255);
        } else if (entity.subPart.tickCount < 10) {
            float progress = (entity.subPart.tickCount + partialTick) / 10.0f;
            color = new Color(
                    (float) (0.5f + 0.5f * (progress + (1.0f - progress) * Math.random())),
                    (float) (0.5f + 0.5f * (progress + (1.0f - progress) * Math.random())),
                    (float) (0.5f + 0.5f * (progress + (1.0f - progress) * Math.random())),
                    (float) Math.pow(progress, 2.5f));
        }
        int overlay = OverlayTexture.NO_OVERLAY;
        if (entity.subPart.hurtTime > 0) overlay = OverlayTexture.pack(Math.min(entity.subPart.hurtTime, 15), 10);
        var pos = entity.subPart.transform.getTranslation();
        BlockPos blockpos = new BlockPos((int) pos.x, (int) pos.y, (int) pos.z);
        poseStack.pushPose();//开始渲染
        poseStack.setIdentity();
        poseStack.mulPose(worldMatrix);
        poseStack.pushPose();
        ModelRenderHelperKt.render(
                modelController.getOriginModel(),
                modelInstance.getPose(),
                poseStack.last().pose(),
                poseStack.last().normal(),
                bufferSource.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity))),
//                bufferSource.getBuffer(RenderTypeUtil.transparentRepair(getTextureLocation(entity), false)),
                LightTexture.pack(this.getBlockLightLevel(entity, blockpos), this.getSkyLightLevel(entity, blockpos)),
                overlay,
                color.getRGB(),
                partialTick,
                false);
        if (entity.subPart.tickCount < 10) { // 部件放置时的淡入效果
            poseStack.pushPose();
            poseStack.scale(1.1f, 1.1f, 1.1f);
            ModelRenderHelperKt.render(
                    modelController.getOriginModel(),
                    modelInstance.getPose(),
                    poseStack.last().pose(),
                    poseStack.last().normal(),
                    bufferSource.getBuffer(RenderTypeUtil.pureEffect(partialTick, (float) (15f * Math.sqrt((10.0 - entity.subPart.tickCount - partialTick) / 10)))),
                    LightTexture.pack(this.getBlockLightLevel(entity, blockpos), this.getSkyLightLevel(entity, blockpos)),
                    overlay,
                    color.getRGB(),
                    partialTick,
                    false);
            poseStack.popPose();
        }
        poseStack.popPose();
        poseStack.popPose();//结束渲染
    }

}
