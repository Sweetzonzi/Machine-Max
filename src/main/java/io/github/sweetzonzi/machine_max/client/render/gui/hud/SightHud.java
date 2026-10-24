package io.github.sweetzonzi.machine_max.client.render.gui.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.input.CameraController;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractViewpointSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.CameraSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SightSubsystem;
import io.github.sweetzonzi.machine_max.util.ScreenProjectionUtil;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

/** 实体视点与第三人称座椅观察共用的瞄准环，将操作者目标投影到当前实际视野。 */
@OnlyIn(Dist.CLIENT)
public class SightHud implements LayeredDraw.Layer {
    /** 可由资源包覆盖的圆环贴图，透明度与线宽由素材定义。 */
    private static final ResourceLocation AIM_RING = ResourceLocation.fromNamespaceAndPath(
            MachineMax.MOD_ID, "textures/gui/aim_ring.png");
    /** 贴图的显示边长（GUI 像素），包括透明留边。 */
    private static final int RING_SIZE = 16;
    /** 可见圆环外半径，用于屏幕边界判断（GUI 像素）。 */
    private static final float CENTER_RING_OUTER_RADIUS = 6f;

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, @NotNull DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            return;
        }

        if (!CameraController.isOnBoard()) {
            return;
        }
        AbstractViewpointSubsystem camera = CameraController.getActiveCamera();
        // 普通座椅只在第三人称显示，实体镜头使用设备第一人称。
        if (camera == null && mc.options.getCameraType().isFirstPerson()) {
            return;
        }

        int screenW = guiGraphics.guiWidth();
        int screenH = guiGraphics.guiHeight();
        float centerX = screenW * 0.5f;
        float centerY = screenH * 0.5f;

        // 圆环表示输入目标，画面表示实际光轴，二者之间可以存在转速或行程造成的偏移。
        // 可转镜头的无稳轴目标随本帧挂点合成，避免混入 tick 末安装体位姿。
        Vec3 target = camera instanceof CameraSubsystem device
                ? device.sampleViewTarget(mc.gameRenderer.getMainCamera().getPartialTickTime())
                : camera == null ? CameraController.getRenderedSeatAimPointWorld()
                : CameraController.getExpectedAimPointWorld();
        if (target == null) {
            return;
        }
        float[] offset = ScreenProjectionUtil.worldToScreenOffset(target, mc.gameRenderer.getMainCamera(),
                CameraController.getCurrentFov(), screenW, screenH);
        if (camera instanceof SightSubsystem sight
                && (!sight.sightAttr.staticAttribute.horizontalStabilized || !sight.sightAttr.staticAttribute.verticalStabilized)) {
            var settings = sight.sightAttr.staticAttribute;
            Vec3 relative = CameraController.getRelativeAimPointWorld();
            float[] relativeOffset = relative == null ? null : ScreenProjectionUtil.worldToScreenOffset(relative,
                    mc.gameRenderer.getMainCamera(), CameraController.getCurrentFov(), screenW, screenH);
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
        // 贴图中心保留浮点投影，亚像素误差不会在半像素边界放大成整像素往返。
        float x = centerX + offset[0];
        float y = centerY + offset[1];
        if (x < CENTER_RING_OUTER_RADIUS || y < CENTER_RING_OUTER_RADIUS
                || x > screenW - CENTER_RING_OUTER_RADIUS || y > screenH - CENTER_RING_OUTER_RADIUS) {
            return;
        }
        renderCenterRing(guiGraphics, x, y);
    }

    /** 浮点平移保留亚像素位置，单个贴图四边形显示素材中的线宽与透明度。 */
    private void renderCenterRing(GuiGraphics guiGraphics, float cx, float cy) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(cx - RING_SIZE * .5f, cy - RING_SIZE * .5f, 0);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        guiGraphics.blit(AIM_RING, 0, 0, 0, 0, RING_SIZE, RING_SIZE, RING_SIZE, RING_SIZE);
        RenderSystem.disableBlend();
        guiGraphics.pose().popPose();
    }
}
