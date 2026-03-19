package io.github.sweetzonzi.machine_max.client.render.renderer;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.ItemAnimatable;
import cn.solarmoon.spark_core.animation.model.ModelInstance;
import cn.solarmoon.spark_core.animation.renderer.IGeoRenderer;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.animation.renderer.layer.RenderLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.common.item.ICustomModelItem;
import io.github.sweetzonzi.machine_max.common.item.prop.FabricatingBlueprintItem;
import io.github.sweetzonzi.machine_max.common.item.prop.VehicleBlueprintItem;
import io.github.sweetzonzi.machine_max.common.vehicle.data.BlueprintData;
import io.github.sweetzonzi.machine_max.common.visual.PartAnimatable;
import io.github.sweetzonzi.machine_max.common.visual.VehicleAnimatable;
import io.github.sweetzonzi.machine_max.common.visual.SubPartAnimatable;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Brightness;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
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

            // 检查动画体类型，分发到对应的渲染逻辑
            if (animatable instanceof VehicleAnimatable vehicleAnimatable) {
                // VehicleAnimatable渲染逻辑：载具级别的多零件渲染
                renderVehicleAnimatable(vehicleAnimatable, customModelItem, stack, displayContext,
                        poseStack, buffer, packedLight, packedOverlay);
            } else if (animatable instanceof PartAnimatable partAnimatable) {
                // PartAnimatable渲染逻辑：部件级别的多零件渲染，支持start_bone和end_bones过滤
                renderPartAnimatable(partAnimatable, customModelItem, stack, displayContext,
                        poseStack, buffer, packedLight, packedOverlay);
            } else {
                // 原有渲染逻辑，处理ItemAnimatable等其他动画类型
                renderDefaultAnimatable(animatable, customModelItem, stack, displayContext,
                        poseStack, buffer, packedLight, packedOverlay);
            }
        }
    }

    /**
     * 渲染默认的动画对象（ItemAnimatable等），使用原有渲染逻辑
     */
    private void renderDefaultAnimatable(
            IAnimatable<?> animatable,
            ICustomModelItem customModelItem,
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        Level level = Minecraft.getInstance().level;
        ModelInstance modelInstance = animatable.getModelController().getModel();
        // 应用物品级别的变换（偏移、旋转、缩放）
        Vector3f offset = customModelItem.getRenderOffset(stack, level, displayContext);
        Vector3f rotation = customModelItem.getRenderRotation(stack, level, displayContext);
        Vector3f scale = customModelItem.getRenderScale(stack, level, displayContext);
        if (modelInstance == null) return;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        if (displayContext == ItemDisplayContext.GUI) {
            poseStack.mulPose(new Quaternionf().rotateY((float) Math.PI));
            poseStack.last().normal().rotateLocalY((float) Math.toRadians(-45.0));
            if ((customModelItem instanceof VehicleBlueprintItem
                    && VehicleBlueprintItem.getBlueprintData(stack) != BlueprintData.EMPTY_BLUEPRINT
                    && VehicleBlueprintItem.getBlueprintData(stack).getIcon() != BlueprintData.EMPTY
                    && VehicleBlueprintItem.getBlueprintData(stack).isRenderBackground())
                    || customModelItem instanceof FabricatingBlueprintItem) {
                poseStack.pushPose();
                poseStack.translate(0F, 0F, 5.5F);
                // 图标下额外渲染蓝图背景，以和装配体/部件物品做出区分
                ModelRenderHelperKt.render(
                        animatable.getModelController().getOriginModel(),
                        modelInstance.getPose(),
                        poseStack,
                        buffer.getBuffer(RenderType.entityCutout(VehicleBlueprintItem.BG_TEXTURE)),
                        Brightness.FULL_BRIGHT.pack(),
                        packedOverlay,
                        Color.WHITE.getRGB(),
                        1
                );
                poseStack.popPose();
            }
        }
        poseStack.pushPose();
        poseStack.translate(offset.x, offset.y, offset.z);
        poseStack.mulPose(new Quaternionf().rotateZYX(rotation.x, rotation.y, rotation.z));
        poseStack.scale(scale.x, scale.y, scale.z);

        ModelRenderHelperKt.render(
                animatable.getModelController().getOriginModel(),
                modelInstance.getPose(),
                poseStack,
                buffer.getBuffer(RenderType.entityCutout(animatable.getModelController().getTextureLocation())),
                packedLight,
                packedOverlay,
                customModelItem.getColor(stack, level, displayContext).getRGB(),
                1
        );
        poseStack.popPose();
        poseStack.popPose();
    }

    /**
     * 渲染PartAnimatable对象，按零件分割渲染，支持start_bone和end_bones过滤
     * 渲染效果与PartAssemblyRenderer中的组装预览保持一致
     */
    private void renderPartAnimatable(
            PartAnimatable partAnimatable,
            ICustomModelItem customModelItem,
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        Level level = Minecraft.getInstance().level;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, -0.5F);

        // 应用物品级别的变换（偏移、旋转、缩放）
        Vector3f offset = customModelItem.getRenderOffset(stack, level, displayContext);
        Vector3f rotation = customModelItem.getRenderRotation(stack, level, displayContext);
        Vector3f scale = customModelItem.getRenderScale(stack, level, displayContext);

        poseStack.translate(offset.x, offset.y, offset.z);
        poseStack.mulPose(new Quaternionf().rotateZYX(rotation.x, rotation.y, rotation.z));
        poseStack.scale(scale.x, scale.y, scale.z);

        // 遍历部件的所有零件
        for (SubPartAnimatable subPart : partAnimatable.getSubParts().values()) {
            poseStack.pushPose();
            // 应用零件的变换矩阵（使用1f作为插值系数，物品渲染通常不需要插值）
            poseStack.mulPose(subPart.getRenderWorldPositionMatrix(1f));

            // 渲染该零件的所有骨骼（已根据start_bone和end_bones过滤）
            for (OBone bone : subPart.getBones().values()) {
                ModelRenderHelperKt.render(
                        bone,
                        subPart.getModelController().getModel().getPose(),
                        poseStack,
                        buffer.getBuffer(RenderType.entityCutout(
                                subPart.getModelController().getTextureLocation())),
                        packedLight,
                        packedOverlay,
                        customModelItem.getColor(stack, Minecraft.getInstance().level, displayContext).getRGB(),
                        1f,
                        false
                );
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    /**
     * 渲染VehicleAnimatable对象，载具级别的多零件渲染。
     *
     * <p>遍历载具的所有零件（SubPartAnimatable），应用各自的变换矩阵，
     * 渲染每个零件的骨骼（已根据start_bone和end_bones过滤）。</p>
     *
     * <p>渲染逻辑与{@link #renderPartAnimatable}相似，但处理的是整个载具而非单个部件。
     * 用于载具蓝图/装配体在GUI中的3D预览（当未提供图标时）。</p>
     *
     * @param vehicleAnimatable 载具动画体对象
     * @param customModelItem   自定义模型物品接口
     * @param stack             物品堆栈
     * @param displayContext    渲染上下文
     * @param poseStack         位姿栈
     * @param buffer            渲染缓冲区
     * @param packedLight       打包的光照值
     * @param packedOverlay     打包的叠加层值
     */
    private void renderVehicleAnimatable(
            VehicleAnimatable vehicleAnimatable,
            ICustomModelItem customModelItem,
            ItemStack stack,
            ItemDisplayContext displayContext,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            int packedOverlay
    ) {
        Level level = Minecraft.getInstance().level;
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);

        // 应用物品级别的变换（偏移、旋转、缩放）
        Vector3f offset = customModelItem.getRenderOffset(stack, level, displayContext);
        Vector3f rotation = customModelItem.getRenderRotation(stack, level, displayContext);
        Vector3f scale = customModelItem.getRenderScale(stack, level, displayContext);


        if (displayContext == ItemDisplayContext.GUI) {
            if (customModelItem instanceof VehicleBlueprintItem
                    && VehicleBlueprintItem.getBlueprintData(stack).isRenderBackground()) {
                // 图标下额外渲染蓝图背景，以和装配体物品做出区分
                poseStack.pushPose();
                poseStack.mulPose(new Quaternionf().rotateY((float) Math.PI));
                poseStack.last().normal().rotateLocalY((float) Math.toRadians(-45.0));
                poseStack.translate(0, 0, 10);
                ModelRenderHelperKt.render(
                        vehicleAnimatable.getModelController().getOriginModel(),
                        vehicleAnimatable.getModelController().getModel().getPose(),
                        poseStack,
                        buffer.getBuffer(RenderType.entityCutout(VehicleBlueprintItem.BG_TEXTURE)),
                        Brightness.FULL_BRIGHT.pack(),
                        packedOverlay,
                        Color.WHITE.getRGB(),
                        1
                );
                poseStack.popPose();
            }
        }

        // 应用载具缩放比例
        float vehicleScale = vehicleAnimatable.getScale();
        poseStack.scale(vehicleScale, vehicleScale, vehicleScale);

        poseStack.translate(offset.x, offset.y, offset.z);
        poseStack.mulPose(new Quaternionf().rotateZYX(rotation.x, rotation.y, rotation.z));
        poseStack.scale(scale.x, scale.y, scale.z);

        if (displayContext == ItemDisplayContext.GUI)
            poseStack.translate(0, 0, -10); // 确保模型渲染于背景前

        // 遍历载具的所有零件
        for (SubPartAnimatable subPart : vehicleAnimatable.getSubParts().values()) {
            poseStack.pushPose();
            // 应用零件的变换矩阵（使用1f作为插值系数，物品渲染通常不需要插值）
            poseStack.mulPose(subPart.getRenderWorldPositionMatrix(1f));
            // 渲染该零件的所有骨骼（已根据start_bone和end_bones过滤）
            for (OBone bone : subPart.getBones().values()) {
                ModelRenderHelperKt.render(
                        bone,
                        subPart.getModelController().getModel().getPose(),
                        poseStack,
                        buffer.getBuffer(RenderType.entityCutout(
                                subPart.getModelController().getTextureLocation())),
                        packedLight,
                        packedOverlay,
                        customModelItem.getColor(stack, Minecraft.getInstance().level, displayContext).getRGB(),
                        1f,
                        false
                );
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    @NotNull
    @Override
    public List<RenderLayer<ItemStack, ItemAnimatable>> getLayers() {
        return List.of();
    }
}
