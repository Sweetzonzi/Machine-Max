package io.github.sweetzonzi.machine_max.client.render.renderable;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.sweetzonzi.machine_max.common.visual.ScopeBehavior;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 一个运行时 HUD 元素，宿主布局里的一个条目对应一个实例。
 * <p>
 * 模型载体 {@link GuiAnimatable} 与页面载体 {@link AuiHudAnimatable} 共用投放契约。
 * {@link io.github.sweetzonzi.machine_max.client.render.gui.hud.CustomHud} 管理透视投影与
 * 炮镜跟随变换，再按 {@code (offset.x, -offset.y, offset.z)} 平移到元素原点。
 * 元素在该原点内应用自己的旋转、缩放和内容坐标适配。
 * <p>
 * <b>调用线程：</b>客户端主线程（渲染帧）。
 */
@OnlyIn(Dist.CLIENT)
public interface HudElementInstance {

    /** 该元素是否进入透视批次。 */
    boolean isPerspective();

    /** 正交路径：由元素自己压入变换并绘制，不设置投影矩阵。 */
    void renderGui(GuiGraphics guiGraphics, float partialTick);

    /** 透视投放的相机空间偏移；正 X 向右、正 Y 向下、负 Z 位于相机前方。 */
    Vector3f getOffset(float partialTick);

    /** 布局旋转，角度按 Z/Y/X 顺序合成为四元数。 */
    Quaternionf getQuaternion(float partialTick);

    ScopeBehavior scopeBehavior();

    boolean ignoreZoom();

    /** 在外层给定的透视投影和元素原点内绘制，负责自身旋转及缩放，不重复应用 offset。 */
    void renderContent(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, float partialTick);

    /** 释放元素持有的资源（模型控制器、AUI Document 等）。 */
    void dispose();
}
