package io.github.sweetzonzi.machine_max.client.render.renderer;

import cn.solarmoon.spark_core.animation.IBlockEntityAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimInstance;
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex;
import cn.solarmoon.spark_core.animation.model.ModelInstance;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.sweetzonzi.machine_max.common.block.fabricator.FabricatorBlock;
import io.github.sweetzonzi.machine_max.common.block.fabricator.FabricatorBlockEntity;
import io.github.sweetzonzi.machine_max.common.registry.MMBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public class BlockEntityItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final BlockEntityRenderDispatcher blockEntityRenderDispatcher;
    private final FabricatorBlockEntity fabricator = new FabricatorBlockEntity(BlockPos.ZERO, MMBlocks.getFABRICATOR_BLOCK().get().defaultBlockState());
    private boolean fabricatorReady = false;

    public BlockEntityItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.blockEntityRenderDispatcher = Minecraft.getInstance().getBlockEntityRenderDispatcher();
    }

    @Override
    public void renderByItem(
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay) {
        if (stack.getItem() instanceof BlockItem block) {
            poseStack.pushPose();
            poseStack.translate(0.5f, 0.7f / 2f, 0.5f);
            poseStack.pushPose();
            poseStack.scale(0.7f, 0.7f, 0.7f);
            poseStack.pushPose();
            if(displayContext == ItemDisplayContext.GUI)poseStack.mulPose(Axis.XP.rotationDegrees(45f));
            poseStack.pushPose();
            if(displayContext == ItemDisplayContext.GUI)poseStack.mulPose(Axis.YP.rotationDegrees(-45f));
            if (block.getBlock() instanceof FabricatorBlock) {
                if (!fabricatorReady) {
                    IBlockEntityAnimatable<FabricatorBlockEntity> animatable = fabricator;
                    animatable.getModelController().setModel(new ModelInstance(animatable, animatable.getDefaultModelIndex()));
                    // 初始化动画
                    fabricator.workAnim = new AnimInstance(fabricator, new AnimIndex(fabricator.getDefaultModelIndex(), "work"));
                    fabricator.idleAnim = new AnimInstance(fabricator, new AnimIndex(fabricator.getDefaultModelIndex(), "idle"));
                    fabricatorReady = true;
                }
                this.blockEntityRenderDispatcher.renderItem(fabricator, poseStack, buffer, packedLight, packedOverlay);
            }
            poseStack.popPose();
            poseStack.popPose();
            poseStack.popPose();
            poseStack.popPose();
        }
    }
}
