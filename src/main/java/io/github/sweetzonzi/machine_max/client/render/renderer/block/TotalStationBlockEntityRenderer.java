package io.github.sweetzonzi.machine_max.client.render.renderer.block;

import cn.solarmoon.spark_core.animation.renderer.GeoBlockEntityRenderer;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.common.block.total_station.TotalStationBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

import java.awt.*;

public class TotalStationBlockEntityRenderer extends GeoBlockEntityRenderer<TotalStationBlockEntity> {

    public TotalStationBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRenderOffScreen(TotalStationBlockEntity blockEntity) {
        return true;
    }

    @Override
    public void render(TotalStationBlockEntity blockEntity, float partialTick,
                       PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        if (blockEntity.hasLevel()) {
            super.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        } else {
            var modelController = blockEntity.getModelController();
            ModelRenderHelperKt.render(
                    modelController.getOriginModel(),
                    modelController.getModel().getPose(),
                    poseStack,
                    bufferSource.getBuffer(RenderType.entityTranslucent(getGeoTextureLocation(blockEntity))),
                    packedLight,
                    packedOverlay,
                    Color.WHITE.getRGB(),
                    1f,
                    true);
        }
    }
}