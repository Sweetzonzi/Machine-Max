package io.github.tt432.machinemax.client.renderer;

import cn.solarmoon.spark_core.animation.renderer.GeoLivingEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.tt432.machinemax.common.entity.CoreEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;

public class CoreEntityRenderer extends LivingEntityRenderer<CoreEntity, MMEmptyModel<CoreEntity>> {
    public CoreEntityRenderer(EntityRendererProvider.Context context, MMEmptyModel<CoreEntity> model, float shadowRadius) {
        super(context, model, shadowRadius);
    }

    @Override
    public void render(CoreEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(CoreEntity entity) {
        return null;
    }
}
