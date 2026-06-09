package io.github.sweetzonzi.machine_max.client.render.gui.hud;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sweetzonzi.machine_max.client.input.CameraController;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.CameraSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SightSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CameraSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.util.ScreenProjectionUtil;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * 瞄准镜 HUD：仅在炮镜模式且当前激活的摄像机为 SightSubsystem 时渲染。<p>
 * 准星不绘制于屏幕中心，而是获取炮管世界瞄准点（locator 前方远处一点），
 * 通过 {@link ScreenProjectionUtil#worldToScreenOffset} 投影到屏幕空间，
 * 反映炮管在画面中的真实位置。稳定器和非稳定器模式均适用。
 */
@OnlyIn(Dist.CLIENT)
public class SightHud implements LayeredDraw.Layer {
    // ===== 炮管十字线参数 =====
    /** 十字线半长（像素） */
    private static final int CROSSHAIR_HALF_LENGTH = 8;
    /** 十字线间隙（中心留空半长，像素） */
    private static final int CROSSHAIR_GAP = 3;
    /** 十字线线宽（像素） */
    private static final int CROSSHAIR_THICKNESS = 2;
    // ===== 中心圆环参数 =====
    /** 中心圆环外半径（像素） */
    private static final float CENTER_RING_OUTER_RADIUS = 6f;
    /** 中心圆环内半径（像素），外-内 = 环宽 */
    private static final float CENTER_RING_INNER_RADIUS = 4f;
    /** 圆环分段数（越多越平滑） */
    private static final int RING_SEGMENTS = 48;
    /** 颜色分量（白色） */
    private static final float COLOR_R = 1.0f;
    private static final float COLOR_G = 1.0f;
    private static final float COLOR_B = 1.0f;
    private static final float COLOR_A = 1.0f;
    /** 十字线颜色（白色） */
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    /** 准星距离屏幕边缘的最小边距（像素），超出 FOV 时贴边显示 */
    private static final int EDGE_MARGIN = 16;

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, @NotNull DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;

        // 仅在炮镜模式且激活的摄像机是 SightSubsystem 时渲染
        if (!CameraController.isCameraMode()) return;
        CameraSubsystem camera = CameraController.getActiveCamera();
        if (!(camera instanceof SightSubsystem)) return;

        int screenW = guiGraphics.guiWidth();
        int screenH = guiGraphics.guiHeight();
        int centerX = screenW / 2;
        int centerY = screenH / 2;

        // 屏幕中心绘制圆环（视角参考点），炮管位置绘制十字线
        renderCenterRing(guiGraphics, centerX, centerY);
        // 获取炮管世界瞄准点（唯一真相源），投影到屏幕空间
        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(false);
        Vec3 barrelAimPoint = CameraController.getCameraAimPointWorld(partialTick);
        if (barrelAimPoint == null) return;

        Camera mcCam = mc.gameRenderer.getMainCamera();
        float vFov = CameraSubsystemStaticAttr.REFERENCE_FOV / CameraController.getCurrentZoom();

        float[] offset = ScreenProjectionUtil.worldToScreenOffset(
                barrelAimPoint, mcCam, vFov, screenW, screenH);
        if (offset == null) return;

        int crossX = Math.clamp(centerX + Math.round(offset[0]), EDGE_MARGIN, screenW - EDGE_MARGIN);
        int crossY = Math.clamp(centerY + Math.round(offset[1]), EDGE_MARGIN, screenH - EDGE_MARGIN);

        renderCrosshair(guiGraphics, crossX, crossY);
    }

    private void renderCrosshair(GuiGraphics guiGraphics, int cx, int cy) {
        // 水平线（左侧）
        guiGraphics.fill(
                cx - CROSSHAIR_HALF_LENGTH, cy - CROSSHAIR_THICKNESS / 2,
                cx - CROSSHAIR_GAP, cy + (CROSSHAIR_THICKNESS + 1) / 2,
                COLOR_WHITE);
        // 水平线（右侧）
        guiGraphics.fill(
                cx + CROSSHAIR_GAP, cy - CROSSHAIR_THICKNESS / 2,
                cx + CROSSHAIR_HALF_LENGTH, cy + (CROSSHAIR_THICKNESS + 1) / 2,
                COLOR_WHITE);
        // 垂直线（上方）
        guiGraphics.fill(
                cx - CROSSHAIR_THICKNESS / 2, cy - CROSSHAIR_HALF_LENGTH,
                cx + (CROSSHAIR_THICKNESS + 1) / 2, cy - CROSSHAIR_GAP,
                COLOR_WHITE);
        // 垂直线（下方）
        guiGraphics.fill(
                cx - CROSSHAIR_THICKNESS / 2, cy + CROSSHAIR_GAP,
                cx + (CROSSHAIR_THICKNESS + 1) / 2, cy + CROSSHAIR_HALF_LENGTH,
                COLOR_WHITE);
        // 中心点
        guiGraphics.fill(
                cx - 1, cy - 1,
                cx + 1, cy + 1,
                COLOR_WHITE);
    }

    /**
     * 在指定位置用三角网格绘制圆环（视角中心参考）。
     * 通过 {@code bufferSource} 直接提交浮点精度顶点，消除扫描线 fill 的锯齿问题。
     * <p>
     * 圆环由 {@link #RING_SEGMENTS} 个四边形拼接而成，
     * 每段为外圆顶点 → 内圆顶点 → 下一内圆顶点 → 下一外圆顶点。
     */
    private void renderCenterRing(GuiGraphics guiGraphics, int cx, int cy) {
        float outerR = CENTER_RING_OUTER_RADIUS;
        float innerR = CENTER_RING_INNER_RADIUS;
        int segments = RING_SEGMENTS;

        Matrix4f pose = guiGraphics.pose().last().pose();
        VertexConsumer consumer = guiGraphics.bufferSource().getBuffer(RenderType.gui());

        for (int i = 0; i < segments; i++) {
            double a0 = 2.0 * Math.PI * i / segments;
            double a1 = 2.0 * Math.PI * (i + 1) / segments;

            float cos0 = (float) Math.cos(a0);
            float sin0 = (float) Math.sin(a0);
            float cos1 = (float) Math.cos(a1);
            float sin1 = (float) Math.sin(a1);

            float ox0 = cx + outerR * cos0;
            float oy0 = cy + outerR * sin0;
            float ix0 = cx + innerR * cos0;
            float iy0 = cy + innerR * sin0;
            float ix1 = cx + innerR * cos1;
            float iy1 = cy + innerR * sin1;
            float ox1 = cx + outerR * cos1;
            float oy1 = cy + outerR * sin1;

            // 四边形顶点顺序：外0 → 内0 → 内1 → 外1（逆时针）
            consumer.addVertex(pose, ox0, oy0, 0).setColor(COLOR_R, COLOR_G, COLOR_B, COLOR_A);
            consumer.addVertex(pose, ix0, iy0, 0).setColor(COLOR_R, COLOR_G, COLOR_B, COLOR_A);
            consumer.addVertex(pose, ix1, iy1, 0).setColor(COLOR_R, COLOR_G, COLOR_B, COLOR_A);
            consumer.addVertex(pose, ox1, oy1, 0).setColor(COLOR_R, COLOR_G, COLOR_B, COLOR_A);
        }
    }
}
