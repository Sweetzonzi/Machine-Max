package io.github.sweetzonzi.machine_max.client.render.gui.hud;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.input.CameraController;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractViewpointSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.CameraSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.LauncherSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
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
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** 实体视点与第三人称座椅观察共用的 HUD，显示操作者目标与当前主武器各发射管的轴向。 */
@OnlyIn(Dist.CLIENT)
public class SightHud implements LayeredDraw.Layer {
    /** 可由资源包覆盖的圆环贴图，透明度与线宽由素材定义。 */
    private static final ResourceLocation AIM_RING = ResourceLocation.fromNamespaceAndPath(
            MachineMax.MOD_ID, "textures/gui/aim_ring.png");
    /** 贴图的显示边长（GUI 像素），包括透明留边。 */
    private static final int RING_SIZE = 16;
    /** 发射管轴向使用半尺寸圆环，每根有效发射管独立投影。 */
    private static final int WEAPON_RING_SIZE = 8;
    /** 可见圆环外半径，用于屏幕边界判断（GUI 像素）。 */
    private static final float CENTER_RING_OUTER_RADIUS = 6f;

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, @NotNull DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
            return;
        }

        SeatSubsystem seat = CameraController.getControllingSeat();
        if (seat == null || !seat.isActive() || seat.getSubPart().isRemoved) {
            return;
        }
        AbstractViewpointSubsystem camera = CameraController.getActiveCamera();
        // 普通座椅只在第三人称显示，实体镜头使用设备第一人称。
        if (camera == null && mc.options.getCameraType().isFirstPerson()) {
            return;
        }

        int screenW = guiGraphics.guiWidth();
        int screenH = guiGraphics.guiHeight();
        renderTargetRing(guiGraphics, camera, screenW, screenH);
        renderWeaponRings(guiGraphics, seat, screenW, screenH);
    }

    private void renderTargetRing(GuiGraphics guiGraphics, @Nullable AbstractViewpointSubsystem camera,
                                  int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
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
        renderRing(guiGraphics, offset, RING_SIZE, screenW, screenH);
    }

    private void renderWeaponRings(GuiGraphics guiGraphics, SeatSubsystem seat, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        var renderCamera = mc.gameRenderer.getMainCamera();
        float partialTick = renderCamera.getPartialTickTime();
        Set<LauncherSubsystem> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var controller : seat.getActiveMainWeaponControllers()) {
            for (var launcher : controller.getLaunchers().keySet()) {
                var subPart = launcher.getSubPart();
                if (!seen.add(launcher) || !launcher.isActive() || subPart.isRemoved) continue;
                // 挂点与相机使用同一插值时刻；只读已发布状态，管轴不合入 FireCommand 的修正量。
                var mount = subPart.getLerpedLocatorWorldTransform(launcher.attr.locator, partialTick);
                Vector3f forward = SparkMathKt.toQuaternionf(mount.getRotation()).transform(new Vector3f(0, 0, -1));
                float[] offset = ScreenProjectionUtil.worldDirectionToScreenOffset(
                        new Vec3(forward.x, forward.y, forward.z), renderCamera,
                        CameraController.getCurrentFov(), screenW, screenH);
                renderRing(guiGraphics, offset, WEAPON_RING_SIZE, screenW, screenH);
            }
        }
    }

    /** 浮点平移保留亚像素位置，贴图尺寸同时决定圆环大小与屏幕边界余量。 */
    private void renderRing(GuiGraphics guiGraphics, float @Nullable [] offset, int size, int screenW, int screenH) {
        if (offset == null) return;
        // 贴图中心保留浮点投影，亚像素误差不会在半像素边界放大成整像素往返。
        float x = screenW * .5f + offset[0];
        float y = screenH * .5f + offset[1];
        float radius = CENTER_RING_OUTER_RADIUS * size / RING_SIZE;
        if (x < radius || y < radius || x > screenW - radius || y > screenH - radius) {
            return;
        }
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x - size * .5f, y - size * .5f, 0);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        guiGraphics.blit(AIM_RING, 0, 0, 0, 0, size, size, size, size);
        RenderSystem.disableBlend();
        guiGraphics.pose().popPose();
    }
}
