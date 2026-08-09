package io.github.sweetzonzi.machine_max.client.input;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Matrix3f;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.event.ComputeCameraPosEvent;
import io.github.sweetzonzi.machine_max.client.input.CameraShakeController;
import io.github.sweetzonzi.machine_max.network.payload.PlayerLookAtPayload;
import io.github.sweetzonzi.machine_max.util.environment.EnvironmentSettings;
import io.github.sweetzonzi.machine_max.util.environment.EnvironmentWrapper;
import io.github.sweetzonzi.machine_max.common.attachment.ControlPreference;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.CameraSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CameraSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machine_max.network.payload.ViewInputPayload;
import io.github.sweetzonzi.machine_max.util.MMMath;
import jme3utilities.math.MyMath;
import lombok.Getter;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

import java.util.List;

@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
public class CameraController {
    private static Minecraft client;
    /**
     * 玩家乘坐载具的刚体变换，用于基于部件坐标系额外旋转视角
     */
    private static Transform extraTransform = new Transform();
    /**
     * 玩家乘坐载具的上一tick刚体变换，用于插值
     */
    private static Transform oldExtraTransform = new Transform();
    private static boolean onBoard = false;
    private static boolean justLeft = false;
    private static float targetViewPitch = 0;
    private static float targetViewYaw = 0;
    private static float targetViewRoll = 0;
    private static float aimPitch = 0;
    private static float aimYaw = 0;
    private static float aimRoll = 0;
    private static float pitch = 0;
    private static float yaw = 0;
    private static float roll = 0;
    public static Vec3 aimDirection = new Vec3(1, 0, 0);
    private static float speedDistanceFactor = 0.0f;
    private static boolean anglesInitialized = false;
    private static Vec3 lastSentAimPoint = null;
    private static final double AIM_MAX_DISTANCE = 64.0;
    private static final double AIM_POINT_THRESHOLD_SQ = 0.0001;

    // ===== 炮镜模式状态 =====
    /**
     * 当前激活的摄像机（null=普通座椅视角）
     */
    @Getter
    private static CameraSubsystem activeCamera = null;
    /**
     * 世界空间瞄准点（唯一真相源，稳定轴使用）。鼠标不动则此点不动
     */
    private static Vec3 aimPoint = null;
    /**
     * 无稳轴鼠标意图偏移（度），turnCamera 累加，鼠标不动则不变。代表玩家期望的观察方向
     */
    private static float localPitchOffsetDeg = 0f;
    /**
     * 无稳轴鼠标意图偏移（度），turnCamera 累加，鼠标不动则不变。代表玩家期望的观察方向
     */
    private static float localYawOffsetDeg = 0f;
    /**
     * aimLocal 追赶 local 的最大角速度（度/秒），模拟炮塔恒定转速上限。
     * 30 ≈ 0.5°/frame@60fps，小差值一步到位，大差值匀速追赶
     */
    private static final float AIM_LOCAL_MAX_SPEED_DEG = 60f;
    /**
     * 追赶 localPitchOffsetDeg 的延迟瞄准偏移（度），每帧固定角速度追赶
     */
    private static float aimLocalPitchOffsetDeg = 0f;
    /**
     * 追赶 localYawOffsetDeg 的延迟瞄准偏移（度），每帧固定角速度追赶
     */
    private static float aimLocalYawOffsetDeg = 0f;
    private static float pendingLocalPitchOffsetDeg = 0f;
    private static float pendingLocalYawOffsetDeg = 0f;
    /**
     * 上一次 aimLocal 更新的纳秒时间戳，用于帧率无关的 dt 计算
     */
    private static long lastAimLocalUpdateNanos = 0;
    /**
     * 变焦目标倍率（toggleZoom/adjustZoom 修改此值），currentZoom 在渲染帧中逐渐追上
     */
    private static float targetZoom = 1f;
    /**
     * 当前变焦倍率（平滑过渡值），驱动 FOV 和灵敏度
     */
    @Getter
    private static float currentZoom = 1f;
    /**
     * 变焦过渡速度（exp衰减系数），值越大越快。10≈0.3s内达95%
     */
    private static final float ZOOM_SPEED = 10.0f;
    /**
     * 上次变焦 lerp 的纳秒时间戳，用于帧率无关平滑
     */
    private static long lastZoomLerpNanos = 0;

    public static boolean isCameraMode() {
        return activeCamera != null;
    }

    /** @return 玩家当前是否乘坐载具（座椅模式） */
    public static boolean isOnBoard() {
        return onBoard;
    }

    /**
     * 获取炮管在世界空间中指向的远处瞄准点（唯一真相源）。<p>
     * 从相机子系统 locator（与炮管随动）的前方 Z- 方向投射 1000 米，
     * 得到世界坐标点。该点可通过 {@code ScreenProjectionUtil.worldToScreenOffset}
     * 投影到屏幕坐标系中显示炮管准星。<br>
     * 适用于稳定器模式和非稳定器模式，因为始终基于 locator 的实际世界位姿计算。
     *
     * @param partialTick 插值因子（0~1），用于 locator 平滑插值
     * @return 炮管前方远处一点的世界坐标，摄像机未激活时返回 {@code null}
     */
    @Nullable
    public static Vec3 getCameraAimPointWorld(float partialTick) {
        if (activeCamera == null) return null;

        Transform locator = activeCamera.getLerpedLocatorWorldTransform(partialTick);

        // 炮管在世界空间中的前方指向（locator Z- 即炮管指向）
        Vector3f barrelForward = new Vector3f(0, 0, -1);
        locator.getRotation().toRotationMatrix().mult(barrelForward, barrelForward);
        barrelForward.normalize();

        // 炮管前方远处一点（世界坐标）
        return SparkMathKt.toVec3(locator.getTranslation().add(barrelForward.mult(1000)));
    }

