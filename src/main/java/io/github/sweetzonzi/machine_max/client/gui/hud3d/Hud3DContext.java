package io.github.sweetzonzi.machine_max.client.gui.hud3d;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

public class Hud3DContext {

    public final Minecraft mc;
    public final LocalPlayer player;
    public final PoseStack poseStack;
    public final MultiBufferSource buffer;
    public final float partialTicks;
    public final int packedLight;
    public final Font font;
    public final ItemRenderer itemRenderer;

    /**
     * 构造一个Hud3D上下文对象
     * @param mc Minecraft客户端实例
     * @param player 本地玩家实例
     * @param poseStack 姿态栈
     * @param buffer 多缓冲源
     * @param partialTicks 部分刻度
     * @param packedLight 打包的光照信息
     */
    public Hud3DContext(
            Minecraft mc,
            LocalPlayer player,
            PoseStack poseStack,
            MultiBufferSource buffer,
            float partialTicks,
            int packedLight
    ) {
        this.mc = mc;
        this.player = player;
        this.poseStack = poseStack;
        this.buffer = buffer;
        this.partialTicks = partialTicks;
        this.packedLight = packedLight;
        this.font = mc.font;
        this.itemRenderer = mc.getItemRenderer();
    }

    /* ======================== 基础矩形 ======================== */

    /**
     * 绘制一个指定颜色的矩形（默认z坐标为0）
     * @param x1 矩形左上角x坐标
     * @param y1 矩形左上角y坐标
     * @param x2 矩形右下角x坐标
     * @param y2 矩形右下角y坐标
     * @param argb 颜色值（ARGB格式）
     */
    public void fill(float x1, float y1, float x2, float y2, int argb) {
        fill(x1, y1, x2, y2, argb, 0);
    }

    /**
     * 绘制一个指定颜色和z坐标的矩形
     * @param x1 矩形左上角x坐标
     * @param y1 矩形左上角y坐标
     * @param x2 矩形右下角x坐标
     * @param y2 矩形右下角y坐标
     * @param argb 颜色值（ARGB格式）
     * @param z z坐标（深度）
     */
    public void fill(float x1, float y1, float x2, float y2, int argb, float z) {
        float a = (argb >>> 24) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;

        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        VertexConsumer vc = buffer.getBuffer(RenderType.gui());

        vc.addVertex(matrix, x1, y2, z).setColor(r, g, b, a);
        vc.addVertex(matrix, x2, y2, z).setColor(r, g, b, a);
        vc.addVertex(matrix, x2, y1, z).setColor(r, g, b, a);
        vc.addVertex(matrix, x1, y1, z).setColor(r, g, b, a);
    }

    /* ======================== 渐变矩形 ======================== */

    /**
     * 左→右线性渐变填充矩形
     * @param x1 矩形左上角x坐标
     * @param y1 矩形左上角y坐标
     * @param x2 矩形右下角x坐标
     * @param y2 矩形右下角y坐标
     * @param leftColor 左侧颜色值（ARGB格式）
     * @param rightColor 右侧颜色值（ARGB格式）
     * @param z z坐标（深度）
     */
    public void fillGradient(
            float x1, float y1, float x2, float y2,
            int leftColor, int rightColor,
            float z
    ) {
        float la = (leftColor >>> 24) / 255f;
        float lr = ((leftColor >> 16) & 0xFF) / 255f;
        float lg = ((leftColor >> 8) & 0xFF) / 255f;
        float lb = (leftColor & 0xFF) / 255f;

        float ra = (rightColor >>> 24) / 255f;
        float rr = ((rightColor >> 16) & 0xFF) / 255f;
        float rg = ((rightColor >> 8) & 0xFF) / 255f;
        float rb = (rightColor & 0xFF) / 255f;

        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        VertexConsumer vc = buffer.getBuffer(RenderType.gui());

        vc.addVertex(matrix, x1, y2, z).setColor(lr, lg, lb, la);
        vc.addVertex(matrix, x2, y2, z).setColor(rr, rg, rb, ra);
        vc.addVertex(matrix, x2, y1, z).setColor(rr, rg, rb, ra);
        vc.addVertex(matrix, x1, y1, z).setColor(lr, lg, lb, la);
    }

    /* ======================== 文本 ======================== */

    /**
     * 绘制文本（无阴影）
     * @param text 要绘制的文本组件
     * @param x 文本左上角x坐标
     * @param y 文本左上角y坐标
     * @param color 文本颜色
     */
    public void drawText(Component text, float x, float y, int color) {
        drawText(text, x, y, color, false);
    }

    /**
     * 绘制文本
     * @param text 要绘制的文本组件
     * @param x 文本左上角x坐标
     * @param y 文本左上角y坐标
     * @param color 文本颜色
     * @param shadow 是否绘制阴影
     */
    public void drawText(Component text, float x, float y, int color, boolean shadow) {
        font.drawInBatch(
                text,
                x,
                y,
                color,
                shadow,
                poseStack.last().pose(),
                buffer,
                Font.DisplayMode.NORMAL,
                0,
                packedLight
        );
    }

    /**
     * 绘制居中文本
     * @param text 要绘制的文本组件
     * @param centerX 文本中心x坐标
     * @param y 文本左上角y坐标
     * @param color 文本颜色
     */
    public void drawCenteredText(Component text, float centerX, float y, int color) {
        drawText(text, centerX - font.width(text) / 2f, y, color);
    }

    /**
     * 绘制居中文本
     * @param text 要绘制的文本组件
     * @param centerX 文本中心x坐标
     * @param y 文本左上角y坐标
     * @param color 文本颜色
     * @param shadow 是否绘制阴影
     */
    public void drawCenteredText(Component text, float centerX, float y, int color, boolean shadow) {
        drawText(text, centerX - font.width(text) / 2f, y, color, shadow);
    }

    /**
     * 在 3D HUD 平面中绘制物品
     *
     * @param stack  物品
     * @param x      左上角 X
     * @param y      左上角 Y
     * @param scale  缩放（1 = GUI 尺寸）
     */
    public void drawItem(ItemStack stack, float x, float y, float scale) {
        if (stack.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(x, y, 0);
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.XP.rotationDegrees(180));

        itemRenderer.renderStatic(
                stack,
                ItemDisplayContext.GUI,
                packedLight,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                Minecraft.getInstance().level,
                0
        );
        poseStack.popPose();
    }
}