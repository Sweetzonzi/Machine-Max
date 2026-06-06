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
import io.github.sweetzonzi.machine_max.common.attachment.ControlPreference;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.CameraSubsystem;
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
import org.joml.Quaternionf;

import java.util.List;

@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
public class CameraController {
    private static Minecraft client;
    /** 玩家乘坐载具的刚体变换，用于基于部件坐标系额外旋转视角 */
    private static Transform extraTransform = new Transform();
    /** 玩家乘坐载具的上一tick刚体变换，用于插值 */
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
    /** 当前激活的摄像机（null=普通座椅视角） */
    @Getter
    private static CameraSubsystem activeCamera = null;
    /** 世界空间瞄准点（唯一真相源，稳定轴使用）。鼠标不动则此点不动 */
    private static Vec3 aimPoint = null;
    /** 本 tick 无稳俯仰轴鼠标累积偏移（度），tickCameraMode 发包后清零 */
    private static float localPitchOffsetDeg = 0f;
    /** 本 tick 无稳偏航轴鼠标累积偏移（度），tickCameraMode 发包后清零 */
    private static float localYawOffsetDeg = 0f;
    /** 无稳轴累积偏移上限（度），避免一次快速鼠标滑动造成过大偏转 */
    private static final float LOCAL_OFFSET_LIMIT_DEG = 90f;
    /** 当前变焦倍率，在 [baseZoom, maxZoom] 之间 */
    private static float currentZoom = 1f;
    /** 连续变焦混合值（0=baseZoom, 1=maxZoom） */
    private static float zoomBlend = 0f;

    public static boolean isCameraMode() {
        return activeCamera != null;
    }

    @SubscribeEvent
    public static void updateCameraPos(ComputeCameraPosEvent event) {
        if (client == null) client = Minecraft.getInstance();
        Camera camera = event.getCamera();
        float partialTick = (float) event.getPartialTick();
        CameraType type = client.options.getCameraType();
        Entity entity = camera.getEntity();

        if (activeCamera != null && activeCamera.isActive()) {
            Transform locator = activeCamera.getLerpedLocatorWorldTransform(partialTick);
            event.setCameraPos(SparkMathKt.toVec3(locator.getTranslation()));
            return;
        }

        if (((IEntityMixin) entity).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
            Quaternionf seatRot = new Quaternionf();
            seat.getSubPart().getWorldPositionMatrix(partialTick).getNormalizedRotation(seatRot);
            if (!type.isFirstPerson() && seat.attr.staticAttribute.views.focusOnCenter()) {
                if (seat.getOwner().getSubPart().getPart().getAssembly() instanceof VehicleCore vehicle) {
                    event.setCameraPos(vehicle.getPosition().scale(partialTick).add(vehicle.getOldPosition().scale(1 - partialTick))
                            .add(SparkMathKt.toVec3(MMMath.localVectorToWorldVector(
                                    PhysicsHelperKt.toBVector3f(seat.attr.staticAttribute.views.thirdPersonOffset()),
                                    SparkMathKt.toBQuaternion(seatRot)))));
                }
            } else {
                Transform transform = seat.getOwner().getSubPart().getLerpedLocatorWorldTransform(seat.attr.locator, new Transform().setTranslation(new Vector3f(0, 1.1f, 0)), partialTick);
                event.setCameraPos(SparkMathKt.toVec3(transform.getTranslation())
                        .add(SparkMathKt.toVec3(MMMath.localVectorToWorldVector(
                                PhysicsHelperKt.toBVector3f(seat.attr.staticAttribute.views.firstPersonOffset()),
                                SparkMathKt.toBQuaternion(seatRot)))));
            }
        }
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
        Camera camera = event.getCamera();
        CameraType type = client.options.getCameraType();
        Entity entity = camera.getEntity();
        float partialTick = (float) event.getPartialTick();

        if (!anglesInitialized) {
            initializeAngles(entity, partialTick);
        }

        // 炮镜模式
        if (activeCamera != null && activeCamera.isActive()) {
            if (type.isFirstPerson() || type.isMirrored())
                client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            updateCameraRotCameraMode(event, partialTick);
            return;
        }

        AbstractControllableSubsystem subsystem = ((IEntityMixin) entity).machine_Max$getControllingSubsystem();
        if (subsystem instanceof SeatSubsystem seat) {
            updateCameraRotSeatMode(event, seat, type, partialTick);
        } else {
            updateCameraRotDefault(event, entity, partialTick);
        }
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
     * 分轴处理：稳定轴从 locator→aimPoint 反算局部角（补偿车体晃动），无稳轴固定在 0°（摄像机无内部偏转，跟 mount 走）。
     */
    private static void updateCameraRotCameraMode(ViewportEvent.ComputeCameraAngles event, float partialTick) {
        CameraSubsystem camera = activeCamera;
        Transform locator = camera.getLerpedLocatorWorldTransform(partialTick);
        var sa = camera.attr.staticAttribute;

        // 若尚无瞄准点，从摄像机正前方 100 米处初始化一个
        if (aimPoint == null) {
            Vector3f forward = new Vector3f(0, 0, -1);
            locator.getRotation().toRotationMatrix().mult(forward, forward);
            aimPoint = SparkMathKt.toVec3(locator.getTranslation().add(forward.mult(100)));
        }

        // 分轴计算摄像机内部偏转角：
        //   稳定轴 → 从 locator→aimPoint 反算局部角（补偿 mount 运动）
        //   无稳轴 → 0（摄像机固定于 mount，无内部陀螺平台）
        float localPitch = sa.isVerticalStabilized() ?
                computeLocalPitchToPoint(locator, aimPoint) : 0f;
        float localYaw = sa.isHorizontalStabilized() ?
                computeLocalYawToPoint(locator, aimPoint) : 0f;

        // 从 localPitch/localYaw + locator 构建世界方向
        Matrix3f aimMat = new Quaternion().fromAngles(localPitch, localYaw, 0).toRotationMatrix();
        Vector3f dir = new Vector3f(0, 0, -1);
        aimMat.mult(dir, dir);
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

        // 每帧用当前渲染方向重算 aimPoint（保持距离），供服务端开火容差判断。
        // 稳定轴：dir 指向 aimPoint → aimPoint 不变；无稳轴：dir 随 mount 变化 → aimPoint 随之更新。
        float dist = locator.getTranslation().distance(PhysicsHelperKt.toBVector3f(aimPoint));
        if (dist < 1f) dist = 100f;
        aimPoint = SparkMathKt.toVec3(locator.getTranslation().clone().addLocal(dir.clone().multLocal(dist)));

        aimDirection = aimDir;
    }

    /** 座椅模式下的相机旋转计算（保留现有逻辑） */
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

    /** 默认（非载具）相机旋转 */
    private static void updateCameraRotDefault(ViewportEvent.ComputeCameraAngles event, Entity entity, float partialTick) {
        if (onBoard) {
            onBoard = false;
            justLeft = true;
            anglesInitialized = false;
        }
        event.setPitch(entity.getViewXRot(partialTick));
        event.setYaw(entity.getViewYRot(partialTick));
        event.setRoll(0F);
        aimDirection = new Vec3(Math.cos(aimPitch) * Math.sin(aimYaw), Math.sin(aimPitch), Math.cos(aimPitch) * Math.cos(aimYaw));
    }

    /** 避免手臂位置跳变，取消手臂随动旋转 */
    @SubscribeEvent
    public static void onRenderArm(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player instanceof IEntityMixin passenger && passenger.machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
            if (!seat.attr.staticAttribute.allowUseItems) event.setCanceled(true);
            PoseStack poseStack = event.getPoseStack();
            float partialTicks = event.getPartialTick();
            float f3 = Mth.lerp(partialTicks, player.yBobO, player.yBob);
            poseStack.mulPose(Axis.YP.rotationDegrees((player.getViewYRot(partialTicks) - f3) * -0.1F));
        }
    }

