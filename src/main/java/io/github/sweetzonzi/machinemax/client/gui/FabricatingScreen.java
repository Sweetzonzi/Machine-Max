package io.github.sweetzonzi.machinemax.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.client.renderable.GuiAnimatable;
import io.github.sweetzonzi.machinemax.common.visual.AnimatableParams;
import io.github.sweetzonzi.machinemax.common.crafting.FabricatingMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;

import java.awt.*;
import java.util.Map;

/**
 * 参考自TACZ的GunSmithTableScreen
 */
@OnlyIn(Dist.CLIENT)
public class FabricatingScreen extends AbstractContainerScreen<FabricatingMenu> {
    public FabricatingScreen(FabricatingMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();
        //TODO:添加Widgets
        addRenderableOnly(new GuiAnimatable(
                new AnimatableParams(
                        ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "gui/speed_hud.geo"),
                        ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "gui"),
                        ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "textures/gui/speed_hud.png"),
                        new Vec3(0, 0, -100),
                        false,
                        Map.of())));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBlurredBackground(partialTick);
//        this.renderBg(guiGraphics, partialTick, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        //不渲染标题栏
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        LocalPlayer player = Minecraft.getInstance().player;
        int height = guiGraphics.guiHeight();
        int width = guiGraphics.guiWidth();
        int centerX = width / 2;
        int centerY = height / 2;
        MultiBufferSource.BufferSource multiBufferSource = guiGraphics.bufferSource();
        PoseStack poseStack = guiGraphics.pose();
//        renderRays(poseStack, (float) Math.random(), multiBufferSource.getBuffer(RenderType.dragonRays()), centerX, centerY);
    }

    public static void renderRectangle(PoseStack poseStack, float dragonDeathCompletion, VertexConsumer buffer, int x, int y) {
        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        poseStack.scale(5.0F, 5.0F, 1.0F);
        float f = Math.min(dragonDeathCompletion > 0.8F ? (dragonDeathCompletion - 0.8F) / 0.2F : 0.0F, 1.0F);
        int i = FastColor.ARGB32.colorFromFloat(1.0F, 1.0F, 1.0F, 1.0F);
        int j = Color.HSBtoRGB(5f, 1.0f, 0.1f);
        org.joml.Vector3f vector3f1 = new org.joml.Vector3f(-1, -1, 0);
        org.joml.Vector3f vector3f2 = new org.joml.Vector3f(1, -1, 0);
        org.joml.Vector3f vector3f3 = new org.joml.Vector3f(1, 1, 0);
        org.joml.Vector3f vector3f4 = new org.joml.Vector3f(-1, 1, 0);

        // 根据dragonDeathCompletion调整颜色
        int color1 = i; // 矩形的顶点颜色可以按照需求调整
        int color2 = j;

        // 绘制矩形的两个三角形
        PoseStack.Pose posestack$pose = poseStack.last();
        buffer.addVertex(posestack$pose, vector3f1).setColor(color1);
        buffer.addVertex(posestack$pose, vector3f2).setColor(color2);
        buffer.addVertex(posestack$pose, vector3f3).setColor(color2);

        buffer.addVertex(posestack$pose, vector3f1).setColor(color1);
        buffer.addVertex(posestack$pose, vector3f3).setColor(color2);
        buffer.addVertex(posestack$pose, vector3f4).setColor(color2);
        // 绘制矩形的两个三角形（背面）
        buffer.addVertex(posestack$pose, vector3f1).setColor(color1);
        buffer.addVertex(posestack$pose, vector3f3).setColor(color2);
        buffer.addVertex(posestack$pose, vector3f2).setColor(color2);

        buffer.addVertex(posestack$pose, vector3f1).setColor(color1);
        buffer.addVertex(posestack$pose, vector3f4).setColor(color2);
        buffer.addVertex(posestack$pose, vector3f3).setColor(color2);
        poseStack.popPose();
    }


    public static void renderRays(PoseStack poseStack, float dragonDeathCompletion, VertexConsumer buffer, int x, int y) {
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
        k = 1;
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

}
