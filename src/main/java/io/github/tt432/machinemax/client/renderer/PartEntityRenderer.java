package io.github.tt432.machinemax.client.renderer;

import cn.solarmoon.spark_core.animation.renderer.GeoEntityRenderer;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.tt432.machinemax.common.entity.MMPartEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public class PartEntityRenderer extends GeoEntityRenderer<MMPartEntity> {

    protected PartEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull MMPartEntity entity) {
        if(entity.part!=null) return entity.part.modelIndex.getTextureLocation();
        else return null;
    }

    @Override
    public void render(@NotNull MMPartEntity entity, float entityYaw, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight) {
        if (entity.part == null || entity.part.rootSubPart == null || entity.part.modelIndex == null) return;
        var worldMatrix = entity.part.getWorldPositionMatrix(partialTick);
        poseStack.pushPose();//开始渲染
        ModelRenderHelperKt.render(
                entity.part.getModel(),
                entity.part.getBones(),
                worldMatrix,
                poseStack.last().normal(),
                bufferSource.getBuffer(getRenderType(entity)),
                packedLight,
                OverlayTexture.NO_OVERLAY,
                getColor(entity, partialTick),
                partialTick,
                entity.getPhysicsLevel().getPartialTicks()
                );
        poseStack.popPose();//结束渲染

    }
}
