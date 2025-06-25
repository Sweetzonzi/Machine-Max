/*
 *     MCEF (Minecraft Chromium Embedded Framework)
 *     Copyright (C) 2023 CinemaMod Group
 *
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 *
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 *     USA
 */

package io.github.sweetzonzi.machinemax.client.screen;

import com.cinemamod.mcef.MCEF;
import com.cinemamod.mcef.MCEFBrowser;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import io.github.sweetzonzi.machinemax.external.js.hook.KeyHooks;
import io.github.sweetzonzi.machinemax.web.MMWebApp;
import io.github.sweetzonzi.machinemax.web.hud.WebAppHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;

public class MMWebScreen extends Screen {
    private static final int BROWSER_DRAW_OFFSET = 20;

    private KeyHooks.EVENT reloadButton = new KeyHooks.EVENT("r");

    public MMWebScreen() {
        super(Component.literal(""));
    }

    @Override
    protected void init() {
        super.init();
        resizeBrowser();
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {

    }

    @Override
    protected void renderBlurredBackground(float partialTick) {

    }

    @Override
    public boolean isPauseScreen() {
        return false;
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

    @Override
    public void resize(Minecraft minecraft, int i, int j) {
        super.resize(minecraft, i, j);
        resizeBrowser();
    }


    @Override
    public void render(GuiGraphics guiGraphics, int i, int j, float f) {
        super.render(guiGraphics, i, j, f);
        if (MMWebApp.browser == null) return;
        if (!WebAppHud.initialed) resizeBrowser();
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

        reloadButton.OnKeyDown(() -> {
                    if (MMWebApp.browser != null) {
//                        MMWebApp.browser.reload();
                        MMWebApp.browser.close();
                        MMWebApp.browser = null;
                        WebAppHud.initialed = false;
                    }
                });
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        MMWebApp.browser.sendMousePress(mouseX(mouseX), mouseY(mouseY), button);
        MMWebApp.browser.setFocus(true);
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        MMWebApp.browser.sendMouseRelease(mouseX(mouseX), mouseY(mouseY), button);
        MMWebApp.browser.setFocus(true);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        MMWebApp.browser.sendMouseMove(mouseX(mouseX), mouseY(mouseY));
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        MMWebApp.browser.sendMouseWheel(mouseX(mouseX), mouseY(mouseY), scrollY, 0);
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        MMWebApp.browser.sendKeyPress(keyCode, scanCode, modifiers);
        MMWebApp.browser.setFocus(true);
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        MMWebApp.browser.sendKeyRelease(keyCode, scanCode, modifiers);
        MMWebApp.browser.setFocus(true);
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (codePoint == (char) 0) return false;
        MMWebApp.browser.sendKeyTyped(codePoint, modifiers);
        MMWebApp.browser.setFocus(true);
        return super.charTyped(codePoint, modifiers);
    }
}