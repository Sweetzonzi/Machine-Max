package io.github.sweetzonzi.machine_max.mixin;

import net.minecraft.client.renderer.entity.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ItemRenderer.class)
abstract public class ItemRendererMixin {
//
//    @Inject(method = "render",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Lnet/neoforged/neoforge/client/ClientHooks;handleCameraTransforms(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/resources/model/BakedModel;Lnet/minecraft/world/item/ItemDisplayContext;Z)Lnet/minecraft/client/resources/model/BakedModel;",
//                    shift = At.Shift.AFTER
//            ), cancellable = true)
//    private void distributeRender(ItemStack itemStack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource bufferSource, int combinedLight, int combinedOverlay, BakedModel p_model, CallbackInfo ci) {
//        //对于拥有自定义模型的物品，跳过常规渲染流程，调用自定义渲染器渲染物品
//        if (itemStack.getItem() instanceof ICustomModelItem){
////        || (itemStack.getItem() instanceof BlockItem block && block.getBlock() instanceof FabricatorBlock)) {
//            IClientItemExtensions.of(itemStack).getCustomRenderer().renderByItem(itemStack, displayContext, poseStack, bufferSource, combinedLight, combinedOverlay);
//            poseStack.popPose();
//            ci.cancel();
//        }
//    }
}
