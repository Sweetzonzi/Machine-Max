package io.github.sweetzonzi.machine_max.client.render.renderer.block;

import cn.solarmoon.spark_core.animation.renderer.GeoBlockEntityRenderer;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.registry.client.SparkShaders;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.sweetzonzi.machine_max.common.block.fabricator.FabricatorBlockEntity;
import io.github.sweetzonzi.machine_max.common.block.research_table.ResearchTableBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

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
        // 先渲染基础模型
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