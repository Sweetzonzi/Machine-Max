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
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
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
//        renderRays(poseStack, partialTick, multiBufferSource.getBuffer(RenderType.dragonRays()), centerX, centerY);
        renderPartToAssembly(poseStack, multiBufferSource, partialTick, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(), centerX, centerY);
    }

    private static void renderRays(PoseStack poseStack, float dragonDeathCompletion, VertexConsumer buffer, int x, int y) {
        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        poseStack.scale(5.0F, 5.0F, 1.0F);
        float f = Math.min(dragonDeathCompletion > 0.8F ? (dragonDeathCompletion - 0.8F) / 0.2F : 0.0F, 1.0F);
        int i = FastColor.ARGB32.colorFromFloat(1.0F, 1.0F, 1.0F, 1.0F);
        int j = Color.HSBtoRGB(5f, 1.0f, 0.1f);
        RandomSource randomsource = RandomSource.create(432L);
        org.joml.Vector3f vector3f = new org.joml.Vector3f();
        org.joml.Vector3f vector3f1 = new org.joml.Vector3f();
        org.joml.Vector3f vector3f2 = new org.joml.Vector3f();
        org.joml.Vector3f vector3f3 = new org.joml.Vector3f();
        Quaternionf quaternionf = new Quaternionf();
        int k = Mth.floor((dragonDeathCompletion + dragonDeathCompletion * dragonDeathCompletion) / 2.0F * 60.0F);
        for (int l = 0; l < k; l++) {
            quaternionf.rotationXYZ(
                            randomsource.nextFloat() * (float) (Math.PI * 2),
                            randomsource.nextFloat() * (float) (Math.PI * 2),
                            randomsource.nextFloat() * (float) (Math.PI * 2)
                    )
                    .rotateXYZ(
                            randomsource.nextFloat() * (float) (Math.PI * 2),
                            randomsource.nextFloat() * (float) (Math.PI * 2),
                            randomsource.nextFloat() * (float) (Math.PI * 2) + dragonDeathCompletion * (float) (Math.PI / 2)
                    );
            poseStack.mulPose(quaternionf);
            float f1 = randomsource.nextFloat() * 20.0F + 5.0F + f * 10.0F;
            float f2 = randomsource.nextFloat() * 2.0F + 1.0F + f * 2.0F;
            vector3f1.set(-1 * f2, f1, -0.5F * f2);
            vector3f2.set(1 * f2, f1, -0.5F * f2);
            vector3f3.set(0.0F, f1, f2);
            PoseStack.Pose posestack$pose = poseStack.last();
            buffer.addVertex(posestack$pose, vector3f).setColor(i);
            buffer.addVertex(posestack$pose, vector3f1).setColor(j);
            buffer.addVertex(posestack$pose, vector3f2).setColor(j);
            buffer.addVertex(posestack$pose, vector3f).setColor(i);
            buffer.addVertex(posestack$pose, vector3f2).setColor(j);
            buffer.addVertex(posestack$pose, vector3f3).setColor(j);
            buffer.addVertex(posestack$pose, vector3f).setColor(i);
            buffer.addVertex(posestack$pose, vector3f3).setColor(j);
            buffer.addVertex(posestack$pose, vector3f1).setColor(j);
        }

        poseStack.popPose();
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
        Minecraft.getInstance().font.drawInBatch("test2", 0, 0, Color.RED.getRGB(), true, new Matrix4f().translate(centerX, centerY, 0).rotateZYX(0.4f, 0.4f, 0.4f),
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
                partialTick
        );
        bufferSource.endBatch();
        RenderSystem.applyModelViewMatrix();
    }
}
