package io.github.tt432.machinemax.client.renderer;

import cn.solarmoon.spark_core.animation.renderer.GeoEntityRenderer;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.tt432.machinemax.common.entity.MMPartEntity;
import io.github.tt432.machinemax.common.item.prop.MMPartItem;
import io.github.tt432.machinemax.common.vehicle.PartProjection;
import io.github.tt432.machinemax.common.vehicle.PartType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Brightness;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.awt.*;

public class PartEntityRenderer extends GeoEntityRenderer<MMPartEntity> {

    protected PartEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull MMPartEntity entity) {
        if(entity.part!=null) return entity.part.modelIndex.getTextureLocation();
        else return null;
    }

    @Override
    public void render(@NotNull MMPartEntity entity, float entityYaw, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource bufferSource, int packedLight) {
        if (entity.part == null || entity.part.rootSubPart == null || entity.part.modelIndex == null) return;
        var worldMatrix = entity.part.getWorldPositionMatrix(partialTick);
        poseStack.pushPose();//开始渲染
        ModelRenderHelperKt.render(
                entity.part.getModel(),
                entity.part.getBones(),
                worldMatrix,
                poseStack.last().normal(),
                bufferSource.getBuffer(getRenderType(entity)),
                packedLight,
                OverlayTexture.NO_OVERLAY,
                new Color(255,255,255,255).getRGB(),
                partialTick);
        poseStack.popPose();//结束渲染
    }


}
