package io.github.sweetzonzi.machine_max.client.input;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.util.MMMath;
import io.github.sweetzonzi.machine_max.client.event.ComputeCameraPosEvent;
import io.github.sweetzonzi.machine_max.common.attachment.ControlPreference;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.*;
import io.github.sweetzonzi.machine_max.common.mech.control.view.ViewMath;
import io.github.sweetzonzi.machine_max.common.mech.signal.ViewInputSignal;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.*;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractViewpointSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machine_max.network.payload.ViewInputPayload;
import lombok.Getter;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.List;

/**
 * 客户端主线程的视角路由、输入交付和显示效果。观察状态由第三人称或逐设备实例持有。
 * 固定炮镜显示真实光轴，鼠标更新独立的武器期望目标；渲染方法只读这些状态。
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
public final class CameraController {
    private static final Minecraft CLIENT = Minecraft.getInstance();

    /** 座椅正常观察与临时自由观察各自保存状态，临时观察不改写武器目标。 */
    private static final ThirdPersonViewpoint SEAT_VIEW = new ThirdPersonViewpoint();
    private static final ThirdPersonViewpoint FREE_LOOK = new ThirdPersonViewpoint();

    /** 以下逻辑状态均由客户端主线程持有；离座或换世界时由 clearWorld 释放。 */
    private static SeatSubsystem controllingSeat;
    @Getter
    private static AbstractViewpointSubsystem activeCamera;
    /** 当前炮镜输入目标及其主 WC；切入时由机构实际角初始化，离座时释放。 */
    private static AimTargetState aimTarget;
    private static SightSubsystem fixedSight;
    private static WeaponControllerSubsystem fixedController;
    /** 逻辑观察结果与本帧显示位姿分开保存，显示抖动只写入后者的应用结果。 */
    private static ViewMath.Snapshot logicalView;
    private static AimPose renderPose;
    /** 发往 WC、供 HUD 投影的有限世界目标点。 */
    private static Vec3 expectedPoint;
    private static Vec3 freeLookControlPoint;
    private static boolean wasFreeLook;
    private static boolean seatFollowing;
    private static boolean seatFirstPerson;
    /** 切入设备前的原版视角，退出时恢复。 */
    private static CameraType seatCameraType;

    /** 速度对第三人称距离的平滑修正，每逻辑 tick 更新一次。 */
    private static float speedDistanceFactor;
    private static float targetZoom = 1;
    @Getter
    private static float currentZoom = 1;
    /** 显示变焦按真实经过时间平滑，单位为纳秒；零表示尚未采样。 */
    private static long lastZoomNanos;
    /** 当前逻辑观察方向，供交互与内容查询使用，不包含显示抖动。 */
    public static Vec3 aimDirection = new Vec3(0, 0, -1);

    public static boolean isCameraMode() {
        return activeCamera != null;
    }

    public static boolean isOnBoard() {
        return controllingSeat != null;
    }

    /** 主线程／HUD 读取当前期望目标；尚未进入有效控制来源时为 null。 */
    @Nullable
    public static Vec3 getExpectedAimPointWorld() {
        return expectedPoint;
    }

    /** HUD 读取无稳轴累计偏差所对应的机构正解点；采样未就绪时不绘制目标圈。 */
    @Nullable
    public static Vec3 getRelativeAimPointWorld() {
        AimGeometrySnapshot geometry = fixedSight == null ? null : fixedSight.getAimGeometry();
        if (aimTarget == null || geometry == null) {
            return null;
        }
        Vec3 target = aimTarget.relativePoint(geometry);
        AimPose actual = geometry.forward(geometry.actualAngles());
        AimPose displayed = renderPose != null ? renderPose : fixedSight.samplePose(1);
        // 机构采样提供目标相对实际光轴的偏差，本帧光轴提供显示参考。
        // 重基准可消除物理采样与渲染插值的时差；追到目标后，车体运动不会把圈推离中心。
        Vec3 offset = target.subtract(actual.position());
        Vector3f direction = new Vector3f((float) offset.x, (float) offset.y, (float) offset.z);
        SparkMathKt.toQuaternionf(actual.rotation()).conjugate().transform(direction);
        SparkMathKt.toQuaternionf(displayed.rotation()).transform(direction);
        return displayed.position().add(direction.x, direction.y, direction.z);
    }

    /** 渲染线程读取实际光轴的前方点，供内容分划的空间投影使用。 */
    @Nullable
    public static Vec3 getCameraAimPointWorld(float partialTick) {
        if (activeCamera == null) {
            return null;
        }
        AimPose pose = activeCamera.samplePose(partialTick);
        return pose.position().add(pose.forward().scale(AimMath.AIM_DISTANCE_METERS));
    }

    /**
     * 客户端主线程每 tick 更新活动来源、逻辑观察和完整瞄准目标。<br>
     * 设备自己的转轴在子系统 tick 推进；此处负责读取结果并上报，渲染不进入该更新链。
     */
    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        // 世界引用失效时先释放旧座椅输出，避免高优先级信号滞留。
        if (CLIENT.player == null || CLIENT.level == null) {
            clearWorld();
            return;
        }
        SeatSubsystem seat = ((IEntityMixin) CLIENT.player).machine_Max$getControllingSubsystem() instanceof SeatSubsystem s ? s : null;
        // 同一装配体内换座继承操作者目标；离座或换载具建立独立的累计状态。
        // 必须先保存继承值再清理旧来源，清理会释放全部控制组输出。
        if (seat != controllingSeat) {
            Vec3 inherited = controllingSeat != null && seat != null
                    && controllingSeat.getSubPart().getPart().assembly == seat.getSubPart().getPart().assembly ? expectedPoint : null;
            AimTargetState inheritedTarget = inherited != null ? aimTarget : null;
            clearWorld();
            controllingSeat = seat;
            aimTarget = inheritedTarget;
            if (seat == null) {
                return;
            }
            enterSeat(inherited);
        }
        if (seat == null) {
            aimDirection = CLIENT.player.getLookAngle();
            return;
        }
        // 发现关系、激活或耐久状态失效时退回座椅观察，让失效设备退出输入来源。
        if (activeCamera != null && (!seat.getDiscoveredCameras().contains(activeCamera)
                || !activeCamera.isActive() || activeCamera.isDestroyed())) {
            exitCameraMode();
        }
        if (seat.getSubPart().getPart().assembly instanceof VehicleCore vehicle) {
            speedDistanceFactor = .8f * speedDistanceFactor
                    + .2f * (float) (2 * MMMath.sigmoid(.1 * vehicle.getVelocity().length()) - 1);
        }
        // 逻辑更新分三路：座椅推进虚拟观察，可转镜头读取自身转轴，炮镜读取真实挂点。
        // 视角约束先执行，使座椅初始化使用最终的第一／第三人称设置。
        enforcePerspective(seat);
        if (activeCamera == null) {
            boolean following = CLIENT.options.getCameraType().isFirstPerson() || ControlPreference.shouldFollowPose(seat);
            if (following != seatFollowing || seatFirstPerson != CLIENT.options.getCameraType().isFirstPerson()) {
                enterSeat(expectedPoint);
            }
            updateSeat(Vec2.ZERO);
        } else if (activeCamera instanceof CameraSubsystem) {
            publishDevice();
        } else {
            updateFixed();
        }
        send();
        // 交互方向与当前逻辑光轴一致；临时自由观察期间保留武器与玩家控制方向。
        if (!RawInputHandler.freeCam && logicalView != null) {
            Vec2 angles = ViewMath.directionAngles(logicalView.pose().forward());
            CLIENT.player.setXRot((float) -Math.toDegrees(angles.x));
            CLIENT.player.setYRot((float) Math.toDegrees(angles.y) + 180);
        }
    }

    /** 客户端主线程逐采样累计俯仰／偏航输入；每 tick 发送完整目标值。 */
    public static void turnCamera(double right, double down) {
        if (controllingSeat == null) {
            return;
        }
        // 上游已处理原版灵敏度与反转设置，这里只转换单位并按设备倍率缩放。
        Vec2 delta = AimMath.mouse(right, down, activeCamera == null ? 1 : currentZoom);
        if (activeCamera instanceof SightSubsystem) {
            // 光学参考解释稳定轴输入；主 WC 的物理采样提供无稳轴实际角和正解几何。
            updateFixed();
            AimGeometrySnapshot geometry = fixedSight.getAimGeometry();
            if (aimTarget != null && (mode() == AimTargetState.Mode.WORLD || geometry != null)) {
                aimTarget.applyMouse(fixedSight.samplePose(1), geometry, delta);
                expectedPoint = aimTarget.getWorldPoint();
            }
        } else if (activeCamera instanceof CameraSubsystem camera) {
            camera.turnView(delta);
        } else {
            updateSeat(delta);
        }
    }

    /** 固定炮镜的稳定配置描述武器目标；第三人称与可转镜头统一发布世界点。 */
    private static AimTargetState.Mode mode() {
        return activeCamera instanceof SightSubsystem sight
                ? AimTargetState.Mode.of(sight.sightAttr.staticAttribute.horizontalStabilized, sight.sightAttr.staticAttribute.verticalStabilized)
                : AimTargetState.Mode.WORLD;
    }

    /** 客户端主线程每 tick 上报完整目标；鼠标增量只在本地累计。 */
    private static void send() {
        if (expectedPoint == null || (activeCamera == null && RawInputHandler.freeCam)
                || (activeCamera instanceof SightSubsystem && aimTarget == null)) {
            return;
        }
        // 座椅和可转镜头始终按世界点联动武器；只有固定炮镜使用累计相对角。
        // 无稳目标从当前主 WC 的实际角初始化，之后只发送累计后的完整值。
        Vec2 relativeAim = activeCamera instanceof SightSubsystem ? aimTarget.getRelativeAim() : Vec2.ZERO;
        int viewPart = activeCamera == null ? -1 : activeCamera.getSubPart().getId();
        String viewName = activeCamera == null ? "seat" : activeCamera.getName();
        // 本地信号与上行载荷使用同一完整值，服务端再校验座椅、设备和活动控制组。
        // 重复发送只覆盖状态，不会让每个物理子步再次累计鼠标增量。
        controllingSeat.setViewInputSignal(new ViewInputSignal(expectedPoint, relativeAim, mode()), viewPart, viewName);
        PacketDistributor.sendToServer(new ViewInputPayload(controllingSeat.getSubPart().getId(), controllingSeat.getName(),
                viewPart, viewName, controllingSeat.getControlGroupSet().getActiveIndex(), expectedPoint, relativeAim));
    }

    /** 主线程维护固定炮镜的输入来源，并单独发布实际观察光轴。 */
    private static void updateFixed() {
        SightSubsystem sight = (SightSubsystem) activeCamera;
        AimPose actual = sight.samplePose(1);
        WeaponControllerSubsystem controller = controllingSeat.getActiveMainWeaponController();
        sight.setAimController(controller);
        if (fixedSight != sight || fixedController != controller) {
            if (fixedSight != null && fixedSight != sight) {
                fixedSight.setAimController(null);
            }
            // 换视点或主 WC 时释放旧输出，使用新机构实际角建立输入初值。
            controllingSeat.clearInputSignals();
            aimTarget = null;
            fixedSight = sight;
            fixedController = controller;
        }
        AimGeometrySnapshot geometry = sight.getAimGeometry();
        if (mode() != AimTargetState.Mode.WORLD && geometry == null) {
            // 物理线程尚未发布当前机构时，画面可用，但不发送错误零位目标。
            if (aimTarget != null) {
                controllingSeat.clearInputSignals();
            }
            aimTarget = null;
            logicalView = new ViewMath.Snapshot(actual, point(actual));
            aimDirection = actual.forward();
            return;
        }
        if (aimTarget == null) {
            Vec3 inherited = expectedPoint != null ? expectedPoint : point(actual);
            aimTarget = new AimTargetState(mode(), inherited, geometry == null ? Vec2.ZERO : geometry.actualAngles());
        }
        if (geometry != null) {
            aimTarget.updateGeometry(geometry);
        }

        // 逻辑目标供输入与 HUD 使用；画面始终来自真实挂点，不能用目标角旋转画面。
        expectedPoint = aimTarget.getWorldPoint();
        logicalView = new ViewMath.Snapshot(actual, expectedPoint);
        aimDirection = actual.forward();
    }

    /** 主线程以可转镜头当前实际观察角生成瞄准点，不采用尚未追到的期望角。 */
    private static void publishDevice() {
        AimPose pose = activeCamera.samplePose(1);
        expectedPoint = point(pose);
        logicalView = new ViewMath.Snapshot(pose, expectedPoint);
        aimDirection = pose.forward();
    }

    private static Vec3 point(AimPose pose) {
        return pose.position().add(pose.forward().scale(AimMath.AIM_DISTANCE_METERS));
    }

    /** 主线程切入座椅观察，按跟随设置转换继承目标，并清理固定炮镜来源。 */
    private static void enterSeat(Vec3 inherited) {
        if (controllingSeat == null) {
            return;
        }
        // 第一人称固定跟随座椅；第三人称按玩家覆盖或座椅默认值选择跟随／自由。
        seatFirstPerson = CLIENT.options.getCameraType().isFirstPerson();
        seatFollowing = seatFirstPerson || ControlPreference.shouldFollowPose(controllingSeat);
        AimPose mount = seatMount(1);
        if (inherited == null) {
            inherited = point(mount);
        }
        // 座椅观察继承世界点；退出炮镜时解除机构采样引用。
        SEAT_VIEW.enter(seatFollowing, mount.rotation(), mount.position(), inherited);
        if (fixedSight != null) {
            fixedSight.setAimController(null);
        }
        fixedSight = null;
        fixedController = null;
        wasFreeLook = false;
        updateSeat(Vec2.ZERO);
    }

    /**
     * 主线程按座椅挂点更新普通观察或临时自由观察。<br>
     * 临时观察只改变画面；进入时保存控制目标，退出时恢复该目标的指向。
     *
     * @param input 本次鼠标俯仰／偏航增量，弧度，零值也更新乘坐随动
     */
    private static void updateSeat(Vec2 input) {
        AimPose mount = seatMount(1);
        // 自由观察保留独立状态，不能让临时查看方向变成新的武器指向。
        if (RawInputHandler.freeCam && !wasFreeLook) {
            freeLookControlPoint = expectedPoint;
            FREE_LOOK.enter(false, mount.rotation(), mount.position(), expectedPoint != null ? expectedPoint : point(mount));
        } else if (!RawInputHandler.freeCam && wasFreeLook && freeLookControlPoint != null) {
            SEAT_VIEW.enter(seatFollowing, mount.rotation(), mount.position(), freeLookControlPoint);
        }
        wasFreeLook = RawInputHandler.freeCam;
        // 两套观察状态独立，只有普通观察可以更新待发送的武器世界点。
        ThirdPersonViewpoint view = wasFreeLook ? FREE_LOOK : SEAT_VIEW;
        // 此处限位只属于座椅观察范围，与武器的机械行程无关。
        AimAxisLimit pitch = new AimAxisLimit(false, Math.toRadians(controllingSeat.attr.staticAttribute.views.minPitch()),
                Math.toRadians(controllingSeat.attr.staticAttribute.views.maxPitch()));
        float yaw = controllingSeat.attr.staticAttribute.views.yawLimit();
        AimAxisLimit yawLimit = yaw >= 360 ? AimAxisLimit.CONTINUOUS : new AimAxisLimit(false, Math.toRadians(-yaw / 2), Math.toRadians(yaw / 2));
        Vec3 origin = seatOrigin(view, mount, 1);
        logicalView = view.update(mount.rotation(), origin, input, yawLimit, pitch);
        if (!wasFreeLook) {
            expectedPoint = logicalView.worldPoint();
        }
        aimDirection = logicalView.pose().forward();
    }

    private static AimPose seatMount(float partialTick) {
        var mount = controllingSeat.getSubPart().getLerpedLocatorWorldTransform(controllingSeat.attr.locator, partialTick);
        return new AimPose(SparkMathKt.toVec3(mount.getTranslation()), mount.getRotation());
    }

    /** 位置轨道取座椅／装配体中心，姿态参考始终只取座椅挂点。 */
    private static Vec3 seatOrigin(ThirdPersonViewpoint view, AimPose mount, float partialTick) {
        // 挂点旋转决定局部偏移的世界方向；中心跟随只改变环绕位置的支点。
        var settings = controllingSeat.attr.staticAttribute.views;
        boolean first = CLIENT.options.getCameraType().isFirstPerson();
        Quaternionf reference = SparkMathKt.toQuaternionf(mount.rotation());
        Vec3 offset = first ? settings.firstPersonOffset() : settings.thirdPersonOffset();
        Vector3f localOffset = new Vector3f((float) offset.x, (float) offset.y, (float) offset.z);
        reference.transform(localOffset);
        Vec3 pivot = mount.position();
        if (!first && settings.focusOnCenter()
                && controllingSeat.getSubPart().getPart().assembly instanceof VehicleCore vehicle) {
            pivot = vehicle.getOldPosition().scale(1 - partialTick).add(vehicle.getPosition().scale(partialTick));
        } else {
            Vector3f eye = reference.transform(new Vector3f(0, 1.1f, 0));
            pivot = pivot.add(eye.x, eye.y, eye.z);
        }
        pivot = pivot.add(localOffset.x, localOffset.y, localOffset.z);
        if (first) {
            return pivot;
        }
        // 第三人称沿当前逻辑视线后退，前置视角反转距离，观察目标本身保持独立。
        double distance = (settings.distanceScale() + .4 * speedDistanceFactor)
                * (controllingSeat.getSubPart().getPart().assembly instanceof VehicleCore vehicle ? vehicle.cameraDistance : 4);
        Vec3 forward = view.sample(mount.rotation(), pivot).pose().forward();
        if (CLIENT.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
            distance = -distance;
        }
        Vec3 destination = pivot.subtract(forward.scale(distance));
        // 用相机小包围盒的八个角探测遮挡，距离缩短只影响显示位置。
        double fraction = 1;
        for (int i = 0; i < 8; i++) {
            Vec3 corner = new Vec3((i & 1) == 0 ? -.1 : .1, (i & 2) == 0 ? -.1 : .1, (i & 4) == 0 ? -.1 : .1);
            BlockHitResult hit = CLIENT.level.clip(new ClipContext(pivot.add(corner), destination.add(corner),
                    ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, CLIENT.player));
            if (hit.getType() != HitResult.Type.MISS && Math.abs(distance) > 1e-6) {
                fraction = Math.min(fraction, hit.getLocation().distanceTo(pivot.add(corner)) / Math.abs(distance));
            }
        }
        return pivot.add(destination.subtract(pivot).scale(fraction));
    }

    /** 主线程执行设备第一人称和座椅允许视角的限制。 */
    private static void enforcePerspective(SeatSubsystem seat) {
        if (activeCamera != null) {
            if (!CLIENT.options.getCameraType().isFirstPerson()) {
                setPerspective(CameraType.FIRST_PERSON);
            }
            return;
        }
        for (int i = 0; i < 3; i++) {
            CameraType type = CLIENT.options.getCameraType();
            if (type.isFirstPerson() ? seat.attr.staticAttribute.views.enableFirstPerson()
                    : seat.attr.staticAttribute.views.enableThirdPerson()) {
                break;
            }
            setPerspective(type.cycle());
        }
    }

    /** 主线程切换原版视角，并同步刷新后处理与场景可见性。 */
    private static void setPerspective(CameraType type) {
        if (CLIENT.options.getCameraType() == type) {
            return;
        }
        CLIENT.options.setCameraType(type);
        CLIENT.gameRenderer.checkEntityPostEffect(type.isFirstPerson() ? CLIENT.getCameraEntity() : null);
        CLIENT.levelRenderer.needsUpdate();
    }

    /** 主线程按发现顺序切换设备，继承当前期望目标。 */
    public static void switchCamera(int direction) {
        if (controllingSeat == null) {
            return;
        }
        // 发现顺序决定循环顺序；仅允许有效且启用循环切换的设备进入列表。
        List<AbstractViewpointSubsystem> devices = controllingSeat.getDiscoveredCameras().stream()
                .filter(c -> c.isActive() && !c.isDestroyed() && c.attr.staticAttribute.allowCycle).toList();
        if (devices.isEmpty()) {
            return;
        }
        // 越过设备列表边界即回到座椅，切出炮镜前先保留其最新世界目标。
        int index = activeCamera == null || direction == 0 ? 0 : devices.indexOf(activeCamera) + Integer.signum(direction);
        if (index < 0 || index >= devices.size()) {
            exitCameraMode();
            return;
        }
        if (activeCamera instanceof SightSubsystem) {
            updateFixed();
        }
        if (activeCamera == null) {
            seatCameraType = CLIENT.options.getCameraType();
        }
        // 可转镜头继承世界点；固定炮镜的无稳轴由当前主 WC 实际角初始化。
        activeCamera = devices.get(index);
        controllingSeat.setActiveCamera(activeCamera);
        if (activeCamera instanceof CameraSubsystem camera) {
            camera.enterView(expectedPoint);
            if (fixedSight != null) {
                fixedSight.setAimController(null);
            }
            fixedSight = null;
            fixedController = null;
            publishDevice();
        } else {
            updateFixed();
        }
        // 倍率与平滑计时属于设备显示状态，切换时使用新设备的基础倍率。
        targetZoom = currentZoom = activeCamera.attr.staticAttribute.baseZoom;
        lastZoomNanos = 0;
        send();
    }

    /** 主线程退出实体视点，恢复切入前的原版视角并继承当前期望目标。 */
    public static void exitCameraMode() {
        if (activeCamera == null) {
            return;
        }
        if (activeCamera instanceof SightSubsystem) {
            updateFixed();
        }
        // 先解除实体设备来源，再恢复切入前的原版视角和座椅观察状态。
        activeCamera = null;
        if (seatCameraType != null) {
            setPerspective(seatCameraType);
        }
        if (controllingSeat != null) {
            controllingSeat.setActiveCamera(null);
        }
        targetZoom = currentZoom = 1;
        enterSeat(expectedPoint);
    }

    /** 主线程离座或换世界时释放信号及客户端引用，防止目标留在高优先级频道。 */
    private static void clearWorld() {
        // 输出释放必须先于引用清空，否则失去旧座椅后无法撤销其频道占用。
        if (controllingSeat != null) {
            controllingSeat.clearInputSignals();
            controllingSeat.setActiveCamera(null);
        }
        if (fixedSight != null) {
            fixedSight.setAimController(null);
        }
        controllingSeat = null;
        activeCamera = null;
        aimTarget = null;
        fixedSight = null;
        fixedController = null;
        logicalView = null;
        renderPose = null;
        expectedPoint = null;
        targetZoom = currentZoom = 1;
        speedDistanceFactor = 0;
        wasFreeLook = false;
        CameraShakeController.onLeaveVehicle();
    }

    /** 渲染阶段应用本帧已采样的显示位置，显示位置抖动不写回逻辑目标。 */
    @SubscribeEvent
    public static void updateCameraPos(ComputeCameraPosEvent event) {
        if (controllingSeat == null || renderPose == null) {
            event.setCameraPos(event.getCameraPos().add(CameraShakeController.getPositionOffset()
                    .scale(CameraShakeController.getPositionShakeFactor())));
            return;
        }
        // Camera.setup 先触发旋转事件，再设置位置并触发本事件；两处共用旋转事件采样的位姿。
        // 此处重新采样会让位置与朝向分别读取物理线程发布的不同状态。
        // 实体炮镜保持挂点位置；座椅位置抖动仅叠加到本次显示结果。
        Vec3 position = renderPose.position();
        if (activeCamera == null) {
            position = position.add(CameraShakeController.getPositionOffset()
                    .scale(CameraShakeController.getPositionShakeFactor()));
        }
        event.setCameraPos(position);
    }

    /** 载具视点的距离与碰撞轨道已由 seatOrigin 处理，原版距离设为零。 */
    @SubscribeEvent
    public static void updateCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        if (controllingSeat != null) {
            event.setDistance(0);
        }
    }

    /** 渲染阶段按刻内比例采样本帧位姿并应用旋转；步行时使用原版基础旋转。 */
    @SubscribeEvent
    public static void updateCameraRot(ViewportEvent.ComputeCameraAngles event) {
        if (CLIENT.level == null) {
            return;
        }
        CameraShakeController.tick((int) CLIENT.level.getGameTime(), (float) event.getPartialTick());
        // Camera.setup 中旋转事件早于位置事件，必须在这里生成本帧位姿。
        // 挂点的位置与四元数旋转由 samplePose／seatMount 按 partial 插值；
        // 可转摄像头按本帧挂点与世界目标求方向，并使用刻内时间限制可达转角。
        if (controllingSeat != null && logicalView != null) {
            float partial = (float) event.getPartialTick();
            if (activeCamera != null) {
                renderPose = activeCamera.samplePose(partial);
            } else {
                AimPose mount = seatMount(partial);
                ThirdPersonViewpoint view = wasFreeLook ? FREE_LOOK : SEAT_VIEW;
                // 鼠标观察角已逐帧累计，只与插值后的挂点合成，不推进逻辑或重复累计输入。
                // 环绕位置与朝向在此一起采样，位置事件仅应用这份结果。
                renderPose = view.sample(mount.rotation(), seatOrigin(view, mount, partial)).pose();
            }
            // 在渲染边界转换为原版角度符号，欧拉角仅用于事件输出。
            Quaternionf rotation = SparkMathKt.toQuaternionf(renderPose.rotation());
            Vector3f euler = rotation.getEulerAnglesYXZ(new Vector3f());
            event.setPitch((float) -Math.toDegrees(euler.x));
            event.setYaw(180 - (float) Math.toDegrees(euler.y));
            event.setRoll((float) -Math.toDegrees(euler.z));
        } else {
            renderPose = null;
        }
        // 显示抖动最后合成，不能参与世界目标累计或网络输入。
        Vec3 shake = CameraShakeController.getRotationOffset();
        float scale = activeCamera != null ? CameraShakeController.CAMERA_MODE_ROTATION_SCALE
                : CLIENT.options.getCameraType().isFirstPerson() ? CameraShakeController.FIRST_PERSON_ROTATION_SCALE
                : CameraShakeController.THIRD_PERSON_ROTATION_SCALE;
        event.setPitch(event.getPitch() + (float) shake.x * scale);
        event.setYaw(event.getYaw() + (float) shake.y * scale);
        event.setRoll(event.getRoll() + (float) shake.z * scale);
    }

    /** 渲染阶段按经过秒数平滑显示变焦，输入只读取当前倍率。 */
    @SubscribeEvent
    public static void updateCameraScale(ViewportEvent.ComputeFov event) {
        if (activeCamera == null) {
            return;
        }
        // 使用实际经过秒数做指数平滑，避免不同渲染帧率改变变焦过渡速度。
        long now = System.nanoTime();
        if (lastZoomNanos != 0) {
            double seconds = Math.min((now - lastZoomNanos) / 1e9, .5);
            currentZoom += (targetZoom - currentZoom) * (float) (1 - Math.exp(-10 * seconds));
        }
        lastZoomNanos = now;
        event.setFOV(AbstractViewpointSubsystemStaticAttr.REFERENCE_FOV / currentZoom);
    }

    /** 主线程在设备的基础倍率与最大倍率之间切换。 */
    public static void toggleZoom() {
        if (activeCamera == null) {
            return;
        }
        var attr = activeCamera.attr.staticAttribute;
        targetZoom = targetZoom <= attr.baseZoom + .2f ? attr.maxZoom : attr.baseZoom;
    }

    /** 主线程按倍率增量调整变焦，并限制在设备允许范围内。 */
    public static void adjustZoom(float delta) {
        if (activeCamera != null) {
            targetZoom = Math.clamp(targetZoom + delta,
                    activeCamera.attr.staticAttribute.baseZoom, activeCamera.attr.staticAttribute.maxZoom);
        }
    }

    /** 临时自由观察沿用较低的鼠标灵敏度。 */
    @SubscribeEvent
    public static void modifySensitivity(CalculatePlayerTurnEvent event) {
        if (RawInputHandler.freeCam) {
            event.setMouseSensitivity(event.getMouseSensitivity() * .5);
        }
    }

    /** 渲染阶段按座椅权限隐藏手部；允许显示时应用与观察方向对应的摆动。 */
    @SubscribeEvent
    public static void onRenderArm(RenderHandEvent event) {
        if (controllingSeat == null || CLIENT.player == null) {
            return;
        }
        if (!controllingSeat.attr.isAllowUseItems() || isCameraMode()) {
            event.setCanceled(true);
        }
        PoseStack stack = event.getPoseStack();
        float bob = Mth.lerp(event.getPartialTick(), CLIENT.player.yBobO, CLIENT.player.yBob);
        stack.mulPose(Axis.YP.rotationDegrees((CLIENT.player.getViewYRot(event.getPartialTick()) - bob) * -.1f));
    }
}
