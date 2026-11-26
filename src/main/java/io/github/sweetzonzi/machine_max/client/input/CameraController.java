package io.github.sweetzonzi.machine_max.client.input;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Quaternion;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.util.MMMath;
import io.github.sweetzonzi.machine_max.util.RotationDamper;
import io.github.sweetzonzi.machine_max.client.event.ComputeCameraPosEvent;
import io.github.sweetzonzi.machine_max.client.target.TargetDesignatorHandler;
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
 * 固定炮镜显示真实光轴，鼠标更新独立的武器期望目标；渲染只读取逻辑状态，仅推进显示过渡。
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
public final class CameraController {
    private static final Minecraft CLIENT = Minecraft.getInstance();

    /** 座椅正常观察与临时自由观察各自保存状态，临时观察不改写武器目标。 */
    private static final ThirdPersonViewpoint SEAT_VIEW = new ThirdPersonViewpoint();
    private static final ThirdPersonViewpoint FREE_LOOK = new ThirdPersonViewpoint();

    /** 以下逻辑状态均由客户端主线程持有；离座或换世界时由 clearWorld 释放。 */
    @Getter
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
    /** 本帧从未偏置焦点解析出的武器目标，与回正中的显示朝向分别缓存。 */
    private static Vec3 renderedSeatAimPoint;
    /** 发往 WC、供 HUD 投影的有限世界目标点。 */
    private static Vec3 expectedPoint;
    private static boolean wasFreeLook;
    private static boolean seatFollowing;
    private static boolean seatFirstPerson;
    /** 切入设备前的原版视角，退出时恢复。 */
    private static CameraType seatCameraType;

    /** 第三人称实际相机朝向追赶逻辑焦点的时间常数（秒）。 */
    private static final double FOLLOW_SECONDS = .05;
    /** 只平滑显示相机朝向；逻辑焦点与武器目标保持即时更新。 */
    private static final RotationDamper displayFollowRotation = new RotationDamper(FOLLOW_SECONDS);

    /** 速度对第三人称距离的平滑修正，每逻辑 tick 更新一次。 */
    private static float speedDistanceFactor;
    private static float targetZoom = 1;
    @Getter
    private static float currentZoom = 1;
    /** 当前画面的垂直视场角，供实体镜头与座椅观察共用屏幕投影。 */
    @Getter
    private static float currentFov = AbstractViewpointSubsystemStaticAttr.REFERENCE_FOV;
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

    /**
     * HUD 读取本帧座椅观察焦点解析出的控制目标；跟随模式按刻内焦点合成，避免 tick 末角度造成跳动。
     * 自由观察保持世界焦点；临时自由观察保留武器目标，因此圆环可以偏离画面中心。
     * 回正期间缓存的是正常控制目标，独立于平滑后的画面朝向。
     */
    @Nullable
    public static Vec3 getRenderedSeatAimPointWorld() {
        if (controllingSeat == null || activeCamera != null || expectedPoint == null) {
            return null;
        }
        return renderedSeatAimPoint != null ? renderedSeatAimPoint : expectedPoint;
    }