    @SubscribeEvent
    public static void updateCameraScale(ViewportEvent.ComputeFov event) {
        if (activeCamera != null && activeCamera.isActive()) {
            var sa = activeCamera.attr.staticAttribute;
            double fov = sa.getBaseFov() / currentZoom;
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
     *           无稳轴 → 累积鼠标增量到 localPitch/YawOffsetDeg（每 tick 清零发包）。
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
                if (horStab)  { dx += localRight.x * moveX; dy += localRight.y * moveX; dz += localRight.z * moveX; }
                if (vertStab) { dx -= localUp.x * moveY;     dy -= localUp.y * moveY;     dz -= localUp.z * moveY; }
                aimPoint = aimPoint.add(dx, dy, dz);
            }

            // 无稳轴：累积鼠标增量
            if (!vertStab) localPitchOffsetDeg = Math.clamp(localPitchOffsetDeg - f, -LOCAL_OFFSET_LIMIT_DEG, LOCAL_OFFSET_LIMIT_DEG);
            if (!horStab)  localYawOffsetDeg   = Math.clamp(localYawOffsetDeg   - f1, -LOCAL_OFFSET_LIMIT_DEG, LOCAL_OFFSET_LIMIT_DEG);
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
            if (activeCamera != null) {
                if (!activeCamera.isActive() || activeCamera.isDestroyed()) {
                    exitCameraMode();
                }
            }

            if (activeCamera != null) {
                tickCameraMode(seat);
                return;
            }

            tickSeatMode(seat);
        } else {
            exitCameraMode();
            lastSentAimPoint = null;
        }
    }

    /** 炮镜模式每 tick（20tps）：发送 aimPoint + 无稳轴偏移到服务端，清零本 tick 偏移 */
    private static void tickCameraMode(SeatSubsystem seat) {
        CameraSubsystem camera = activeCamera;
        if (aimPoint == null) return;
        SubPart subPart = camera.getOwner().getSubPart();

        PacketDistributor.sendToServer(new ViewInputPayload(
                subPart.getId(), camera.getName(),
                aimPoint.x, aimPoint.y, aimPoint.z,
                localPitchOffsetDeg, localYawOffsetDeg));

        lastSentAimPoint = aimPoint;

        // 清零本 tick 偏移，供下一帧累积
        localPitchOffsetDeg = 0f;
        localYawOffsetDeg = 0f;
    }

    /** 座椅模式每 tick（现有逻辑） */
    private static void tickSeatMode(SeatSubsystem seat) {
        while ((!seat.attr.staticAttribute.views.enableFirstPerson() && client.options.getCameraType() == CameraType.FIRST_PERSON) ||
                (!seat.attr.staticAttribute.views.enableThirdPerson() && (client.options.getCameraType() == CameraType.THIRD_PERSON_BACK
                        || client.options.getCameraType() == CameraType.THIRD_PERSON_FRONT))) {
            client.options.setCameraType(client.options.getCameraType().cycle());
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
        if (lastSentAimPoint == null || aimPoint.distanceToSqr(lastSentAimPoint) > AIM_POINT_THRESHOLD_SQ) {
            lastSentAimPoint = aimPoint;
            SubPart ownerSubPart = seat.getOwner().getSubPart();
            PacketDistributor.sendToServer(new ViewInputPayload(
                    ownerSubPart.getId(),
                    seat.getName(),
                    aimPoint.x, aimPoint.y, aimPoint.z,
                    0f, 0f));
        }
    }

    // ===== 炮镜辅助方法 =====

    /**
     * 从 locator 到世界瞄准点计算局部坐标系下的俯仰角（弧度）。
     * 稳定轴使用——用于补偿 mount 运动，使摄像机保持看向同一世界点。
     */
    private static float computeLocalPitchToPoint(Transform locator, Vec3 worldPoint) {
        Vector3f toTarget = PhysicsHelperKt.toBVector3f(worldPoint).subtract(locator.getTranslation());
        Vector3f localDir = new Vector3f();
        locator.getRotation().inverse().toRotationMatrix().mult(toTarget, localDir);
        float dist = (float) Math.sqrt(localDir.x * localDir.x + localDir.y * localDir.y + localDir.z * localDir.z);
        if (dist < 0.001f) return 0f;
        return (float) Math.asin(Math.clamp(localDir.y / dist, -1.0, 1.0));
    }

    /**
     * 从 locator 到世界瞄准点计算局部坐标系下的偏航角（弧度）。
     * 稳定轴使用——用于补偿 mount 运动，使摄像机保持看向同一世界点。
     */
    private static float computeLocalYawToPoint(Transform locator, Vec3 worldPoint) {
        Vector3f toTarget = PhysicsHelperKt.toBVector3f(worldPoint).subtract(locator.getTranslation());
        Vector3f localDir = new Vector3f();
        locator.getRotation().inverse().toRotationMatrix().mult(toTarget, localDir);
        return (float) Math.atan2(localDir.x, localDir.z);
    }

    /** 按方向切换摄像机 */
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
            activeCamera = cameras.get((idx + 1) % cameras.size());
        } else {
            int idx = cameras.indexOf(activeCamera);
            if (idx <= 0) {
                exitCameraMode();
                return;
            } else {
                activeCamera = cameras.get(idx - 1);
            }
        }

        // 初始化瞄准点：从摄像机正前方 100 米处投射
        Transform locator = activeCamera.getLerpedLocatorWorldTransform(1f);
        Vector3f forward = new Vector3f(0, 0, -1);
        locator.getRotation().toRotationMatrix().mult(forward, forward);
        aimPoint = SparkMathKt.toVec3(locator.getTranslation().add(forward.mult(100)));

        resetCameraModeState();
    }

    /** 退出炮镜模式 */
    public static void exitCameraMode() {
        activeCamera = null;
        aimPoint = null;
        localPitchOffsetDeg = 0f;
        localYawOffsetDeg = 0f;
        currentZoom = 1f;
        zoomBlend = 0f;
    }

    /** 重置炮镜模式状态（切换摄像机时调用） */
    private static void resetCameraModeState() {
        localPitchOffsetDeg = 0f;
        localYawOffsetDeg = 0f;
        if (activeCamera != null) {
            var sa = activeCamera.attr.staticAttribute;
            currentZoom = sa.getBaseZoom();
            zoomBlend = 0f;
        }
    }

    /** 一键切换缩放 */
    public static void toggleZoom() {
        if (activeCamera == null) return;
        var sa = activeCamera.attr.staticAttribute;
        if (currentZoom <= sa.getBaseZoom() + 0.01f) {
            currentZoom = sa.getMaxZoom();
        } else {
            currentZoom = sa.getBaseZoom();
        }
    }

    /** 连续变焦 */
    public static void adjustZoom(float delta) {
        if (activeCamera == null) return;
        var sa = activeCamera.attr.staticAttribute;
        currentZoom = Math.clamp(currentZoom + delta, sa.getBaseZoom(), sa.getMaxZoom());
        zoomBlend = (currentZoom - sa.getBaseZoom()) / (sa.getMaxZoom() - sa.getBaseZoom());
    }

    @SubscribeEvent
    public static void modifySensitivity(CalculatePlayerTurnEvent event) {
        double raw = event.getMouseSensitivity();
        if (RawInputHandler.freeCam) raw *= 0.5;
        event.setMouseSensitivity(raw);
    }
}
