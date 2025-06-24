package io.github.sweetzonzi.machinemax.client.gui;

import cn.solarmoon.spark_core.animation.renderer.ModelRenderHelperKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import io.github.sweetzonzi.machinemax.client.screen.MMWebScreen;
import io.github.sweetzonzi.machinemax.common.item.prop.MMPartItem;
import io.github.sweetzonzi.machinemax.common.vehicle.visual.PartProjection;
import io.github.sweetzonzi.machinemax.common.vehicle.PartType;
import io.github.sweetzonzi.machinemax.external.js.hook.Hook;
import io.github.sweetzonzi.machinemax.web.MMWebApp;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Brightness;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.awt.*;

@OnlyIn(Dist.CLIENT)
public class WebAppHud implements LayeredDraw.Layer {
    private static final int BROWSER_DRAW_OFFSET = 20;
    private Minecraft minecraft;
    private int width;
    private int height;
    private boolean initialed = false;
    public static boolean hidden = true;


    public enum HudStatus {
        blink,
        on,
        off
    }

    public static void setStatus(String hudTag, HudStatus status) {
        MMWebApp.sendPacket(hudTag, status.name());
    }

    @Override
    public void render(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (minecraft.screen instanceof MMWebScreen || player == null || MMWebApp.browser == null || hidden) return;
        if (! initialed || width != guiGraphics.guiWidth() || height != guiGraphics.guiHeight()) {
            width = guiGraphics.guiWidth();
            height = guiGraphics.guiHeight();
            resizeBrowser();
            initialed = true;
        }
        // 禁用深度测试（避免透明物体遮挡问题）
        RenderSystem.disableDepthTest();

        // 启用混合模式（关键修复）
        RenderSystem.enableBlend();
        // 设置混合函数：源颜色（纹理）的Alpha与目标颜色（背景）混合
//        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.defaultBlendFunc(); // 等价于 glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        // 设置着色器和纹理
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, MMWebApp.browser.getRenderer().getTextureID());

        // 绘制四边形
        Tesselator t = Tesselator.getInstance();
        BufferBuilder buffer = t.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(BROWSER_DRAW_OFFSET, height - BROWSER_DRAW_OFFSET, 0)
                .setUv(0.0f, 1.0f)
                .setColor(255, 255, 255, 255); // 白色不透明（顶点颜色不影响纹理Alpha，仅用于光照等场景）
        buffer.addVertex(width - BROWSER_DRAW_OFFSET, height - BROWSER_DRAW_OFFSET, 0)
                .setUv(1.0f, 1.0f)
                .setColor(255, 255, 255, 255);
        buffer.addVertex(width - BROWSER_DRAW_OFFSET, BROWSER_DRAW_OFFSET, 0)
                .setUv(1.0f, 0.0f)
                .setColor(255, 255, 255, 255);
        buffer.addVertex(BROWSER_DRAW_OFFSET, BROWSER_DRAW_OFFSET, 0)
                .setUv(0.0f, 0.0f)
                .setColor(255, 255, 255, 255);
        BufferUploader.drawWithShader(buffer.build());

        // 恢复状态（重要：避免影响后续渲染）
        RenderSystem.setShaderTexture(0, 0);
        RenderSystem.disableBlend(); // 关闭混合（如果后续不需要）
        RenderSystem.enableDepthTest(); // 恢复深度测试

    }
    private int mouseX(double x) {
        return (int) ((x - BROWSER_DRAW_OFFSET) * minecraft.getWindow().getGuiScale());
    }

    private int mouseY(double y) {
        return (int) ((y - BROWSER_DRAW_OFFSET) * minecraft.getWindow().getGuiScale());
    }

    private int scaleX(double x) {
        return (int) ((x - BROWSER_DRAW_OFFSET * 2) * minecraft.getWindow().getGuiScale());
    }

    private int scaleY(double y) {
        return (int) ((y - BROWSER_DRAW_OFFSET * 2) * minecraft.getWindow().getGuiScale());
    }
    private void resizeBrowser() {
        if (width > 100 && height > 100) {
            MMWebApp.browser.resize(scaleX(width), scaleY(height));
        }
    }
}
