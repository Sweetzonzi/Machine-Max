package io.github.sweetzonzi.machine_max.client.renderer;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.ItemAnimatable;
import cn.solarmoon.spark_core.animation.model.ModelInstance;
import cn.solarmoon.spark_core.animation.renderer.IGeoRenderer;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.animation.renderer.layer.RenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.common.item.ICustomModelItem;
import io.github.sweetzonzi.machine_max.common.item.prop.VehicleBlueprintItem;
import io.github.sweetzonzi.machine_max.common.vehicle.data.BlueprintData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Brightness;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.awt.*;
import java.util.List;

public class CustomModelItemRenderer extends BlockEntityWithoutLevelRenderer implements IGeoRenderer<ItemStack, ItemAnimatable> {
    public CustomModelItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    private static final Vector3f AXIS_ZP = new Vector3f(0, 0, 1);
    private static final Vector3f AXIS_ZN = new Vector3f(0, 0, -1);

    @Override
    public void renderByItem(
            @NotNull ItemStack stack,
            @NotNull ItemDisplayContext displayContext,
            @NotNull PoseStack poseStack,
            @NotNull MultiBufferSource buffer,
            int packedLight,
            int packedOverlay) {
        if (stack.getItem() instanceof ICustomModelItem customModelItem) {
            IAnimatable<?> animatable = customModelItem.getRenderInstance(stack, Minecraft.getInstance().level, displayContext);
            if (animatable == null) return;
            ModelInstance modelInstance = animatable.getModelController().getModel();
            if (modelInstance == null) return;
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.5F, 0.5F);
            if (displayContext == ItemDisplayContext.GUI) {
                poseStack.mulPose(new Quaternionf().rotateY((float) Math.PI));
                poseStack.last().normal().rotateLocalY((float) Math.toRadians(-45.0));
                if (customModelItem instanceof VehicleBlueprintItem
                        && VehicleBlueprintItem.getBlueprintData(stack).getIcon() != BlueprintData.EMPTY
                        && VehicleBlueprintItem.getBlueprintData(stack).isRenderBackground()) {
                    // 图标下额外渲染蓝图背景，以和装配体做出区分
                    ModelRenderHelperKt.render(
                            animatable.getModelController().getOriginModel(),
                            modelInstance.getPose(),
                            poseStack.last().pose().translate(AXIS_ZN),
                            poseStack.last().normal(),
                            buffer.getBuffer(RenderType.entityCutout(VehicleBlueprintItem.BG_TEXTURE)),
                            Brightness.FULL_BRIGHT.pack(),
                            packedOverlay,
                            Color.WHITE.getRGB(),
                            1,
                            true
                    );
                }
            }
            ModelRenderHelperKt.render(
                    animatable.getModelController().getOriginModel(),
                    modelInstance.getPose(),
                    poseStack.last().pose()
                            .translate(customModelItem.getRenderOffset(stack, Minecraft.getInstance().level, displayContext))
                            .rotateZYX(customModelItem.getRenderRotation(stack, Minecraft.getInstance().level, displayContext))
                            .scale(customModelItem.getRenderScale(stack, Minecraft.getInstance().level, displayContext)),
                    poseStack.last().normal(),
                    buffer.getBuffer(RenderType.entityTranslucent(animatable.getModelController().getTextureLocation())),
                    Brightness.FULL_BRIGHT.pack(),
                    packedOverlay,
                    customModelItem.getColor(stack, Minecraft.getInstance().level, displayContext).getRGB(),
                    1,
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
