package io.github.tt432.machinemax.client.renderer;

import cn.solarmoon.spark_core.animation.renderer.GeoEntityRenderer;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.tt432.machinemax.common.entity.MMPartEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;

public class PartLivingEntityRenderer extends GeoEntityRenderer<MMPartEntity> {

    protected PartLivingEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull MMPartEntity entity) {
        return null;
    }

    @Override
    public void render(@NotNull MMPartEntity entity, float entityYaw, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight) {
        if (entity.part == null || entity.part.rootBody == null || entity.part.modelIndex == null) return;
        poseStack.pushPose();//开始渲染
        ModelRenderHelperKt.render(entity.part,
                new Matrix3f(),
                bufferSource.getBuffer(getRenderType(entity)),
                packedLight,
                getOverlay(entity, partialTick),
                getColor(entity, partialTick),
                partialTick);
        poseStack.popPose();//结束渲染
    }
}
