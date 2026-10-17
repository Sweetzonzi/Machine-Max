package io.github.sweetzonzi.machine_max.client.render.gui.hud;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.input.CameraController;
import io.github.sweetzonzi.machine_max.client.render.gui.MMGuiManager;
import io.github.sweetzonzi.machine_max.client.render.renderable.GuiAnimatable;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.CameraSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CameraSubsystemStaticAttr;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自定义 HUD 叠加层。
 * <p>
 * 每个客户端 tick 按当前视角收集宿主：座椅或相机/瞄具，加上座椅当前控制组选中的
 * 武器控制器。每个宿主通过 {@link IHudHost#hudLayoutFor(HudView)} 取得实际布局
 * （默认由资源键解析，玩家自定义布局可直接返回保存的对象），为布局里的每个
 * {@link HudElement} 创建一个运行时元素。
 * <p>
 * 运行时元素按宿主分组，元素身份是“该宿主布局数组里的序数”。宿主集合或视角变化时
 * 整体清理并重建，不做逐元素差集；布局与样式都是内容包静态资源，这个粒度足够。
 * 内容包重载不会立刻刷新，上下车、切换视角或重连世界时随重建生效。
 * <p>
 * 宿主之间的绘制顺序由宿主集合的迭代顺序决定，不作契约：内容包负责让同一视角的
 * 元素互不遮挡。透视 HUD 由统一的外层投影管理器批量渲染（一次 flush），
 * 正交 HUD 使用 Minecraft 默认 GUI 投影逐个渲染，从而避免每组件单独 flush 的性能问题。
 */
@OnlyIn(Dist.CLIENT)
public class CustomHud implements LayeredDraw.Layer {
    // 当前视角的宿主 → 该宿主的运行时元素；空列表表示宿主在当前视角没有 HUD
    private final Map<IHudHost, List<GuiAnimatable>> huds = new LinkedHashMap<>();
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
     * 主线程，每个客户端 tick。解析当前视角的宿主集合，集合或视角变化时整体重建。
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

        if (view != activeView || !expected.equals(huds.keySet())) {
            rebuild(expected, view);
        }
    }

    /**
     * 为每个宿主建立运行时元素。没有布局、布局为空或样式不可用时，该宿主保留一个空列表：
     * 键集合必须与期望宿主集合一致，否则每 tick 都会判定为变化并反复重建。
     */
    private void rebuild(Set<IHudHost> hosts, HudView view) {
        clearAll();
        activeView = view;
        for (IHudHost host : hosts) {
            List<GuiAnimatable> elements = new ArrayList<>();
            HudLayout layout = host.hudLayoutFor(view);
            if (layout == null) layout = HudLayout.EMPTY_LAYOUT;
            for (HudElement element : layout.elements()) {
                HudStyle style = MMDynamicRes.HUD_STYLES.get(element.style());
                if (style == null) {
                    if (warnedStyles.add(element.style())) {
                        MachineMax.LOGGER.warn("HUD 样式不存在，跳过该条目: {}", element.style());
                    }
                    continue;
                }
                if (!(style instanceof ModelHudStyle modelStyle)) {
                    if (warnedStyles.add(element.style())) {
                        MachineMax.LOGGER.warn("HUD 样式载体尚不支持，跳过该条目: {} (type={})",
                                element.style(), style.type());
                    }
                    continue;
                }
                elements.add(new GuiAnimatable(element.style(), modelStyle, element));
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
        for (List<GuiAnimatable> elements : huds.values()) {
            for (GuiAnimatable hud : elements) {
                if (!hud.isPerspective()) {
                    hud.render(guiGraphics, 0, 0, partialTick);
                }
            }
        }

        // 第2趟：透视 HUD — 统一设置一次透视投影，渲染所有，flush一次
        if (hasPerspective()) {
            renderPerspectiveBatch(guiGraphics, partialTick);
        }
    }

    /** 当前是否有透视元素；没有时跳过投影矩阵的备份/恢复。 */
    private boolean hasPerspective() {
        for (List<GuiAnimatable> elements : huds.values()) {
            for (GuiAnimatable hud : elements) {
                if (hud.isPerspective()) return true;
            }
        }
        return false;
    }

    /**
     * 批量渲染透视 HUD（一次投影设置 + 一次 flush）。
     */
    private void renderPerspectiveBatch(GuiGraphics guiGraphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        // 备份当前渲染状态
        RenderSystem.backupProjectionMatrix();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        Matrix4f modelViewMatrix = RenderSystem.getModelViewMatrix();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        // 设置透视投影矩阵（使用玩家 FOV 和屏幕宽高比）
        double fov = mc.options.fov().get();
        Matrix4f projectionMatrix = new Matrix4f().setPerspective(
                (float) (fov * Math.PI / 180),
                (float) mc.getWindow().getWidth() / (float) mc.getWindow().getHeight(),
                0.1F, 1000);
        RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);
        modelViewStack.set(VIEW_MATRIX);
        RenderSystem.applyModelViewMatrix();

        // 渲染所有透视 HUD
        for (List<GuiAnimatable> elements : huds.values()) {
            for (GuiAnimatable hud : elements) {
                if (!hud.isPerspective()) continue;
                PoseStack poseStack = guiGraphics.pose();
                poseStack.pushPose();
                poseStack.setIdentity();
                // 设置每个 HUD 对应的光照方向
                Lighting.setupForEntityInInventory(hud.getQuaternion(partialTick));
                // 应用透视偏移（仅位置变换，旋转/缩放由 renderContent 内部处理）
                Vector3f offset = hud.getOffset(partialTick);
                poseStack.translate(offset.x, -offset.y, offset.z);
                hud.renderContent(poseStack, guiGraphics.bufferSource(), partialTick);
                poseStack.popPose();
            }
        }

        // 统一 flush
        guiGraphics.bufferSource().endBatch();
        // 恢复默认光照
        Lighting.setupFor3DItems();
        // 恢复渲染状态
        modelViewStack.set(modelViewMatrix);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.restoreProjectionMatrix();
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
        // 每个元素按 scope_behavior 单独设置 poseStack
        for (List<GuiAnimatable> elements : huds.values()) {
            for (GuiAnimatable hud : elements) {
                if (hud.isPerspective()) continue;
                renderOrthogonalInScope(guiGraphics, hud, screenW, screenH,
                        scopeCenterX, scopeCenterY, pitchDiff, yawDiff,
                        currentZoom, partialTick);
            }
        }

        // === 第2趟：透视 UGC 元素（统一投影矩阵，批量 flush） ===
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
     * 统一设置一次透视投影矩阵，然后对每个元素按 scope_behavior 变换后渲染，最后统一 flush。
     * <p>
     * 变换链与正交相同，区别在于原点使用摄像机局部坐标而非像素偏移。
     */
    private void renderPerspectiveInScopeBatch(GuiGraphics guiGraphics,
                                               float[] scopeCameraLocal, float pitchDiff, float yawDiff,
                                               float currentZoom, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        // 备份当前渲染状态
        RenderSystem.backupProjectionMatrix();
        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        Matrix4f modelViewMatrix = RenderSystem.getModelViewMatrix();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        // 设置透视投影矩阵（使用玩家 FOV 和屏幕宽高比）
        double fov = mc.options.fov().get();
        Matrix4f projectionMatrix = new Matrix4f().setPerspective(
                (float) (fov * Math.PI / 180),
                (float) mc.getWindow().getWidth() / (float) mc.getWindow().getHeight(),
                0.1F, 1000);
        RenderSystem.setProjectionMatrix(projectionMatrix, VertexSorting.DISTANCE_TO_ORIGIN);
        modelViewStack.set(VIEW_MATRIX);
        RenderSystem.applyModelViewMatrix();

        // 渲染所有透视 HUD
        for (List<GuiAnimatable> elements : huds.values()) {
            for (GuiAnimatable hud : elements) {
                if (!hud.isPerspective()) continue;
                PoseStack poseStack = guiGraphics.pose();
                poseStack.pushPose();
                poseStack.setIdentity();
                Lighting.setupForEntityInInventory(hud.getQuaternion(partialTick));

                ScopeBehavior behavior = hud.scopeBehavior();

                // FOLLOW_* 模式：平移到 scope 摄像机局部坐标 + 缩放 + 旋转
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
                // SCREEN_FIXED：仅应用元素自身偏移（与普通模式一致）

                Vector3f offset = hud.getOffset(partialTick);
                poseStack.translate(offset.x, -offset.y, offset.z);
                hud.renderContent(poseStack, guiGraphics.bufferSource(), partialTick);
                poseStack.popPose();
            }
        }

        // 统一 flush
        guiGraphics.bufferSource().endBatch();
        // 恢复默认光照
        Lighting.setupFor3DItems();
        // 恢复渲染状态
        modelViewStack.set(modelViewMatrix);
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.restoreProjectionMatrix();
    }

    /** 当前没有任何 HUD 宿主时清理；已有状态才销毁，避免每 tick 空转。 */
    private void clearView() {
        if (!huds.isEmpty() || activeView != null) {
            clearAll();
        }
    }

    /** 清除所有运行时元素。 */
    private void clearAll() {
        for (List<GuiAnimatable> elements : huds.values()) {
            for (GuiAnimatable hud : elements) hud.destroy();
        }
        huds.clear();
        activeView = null;
    }
}
