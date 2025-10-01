package cn.solarmoon.spark_core.printer

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.renderer.GeoBlockEntityRenderer
import cn.solarmoon.spark_core.registry.client.SparkShaders
import cn.solarmoon.spark_core.util.toVec3
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderStateShard
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.world.level.block.Block
import net.neoforged.neoforge.client.model.data.ModelData
import org.joml.Matrix4f

class PrinterBlockEntityRenderer(
    context: BlockEntityRendererProvider.Context
): GeoBlockEntityRenderer<PrinterBlockEntity>(context) {

    override fun render(
        blockEntity: PrinterBlockEntity,
        partialTick: Float,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        packedOverlay: Int
    ) {
        val partialTicks = Minecraft.getInstance().timer.getGameTimeDeltaPartialTick(true)

        val flatCenter = blockEntity.modelController.model!!.pose.getSpaceLocator("flat")
        val extruder = blockEntity.modelController.model!!.pose.getSpaceLocator("extruder", partialTicks = partialTicks)
        val result = blockEntity.currentRecipe?.result ?: blockEntity.result

        if (blockEntity.workAnim.time > 1.5) {
            poseStack.pushPose()
            val h = (extruder.y - flatCenter.y)
            val scale = 4f / 16f
            poseStack.translate(flatCenter.x, flatCenter.y, flatCenter.z)
            poseStack.translate(0.5, 0.0, 0.5)
            poseStack.scale(scale, h, scale)
            poseStack.translate(-0.5, 0.0, -0.5)
            context.blockRenderDispatcher.renderSingleBlock(
                Block.byItem(result.item).defaultBlockState(),
                poseStack, bufferSource, packedLight, packedOverlay,
                ModelData.EMPTY, createClippedRenderType(h, poseStack.last().pose())
            )
            poseStack.popPose()
        }

        super.render(blockEntity, partialTick, poseStack, bufferSource, packedLight, packedOverlay)
    }

    private fun createClippedRenderType(maxHeight: Float, mat: Matrix4f): RenderType {
        return RenderType.create(
            "printer_clip",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            256,
            false,
            false,
            RenderType.CompositeState.builder()
                .setShaderState(RenderStateShard.ShaderStateShard {
                    SparkShaders.H_CLIP.apply {
                        safeGetUniform("MaxHeight").set(maxHeight)
                        safeGetUniform("ModelMat").set(mat)
                    }
                })
                .setTextureState(RenderType.BLOCK_SHEET)
                .setTransparencyState(RenderType.TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(RenderType.LIGHTMAP)
                .setWriteMaskState(RenderType.COLOR_DEPTH_WRITE)
                .setCullState(RenderType.NO_CULL)
                .createCompositeState(false)
        )
    }



}