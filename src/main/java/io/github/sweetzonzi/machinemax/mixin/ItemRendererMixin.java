package io.github.sweetzonzi.machinemax.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machinemax.client.renderer.ICustomModelItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
abstract public class ItemRendererMixin {
//    @ModifyVariable(
//            method = "render",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Lnet/neoforged/neoforge/client/ClientHooks;handleCameraTransforms(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemDisplayContext;Z)Lnet/minecraft/client/resources/model/BakedModel;"
//            ),
//            ordinal = 0, // p_model 是第 0 个局部变量（索引从 0 开始）
//            argsOnly = true)
//    private BakedModel modifyModelBeforeTransforms(
//            BakedModel originalModel, // 当前 p_model 的值
//            ItemStack itemStack,
//            ItemDisplayContext displayContext,
//            boolean leftHand,
//            PoseStack poseStack,
//            MultiBufferSource bufferSource,
//            int combinedLight,
//            int combinedOverlay
//    ) {
//        boolean flag = displayContext == ItemDisplayContext.GUI || displayContext == ItemDisplayContext.GROUND || displayContext == ItemDisplayContext.FIXED;
//        //对于拥有自定义模型的物品，如果设置为在GUI使用贴图，则相应修改模型
//        if (flag && itemStack.getItem() instanceof ICustomModelItem customModelItem && customModelItem.use2dModel(itemStack, Minecraft.getInstance().level, displayContext)) {
//            return ((ItemRenderer) (Object) this).getItemModelShaper()
//                    .getModelManager()
//                    .getModel(customModelItem.get2dModelResourceLocation());
//        }
//        return originalModel; // 否则返回原模型
//    }

    @Inject(method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/client/ClientHooks;handleCameraTransforms(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemDisplayContext;Z)Lnet/minecraft/client/resources/model/BakedModel;",
                    shift = At.Shift.AFTER
            ), cancellable = true)
    private void distributeRender(ItemStack itemStack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight, int combinedOverlay, BakedModel p_model, CallbackInfo ci) {
        //对于拥有自定义模型的物品，跳过常规渲染流程，调用自定义渲染器渲染物品
        if (itemStack.getItem() instanceof ICustomModelItem) {
            IClientItemExtensions.of(itemStack).getCustomRenderer().renderByItem(itemStack, displayContext, poseStack, bufferSource, combinedLight, combinedOverlay);
            poseStack.popPose();
            ci.cancel();
        }
    }

//    @Inject(method = "render",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Lnet/neoforged/neoforge/client/ClientHooks;handleCameraTransforms(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemDisplayContext;Z)Lnet/minecraft/client/resources/model/BakedModel;",
//                    shift = At.Shift.AFTER
//            ), cancellable = true)
//    private void distributeRender(ItemStack itemStack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight, int combinedOverlay, BakedModel p_model, CallbackInfo ci) {
//        boolean flag = displayContext == ItemDisplayContext.GUI || displayContext == ItemDisplayContext.GROUND || displayContext == ItemDisplayContext.FIXED;
//        //对于拥有自定义模型的物品，跳过常规渲染流程，调用自定义渲染器渲染物品
//        if (itemStack.getItem() instanceof ICustomModelItem customModelItem) {
//            if (!flag || !customModelItem.use2dModel(itemStack, Minecraft.getInstance().level, displayContext)) {
//                IClientItemExtensions.of(itemStack).getCustomRenderer().renderByItem(itemStack, displayContext, poseStack, bufferSource, combinedLight, combinedOverlay);
//                poseStack.popPose();
//                ci.cancel();
//            }
//        }
//    }
}
