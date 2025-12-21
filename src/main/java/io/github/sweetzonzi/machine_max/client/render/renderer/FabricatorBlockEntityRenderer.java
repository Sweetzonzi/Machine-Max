package io.github.sweetzonzi.machine_max.client.render.renderer;

import cn.solarmoon.spark_core.animation.renderer.GeoBlockEntityRenderer;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.registry.client.SparkShaders;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.sweetzonzi.machine_max.common.block.fabricator.FabricatorBlockEntity;
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
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

import java.awt.*;

public class FabricatorBlockEntityRenderer extends GeoBlockEntityRenderer<FabricatorBlockEntity> {

    public FabricatorBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(FabricatorBlockEntity blockEntity, float partialTick,
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
        // 如果正在工作，渲染制造中的物品
        if (blockEntity.getCurrentState() != FabricatorBlockEntity.State.IDLE && blockEntity.getRenderingTask() != null) {
            renderFabricatingItem(blockEntity, partialTick, poseStack, bufferSource, packedLight);
        }
    }

    private void renderFabricatingItem(FabricatorBlockEntity blockEntity, float partialTick,
                                       PoseStack poseStack, MultiBufferSource bufferSource,
                                       int packedLight) {
        // 获取渲染物
        FabricatorBlockEntity.ProductionTask task = blockEntity.getRenderingTask();
        // 计算制造进度（0-1）
        float progress = task.getProgressPercent();

        // 获取要制造的物品对应的方块状态
        ItemStack result = task.result;

        poseStack.pushPose();
        float scale = 0.3f;
        float scale2 = 0.4f * progress;
        if (result.getItem() instanceof BlockItem item) {
            poseStack.translate(0.5-scale/2, 0.2, 0.5-scale/2);// 移动到平台位置
            poseStack.pushPose();
            poseStack.scale(scale, scale, scale);// 缩放
            // 计算当前高度（从平台到挤出机）
            float currentHeight = 0.3f + 2 * scale2;
            // 使用裁剪渲染类型
            RenderType renderType = createClippedRenderType(currentHeight, poseStack.last().pose());
            // 绘制方块
            getContext().getBlockRenderDispatcher().renderSingleBlock(
                    Block.byItem(item).defaultBlockState(),
                    poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY,
                    ModelData.EMPTY, renderType
            );
        } else {
            poseStack.translate(0.5f, 0.2f, 0.5f);// 移动到平台位置
            poseStack.pushPose();
            poseStack.scale(scale, scale, scale);// 缩放
            poseStack.scale(1, scale2, 1);// 缩放
            Minecraft.getInstance().getItemRenderer().renderStatic(
                    result,
                    ItemDisplayContext.GROUND,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    poseStack,
                    bufferSource,
                    Minecraft.getInstance().level,
                    0
            );
        }
        poseStack.popPose();
        poseStack.popPose();
    }

    private RenderType createClippedRenderType(float maxHeight, Matrix4f modelMatrix) {
        // 创建自定义裁剪渲染类型（类似于Kotlin版本）
        return RenderType.create(
                "fabricator_clip",
                DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS,
                256, false, false,
                RenderType.CompositeState.builder()
                        .setShaderState(new RenderStateShard.ShaderStateShard(() -> {
                            // 使用裁剪着色器
                            ShaderInstance clipShader = SparkShaders.getH_CLIP();
                            clipShader.safeGetUniform("MaxHeight").set(maxHeight);
                            clipShader.safeGetUniform("ModelMat").set(modelMatrix);
                            return clipShader;
                        }))
                        .setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false)
        );
    }

    private ShaderInstance getClipShader() {
        // 获取或创建裁剪着色器
        // 需要创建对应的着色器文件
        return null; // 实际实现中返回着色器实例
    }
}