    @SubscribeEvent
    public static void updateCameraPos(ComputeCameraPosEvent event) {
        if (client == null) client = Minecraft.getInstance();
        Camera camera = event.getCamera();
        float partialTick = (float) event.getPartialTick();
        CameraType type = client.options.getCameraType();
        Entity entity = camera.getEntity();

        // 炮镜模式：相机固定在 locator 位置，不应用任何抖动/惯性效果
        if (activeCamera != null && activeCamera.isActive()) {
            Transform locator = activeCamera.getLerpedLocatorWorldTransform(partialTick);
            event.setCameraPos(SparkMathKt.toVec3(locator.getTranslation()));
            return;
        }

        // 计算理想相机位置（idealPos = 无抖动/惯性偏移时的相机位置）
        Vec3 idealPos = null;

        if (((IEntityMixin) entity).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
            Quaternionf seatRot = new Quaternionf();
            seat.getSubPart().getWorldPositionMatrix(partialTick).getNormalizedRotation(seatRot);
            if (!type.isFirstPerson() && seat.attr.staticAttribute.views.focusOnCenter()) {
                if (seat.getOwner().getSubPart().getPart().getAssembly() instanceof VehicleCore vehicle) {
                    idealPos = vehicle.getPosition().scale(partialTick).add(vehicle.getOldPosition().scale(1 - partialTick))
                            .add(SparkMathKt.toVec3(MMMath.localVectorToWorldVector(
                                    PhysicsHelperKt.toBVector3f(seat.attr.staticAttribute.views.thirdPersonOffset()),
                                    SparkMathKt.toBQuaternion(seatRot))));
                }
            } else {
                Transform transform = seat.getOwner().getSubPart().getLerpedLocatorWorldTransform(seat.attr.locator, new Transform().setTranslation(new Vector3f(0, 1.1f, 0)), partialTick);
                idealPos = SparkMathKt.toVec3(transform.getTranslation())
                        .add(SparkMathKt.toVec3(MMMath.localVectorToWorldVector(
                                PhysicsHelperKt.toBVector3f(seat.attr.staticAttribute.views.firstPersonOffset()),
                                SparkMathKt.toBQuaternion(seatRot))));
            }

            // System B：载具相机惯性追赶 —— 暂已关闭（弹簧阻尼效果一般，临时移除，保留调用可随时恢复）
            // if (seat.getOwner().getSubPart().getPart().getAssembly() instanceof VehicleCore vehicle) {
            //     float maxOffset = computeVehicleMaxOffset(vehicle);
            //     CameraShakeController.onVehicleCameraChase(idealPos, maxOffset);
            // }
        }

        // 非载具模式下以当前相机位置作为基准
        if (idealPos == null) {
            idealPos = camera.getPosition();
        }

        // System B：应用载具惯性追赶偏移 —— 暂已关闭（与上方 onVehicleCameraChase 一并移除）
        // Vec3 chaseOff = CameraShakeController.getVehicleChaseOffset();
        // if (chaseOff.lengthSqr() > 1e-8) {
        //     float fpScale = type.isFirstPerson() ? CameraShakeController.getFpVehicleScale() : 1.0f;
        //     idealPos = idealPos.add(chaseOff.scale(fpScale));
        // }

        // System A：应用位置抖动偏移（仅第三人称非炮镜模式）
        float posScale = CameraShakeController.getPositionShakeFactor();
        if (posScale > 0) {
            Vec3 posOff = CameraShakeController.getPositionOffset();
            if (posOff.lengthSqr() > 1e-8) {
                idealPos = idealPos.add(posOff.scale(posScale));
            }
        }

        event.setCameraPos(idealPos);
    }

