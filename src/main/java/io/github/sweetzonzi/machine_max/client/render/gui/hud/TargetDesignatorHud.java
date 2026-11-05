package io.github.sweetzonzi.machine_max.client.render.gui.hud;

import io.github.sweetzonzi.machine_max.client.input.CameraController;
import io.github.sweetzonzi.machine_max.client.target.TargetDesignatorHandler;
import io.github.sweetzonzi.machine_max.common.mech.target.Target;
import io.github.sweetzonzi.machine_max.util.ScreenProjectionUtil;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 目标指示器的标注层：把可锁载具与可锁命中框的中心点投到屏幕上，各画一个方形标记。
 *
 * <p>标记是屏幕空间的：不投影碰撞箱几何，边长固定 28 像素，因此不随距离、不随刚体朝向变化，
 * 也不会有缩放抖动。世界点转像素复用 {@link ScreenProjectionUtil#worldToScreenOffset}，
 * 与炮镜环同一套；返回 {@code null} 表示点在相机后方。</p>
 *
 * <p>GUI 层没有深度测试，遮挡完全由 {@link TargetDesignatorHandler} 每 tick 的射线判定负责。</p>
 */
@OnlyIn(Dist.CLIENT)
public class TargetDesignatorHud implements LayeredDraw.Layer {

    /** 标记边长（像素），固定值：不随距离、不随刚体朝向变化，因此不会有缩放抖动。 */
    private static final float BOX_PX = 28f;

    private static final int COLOR_IDLE = 0x9AFFFFFF;
    private static final int COLOR_LOCK = 0xFFFF4A4A;

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, @NotNull DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        List<Target> candidates = TargetDesignatorHandler.candidates();
        if (candidates.isEmpty()) return;

        Camera camera = mc.gameRenderer.getMainCamera();
        float fov = CameraController.getCurrentFov();
        int width = guiGraphics.guiWidth();
        int height = guiGraphics.guiHeight();
        Target lock = TargetDesignatorHandler.lock();
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        float half = BOX_PX / 2f;

        for (Target candidate : candidates) {
            Vec3 point = candidate.point(partialTick);
            float[] offset = ScreenProjectionUtil.worldToScreenOffset(point, camera, fov, width, height);
            if (offset == null) continue;
            float centerX = width / 2f + offset[0];
            float centerY = height / 2f + offset[1];
            if (centerX + half < 0f || centerX - half > width || centerY + half < 0f || centerY - half > height) {
                continue;
            }
            boolean locked = candidate == lock;
            drawBox(guiGraphics, centerX, centerY, half, locked);
            if (locked) drawLabel(guiGraphics, mc, candidate.label(), centerX, centerY + half);
        }
    }

    private static void drawBox(GuiGraphics guiGraphics, float centerX, float centerY, float half, boolean locked) {
        int color = locked ? COLOR_LOCK : COLOR_IDLE;
        int thickness = locked ? 2 : 1;
        int left = Math.round(centerX - half);
        int right = Math.round(centerX + half);
        int top = Math.round(centerY - half);
        int bottom = Math.round(centerY + half);
        guiGraphics.fill(left, top, right, top + thickness, color);
        guiGraphics.fill(left, bottom - thickness, right, bottom, color);
        guiGraphics.fill(left, top, left + thickness, bottom, color);
        guiGraphics.fill(right - thickness, top, right, bottom, color);
    }

    private static void drawLabel(GuiGraphics guiGraphics, Minecraft mc, Component label,
                                  float centerX, float bottom) {
        int textWidth = mc.font.width(label);
        guiGraphics.drawString(mc.font, label,
                Math.round(centerX - textWidth / 2f), Math.round(bottom + 3f), COLOR_LOCK, true);
    }
}
