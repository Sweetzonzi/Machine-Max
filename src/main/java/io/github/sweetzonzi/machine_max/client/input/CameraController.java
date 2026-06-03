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
import net.minecraft.core.particles.ParticleTypes;
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
    /** 世界空间瞄准点。稳定轴由鼠标在世界空间平移此点；非稳定轴的漂移由 localPitch/localYaw 体现 */
    private static Vec3 aimPoint = null;
    /** 局部俯仰角偏移（弧度），相对于摄像机基座。无垂稳时鼠标修改此值；有垂稳时从 aimPoint 反算 */
    private static float localPitch = 0f;
    /** 局部偏航角偏移（弧度），相对于摄像机基座。无水平稳时鼠标修改此值；有水平稳时从 aimPoint 反算 */
    private static float localYaw = 0f;
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
        if (activeCamera != null && activeCamera.isActive()) { // 炮镜模式下，相机距离设为0以完全匹配locator位置
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
            if (type.isFirstPerson() || type.isMirrored()) // 强制后向第三人称，避免手臂渲染
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
     * 炮镜模式下的相机旋转计算（每渲染帧），按稳定模式组合：
     * - 稳定轴：从 aimPoint 反算 localAngle（补偿 mount 运动）
     * - 非稳定轴：localAngle 保持 turnCamera 设置的值（随 mount 漂移）
     * - 若存在非稳定轴，从最终方向重算 aimPoint（该轴的 aimPoint 会随 mount 漂移）
     */
    private static void updateCameraRotCameraMode(ViewportEvent.ComputeCameraAngles event, float partialTick) {
        CameraSubsystem camera = activeCamera;
        Transform locator = camera.getLerpedLocatorWorldTransform(partialTick);
        var sa = camera.attr.staticAttribute;
        boolean vs = sa.isVerticalStabilized();
        boolean hs = sa.isHorizontalStabilized();

        // 若尚无瞄准点，从摄像机正前方初始化
        if (aimPoint == null) {
            Vector3f forward = new Vector3f(0, 0, -1);
            locator.getRotation().toRotationMatrix().mult(forward, forward);
            aimPoint = SparkMathKt.toVec3(locator.getTranslation().add(forward.mult(100)));
            localPitch = 0f;
            localYaw = 0f;
        }

        // 记录瞄准距离（用于非稳定轴重算 aimPoint）
        Vector3f locatorPos = locator.getTranslation();
        Vector3f toAim = PhysicsHelperKt.toBVector3f(aimPoint).subtract(locatorPos);
        float aimDist = toAim.length();
        if (aimDist < 1f) aimDist = 100f;

        // 稳定轴：从 aimPoint 反算 localAngle（补偿 mount 运动，咬住世界点）
        if (vs) {
            localPitch = computePitchToPoint(locator, aimPoint);
        }
        if (hs) {
            localYaw = computeYawToPoint(locator, aimPoint);
        }
        // 非稳定轴：localPitch/localYaw 不动（由 turnCamera 设置，不补偿 = 随 mount 漂）

        // clamp 到摄像机限制
        float clampedPitch = Math.clamp(localPitch,
                (float) Math.toRadians(sa.getMinPitch()),
                (float) Math.toRadians(sa.getMaxPitch()));
        float clampedYaw = Math.clamp(localYaw,
                -(float) Math.toRadians(sa.getYawLimit() / 2),
                (float) Math.toRadians(sa.getYawLimit() / 2));

        // 从 locator + 局部角度构建世界方向
        Matrix3f aimMat = new Quaternion().fromAngles(clampedPitch, clampedYaw, 0).toRotationMatrix();
        Vector3f dir = new Vector3f(0, 0, -1);
        aimMat.mult(dir, dir);
        locator.getRotation().toRotationMatrix().mult(dir, dir);

        // 若有非稳定轴，aimPoint 随之漂移；若全稳则 aimPoint 保持不动
        if (!vs || !hs) {
            aimPoint = SparkMathKt.toVec3(locatorPos.add(dir.normalize().mult(aimDist)));
        }

        Vec3 aimDir = SparkMathKt.toVec3(dir.normalize());
        double pitchDeg = -Math.toDegrees(Math.asin(Math.clamp(aimDir.y, -1.0, 1.0)));
        double yawDeg = -Math.toDegrees(Math.atan2(aimDir.x, aimDir.z));

        event.setPitch((float) pitchDeg);
        event.setYaw((float) yawDeg);
        // 从定位器世界旋转中提取roll角度，使车体倾斜时视角随之倾斜
        Quaternionf locatorJoml = SparkMathKt.toQuaternionf(locator.getRotation());
        org.joml.Vector3f euler = new org.joml.Vector3f();
        locatorJoml.getEulerAnglesYXZ(euler);
        event.setRoll((float) Math.toDegrees(-euler.z));
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

    public static void turnCamera(double yRot, double xRot) {
        float f = (float) xRot * 0.15F; // 俯仰，鼠标垂直移动，xRot>0表示鼠标下移
        float f1 = (float) yRot * 0.15F; // 偏航，鼠标水平移动，yRot>0表示鼠标右移
        LocalPlayer player = client.player;
        if (player == null) return;

        // 炮镜模式：根据稳定模式分流鼠标输入
        if (activeCamera != null && activeCamera.isActive()) {
            if (aimPoint == null) return;
            Transform locator = activeCamera.getLerpedLocatorWorldTransform(1f);
            var sa = activeCamera.attr.staticAttribute;
            boolean vs = sa.isVerticalStabilized();
            boolean hs = sa.isHorizontalStabilized();

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

            if (hs) {
                // 水平稳：在世界空间沿 localRight 平移 aimPoint
                float moveX = (float) Math.tan(yawAngle) * dist;
                aimPoint = aimPoint.add(
                        localRight.x * moveX, localRight.y * moveX, localRight.z * moveX);
            } else {
                // 无水平稳：直接累积局部偏航角
                localYaw += yawAngle;
            }

            if (vs) {
                // 垂稳：在世界空间沿 -localUp 平移 aimPoint
                float moveY = (float) Math.tan(pitchAngle) * dist;
                aimPoint = aimPoint.add(
                        -localUp.x * moveY, -localUp.y * moveY, -localUp.z * moveY);
            } else {
                // 无垂稳：直接累积局部俯仰角
                localPitch += pitchAngle;
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
                activeCamera.hasViewer = true;
                tickCameraMode(seat);
                return;
            }

            // 普通座椅视角（现有逻辑）
            tickSeatMode(seat);
        } else {
            exitCameraMode();
            lastSentAimPoint = null;
        }
    }

    /** 炮镜模式每 tick（20tps）：发送世界空间瞄准点到服务端 */
    private static void tickCameraMode(SeatSubsystem seat) {
        CameraSubsystem camera = activeCamera;
        if (aimPoint == null) return;
        SubPart subPart = camera.getOwner().getSubPart();
        if (lastSentAimPoint == null
                || aimPoint.distanceToSqr(lastSentAimPoint) > AIM_POINT_THRESHOLD_SQ) {
            lastSentAimPoint = aimPoint;
            PacketDistributor.sendToServer(new ViewInputPayload(
                    subPart.getId(), camera.getName(),
                    aimPoint.x, aimPoint.y, aimPoint.z));
        }
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
                    aimPoint.x, aimPoint.y, aimPoint.z
            ));
        }
    }

    /**
     * 计算从 locator 到目标世界点的局部俯仰角（弧度）。
     * 用于稳定轴从 aimPoint 反算角度以补偿 mount 运动。
     */
    private static float computePitchToPoint(Transform locator, Vec3 aimPoint) {
        Vector3f locatorPos = locator.getTranslation();
        Vector3f toTarget = PhysicsHelperKt.toBVector3f(aimPoint).subtract(locatorPos);
        Vector3f localDir = new Vector3f();
        locator.getRotation().inverse().toRotationMatrix().mult(toTarget, localDir);
        float dist = (float) Math.sqrt(
                localDir.x * localDir.x + localDir.y * localDir.y + localDir.z * localDir.z);
        if (dist < 0.001f) return 0f;
        return (float) Math.asin(Math.clamp(localDir.y / dist, -1.0, 1.0));
    }

    /**
     * 计算从 locator 到目标世界点的局部偏航角（弧度）。
     * 用于稳定轴从 aimPoint 反算角度以补偿 mount 运动。
     */
    private static float computeYawToPoint(Transform locator, Vec3 aimPoint) {
        Vector3f locatorPos = locator.getTranslation();
        Vector3f toTarget = PhysicsHelperKt.toBVector3f(aimPoint).subtract(locatorPos);
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

        CameraSubsystem oldCamera = activeCamera;

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

        // 清除旧摄像机的观众标记
        if (oldCamera != null && oldCamera != activeCamera) {
            oldCamera.hasViewer = false;
        }

        // 初始化瞄准点：从摄像机正前方 100 米处投射，并重置局部角度
        activeCamera.hasViewer = true;
        Transform locator = activeCamera.getLerpedLocatorWorldTransform(1f);
        Vector3f forward = new Vector3f(0, 0, -1);
        locator.getRotation().toRotationMatrix().mult(forward, forward);
        aimPoint = SparkMathKt.toVec3(locator.getTranslation().add(forward.mult(100)));
        localPitch = 0f;
        localYaw = 0f;

        resetCameraModeState();
    }

    /** 退出炮镜模式 */
    public static void exitCameraMode() {
        if (activeCamera != null) {
            activeCamera.hasViewer = false;
        }
        activeCamera = null;
        aimPoint = null;
        localPitch = 0f;
        localYaw = 0f;
        currentZoom = 1f;
        zoomBlend = 0f;
    }

    /** 重置炮镜模式状态（切换摄像机时调用） */
    private static void resetCameraModeState() {
        localPitch = 0f;
        localYaw = 0f;
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
