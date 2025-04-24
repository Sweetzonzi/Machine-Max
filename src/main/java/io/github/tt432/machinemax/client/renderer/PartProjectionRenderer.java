package io.github.tt432.machinemax.client.renderer;

import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.tt432.machinemax.common.item.prop.MMPartItem;
import io.github.tt432.machinemax.common.vehicle.PartProjection;
import io.github.tt432.machinemax.common.vehicle.PartType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Brightness;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.awt.*;

public class PartProjectionRenderer extends VisualEffectRenderer {

    public PartProjection partToAssembly = null;

    @Override
    public void tick() {
    }

    @Override
    public void physTick(@NotNull PhysicsLevel physicsLevel) {
    }

    @Override
    public void render(@NotNull Minecraft minecraft, @NotNull Vec3 camPos, @NotNull PoseStack poseStack, @NotNull MultiBufferSource multiBufferSource, float partialTick) {
        renderPartToAssembly(poseStack, multiBufferSource, partialTick);
    }

    public void renderPartToAssembly(PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (player.getMainHandItem().getItem() instanceof MMPartItem) {
            ItemStack partItem = player.getMainHandItem();
            PartType partType = MMPartItem.getPartType(partItem, player.level());
            String variant = MMPartItem.getPartAssemblyInfo(partItem, player.level()).variant();
            if (partToAssembly == null || !partType.equals(partToAssembly.type)) {
                partToAssembly = new PartProjection(partType, player.level(), variant,
                        new Transform(
                                PhysicsHelperKt.toBVector3f(player.position()),
                                Quaternion.IDENTITY
                        ));
            }
            if (!partToAssembly.variant.equals(variant)) {
                partToAssembly.setVariant(variant);
            }
            renderPartProjection(partToAssembly, poseStack, bufferSource, partialTick);
        }
    }

    public void renderPartProjection(PartProjection partProjection, PoseStack poseStack, MultiBufferSource bufferSource, float partialTick) {
        poseStack.pushPose();//开始渲染
        ModelRenderHelperKt.render(
                partProjection.getModel(),
                partProjection.getBones(),
                partProjection.getWorldPositionMatrix(partialTick),
                poseStack.last().normal(),
                bufferSource.getBuffer(RenderType.entityTranslucentEmissive(partProjection.modelIndex.getTextureLocation())),
                Brightness.FULL_BRIGHT.pack(),
                OverlayTexture.NO_OVERLAY,
                partProjection.color.getRGB(),
                partialTick);
        poseStack.popPose();//结束渲染
    }
}
