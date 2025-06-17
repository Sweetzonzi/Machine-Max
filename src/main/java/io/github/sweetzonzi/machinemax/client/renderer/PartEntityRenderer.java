package io.github.sweetzonzi.machinemax.client.renderer;

import cn.solarmoon.spark_core.animation.renderer.GeoEntityRenderer;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machinemax.common.entity.MMPartEntity;
import net.minecraft.client.renderer.MultiBufferSource;
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
        if (entity.part != null) return entity.part.modelIndex.getTextureLocation();
        else return ResourceLocation.withDefaultNamespace("missingno");
    }

    @Override
    public void render(@NotNull MMPartEntity entity, float entityYaw, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight) {
        if (entity.part == null || entity.part.rootSubPart == null || entity.part.modelIndex == null) return;
        var worldMatrix = entity.part.getWorldPositionMatrix(partialTick);
        Color color = entity.part.isDestroyed() ? new Color(32, 32, 32, 255) : Color.WHITE;
        int overlay = OverlayTexture.NO_OVERLAY;
        if (entity.part.hurtMarked) overlay = OverlayTexture.pack(10, 10);
        poseStack.pushPose();//开始渲染
        ModelRenderHelperKt.render(
                entity.part.getModel(),
                entity.part.getBones(),
                worldMatrix,
                poseStack.last().normal(),
                bufferSource.getBuffer(getRenderType(entity)),
                packedLight,
                overlay,
                color.getRGB(),
                partialTick,
                false);
        poseStack.popPose();//结束渲染
    }


}
