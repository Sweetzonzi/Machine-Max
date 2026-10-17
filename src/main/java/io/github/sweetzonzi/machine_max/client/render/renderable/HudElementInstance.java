package io.github.sweetzonzi.machine_max.client.render.renderable;

import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 一个运行时 HUD 元素，宿主布局里的一个条目对应一个实例。
 * <p>
 * 两种载体共用该接口：模型载体的 {@link GuiAnimatable} 继续负责透视与正交渲染，
 * AUI 载体的 {@link AuiHudAnimatable} 只走正交批次。{@link io.github.sweetzonzi.machine_max.client.render.gui.hud.CustomHud}
 * 只依赖这里列出的能力，透视批次再按 {@link GuiAnimatable} 收窄。
 * <p>
 * <b>调用线程：</b>客户端主线程（渲染帧）。
 */
@OnlyIn(Dist.CLIENT)
public interface HudElementInstance {

    /** 该元素是否进入透视批次；AUI 载体恒为 {@code false}。 */
    boolean isPerspective();

    /** 正交路径：由元素自己压入变换并绘制，不设置投影矩阵。 */
    void renderGui(GuiGraphics guiGraphics, float partialTick);

    /** 释放元素持有的资源（模型控制器、AUI Document 等）。 */
    void dispose();
}
