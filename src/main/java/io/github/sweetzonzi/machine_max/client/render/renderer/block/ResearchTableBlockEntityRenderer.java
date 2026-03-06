package io.github.sweetzonzi.machine_max.client.render.renderer.block;

import cn.solarmoon.spark_core.animation.renderer.GeoBlockEntityRenderer;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.common.block.research_table.ResearchTableBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

import java.awt.*;

public class ResearchTableBlockEntityRenderer extends GeoBlockEntityRenderer<ResearchTableBlockEntity> {

    public ResearchTableBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRenderOffScreen(ResearchTableBlockEntity blockEntity) {
        return true;
    }

    @Override
    public void render(ResearchTableBlockEntity blockEntity, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        if (blockEntity.hasLevel()) {
            super.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        } else {
            var modelController = blockEntity.getModelController();
            ModelRenderHelperKt.render(
                    modelController.getOriginModel(),
                    modelController.getModel().getPose(),
                    poseStack.last().pose(),
                    poseStack.last().normal(),
                    bufferSource.getBuffer(RenderType.entityTranslucent(getGeoTextureLocation(blockEntity))),
                    packedLight,
                    packedOverlay,
                    Color.WHITE.getRGB(),
                    1f,
                    true);
        }
    }
}