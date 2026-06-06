package io.github.sweetzonzi.machine_max.client.render.gui.hud;

import io.github.sweetzonzi.machine_max.client.input.CameraController;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.CameraSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SightSubsystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

/**
 * 瞄准镜 HUD：仅在炮镜模式且当前激活的摄像机为 SightSubsystem 时渲染。<p>
 * 目前为占位十字线，后续可扩展为标尺分划板渲染。
 */
@OnlyIn(Dist.CLIENT)
public class SightHud implements LayeredDraw.Layer {
    /** 十字线半长（像素） */
    private static final int CROSSHAIR_HALF_LENGTH = 8;
    /** 十字线间隙（中心留空半长，像素） */
    private static final int CROSSHAIR_GAP = 3;
    /** 十字线线宽（像素） */
    private static final int CROSSHAIR_THICKNESS = 2;
    /** 十字线颜色（白色） */
    private static final int COLOR_WHITE = 0xFFFFFFFF;

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, @NotNull DeltaTracker deltaTracker) {
        if (Minecraft.getInstance().options.hideGui) return;

        // 仅在炮镜模式且激活的摄像机是 SightSubsystem 时渲染
        if (!CameraController.isCameraMode()) return;
        CameraSubsystem camera = CameraController.getActiveCamera();
        if (!(camera instanceof SightSubsystem)) return;

        int centerX = guiGraphics.guiWidth() / 2;
        int centerY = guiGraphics.guiHeight() / 2;

        // 水平线（左侧）
        guiGraphics.fill(
                centerX - CROSSHAIR_HALF_LENGTH, centerY - CROSSHAIR_THICKNESS / 2,
                centerX - CROSSHAIR_GAP, centerY + (CROSSHAIR_THICKNESS + 1) / 2,
                COLOR_WHITE);
        // 水平线（右侧）
        guiGraphics.fill(
                centerX + CROSSHAIR_GAP, centerY - CROSSHAIR_THICKNESS / 2,
                centerX + CROSSHAIR_HALF_LENGTH, centerY + (CROSSHAIR_THICKNESS + 1) / 2,
                COLOR_WHITE);
        // 垂直线（上方）
        guiGraphics.fill(
                centerX - CROSSHAIR_THICKNESS / 2, centerY - CROSSHAIR_HALF_LENGTH,
                centerX + (CROSSHAIR_THICKNESS + 1) / 2, centerY - CROSSHAIR_GAP,
                COLOR_WHITE);
        // 垂直线（下方）
        guiGraphics.fill(
                centerX - CROSSHAIR_THICKNESS / 2, centerY + CROSSHAIR_GAP,
                centerX + (CROSSHAIR_THICKNESS + 1) / 2, centerY + CROSSHAIR_HALF_LENGTH,
                COLOR_WHITE);
        // 中心点
        guiGraphics.fill(
                centerX - 1, centerY - 1,
                centerX + 1, centerY + 1,
                COLOR_WHITE);
    }
}
