package io.github.sweetzonzi.machine_max.client.render.gui.hud;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.sweetzonzi.machine_max.client.input.CameraController;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractViewpointSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SightSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractViewpointSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.util.ScreenProjectionUtil;
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

/** 固定炮镜的实际光轴分划与期望目标圆圈。渲染只读取已发布的预测目标。 */
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

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, @NotNull DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            return;
        }

        // 仅在炮镜模式且激活的摄像机是 SightSubsystem 时渲染
        if (!CameraController.isCameraMode()) {
            return;
        }
        AbstractViewpointSubsystem camera = CameraController.getActiveCamera();
        if (!(camera instanceof SightSubsystem sight)) {
            return;
        }

        int screenW = guiGraphics.guiWidth();
        int screenH = guiGraphics.guiHeight();
        int centerX = screenW / 2;
        int centerY = screenH / 2;

        // 分划固定在真实光轴中心，目标圆圈独立投影，表现机构追赶误差。
        renderCrosshair(guiGraphics, centerX, centerY);
        Vec3 target = CameraController.getExpectedAimPointWorld();
        if (target == null) {
            return;
        }
        float[] offset = ScreenProjectionUtil.worldToScreenOffset(target, mc.gameRenderer.getMainCamera(),
                AbstractViewpointSubsystemStaticAttr.REFERENCE_FOV / CameraController.getCurrentZoom(), screenW, screenH);
        var settings = sight.sightAttr.staticAttribute;
        if (!settings.horizontalStabilized || !settings.verticalStabilized) {
            Vec3 relative = CameraController.getRelativeAimPointWorld();
            float[] relativeOffset = relative == null ? null : ScreenProjectionUtil.worldToScreenOffset(relative,
                    mc.gameRenderer.getMainCamera(), AbstractViewpointSubsystemStaticAttr.REFERENCE_FOV
                            / CameraController.getCurrentZoom(), screenW, screenH);
            if (relativeOffset == null || (offset == null && (settings.horizontalStabilized || settings.verticalStabilized))) {
                return;
            }
            // 无稳轴显示累计后尚未被机构追上的偏差；稳定轴显示保持世界点的投影。
            offset = new float[]{settings.horizontalStabilized ? offset[0] : relativeOffset[0],
                    settings.verticalStabilized ? offset[1] : relativeOffset[1]};
        }
        if (offset == null) {
            return;
        }
        int x = centerX + Math.round(offset[0]);
        int y = centerY + Math.round(offset[1]);
        if (x < CENTER_RING_OUTER_RADIUS || y < CENTER_RING_OUTER_RADIUS
                || x > screenW - CENTER_RING_OUTER_RADIUS || y > screenH - CENTER_RING_OUTER_RADIUS) {
            return;
        }
        renderCenterRing(guiGraphics, x, y);
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
     * 在期望目标的屏幕投影位置用三角网格绘制圆环。
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