    /** HUD 读取无稳轴累计偏差所对应的机构正解点；采样未就绪时不绘制目标圈。 */
    @Nullable
    public static Vec3 getRelativeAimPointWorld() {
        float partial = CLIENT.gameRenderer.getMainCamera().getPartialTickTime();
        AimGeometrySnapshot geometry = fixedSight == null || fixedController == null ? null
                : fixedController.sampleAimGeometry(fixedSight, partial);
        if (aimTarget == null || geometry == null) {
            return null;
        }
        // 累计目标在插值基座中正解，再投影到本帧真实炮镜。
        // 同一帧的实际角只填充稳定轴；无稳轴直接使用操作者累计角。
        return aimTarget.relativePoint(geometry);
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
        // 同一装配体内换座继承武器目标；离座或换载具建立独立的观察状态。
        // 必须先保存继承值再清理旧来源，清理会释放全部控制组输出。
        if (seat != controllingSeat) {
            Vec3 inherited = controllingSeat != null && seat != null
                    && controllingSeat.getSubPart().getPart().assembly == seat.getSubPart().getPart().assembly
                    ? currentAimTargetPoint() : null;
            clearWorld();
            controllingSeat = seat;
            if (seat == null) {
                return;
            }
            enterSeat(inherited);
        }
        if (seat == null) {
            aimDirection = CLIENT.player.getLookAngle();
            return;
        }
        // 候选之外的视点（所属控制器离开活动集合、宿主子零件被移除）退回座椅观察，让失效设备退出输入来源。
        if (activeCamera != null && !seat.hasViewCandidate(activeCamera)) {
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
                enterSeat(currentAimTargetPoint());
            }
            updateSeat(Vec2.ZERO);
            advanceDisplayFollow();
        } else if (activeCamera instanceof CameraSubsystem) {
            publishDevice();
        } else {
            updateFixed();
        }
        send();
        publishNativeSeatFacing();
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
            if (aimTarget != null) {
                // 光学领先量与圈的屏幕投影使用同一帧参考。tick 末光轴先于可视光轴，
                // 用它限幅会让触限目标跟随每 tick 的角度台阶，圈相对平滑画面反复跳动。
                // partial 的冻结规则与 GameRenderer.renderLevel 中 Camera.setup 的入参一致。
                float partial = CLIENT.getCameraEntity() != null && CLIENT.level.tickRateManager()
                        .isEntityFrozen(CLIENT.getCameraEntity()) ? 1
                        : CLIENT.getTimer().getGameTimeDeltaPartialTick(true);
                // 无稳轴的 60°偏差也相对于这帧实际机构角限幅，避免触限时混入物理采样台阶。
                AimGeometrySnapshot geometry = mode() == AimTargetState.Mode.WORLD || fixedController == null
                        ? null : fixedController.sampleAimGeometry(fixedSight, partial);
                if (mode() == AimTargetState.Mode.WORLD || geometry != null) {
                    aimTarget.applyMouse(fixedSight.samplePose(partial), geometry, delta);
                    expectedPoint = aimTarget.getWorldPoint();
                }
            }
        } else if (activeCamera instanceof CameraSubsystem camera) {
            camera.turnView(delta);
        } else {
            updateSeat(delta);
            publishNativeSeatFacing();
        }
    }

    /** 原版玩家物品只跟随普通座椅观察；设备瞄准与临时自由观察不改玩家本体朝向。 */
    private static void publishNativeSeatFacing() {
        if (controllingSeat == null || activeCamera != null || RawInputHandler.freeCam
                || logicalView == null || CLIENT.player == null) {
            return;
        }
        Vec2 angles = ViewMath.directionAngles(logicalView.pose().forward());
        CLIENT.player.setXRot((float) -Math.toDegrees(angles.x));
        CLIENT.player.setYRot((float) Math.toDegrees(angles.y) + 180);
    }

    /** 座位视角已自行消费鼠标增量，避免原版再叠加一次玩家朝向变化。 */
    public static boolean shouldSuppressVanillaTurn() {
        return controllingSeat != null;
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
        int viewIndex = activeCamera == null ? -1 : activeCamera.getSubPart().subsystemIndex(activeCamera);
        String viewName = activeCamera == null ? "seat" : activeCamera.getName();
        // 本地信号与上行载荷使用同一完整值，服务端再校验座椅、设备和活动控制组。
        // 重复发送只覆盖状态，不会让每个物理子步再次累计鼠标增量。
        // 指示器锁定期间，这个点换成锁定目标的真值点：武器侧不区分点来自准星还是锁定。
        Vec3 aimPoint = TargetDesignatorHandler.resolveAimPoint(expectedPoint);
        controllingSeat.setViewInputSignal(new ViewInputSignal(aimPoint, relativeAim, mode()), viewPart, viewName);
        PacketDistributor.sendToServer(new ViewInputPayload(controllingSeat.getSubPart().getId(),
                controllingSeat.getSubPart().subsystemIndex(controllingSeat),
                viewPart, viewIndex, controllingSeat.getControlGroupSet().getActiveIndex(), aimPoint, relativeAim));
    }

    /** 主线程维护固定炮镜的输入来源，并单独发布实际观察光轴。 */
    private static void updateFixed() {
        SightSubsystem sight = (SightSubsystem) activeCamera;
        AimPose actual = sight.samplePose(1);
        // 机构取自该视点在候选里的配对控制器；没有配对时回落到现行主 WC 口径。
        WeaponControllerSubsystem controller = controllingSeat.getViewCandidateController(sight);
        if (controller == null) {
            controller = controllingSeat.getActiveMainWeaponController();
        }
        sight.setAimController(controller);
        if (fixedSight != sight || fixedController != controller) {
            if (fixedSight != null && fixedSight != sight) {
                fixedSight.setAimController(null);
            }
            // 换视点或主 WC 时释放旧视点输出，随后按继承目标反解新机构的相对目标。
            // 只清视点频道：扳机多半还按着，清掉会让本端丢掉按住的事实而服务端照旧开火
            controllingSeat.clearViewSignals();
            aimTarget = null;
            fixedSight = sight;
            fixedController = controller;
        }
        AimGeometrySnapshot geometry = sight.getAimGeometry();
        if (mode() != AimTargetState.Mode.WORLD && geometry == null) {
            // 物理线程尚未发布当前机构时，画面可用，但不发送错误零位目标。
            if (aimTarget != null) {
                controllingSeat.clearViewSignals();
            }
            aimTarget = null;
            logicalView = new ViewMath.Snapshot(actual, point(actual));
            aimDirection = actual.forward();
            return;
        }
        if (aimTarget == null) {
            Vec3 inherited = expectedPoint != null ? expectedPoint : point(actual);
            aimTarget = AimTargetState.fromWorldTarget(mode(), inherited, geometry);
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

    /** 客户端主线程切入座椅观察，按目标反算第三人称焦点并清理固定炮镜来源。 */
    private static void enterSeat(Vec3 inheritedAimPoint) {
        if (controllingSeat == null) {
            return;
        }
        // 第一人称固定跟随座椅；第三人称按玩家覆盖或座椅默认值选择跟随／自由。
        seatFirstPerson = CLIENT.options.getCameraType().isFirstPerson();
        seatFollowing = seatFirstPerson || ControlPreference.shouldFollowPose(controllingSeat);
        AimPose mount = seatMount(1);
        Quaternion reference = mount.rotation();
        Vec3 anchor = seatFirstPerson ? mount.position() : seatAimAnchor(mount, reference, 1);
        Vec3 inheritedFocus = inheritedAimPoint;
        if (inheritedAimPoint != null && !seatFirstPerson) {
            inheritedFocus = AimPointResolver.resolveFocusFromAim(
                    reference, inheritedAimPoint, anchor, seatPitchBias());
        } else if (inheritedAimPoint == null && seatFirstPerson) {
            inheritedFocus = point(mount);
        }
        // 第一人称零偏置，直接沿用目标；第三人称先撤销偏置，再由正常更新重新解析同一目标。
        SEAT_VIEW.enter(seatFollowing, reference,
                anchor, inheritedFocus,
                seatPitchBias(), !seatFirstPerson);
        if (fixedSight != null) {
            fixedSight.setAimController(null);
        }
        fixedSight = null;
        fixedController = null;
        wasFreeLook = false;
        updateSeat(Vec2.ZERO);
        AimPose currentMount = seatMount(1);
        displayFollowRotation.reset(
                SparkMathKt.toQuaternionf(desiredThirdPersonRotation(
                        currentMount, logicalView.focusPoint(), 1)), System.nanoTime());
    }

    /**
     * 主线程按座椅挂点更新普通观察或临时自由观察。<br>
     * 临时观察只改变画面；进入时保存控制目标，退出时恢复该目标的指向。
     *
     * @param input 本次鼠标俯仰／偏航增量，弧度，零值也更新乘坐随动
     */
    private static void updateSeat(Vec2 input) {
        AimPose mount = seatMount(1);
        // 逻辑焦点始终使用当前挂点姿态，显示相机的追赶单独在渲染链处理。
        Quaternion reference = mount.rotation();
        // 自由观察保留独立状态，不能让临时查看方向变成新的武器指向。
        if (RawInputHandler.freeCam && !wasFreeLook) {
            // 以逻辑瞄准锚点保存当前显示光轴，让自由观察轨道始终绕载具锚点计算。
            Vec3 anchor = seatAimAnchor(mount, reference, 1);
            Vec3 direction = renderPose != null ? renderPose.forward()
                    : SEAT_VIEW.sample(reference, anchor).pose().forward();
            Vec3 observed = anchor.add(direction.scale(AimMath.AIM_DISTANCE_METERS));
            FREE_LOOK.enter(false, reference, anchor, observed, 0);
            SEAT_VIEW.cancelReturn();
        } else if (!RawInputHandler.freeCam && wasFreeLook) {
            Quaternionf displayed = renderPose != null
                    ? SparkMathKt.toQuaternionf(renderPose.rotation())
                    : (seatFirstPerson
                    ? SparkMathKt.toQuaternionf(SEAT_VIEW.sample(reference,
                            seatAimAnchor(mount, reference, 1)).pose().rotation())
                    : displayFollowRotation.sample(1));
            SEAT_VIEW.beginReturn(displayed);
        }
        wasFreeLook = RawInputHandler.freeCam;
        // 两套观察状态独立，只有普通观察可以更新待发送的武器世界点。
        ThirdPersonViewpoint view = wasFreeLook ? FREE_LOOK : SEAT_VIEW;
        // 此处限位只属于座椅观察范围，与武器的机械行程无关。
        AimAxisLimit pitch = new AimAxisLimit(false, Math.toRadians(controllingSeat.attr.staticAttribute.views.minPitch()),
                Math.toRadians(controllingSeat.attr.staticAttribute.views.maxPitch()));
        float yaw = controllingSeat.attr.staticAttribute.views.yawLimit();
        AimAxisLimit yawLimit = yaw >= 360 ? AimAxisLimit.CONTINUOUS : new AimAxisLimit(false, Math.toRadians(-yaw / 2), Math.toRadians(yaw / 2));
        Vec3 anchor = seatAimAnchor(mount, reference, 1);
        logicalView = view.update(reference, anchor, input, yawLimit, pitch);
        if (!wasFreeLook) {
            expectedPoint = AimPointResolver.resolveFixedDistance(
                    logicalView.pose(), logicalView.focusPoint(), anchor, seatPitchBias());
        }
        aimDirection = logicalView.pose().forward();
    }

    /** 主线程取得切换用的目标点；可转摄像头取尚未追到的期望方向。 */
    @Nullable
    private static Vec3 currentAimTargetPoint() {
        if (activeCamera instanceof CameraSubsystem camera) {
            Vec3 target = camera.sampleViewTarget(1);
            if (target != null) {
                return target;
            }
        }
        if (expectedPoint != null) {
            return expectedPoint;
        }
        return activeCamera == null ? (wasFreeLook ? FREE_LOOK : SEAT_VIEW).focusPoint()
                : point(activeCamera.samplePose(1));
    }

    /** 主线程推进实际相机朝向追赶；不改写逻辑焦点或武器目标。 */
    private static void advanceDisplayFollow() {
        if (seatFirstPerson || wasFreeLook || SEAT_VIEW.isReturning() || logicalView == null) {
            return;
        }
        AimPose mount = seatMount(1);
        displayFollowRotation.advance(
                SparkMathKt.toQuaternionf(desiredThirdPersonRotation(
                        mount, logicalView.focusPoint(), 1)), System.nanoTime());
    }

    /** 按当前相机轨道锚点直接求看向逻辑焦点的期望姿态。 */
    private static Quaternion desiredThirdPersonRotation(AimPose mount, Vec3 focusPoint, float partialTick) {
        Vec3 pivot = seatPivot(mount, mount.rotation(), partialTick);
        return ViewMath.lookAt(mount.rotation(), pivot, focusPoint);
    }

    private static AimPose seatMount(float partialTick) {
        var mount = controllingSeat.getSubPart().getLerpedLocatorWorldTransform(controllingSeat.attr.locator, partialTick);
        return new AimPose(SparkMathKt.toVec3(mount.getTranslation()), mount.getRotation());
    }

    private static float seatPitchBias() {
        return seatFirstPerson ? 0 : (float) Math.toRadians(
                controllingSeat.attr.staticAttribute.views.thirdPersonPitchBias());
    }

    private static Vec3 seatAimAnchor(AimPose mount, Quaternion reference, float partialTick) {
        var settings = controllingSeat.attr.staticAttribute.views;
        boolean first = CLIENT.options.getCameraType().isFirstPerson();
        Quaternionf rotation = SparkMathKt.toQuaternionf(reference);
        Vec3 anchor = mount.position();
        if (first) {
            Vec3 extraOffset = settings.firstPersonOffset();
            Vector3f localOffset = new Vector3f(
                    (float) extraOffset.x,
                    1.1f + (float) extraOffset.y,
                    (float) extraOffset.z);
            rotation.transform(localOffset);
            return anchor.add(localOffset.x, localOffset.y, localOffset.z);
        }
        if (!first && settings.focusOnCenter()
                && controllingSeat.getSubPart().getPart().assembly instanceof VehicleCore vehicle) {
            anchor = vehicle.getOldPosition().scale(1 - partialTick).add(vehicle.getPosition().scale(partialTick));
        } else {
            Vector3f eye = rotation.transform(new Vector3f(0, 1.1f, 0));
            anchor = anchor.add(eye.x, eye.y, eye.z);
        }
        return anchor;
    }

    /** 相机轨道锚点：逻辑瞄准锚点加上按座椅参考旋转的第三人称偏移。 */
    private static Vec3 seatPivot(AimPose mount, Quaternion reference, float partialTick) {
        Vec3 anchor = seatAimAnchor(mount, reference, partialTick);
        if (CLIENT.options.getCameraType().isFirstPerson()) {
            return anchor;
        }
        Vec3 offset = controllingSeat.attr.staticAttribute.views.thirdPersonOffset();
        Vector3f localOffset = new Vector3f((float) offset.x, (float) offset.y, (float) offset.z);
        SparkMathKt.toQuaternionf(reference).transform(localOffset);
        return anchor.add(localOffset.x, localOffset.y, localOffset.z);
    }

    /** 沿指定观察方向后退并处理碰撞；渲染传入平滑方向，使轨道和朝向一起回正。 */
    private static Vec3 seatOrigin(Vec3 pivot, Vec3 forward) {
        var settings = controllingSeat.attr.staticAttribute.views;
        if (CLIENT.options.getCameraType().isFirstPerson()) {
            return pivot;
        }
        // 第三人称沿指定视线后退，前置视角反转距离，观察目标本身保持独立。
        double distance = (settings.distanceScale() + .4 * speedDistanceFactor)
                * (controllingSeat.getSubPart().getPart().assembly instanceof VehicleCore vehicle ? vehicle.cameraDistance : 4);
        if (CLIENT.options.getCameraType() == CameraType.THIRD_PERSON_FRONT) {
            distance = -distance;
        }
        Vec3 destination = ViewMath.orbitPosition(pivot, forward, distance);
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
        // 切换时按候选顺序循环；仅允许启用循环切换的设备进入列表。
        List<AbstractViewpointSubsystem> devices = controllingSeat.getViewCandidates().stream()
                .map(candidate -> candidate.viewpoint())
                .filter(c -> c.attr.staticAttribute.allowCycle).toList();
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
        // 所有视点都继承期望目标；可转摄像头保留尚未追到的观察目标。
        Vec3 inheritedAimPoint = currentAimTargetPoint();
        activeCamera = devices.get(index);
        controllingSeat.setActiveCamera(activeCamera);
        if (activeCamera instanceof CameraSubsystem camera) {
            camera.enterView(inheritedAimPoint);
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

    /** 客户端主线程退出实体视点，恢复原版视角并将设备目标映射为座椅观察状态。 */
    public static void exitCameraMode() {
        if (activeCamera == null) {
            return;
        }
        if (activeCamera instanceof SightSubsystem) {
            updateFixed();
        }
        Vec3 inheritedAimPoint = currentAimTargetPoint();
        // 先解除实体设备来源，再恢复切入前的原版视角和座椅观察状态。
        activeCamera = null;
        if (seatCameraType != null) {
            setPerspective(seatCameraType);
        }
        if (controllingSeat != null) {
            controllingSeat.setActiveCamera(null);
        }
        targetZoom = currentZoom = 1;
        enterSeat(inheritedAimPoint);
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
        renderedSeatAimPoint = null;
        SEAT_VIEW.cancelReturn();
        FREE_LOOK.cancelReturn();
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

    /** 自定义轨道已把配置距离写入 renderPose.position；阻止原版再叠加一次后退距离。 */
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
                renderedSeatAimPoint = null;
            } else {
                AimPose mount = seatMount(partial);
                ThirdPersonViewpoint view = wasFreeLook ? FREE_LOOK : SEAT_VIEW;
                Quaternion reference = mount.rotation();
                Vec3 aimAnchor = seatAimAnchor(mount, reference, partial);
                ViewMath.Snapshot focus = view.sample(reference, aimAnchor);
                if (CLIENT.options.getCameraType().isFirstPerson()) {
                    Quaternionf desired = SparkMathKt.toQuaternionf(focus.pose().rotation());
                    Quaternionf displayed = wasFreeLook ? desired
                            : view.sampleDisplayRotation(desired, System.nanoTime());
                    renderedSeatAimPoint = wasFreeLook ? expectedPoint
                            : AimPointResolver.resolveFixedDistance(
                                    focus.pose(), focus.focusPoint(), aimAnchor, seatPitchBias());
                    renderPose = new AimPose(aimAnchor, SparkMathKt.toBQuaternion(displayed));
                } else {
                    Vec3 trackAnchor = seatPivot(mount, reference, partial);
                    Quaternionf desiredDisplay = wasFreeLook
                            ? SparkMathKt.toQuaternionf(focus.pose().rotation())
                            : SparkMathKt.toQuaternionf(desiredThirdPersonRotation(
                                    mount, focus.focusPoint(), partial));
                    Quaternionf displayed = sampleThirdPersonDisplayRotation(
                            view, desiredDisplay, partial);
                    Vector3f forward = displayed.transform(new Vector3f(0, 0, -1));
                    Vec3 cameraPosition = seatOrigin(trackAnchor, new Vec3(forward.x, forward.y, forward.z));
                    renderedSeatAimPoint = wasFreeLook ? expectedPoint
                            : AimPointResolver.resolveFixedDistance(
                                    focus.pose(), focus.focusPoint(), aimAnchor, seatPitchBias());
                    renderPose = new AimPose(cameraPosition, SparkMathKt.toBQuaternion(displayed));
                }
            }
            // 在渲染边界转换为原版角度符号，欧拉角仅用于事件输出。
            Quaternionf rotation = SparkMathKt.toQuaternionf(renderPose.rotation());
            Vector3f euler = rotation.getEulerAnglesYXZ(new Vector3f());
            event.setPitch((float) -Math.toDegrees(euler.x));
            event.setYaw(180 - (float) Math.toDegrees(euler.y));
            event.setRoll((float) -Math.toDegrees(euler.z));
        } else {
            renderPose = null;
            renderedSeatAimPoint = null;
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

    /** 渲染阶段采样相机朝向追赶，并在自由观察回正结束时同步阻尼状态。 */
    private static Quaternionf sampleThirdPersonDisplayRotation(
            ThirdPersonViewpoint view, Quaternionf desired, float partialTick) {
        if (wasFreeLook) {
            return desired;
        }
        if (!view.isReturning()) {
            return displayFollowRotation.sample(partialTick);
        }
        long nowNanos = System.nanoTime();
        Quaternionf displayed = view.sampleDisplayRotation(desired, nowNanos);
        if (!view.isReturning()) {
            AimPose mount = seatMount(1);
            displayFollowRotation.reset(SparkMathKt.toQuaternionf(
                    desiredThirdPersonRotation(mount, logicalView.focusPoint(), 1)), nowNanos);
        }
        return displayed;
    }

    /** 渲染阶段按经过秒数平滑显示变焦，输入只读取当前倍率。 */
    @SubscribeEvent
    public static void updateCameraScale(ViewportEvent.ComputeFov event) {
        if (activeCamera == null) {
            // 手持物还会单独计算一次固定 FOV，HUD 只使用世界画面的视场角。
            if (event.usedConfiguredFov()) {
                currentFov = (float) event.getFOV();
            }
            return;
        }
        // 使用实际经过秒数做指数平滑，避免不同渲染帧率改变变焦过渡速度。
        long now = System.nanoTime();
        if (lastZoomNanos != 0) {
            double seconds = Math.min((now - lastZoomNanos) / 1e9, .5);
            currentZoom += (targetZoom - currentZoom) * (float) (1 - Math.exp(-10 * seconds));
        }
        lastZoomNanos = now;
        currentFov = AbstractViewpointSubsystemStaticAttr.REFERENCE_FOV / currentZoom;
        event.setFOV(currentFov);
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
