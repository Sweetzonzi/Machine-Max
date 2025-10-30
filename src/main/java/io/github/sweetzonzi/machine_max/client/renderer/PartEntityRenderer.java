package io.github.sweetzonzi.machine_max.client.renderer;

import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelInstance;
import cn.solarmoon.spark_core.animation.renderer.GeoEntityRenderer;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
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
        else return ResourceLocation.withDefaultNamespace("missingno");
    }

    @Override
    public void render(@NotNull MMPartEntity entity, float entityYaw, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight) {
        if (entity.subPart == null) return;
        ModelController modelController = entity.subPart.getModelController();
        ModelInstance modelInstance = modelController.getModel();
        if (modelInstance == null) return;
        var worldMatrix = entity.subPart.getWorldPositionMatrix(partialTick);
        Color color = entity.subPart.isDestroyed() ? new Color(64, 64, 64, 255) : Color.WHITE;
        int overlay = OverlayTexture.NO_OVERLAY;
        if (entity.subPart.hurtTime > 0) overlay = OverlayTexture.pack(Math.min(entity.subPart.hurtTime, 15), 10);
        poseStack.pushPose();//开始渲染
        ModelRenderHelperKt.render(
                modelController.getOriginModel(),
                modelInstance.getPose(),
                worldMatrix,
                poseStack.last().normal(),
                bufferSource.getBuffer(RenderType.entityTranslucent(getTextureLocation(entity))),
//                bufferSource.getBuffer(RenderTypeUtil.transparentRepair(getTextureLocation(entity), false)),
//                bufferSource.getBuffer(RenderTypeUtil.pureEffect(0, 10)),
                packedLight,
                overlay,
                color.getRGB(),
                partialTick,
                false);
        poseStack.popPose();//结束渲染
    }


}
