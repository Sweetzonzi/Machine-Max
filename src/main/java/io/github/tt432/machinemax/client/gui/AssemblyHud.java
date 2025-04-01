package io.github.tt432.machinemax.client.gui;

import cn.solarmoon.spark_core.animation.anim.play.ModelIndex;
import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.SparkMathKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.item.prop.MMPartItem;
import io.github.tt432.machinemax.common.vehicle.PartProjection;
import io.github.tt432.machinemax.common.vehicle.PartType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Brightness;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.awt.*;

public class AssemblyHud implements LayeredDraw.Layer {

    private PartProjection partToAssembly = null;

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        int height = guiGraphics.guiHeight();
        int width = guiGraphics.guiWidth();
        int centerX = width / 2;
        int centerY = height / 2;
        MultiBufferSource.BufferSource multiBufferSource = guiGraphics.bufferSource();
        PoseStack poseStack = guiGraphics.pose();

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        renderPartToAssembly(poseStack, multiBufferSource, partialTick, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(), centerX, centerY);
    }

    public void renderPartToAssembly(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTick, Vec3 camPos, int centerX, int centerY) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (player.getMainHandItem().getItem() instanceof MMPartItem) {
            ItemStack partItem = player.getMainHandItem();
            PartType partType = MMPartItem.getPartType(partItem, player.level());
            String variant = MMPartItem.getPartAssemblyInfo(partItem, player.level()).variant();
            if (partToAssembly == null || !partType.equals(partToAssembly.type)) {
                partToAssembly = new PartProjection(partType, player.level(), variant,
                        new Transform(
                                new Vector3f(),
                                Quaternion.IDENTITY
                        ));
            }
            if (!partToAssembly.variant.equals(variant)) {
                partToAssembly.setVariant(variant);
            }
            renderPartProjection(partToAssembly, poseStack, bufferSource, partialTick, camPos, centerX, centerY);
        }
    }

    public void renderPartProjection(PartProjection partProjection, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTick, Vec3 camPos, int centerX, int centerY) {
        RenderSystem.applyModelViewMatrix();
        Minecraft.getInstance().font.drawInBatch("test2", 0, 0, Color.RED.getRGB(), true, new Matrix4f().translate(centerX, centerY, 0).rotateZYX(0.4f,0.4f,0.4f),
                bufferSource, Font.DisplayMode.SEE_THROUGH, new Color(0, 0, 0, 0).getRGB(), Brightness.FULL_BRIGHT.pack());
        ModelRenderHelperKt.render(
                partProjection.getModel(),
                partProjection.getBones(),
                partProjection.getWorldPositionMatrix(partialTick).translate(camPos.toVector3f()).scale(50),
                poseStack.last().normal(),
                bufferSource.getBuffer(RenderType.entityTranslucent(partProjection.modelIndex.getTextureLocation())),
                Brightness.FULL_BRIGHT.pack(),
                OverlayTexture.NO_OVERLAY,
                partProjection.color.getRGB(),
                partialTick,
                1f
        );
        bufferSource.endBatch();
        RenderSystem.applyModelViewMatrix();
    }
}
