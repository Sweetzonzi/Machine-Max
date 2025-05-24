package io.github.sweetzonzi.machinemax.client.renderer;

import cn.solarmoon.spark_core.animation.ItemAnimatable;
import cn.solarmoon.spark_core.animation.renderer.IGeoRenderer;
import cn.solarmoon.spark_core.animation.renderer.layer.RenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machinemax.MachineMax;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

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
            if (displayContext == ItemDisplayContext.GUI) {
                super.renderByItem(stack, displayContext, poseStack, buffer, packedLight, packedOverlay);
            } else {
                ItemAnimatable itemAnimatable = customModelItem.getRenderInstance(stack, Minecraft.getInstance().level, displayContext);
                if (itemAnimatable == null) return;
                this.render(itemAnimatable, 0, itemAnimatable.getPartialTicks(), poseStack, buffer, packedLight);
            }
        }
    }

    @NotNull
    @Override
    public List<RenderLayer<ItemStack, ItemAnimatable>> getLayers() {
        return List.of();
    }
}