    @SubscribeEvent
    public static void updateCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        Camera camera = event.getCamera();
        Entity entity = camera.getEntity();
        if (activeCamera != null && activeCamera.isActive()) {
            event.setDistance(0f);
            return;
        }
        if (((IEntityMixin) entity).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
            if (seat.getOwner().getSubPart().getPart().getAssembly() instanceof VehicleCore vehicle) {
                speedDistanceFactor = 0.8f * speedDistanceFactor + 0.2f * (float) (2 * MMMath.sigmoid(0.1 * vehicle.getVelocity().length()) - 1);
                float newDistance = (float) ((seat.attr.staticAttribute.views.distanceScale() + 0.4 * speedDistanceFactor) * vehicle.cameraDistance);
                event.setDistance(newDistance);
            }
        }
    }

    private static final Transform tmpViewTransform = Transform.IDENTITY.clone();

    @SubscribeEvent
    public static void updateCameraRot(ViewportEvent.ComputeCameraAngles event) {
        if (client == null) client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) return;

        Camera camera = event.getCamera();
        CameraType type = client.options.getCameraType();
        Entity entity = camera.getEntity();
        float partialTick = (float) event.getPartialTick();

        // ===== 镜头抖动控制器本帧更新（System A + B 弹簧积分） =====
        CameraShakeController.tick((int) client.level.getGameTime(), partialTick);

        if (!anglesInitialized) {
            initializeAngles(entity, partialTick);
        }

        // 炮镜模式（强制第一人称已在 tickCameraMode 中处理）
        if (activeCamera != null && activeCamera.isActive()) {
            updateCameraRotCameraMode(event, partialTick);
            // 炮镜模式：旋转偏移 × CAMERA_MODE_ROTATION_SCALE，位置偏移禁用
            applyShakeRotation(event, CameraShakeController.CAMERA_MODE_ROTATION_SCALE);
            return;
        }

        AbstractControllableSubsystem subsystem = ((IEntityMixin) entity).machine_Max$getControllingSubsystem();
        if (subsystem instanceof SeatSubsystem seat) {
            updateCameraRotSeatMode(event, seat, type, partialTick);
        } else {
            updateCameraRotDefault(event, entity, partialTick);
        }
        // 第一人称 × FIRST_PERSON_ROTATION_SCALE，第三人称 × THIRD_PERSON_ROTATION_SCALE
        applyShakeRotation(event, type.isFirstPerson()
                ? CameraShakeController.FIRST_PERSON_ROTATION_SCALE
                : CameraShakeController.THIRD_PERSON_ROTATION_SCALE);
    }

    private static void initializeAngles(Entity entity, float partialTick) {
        aimPitch = entity.getViewXRot(partialTick);
        aimYaw = entity.getViewYRot(partialTick);
        aimRoll = 0F;
        targetViewPitch = aimPitch;
        targetViewYaw = aimYaw;
        targetViewRoll = aimRoll;
        pitch = aimPitch;
        yaw = aimYaw;
        roll = aimRoll;
        anglesInitialized = true;
    }

    /**
     * 炮镜模式每渲染帧的相机旋转计算（60fps+）。<p>
     * 分轴处理：稳定轴从 locator→aimPoint 反算局部角（补偿车体晃动），
     * 无稳轴用 local-aimLocal 差值（意图与炮塔的滞后差），aimLocal 在本方法内每帧 EMA 平滑逼近 local。<p>
     * 瞄准参考方向与视角方向分离：视角用 local-aimLocal，aimPoint 用 aimLocal，避免反馈环。
     */
    private static void updateCameraRotCameraMode(ViewportEvent.ComputeCameraAngles event, float partialTick) {
        CameraSubsystem camera = activeCamera;
        Transform locator = camera.getLerpedLocatorWorldTransform(partialTick);
        var sa = camera.attr.staticAttribute;

        // === 每帧更新 aimLocal（固定角速度追赶，帧率无关）===
        long now = System.nanoTime();
        if (lastAimLocalUpdateNanos == 0) lastAimLocalUpdateNanos = now;
        float dt = (now - lastAimLocalUpdateNanos) / 1_000_000_000f;
        lastAimLocalUpdateNanos = now;
        if (dt > 0 && dt < 0.5f) {
            float maxStep = AIM_LOCAL_MAX_SPEED_DEG * dt;
            float pitchDiff = localPitchOffsetDeg - aimLocalPitchOffsetDeg;
            float yawDiff = localYawOffsetDeg - aimLocalYawOffsetDeg;
            // 小差值（≤ 一帧最大步长）直接 snap，避免指数逼近在小差值下绝对速度过慢
            if (Math.abs(pitchDiff) <= maxStep) {
                aimLocalPitchOffsetDeg = localPitchOffsetDeg;
            } else {
                aimLocalPitchOffsetDeg += Math.signum(pitchDiff) * maxStep;
            }
            if (Math.abs(yawDiff) <= maxStep) {
                aimLocalYawOffsetDeg = localYawOffsetDeg;
            } else {
                aimLocalYawOffsetDeg += Math.signum(yawDiff) * maxStep;
            }
        }
        // ================================================================

        // 若尚无瞄准点，从摄像机正前方 100 米处初始化一个
        if (aimPoint == null) {
            Vector3f forward = new Vector3f(0, 0, -1);
            locator.getRotation().toRotationMatrix().mult(forward, forward);
            aimPoint = SparkMathKt.toVec3(locator.getTranslation().add(forward.mult(100)));
        }

        // 分轴计算摄像机内部偏转角（用于视角显示）：
        //   稳定轴 → 从 locator→aimPoint 反算局部角（补偿 mount 运动）
        //   无稳轴 → rad(local - aimLocal)，即意图与炮塔实际指向的差值。
        //            鼠标不动时 local 不变，炮塔追赶中 aimLocal→local，差值收敛到 0，
        //            相机稳定在意图世界方向不动（不被拉回炮管朝向）。
        float localPitch = sa.isVerticalStabilized() ?
                computeLocalPitchToPoint(locator, aimPoint) : (float) Math.toRadians(localPitchOffsetDeg - aimLocalPitchOffsetDeg);
        float localYaw = sa.isHorizontalStabilized() ?
                computeLocalYawToPoint(locator, aimPoint) : (float) Math.toRadians(localYawOffsetDeg - aimLocalYawOffsetDeg);

        // 分轴计算瞄准参考方向（用于 aimPoint 反算，不用于视角显示）：
        //   稳定轴 → 从 aimPoint 反算（补偿晃动）
        //   无稳轴 → 0°
        float aimLocalPitch = sa.isVerticalStabilized() ?
                computeLocalPitchToPoint(locator, aimPoint) : 0;
        float aimLocalYaw = sa.isHorizontalStabilized() ?
                computeLocalYawToPoint(locator, aimPoint) : 0;

        // 从 localPitch/localYaw + locator 构建视角世界方向（驱动相机旋转）
        Matrix3f camMat = new Quaternion().fromAngles(localPitch, localYaw, 0).toRotationMatrix();
        Vector3f dir = new Vector3f(0, 0, -1);
        camMat.mult(dir, dir);
        locator.getRotation().toRotationMatrix().mult(dir, dir);
        Vec3 aimDir;
        if (dir.lengthSquared() < 0.000001f) {
            Vector3f forward = new Vector3f(0, 0, -1);
            locator.getRotation().toRotationMatrix().mult(forward, forward);
            aimDir = SparkMathKt.toVec3(forward);
        } else {
            aimDir = SparkMathKt.toVec3(dir.normalize());
        }

        double pitchDeg = -Math.toDegrees(Math.asin(Math.clamp(aimDir.y, -1.0, 1.0)));
        double yawDeg = -Math.toDegrees(Math.atan2(aimDir.x, aimDir.z));

        event.setPitch((float) pitchDeg);
        event.setYaw((float) yawDeg);
        // 从定位器世界旋转中提取 roll 角度，使车体倾斜时视角随之倾斜
        Quaternionf locatorJoml = SparkMathKt.toQuaternionf(locator.getRotation());
        org.joml.Vector3f euler = new org.joml.Vector3f();
        locatorJoml.getEulerAnglesYXZ(euler);
        event.setRoll((float) Math.toDegrees(-euler.z));

        // 每帧用瞄准参考方向（而非视角方向）重算 aimPoint，保持距离。
        // 稳定轴：aimRef 指向 aimPoint → aimPoint 不变；
        // 无稳轴：0° → aimPoint 始终在 mount 正前方（炮口实际指向）。
        Matrix3f aimRefMat = new Quaternion().fromAngles(aimLocalPitch, aimLocalYaw, 0).toRotationMatrix();
        Vector3f aimRef = new Vector3f(0, 0, -1);
        aimRefMat.mult(aimRef, aimRef);
        locator.getRotation().toRotationMatrix().mult(aimRef, aimRef);
        aimPoint = SparkMathKt.toVec3(locator.getTranslation().add(aimRef.mult(1000)));
        aimDirection = aimDir;
    }

    /**
     * 座椅模式下的相机旋转计算（保留现有逻辑）
     */
    private static void updateCameraRotSeatMode(ViewportEvent.ComputeCameraAngles event, SeatSubsystem seat, CameraType type, float partialTick) {
        float lerp = 0.25f;
        pitch = (1 - lerp) * pitch + lerp * targetViewPitch;
        yaw = (1 - lerp) * yaw + lerp * targetViewYaw;
        roll = (1 - lerp) * roll + lerp * targetViewRoll;

        if (type.isFirstPerson() || ControlPreference.shouldFollowPose(seat)) {
            Transform extra = SparkMathKt.lerp(oldExtraTransform, extraTransform, partialTick);
            MyMath.combine(new Transform(Vector3f.ZERO, SparkMathKt.toBQuaternion(new Quaternionf().rotateZYX(
                            (float) Math.toRadians(roll),
                            (float) Math.toRadians(-yaw),
                            (float) Math.toRadians(pitch)))),
                    extra, tmpViewTransform);
            org.joml.Vector3f rot = new org.joml.Vector3f();
            SparkMathKt.toQuaternionf(tmpViewTransform.getRotation()).getEulerAnglesYXZ(rot);
            aimDirection = new Vec3(Math.cos(rot.x) * Math.sin(rot.y), -Math.sin(rot.x), Math.cos(rot.x) * Math.cos(rot.y));
            rot.mul((float) (180 / Math.PI));
            event.setPitch(rot.x);
            event.setYaw(-rot.y);
            event.setRoll(rot.z);
        } else {
            event.setPitch(pitch);
            event.setYaw(yaw);
            event.setRoll(roll);
            double pitchRad = -Math.toRadians(aimPitch);
            double yawRad   = -Math.toRadians(aimYaw);
            aimDirection = new Vec3(Math.cos(pitchRad) * Math.sin(yawRad), Math.sin(pitchRad), Math.cos(pitchRad) * Math.cos(yawRad));
        }

        if (!RawInputHandler.freeCam) {
            if (!onBoard) {
                onBoard = true;
                justLeft = false;
                aimYaw = 180;
                targetViewYaw = 180;
            }
            if (seat.getOwner().getSubPart().getEntity() instanceof MMPartEntity partEntity) {
                event.getCamera().getEntity().setXRot(aimPitch);
                event.getCamera().getEntity().setYRot(aimYaw + 180 + partEntity.getYRot());
            }
            if (justLeft) {
                targetViewPitch = aimPitch;
                targetViewYaw = aimYaw;
                targetViewRoll = aimRoll;
                pitch = aimPitch;
                yaw = aimYaw;
                roll = aimRoll;
            } else {
                targetViewPitch = 0.9f * targetViewPitch + 0.1f * aimPitch;
                targetViewYaw = 0.9f * targetViewYaw + 0.1f * aimYaw;
                targetViewRoll = 0.9f * targetViewRoll + 0.1f * aimRoll;
            }
        }
    }

    /**
     * 默认（非载具）相机旋转
     */
    private static void updateCameraRotDefault(ViewportEvent.ComputeCameraAngles event, Entity entity, float partialTick) {
        if (onBoard) {
            onBoard = false;
            justLeft = true;
            anglesInitialized = false;
            // 离开载具时清空惯性追赶状态，防止下次上车时残留 offset 导致瞬跳
            CameraShakeController.onLeaveVehicle();
        }
        event.setPitch(entity.getViewXRot(partialTick));
        event.setYaw(entity.getViewYRot(partialTick));
        event.setRoll(0F);
        aimDirection = new Vec3(Math.cos(aimPitch) * Math.sin(aimYaw), Math.sin(aimPitch), Math.cos(aimPitch) * Math.cos(aimYaw));
    }

    /**
     * 避免手臂位置跳变，取消手臂随动旋转
     */
    @SubscribeEvent
    public static void onRenderArm(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player instanceof IEntityMixin passenger && passenger.machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
            if (!seat.attr.staticAttribute.allowUseItems || isCameraMode()) event.setCanceled(true);
            PoseStack poseStack = event.getPoseStack();
            float partialTicks = event.getPartialTick();
            float f3 = Mth.lerp(partialTicks, player.yBobO, player.yBob);
            poseStack.mulPose(Axis.YP.rotationDegrees((player.getViewYRot(partialTicks) - f3) * -0.1F));
        }
    }

    @SubscribeEvent
    public static void updateCameraScale(ViewportEvent.ComputeFov event) {
        if (activeCamera != null && activeCamera.isActive()) {
            // 渲染帧级变焦平滑过渡（FPS 无关指数衰减）
            long now = System.nanoTime();
            if (lastZoomLerpNanos == 0) lastZoomLerpNanos = now;
            float dt = (now - lastZoomLerpNanos) / 1_000_000_000f;
            lastZoomLerpNanos = now;
            if (dt > 0 && dt < 1f) {
                float factor = 1.0f - (float) Math.exp(-ZOOM_SPEED * dt);
                currentZoom += (targetZoom - currentZoom) * factor;
            }

            double fov = CameraSubsystemStaticAttr.REFERENCE_FOV / currentZoom;
            event.setFOV(fov);
            return;
        }
        double scale = 1.0;
        double rawFov = event.getFOV();
        event.setFOV(rawFov / scale);
    }

    /**
     * 炮镜模式下鼠标与摄像机交互的核心方法。<p>
     * 分轴处理：稳定轴 → 在世界空间沿摄像机 localRight/localUp 平移 aimPoint；<br>
     * 无稳轴 → 累积鼠标增量到 localPitch/YawOffsetDeg（每 tick 清零发包）。
     */
    public static void turnCamera(double yRot, double xRot) {
        float f = (float) xRot * 0.15F; // 俯仰，鼠标垂直移动，xRot>0表示鼠标下移
        float f1 = (float) yRot * 0.15F; // 偏航，鼠标水平移动，yRot>0表示鼠标右移
        LocalPlayer player = client.player;
        if (player == null) return;

        // 炮镜模式：按稳定/无稳分轴处理
        if (activeCamera != null && activeCamera.isActive()) {
            var sa = activeCamera.attr.staticAttribute;
            boolean vertStab = sa.isVerticalStabilized();
            boolean horStab = sa.isHorizontalStabilized();

            // 变焦越高灵敏度越低：8×时鼠标量降为 1/8
            float sens = 0.15F / Math.max(currentZoom, 0.1f);
            f = (float) xRot * sens;
            f1 = (float) yRot * sens;

            // 稳定轴：在世界空间沿摄像机局部 right/up 平移 aimPoint
            if (horStab || vertStab) {
                if (aimPoint == null) return;
                Transform locator = activeCamera.getLerpedLocatorWorldTransform(1f);
                Vector3f camPos = locator.getTranslation();
                Vector3f aimPos = PhysicsHelperKt.toBVector3f(aimPoint);
                float dist = aimPos.subtract(camPos).length();
                if (dist < 1f) dist = 1f;

                // 取摄像机局部坐标系的 right / up 轴（转到世界空间）
                Vector3f localRight = new Vector3f(1, 0, 0);
                locator.getRotation().toRotationMatrix().mult(localRight, localRight);
                Vector3f localUp = new Vector3f(0, 1, 0);
                locator.getRotation().toRotationMatrix().mult(localUp, localUp);

                float yawAngle = (float) Math.toRadians(f1);
                float pitchAngle = (float) Math.toRadians(f);
                float moveX = (float) Math.tan(yawAngle) * dist;
                float moveY = (float) Math.tan(pitchAngle) * dist;

                double dx = 0, dy = 0, dz = 0;
                if (horStab) {
                    dx += localRight.x * moveX;
                    dy += localRight.y * moveX;
                    dz += localRight.z * moveX;
                }
                if (vertStab) {
                    dx -= localUp.x * moveY;
                    dy -= localUp.y * moveY;
                    dz -= localUp.z * moveY;
                }
                aimPoint = aimPoint.add(dx, dy, dz);
            }

            // 无稳轴：累积鼠标意图偏移（纯累加，不衰减，鼠标不动则意图不变）。
            // 限制相对于 aimLocal（炮塔当前位置），而非 mount 中心 0°：
            // 炮塔转走后仍可在其基础上继续往同方向看 limit 度。
            if (!vertStab) { // 负为上正为下，因此需要反转
                localPitchOffsetDeg = Math.clamp(localPitchOffsetDeg - f,
                        aimLocalPitchOffsetDeg - sa.getMaxPitch(), aimLocalPitchOffsetDeg - sa.getMinPitch());
                pendingLocalPitchOffsetDeg -= f;
            }
            if (!horStab) {
                float yawHalf = sa.getYawLimit() / 2f;
                localYawOffsetDeg = Math.clamp(localYawOffsetDeg - f1,
                        aimLocalYawOffsetDeg - yawHalf, aimLocalYawOffsetDeg + yawHalf);
                pendingLocalYawOffsetDeg -= f1;
            }
            return;
        }

        AbstractControllableSubsystem subsystem = ((IEntityMixin) player).machine_Max$getControllingSubsystem();
        if (subsystem instanceof SeatSubsystem seat) {
            if (!RawInputHandler.freeCam) {
                float minPitch = -seat.attr.staticAttribute.views.minPitch();
                float maxPitch = -seat.attr.staticAttribute.views.maxPitch();
                float yawLimit = seat.attr.staticAttribute.views.yawLimit() / 2;
                float minYaw = 180 - yawLimit;
                float maxYaw = 180 + yawLimit;
                if (ControlPreference.shouldFollowPose(seat) || client.options.getCameraType().isFirstPerson()) {
                    targetViewPitch = Math.clamp(targetViewPitch + f, maxPitch, minPitch);
                    targetViewYaw = Math.clamp(targetViewYaw + f1, minYaw, maxYaw);
                    aimPitch = Math.clamp(aimPitch + f, maxPitch, minPitch);
                    aimYaw = Math.clamp(aimYaw + f1, minYaw, maxYaw);
                } else {
                    targetViewPitch = targetViewPitch + f;
                    targetViewYaw = targetViewYaw + f1;
                    aimPitch = aimPitch + f;
                    aimYaw = aimYaw + f1;
                }
            } else {
                targetViewPitch += f;
                targetViewYaw += f1;
            }
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (client == null) client = Minecraft.getInstance();
        if (client.player == null) return;

        AbstractControllableSubsystem subsystem =
                ((IEntityMixin) client.player).machine_Max$getControllingSubsystem();

        if (subsystem instanceof SeatSubsystem seat) {
            // 验证当前摄像机仍然有效
            if (activeCamera != null) {
                if (!activeCamera.isActive() || activeCamera.isDestroyed()) {
                    exitCameraMode();
                }
            }

            // 炮镜模式
            if (activeCamera != null) {
                tickCameraMode(seat);
                return;
            }

            // 普通座椅视角
            tickSeatMode(seat);
        } else {
            exitCameraMode();
            lastSentAimPoint = null;
        }
    }

    /**
     * 炮镜模式每 tick（20tps）：<p>
     * 强制第一人称视角，发送 aimLocal 增量到服务端驱动炮塔。<br>
     * EMA 更新已移至 updateCameraRotCameraMode（每帧），此处只做视角强制和网络发送。
     */
    private static void tickCameraMode(SeatSubsystem seat) {
        CameraSubsystem camera = activeCamera;
        if (aimPoint == null) return;

        // 炮镜模式下强制第一人称——每 tick 执行一次而非每渲染帧
        if (!client.options.getCameraType().isFirstPerson()) {
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.gameRenderer.checkEntityPostEffect(client.getCameraEntity());
            client.levelRenderer.needsUpdate();
        }

        // 服务端 WeaponController 对无稳轴使用增量累加（targetAngle += rad(offset)）
        // 稳定标志从 camera staticAttr 读取，随网络包发往服务端
        var sa = camera.attr.staticAttribute;

        // 客户端本地派发：驱动 WeaponController 播放特效（炮口焰等）
        seat.setViewInputSignal(aimPoint,
                pendingLocalPitchOffsetDeg, pendingLocalYawOffsetDeg,
                sa.isVerticalStabilized(), sa.isHorizontalStabilized());

        // 发包到服务端驱动实际武器逻辑
        PacketDistributor.sendToServer(new ViewInputPayload(
                seat.getOwner().getSubPart().getId(), seat.getName(),
                aimPoint.x, aimPoint.y, aimPoint.z,
                pendingLocalPitchOffsetDeg, pendingLocalYawOffsetDeg,
                sa.isVerticalStabilized(), sa.isHorizontalStabilized()));

        // 清空待发送旋转量
        pendingLocalPitchOffsetDeg = 0;
        pendingLocalYawOffsetDeg = 0;

        lastSentAimPoint = aimPoint;
    }

    /**
     * 座椅模式每 tick。<br>
     * 同时向服务端发包（驱动实际武器逻辑）和客户端本地派发瞄准点信号（驱动特效播放）。
     */
    private static void tickSeatMode(SeatSubsystem seat) {
        while ((!seat.attr.staticAttribute.views.enableFirstPerson() && client.options.getCameraType() == CameraType.FIRST_PERSON) ||
                (!seat.attr.staticAttribute.views.enableThirdPerson() && (client.options.getCameraType() == CameraType.THIRD_PERSON_BACK
                        || client.options.getCameraType() == CameraType.THIRD_PERSON_FRONT))) {
            boolean wasFirstPerson = client.options.getCameraType().isFirstPerson();
            client.options.setCameraType(client.options.getCameraType().cycle());
            if (wasFirstPerson != client.options.getCameraType().isFirstPerson()) {
                client.gameRenderer.checkEntityPostEffect(
                        client.options.getCameraType().isFirstPerson() ? client.getCameraEntity() : null);
            }
            client.levelRenderer.needsUpdate();
        }
        oldExtraTransform = extraTransform;
        Transform newExtraTransform = seat.getOwner().getSubPart().getLerpedLocatorWorldTransform(seat.attr.locator, 1);
        extraTransform = SparkMathKt.lerp(extraTransform, newExtraTransform, 0.15f);

        Camera mcCamera = client.gameRenderer.getMainCamera();
        Vec3 cameraPos = mcCamera.getPosition();
        Vec3 aimPoint = cameraPos.add(
                aimDirection.x * AIM_MAX_DISTANCE,
                aimDirection.y * AIM_MAX_DISTANCE,
                aimDirection.z * AIM_MAX_DISTANCE
        );

        // 向客户端本地子系统派发瞄准点信号，驱动 WeaponController 等收到信号以播放特效
        seat.setViewInputSignal(aimPoint,
                0, 0,
                true, true);

        if (lastSentAimPoint == null || aimPoint.distanceToSqr(lastSentAimPoint) > AIM_POINT_THRESHOLD_SQ) {
            lastSentAimPoint = aimPoint;
            SubPart ownerSubPart = seat.getOwner().getSubPart();
            PacketDistributor.sendToServer(new ViewInputPayload(
                    ownerSubPart.getId(),
                    seat.getName(),
                    aimPoint.x, aimPoint.y, aimPoint.z,
                    0f, 0f, true, true));
        }
    }

    // ===== 炮镜辅助方法 =====

    /**
     * 从 locator 到世界瞄准点计算局部坐标系下的俯仰角（弧度）。
     * 稳定轴使用——用于补偿 mount 运动，使摄像机保持看向同一世界点。
     */
    private static float computeLocalPitchToPoint(Transform locator, Vec3 worldPoint) {
        Vector3f toTarget = PhysicsHelperKt.toBVector3f(worldPoint).subtract(locator.getTranslation());
        locator.getRotation().mult(new Quaternion().fromAngles(0, (float) Math.PI, 0)).inverse().toRotationMatrix().mult(toTarget, toTarget);
        return (float) Math.asin(Math.clamp(toTarget.normalize().y, -1.0, 1.0));
    }

    /**
     * 从 locator 到世界瞄准点计算局部坐标系下的偏航角（弧度）。
     * 稳定轴使用——用于补偿 mount 运动，使摄像机保持看向同一世界点。
     */
    private static float computeLocalYawToPoint(Transform locator, Vec3 worldPoint) {
        Vector3f toTarget = PhysicsHelperKt.toBVector3f(worldPoint).subtract(locator.getTranslation());
        locator.getRotation().mult(new Quaternion().fromAngles(0, (float) Math.PI, 0)).inverse().toRotationMatrix().mult(toTarget, toTarget);
        return (float) Math.atan2(toTarget.x, toTarget.z);
    }

    /**
     * 按方向切换摄像机
     */
    public static void switchCamera(int direction) {
        if (client == null || client.player == null) return;

        AbstractControllableSubsystem subsystem =
                ((IEntityMixin) client.player).machine_Max$getControllingSubsystem();
        if (!(subsystem instanceof SeatSubsystem seat)) return;

        List<CameraSubsystem> cameras = seat.getDiscoveredCameras()
                .stream()
                .filter(c -> c.isActive() && !c.isDestroyed()
                        && !c.attr.locator.isEmpty()
                        && c.attr.staticAttribute.isAllowCycle())
                .toList();
        if (cameras.isEmpty()) return;

        if (activeCamera == null || direction == 0) {
            activeCamera = cameras.getFirst();
        } else if (direction > 0) {
            int idx = cameras.indexOf(activeCamera);
            if (idx >= cameras.size() - 1) {
                // 超出最后一个炮镜 → 回到座椅原始视角
                exitCameraMode();
                return;
            } else {
                activeCamera = cameras.get(idx + 1);
            }
        } else {
            int idx = cameras.indexOf(activeCamera);
            if (idx <= 0) {
                exitCameraMode();
                return;
            } else {
                activeCamera = cameras.get(idx - 1);
            }
        }

        // 进入炮镜模式：通知子系统当前激活的摄像机，信号由 onTick 统一发送
        seat.setActiveCamera(activeCamera);

        // 初始化瞄准点：从摄像机正前方 100 米处投射
        Transform locator = activeCamera.getLerpedLocatorWorldTransform(1f);
        Vector3f forward = new Vector3f(0, 0, -1);
        locator.getRotation().toRotationMatrix().mult(forward, forward);
        aimPoint = SparkMathKt.toVec3(locator.getTranslation().add(forward.mult(100)));

        resetCameraModeState();
    }

    /**
     * 退出炮镜模式，并将常态视角转向炮镜最后瞄准的方向。
     */
    public static void exitCameraMode() {
        // 在清除 activeCamera 之前，将炮镜瞄准方向同步到常态视角变量
        if (activeCamera != null && activeCamera.isActive()) {
            syncViewAnglesFromCamera();
        }

        // 通知子系统退出炮镜，回到座椅直发模式
        if (client.player != null
                && ((IEntityMixin) client.player).machine_Max$getControllingSubsystem()
                    instanceof AbstractControllableSubsystem controllable) {
            controllable.setActiveCamera(null);
        }
        activeCamera = null;
        aimPoint = null;
        localPitchOffsetDeg = 0f;
        localYawOffsetDeg = 0f;
        aimLocalPitchOffsetDeg = 0f;
        aimLocalYawOffsetDeg = 0f;
        lastAimLocalUpdateNanos = 0;
        targetZoom = 1f;
        currentZoom = 1f;
        lastZoomLerpNanos = 0;
    }

    /**
     * 将炮镜模式缓存的 {@link #aimDirection} 转换为视角角度，同步到常态视角变量，<br>
     * 使退出炮镜后视角无缝转向最后瞄准的方向。<br>
     * aimDirection 由 {@link #updateCameraRotCameraMode} 每渲染帧更新，tick 时直接使用即可。
     */
    private static void syncViewAnglesFromCamera() {
        float pitchDeg = (float) -Math.toDegrees(Math.asin(Math.clamp(aimDirection.y, -1.0, 1.0)));
        float yawDeg = (float) -Math.toDegrees(Math.atan2(aimDirection.x, aimDirection.z));

        // 同步到常态视角变量，使 updateCameraRotSeatMode 从炮镜方向开始
        aimPitch = pitchDeg;
        aimYaw = yawDeg;
        aimRoll = 0f;
        targetViewPitch = pitchDeg;
        targetViewYaw = yawDeg;
        targetViewRoll = 0f;
        pitch = pitchDeg;
        yaw = yawDeg;
        roll = 0f;
    }

    /**
     * 重置炮镜模式状态（切换摄像机时调用）
     */
    private static void resetCameraModeState() {
        localPitchOffsetDeg = 0f;
        localYawOffsetDeg = 0f;
        aimLocalPitchOffsetDeg = 0f;
        aimLocalYawOffsetDeg = 0f;
        lastAimLocalUpdateNanos = 0;
        if (activeCamera != null) {
            var sa = activeCamera.attr.staticAttribute;
            targetZoom = sa.getBaseZoom();
            currentZoom = sa.getBaseZoom();
            lastZoomLerpNanos = 0;
        }
    }

    /**
     * 一键切换缩放
     */
    public static void toggleZoom() {
        if (activeCamera == null) return;
        var sa = activeCamera.attr.staticAttribute;
        if (currentZoom <= sa.getBaseZoom() + 0.2f) {
            targetZoom = sa.getMaxZoom();
        } else {
            targetZoom = sa.getBaseZoom();
        }
    }

    /**
     * 连续变焦
     */
    public static void adjustZoom(float delta) {
        if (activeCamera == null) return;
        var sa = activeCamera.attr.staticAttribute;
        targetZoom = Math.clamp(targetZoom + delta, sa.getBaseZoom(), sa.getMaxZoom());
    }

    @SubscribeEvent
    public static void modifySensitivity(CalculatePlayerTurnEvent event) {
        double raw = event.getMouseSensitivity();
        if (RawInputHandler.freeCam) raw *= 0.5;
        event.setMouseSensitivity(raw);
    }

    /**
     * 根据载具相机距离计算最大滞后距离。大载具允许更大的弹簧阻尼滞后。
     *
     * @param vehicle 当前乘坐的载具
     * @return 最大滞后距离（格），范围 0.5~4.0
     */
    private static float computeVehicleMaxOffset(VehicleCore vehicle) {
        return (float) Mth.clamp(vehicle.cameraDistance * 0.6, 0.5, 4.0);
    }

    /**
     * 应用弹簧阻尼系统产生的旋转偏移（System A 投射物抖屏）。
     * 偏移量已通过 {@link CameraShakeController#onProjectileHit} 中的视角/载具/炮镜因子缩放，
     * 此处仅按当前视角模式做最终倍率调节。
     *
     * @param event 视角事件
     * @param scale 当前视角模式倍率（第一人称 0.4，第三人称 1.0，炮镜 0.3）
     */
    private static void applyShakeRotation(ViewportEvent.ComputeCameraAngles event, float scale) {
        var rotOff = CameraShakeController.getRotationOffset();
        if (rotOff.lengthSqr() > 1e-8) {
            event.setPitch(event.getPitch() + (float) rotOff.x * scale);
            event.setYaw(event.getYaw() + (float) rotOff.y * scale);
            event.setRoll(event.getRoll() + (float) rotOff.z * scale);
        }
    }
}
