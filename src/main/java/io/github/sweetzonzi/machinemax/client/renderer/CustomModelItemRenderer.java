package io.github.sweetzonzi.machinemax.client.renderer;

import cn.solarmoon.spark_core.animation.ItemAnimatable;
import cn.solarmoon.spark_core.animation.renderer.IGeoRenderer;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.animation.renderer.layer.RenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machinemax.common.item.ICustomModelItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Brightness;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

import java.awt.*;
import java.util.List;

public class CustomModelItemRenderer extends BlockEntityWithoutLevelRenderer implements IGeoRenderer<ItemStack, ItemAnimatable> {
    public CustomModelItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(
            @NotNull ItemStack stack,
            @NotNull ItemDisplayContext displayContext,
            @NotNull PoseStack poseStack,
            @NotNull MultiBufferSource buffer,
            int packedLight,
            int packedOverlay) {
        if (stack.getItem() instanceof ICustomModelItem customModelItem) {
            ItemAnimatable itemAnimatable = customModelItem.getRenderInstance(stack, Minecraft.getInstance().level, displayContext);
            if (itemAnimatable == null) return;
            poseStack.pushPose();
            if (displayContext == ItemDisplayContext.GUI) poseStack.mulPose(new Quaternionf().rotateY((float) Math.PI));
            ModelRenderHelperKt.render(
                    itemAnimatable.getModel(),
                    itemAnimatable.getBones(),
                    poseStack.last().pose()
                            .translate(customModelItem.getRenderOffset(stack, Minecraft.getInstance().level, displayContext))
                            .rotateZYX(customModelItem.getRenderRotation(stack, Minecraft.getInstance().level, displayContext))
                            .scale(customModelItem.getRenderScale(stack, Minecraft.getInstance().level, displayContext)),
                    poseStack.last().normal(),
                    buffer.getBuffer(RenderType.entityTranslucent(itemAnimatable.getModelIndex().getTextureLocation())),
                    Brightness.FULL_BRIGHT.pack(),
                    packedOverlay,
                    customModelItem.getColor(stack, Minecraft.getInstance().level, displayContext).getRGB(),
                    itemAnimatable.getPartialTicks(),
                    true
            );
            poseStack.popPose();
        }
    }

    @NotNull
    @Override
    public List<RenderLayer<ItemStack, ItemAnimatable>> getLayers() {
        return List.of();
    }
}
