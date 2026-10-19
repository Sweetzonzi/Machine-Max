package io.github.sweetzonzi.machine_max.client.render.gui.hud;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.input.CameraController;
import io.github.sweetzonzi.machine_max.client.render.gui.MMGuiManager;
import io.github.sweetzonzi.machine_max.client.render.renderable.AuiHudAnimatable;
import io.github.sweetzonzi.machine_max.client.render.renderable.GuiAnimatable;
import io.github.sweetzonzi.machine_max.client.render.renderable.HudElementInstance;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.CameraSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CameraSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.visual.AuiHudStyle;
import io.github.sweetzonzi.machine_max.common.visual.HudElement;
import io.github.sweetzonzi.machine_max.common.visual.HudLayout;
import io.github.sweetzonzi.machine_max.common.visual.HudStyle;
import io.github.sweetzonzi.machine_max.common.visual.HudView;
import io.github.sweetzonzi.machine_max.common.visual.IHudHost;
import io.github.sweetzonzi.machine_max.common.visual.ModelHudStyle;
import io.github.sweetzonzi.machine_max.common.visual.ScopeBehavior;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machine_max.util.ScreenProjectionUtil;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 自定义 HUD 叠加层。
 * <p>
 * 每个客户端 tick 按当前视角收集宿主：座椅或相机/瞄具，加上座椅当前控制组选中的
 * 武器控制器。每个宿主通过 {@link IHudHost#hudLayoutFor(HudView)} 取得实际布局
 * （默认由资源键解析，玩家自定义布局可直接返回保存的对象），为布局里的每个
 * {@link HudElement} 创建一个运行时元素。
 * <p>
 * 运行时元素按宿主分组，元素身份是“该宿主布局数组里的序数”。宿主集合、视角、
 * 实际布局或所引用的样式变化时整体清理并重建，不做逐元素差集。每个 tick 只检查
 * 活跃宿主的布局与布局引用的样式；内容包重载后，资源替换在下一个 tick 生效。
 * <p>
 * 宿主之间的绘制顺序由宿主集合的迭代顺序决定，不作契约：内容包负责让同一视角的
 * 元素互不遮挡。透视 HUD 共用外层投影，正交 HUD 使用 Minecraft 默认 GUI 投影。
 * 投影切换和批次退出时提交顶点，AUI 文档还会在裁剪与合成边界提交自身的绘制。
 */
@OnlyIn(Dist.CLIENT)
public class CustomHud implements LayeredDraw.Layer {
    // 当前视角的宿主 → 该宿主的运行时元素；空列表表示宿主在当前视角没有 HUD
    private final Map<IHudHost, List<HudElementInstance>> huds = new LinkedHashMap<>();
    /** 建立运行时元素时使用的实际布局，包括宿主直接返回的自定义布局。 */
    private final Map<IHudHost, HudLayout> activeLayouts = new LinkedHashMap<>();
    /** 当前布局引用的样式对象；null 表示该样式缺失，供后续 tick 检测补齐。 */
    private final Map<ResourceLocation, HudStyle> activeStyles = new LinkedHashMap<>();
    /** 当前生效的视角；null 表示尚未建立任何宿主。 */
    private HudView activeView;
    /** 已经提示过的缺失样式，避免每 tick 刷屏。 */
    private final Set<ResourceLocation> warnedStyles = new HashSet<>();

    private static final Matrix4f VIEW_MATRIX = new Matrix4f().setLookAt(
            0, 0, 0,        // 摄像机位置
            0, 0, -0.01f,   // 观察方向
            0, 1, 0         // 上方向
    );

    /** 炮镜模式准星/分划距屏幕边缘最小边距（像素） */
    private static final int EDGE_MARGIN = 16;

    public CustomHud() {
        MMGuiManager.customHud = this;
    }

    /**
     * 主线程，每个客户端 tick。检查当前视角的宿主、实际布局与引用样式，变化时整体重建。
     */
    public void tick() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            clearView();
            return;
        }

        AbstractControllableSubsystem subsystem =
                ((IEntityMixin) player).machine_Max$getControllingSubsystem();
        SeatSubsystem seat = subsystem instanceof SeatSubsystem s ? s : null;

        HudView view;
        Set<IHudHost> expected = new LinkedHashSet<>();
        if (CameraController.isCameraMode()) {
            // 炮镜模式：宿主是当前激活的相机/瞄具子系统
            CameraSubsystem camera = CameraController.getActiveCamera();
            if (camera == null || !camera.isActive()) {
                clearView();
                return;
            }
            view = HudView.SCOPE;
            expected.add(camera);
        } else {
            if (seat == null) {
                clearView();
                return;
            }
            view = Minecraft.getInstance().options.getCameraType().isFirstPerson()
                    ? HudView.FIRST : HudView.THIRD;
            expected.add(seat);
        }

        // 座椅选中的武器控制器在普通视角与炮镜视角都显示
        if (seat != null) expected.addAll(seat.getActiveWeaponControllers());

        if (view != activeView || !expected.equals(huds.keySet())
                || layoutsOrStylesChanged(expected, view)) {
            rebuild(expected, view);
        }
    }

    /**
     * 只检查活跃布局与引用样式，不扫描资源注册表。
     * 布局按值比较，允许宿主每 tick 返回内容相同的新对象；样式按身份比较，
     * 可检测内容包重载时的资源替换，也能发现缺失样式被补齐或已有样式被撤下。
     */
    private boolean layoutsOrStylesChanged(Set<IHudHost> hosts, HudView view) {
        for (IHudHost host : hosts) {
            if (!Objects.equals(activeLayouts.get(host), layoutFor(host, view))) return true;
        }
        for (Map.Entry<ResourceLocation, HudStyle> entry : activeStyles.entrySet()) {
            if (entry.getValue() != MMDynamicRes.HUD_STYLES.get(entry.getKey())) return true;
        }
        return false;
    }

    /** 将“该视角没有 HUD”规范化为空布局，供检测与建立元素使用。 */
    private static HudLayout layoutFor(IHudHost host, HudView view) {
        HudLayout layout = host.hudLayoutFor(view);
        return layout == null ? HudLayout.EMPTY_LAYOUT : layout;
    }

    /**
     * 为每个宿主建立运行时元素。没有布局、布局为空或样式不可用时，该宿主保留一个空列表：
     * 键集合必须与期望宿主集合一致，否则每 tick 都会判定为变化并反复重建。
     */
    private void rebuild(Set<IHudHost> hosts, HudView view) {
        clearAll();
        activeView = view;
        for (IHudHost host : hosts) {
            List<HudElementInstance> elements = new ArrayList<>();
            HudLayout layout = layoutFor(host, view);
            activeLayouts.put(host, layout);
            for (HudElement element : layout.elements()) {
                HudStyle style = MMDynamicRes.HUD_STYLES.get(element.style());
                activeStyles.put(element.style(), style);
                if (style == null) {
                    if (warnedStyles.add(element.style())) {
                        MachineMax.LOGGER.warn("HUD 样式不存在，跳过该条目: {}", element.style());
                    }
                    continue;
                }
                if (style instanceof ModelHudStyle modelStyle) {
                    elements.add(new GuiAnimatable(element.style(), modelStyle, element));
                } else if (style instanceof AuiHudStyle auiStyle) {
                    elements.add(new AuiHudAnimatable(element.style(), auiStyle, element));
                } else if (warnedStyles.add(element.style())) {
                    MachineMax.LOGGER.warn("HUD 样式载体尚不支持，跳过该条目: {} (type={})",
                            element.style(), style.type());
                }
            }
            huds.put(host, elements);
        }
    }

    public void physicsTick() {
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, @NotNull DeltaTracker deltaTracker) {
        if (Minecraft.getInstance().options.hideGui) return;
        // 必须用 mc.getTimer() 而非 deltaTracker.getGameTimeDeltaTicks()：
        // deltaTracker 的 partialTick 在构造时固化，到此处可能已轻微过期，导致
        // getLerpedLocatorWorldTransform 插值错位，炮镜 UGC 元素产生严重抖振。
        // SightHud 也使用同一来源，两条管线对齐后 UGC 元素平滑度与硬编码准星一致。
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);

        // 炮镜模式：按 scope_behavior 分派 UGC 元素渲染
        if (CameraController.isCameraMode()) {
            CameraSubsystem camera = CameraController.getActiveCamera();
            if (camera != null && camera.isActive()) {
                renderInScopeMode(guiGraphics, camera, partialTick);
            }
            return; // 炮镜下不渲染座椅HUD
        }

        // 第1趟：正交 HUD — 使用 Minecraft 默认 GUI 正交投影逐个渲染
        for (List<HudElementInstance> elements : huds.values()) {
            for (HudElementInstance hud : elements) {
                if (!hud.isPerspective()) {
                    hud.renderGui(guiGraphics, partialTick);
                }
            }
        }

        // 第2趟：透视 HUD 共用一组投影和相机空间投放规则。
        if (hasPerspective()) {
            renderPerspectiveBatch(guiGraphics, partialTick);
        }
    }

    /** 当前是否有透视元素；没有时跳过投影矩阵的备份/恢复。 */
    private boolean hasPerspective() {
        for (List<HudElementInstance> elements : huds.values()) {
            for (HudElementInstance hud : elements) {
                if (hud.isPerspective()) return true;
            }
        }
        return false;
    }

    /**
     * 批量渲染普通视角的透视 HUD。
     */
    private void renderPerspectiveBatch(GuiGraphics guiGraphics, float partialTick) {
        renderPerspectiveInScopeBatch(guiGraphics, null, 0f, 0f, 1f, partialTick);
    }

    // ===== 炮镜模式渲染方法 =====

    /**
     * 炮镜模式下按 scope_behavior 分派渲染所有 UGC 元素。
     * 计算 scope 基准数据（投影中心、旋转角差、相机局部坐标），
     * 然后分正交和透视两趟渲染。
     */
    private void renderInScopeMode(GuiGraphics guiGraphics, CameraSubsystem camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        int screenW = guiGraphics.guiWidth();
        int screenH = guiGraphics.guiHeight();
        Camera mcCam = mc.gameRenderer.getMainCamera();
        float vFov = CameraSubsystemStaticAttr.REFERENCE_FOV / CameraController.getCurrentZoom();
        float currentZoom = CameraController.getCurrentZoom();

        // === 计算 scope 基准数据 ===
        Vec3 barrelAimPoint = CameraController.getCameraAimPointWorld(partialTick);
        float scopeCenterX = (float) screenW / 2;
        float scopeCenterY = (float) screenH / 2;
        float pitchDiff = 0, yawDiff = 0;
        float[] scopeCameraLocal = null;

        if (barrelAimPoint != null) {
            // 炮管瞄准点的屏幕投影中心（供 FOLLOW_POSITION/TRANSFORM 用）
            float[] screenOff = ScreenProjectionUtil.worldToScreenOffset(
                    barrelAimPoint, mcCam, vFov, screenW, screenH);
            if (screenOff != null) {
                scopeCenterX = Math.clamp(screenOff[0],
                        (float) -screenW / 2 + EDGE_MARGIN, (float) screenW / 2 - EDGE_MARGIN);
                scopeCenterY = Math.clamp(screenOff[1],
                        (float) -screenH / 2 + EDGE_MARGIN, (float) screenH / 2 - EDGE_MARGIN);
            }
            // scope 前方方向相对于玩家摄像机朝向的角差（供 FOLLOW_TRANSFORM 旋转用）
            float[] angles = ScreenProjectionUtil.worldDirToCameraAngles(
                    barrelAimPoint.subtract(mcCam.getPosition()), mcCam);
            pitchDiff = angles[0];
            yawDiff = angles[1];
            // 透视 FOLLOW_POSITION 用的摄像机局部坐标
            scopeCameraLocal = ScreenProjectionUtil.worldToCameraLocal(barrelAimPoint, mcCam);
        }

        // === 第1趟：正交 UGC 元素 ===
        // 每个元素按 scope_behavior 单独设置 poseStack；AUI 载体按自己的锚点投放
        for (List<HudElementInstance> elements : huds.values()) {
            for (HudElementInstance instance : elements) {
                if (instance.isPerspective()) continue;
                if (instance instanceof GuiAnimatable hud) {
                    renderOrthogonalInScope(guiGraphics, hud, screenW, screenH,
                            scopeCenterX, scopeCenterY, pitchDiff, yawDiff,
                            currentZoom, partialTick);
                } else {
                    instance.renderGui(guiGraphics, partialTick);
                }
            }
        }

        // === 第2趟：透视 UGC 元素共用投影矩阵 ===
        if (hasPerspective()) {
            renderPerspectiveInScopeBatch(guiGraphics,
                    scopeCameraLocal, pitchDiff, yawDiff,
                    currentZoom, partialTick);
        }
    }

    /**
     * 渲染单个正交 UGC 元素，按 scope_behavior 分派原点、缩放与旋转。
     * <p>
     * 变换链：
     * <pre>
     *   SCREEN_FIXED:      origin(屏幕中心) → offset → renderContent
     *   FOLLOW_POSITION:   origin(炮镜投影) → scale(zoom) → offset → renderContent
     *   FOLLOW_TRANSFORM:  origin(炮镜投影) → scale(zoom) → rotate → offset → renderContent
     * </pre>
     */
    private void renderOrthogonalInScope(GuiGraphics guiGraphics, GuiAnimatable hud,
                                         int screenW, int screenH, float scCenterX, float scCenterY,
                                         float pitchDiff, float yawDiff, float currentZoom, float partialTick) {
        PoseStack poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate((float) screenW / 2, (float) screenH / 2, 0);

        poseStack.pushPose();
        boolean followScope;
        boolean doRotate;
        switch (hud.scopeBehavior()) {
            case SCREEN_FIXED:
                followScope = false;
                doRotate = false;
                break;
            case FOLLOW_POSITION:
                poseStack.translate(scCenterX, scCenterY, 0);
                followScope = true;
                doRotate = false;
                break;
            case FOLLOW_TRANSFORM:
                poseStack.translate(scCenterX, scCenterY, 0);
                followScope = true;
                doRotate = true;
                break;
            default:
                poseStack.translate(scCenterX, scCenterY, 0);
                followScope = true;
                doRotate = false;
                break;
        }

        // FOV 缩放（FOLLOW_POSITION / FOLLOW_TRANSFORM，且 ignoreZoom=false 时生效）
        if (followScope && currentZoom != 1f && !hud.ignoreZoom()) {
            poseStack.scale(currentZoom, currentZoom, 1f);
        }
        // scope 旋转（FOLLOW_TRANSFORM 专属）
        if (doRotate && (pitchDiff != 0 || yawDiff != 0)) {
            poseStack.mulPose(new Quaternionf().rotationYXZ(-yawDiff, pitchDiff, 0));
        }
        // 元素自身位移（所有模式都有）
        Vector3f off = hud.getOffset(partialTick);
        poseStack.translate(off.x, off.y, off.z);

        // 委托 GuiAnimatable 渲染模型和文本内容
        hud.renderContent(poseStack, guiGraphics.bufferSource(), partialTick);
        poseStack.popPose();
        poseStack.popPose();
    }

    /**
     * 批量渲染透视 UGC 元素（炮镜模式）。
     * 统一设置透视投影，然后对每个元素按 scope_behavior 变换后绘制；
     * 普通视角传入空炮镜位置、零角差和单位缩放，共用同一条投放路径。
     * <p>
     * 变换链与正交相同，区别在于原点使用摄像机局部坐标而非像素偏移。
     */
    private void renderPerspectiveInScopeBatch(GuiGraphics guiGraphics,
                                               float[] scopeCameraLocal, float pitchDiff, float yawDiff,
                                               float currentZoom, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        // 正交顶点必须在投影切换前提交。
        guiGraphics.bufferSource().endBatch();
        boolean previousDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean previousDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        RenderSystem.backupProjectionMatrix();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix();
        try {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            double fov = mc.options.fov().get();
            Matrix4f projectionMatrix = new Matrix4f().setPerspective(
                    (float) Math.toRadians(fov),
                    (float) mc.getWindow().getWidth() / (float) mc.getWindow().getHeight(),
                    0.1F, 1000);
            RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);
            modelViewStack.set(VIEW_MATRIX);
            RenderSystem.applyModelViewMatrix();

            for (List<HudElementInstance> elements : huds.values()) {
                for (HudElementInstance hud : elements) {
                    if (!hud.isPerspective()) continue;
                    PoseStack poseStack = guiGraphics.pose();
                    poseStack.pushPose();
                    try {
                        poseStack.setIdentity();
                        Lighting.setupForEntityInInventory(hud.getQuaternion(partialTick));
                        ScopeBehavior behavior = hud.scopeBehavior();
                        if (behavior == ScopeBehavior.FOLLOW_POSITION
                                || behavior == ScopeBehavior.FOLLOW_TRANSFORM) {
                            if (scopeCameraLocal != null) {
                                poseStack.translate(scopeCameraLocal[0], scopeCameraLocal[1], 0);
                            }
                            if (currentZoom != 1f && !hud.ignoreZoom()) {
                                poseStack.scale(currentZoom, currentZoom, currentZoom);
                            }
                            if (behavior == ScopeBehavior.FOLLOW_TRANSFORM
                                    && (pitchDiff != 0 || yawDiff != 0)) {
                                poseStack.mulPose(new Quaternionf().rotationYXZ(-yawDiff, pitchDiff, 0));
                            }
                        }
                        Vector3f offset = hud.getOffset(partialTick);
                        poseStack.translate(offset.x, -offset.y, offset.z);
                        hud.renderContent(poseStack, guiGraphics.bufferSource(), partialTick);
                    } finally {
                        poseStack.popPose();
                    }
                }
            }
        } finally {
            try {
                guiGraphics.bufferSource().endBatch();
            } finally {
                Lighting.setupFor3DItems();
                modelViewStack.popMatrix();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.depthMask(previousDepthMask);
                if (previousDepthTest) RenderSystem.enableDepthTest();
                else RenderSystem.disableDepthTest();
                RenderSystem.restoreProjectionMatrix();
            }
        }
    }

    /** 当前没有任何 HUD 宿主时清理；已有状态才销毁，避免每 tick 空转。 */
    private void clearView() {
        if (!huds.isEmpty() || activeView != null) {
            clearAll();
        }
    }

    /** 清除所有运行时元素。 */
    private void clearAll() {
        for (List<HudElementInstance> elements : huds.values()) {
            for (HudElementInstance hud : elements) hud.dispose();
        }
        huds.clear();
        activeLayouts.clear();
        activeStyles.clear();
        activeView = null;
    }
}
