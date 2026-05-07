package io.github.sweetzonzi.machine_max.client.input;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.MMClientConfig;
import io.github.sweetzonzi.machine_max.client.event.ComputeCameraPosEvent;
import io.github.sweetzonzi.machine_max.common.attachment.ControlPreference;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machine_max.util.MMMath;
import jme3utilities.math.MyMath;
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
import org.joml.Quaternionf;

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
    // 目标观察方向，根据鼠标滑动实时更新
    private static float targetViewPitch = 0;
    private static float targetViewYaw = 0;
    private static float targetViewRoll = 0;
    // 目标瞄准方向，非自由视角下根据鼠标滑动实时更新
    private static float aimPitch = 0;
    private static float aimYaw = 0;
    private static float aimRoll = 0;
    // 实际观察方向，每帧向目标瞄准方向逼近
    private static float pitch = 0;
    private static float yaw = 0;
    private static float roll = 0;
    public static Vec3 aimDirection = new Vec3(1, 0, 0);
    private static float speedDistanceFactor = 0.0f;
    /**
     * 角度是否已初始化，避免刚进游戏和刚上车时从0开始插值
     */
    private static boolean anglesInitialized = false;

    @SubscribeEvent
    public static void updateCameraPos(ComputeCameraPosEvent event) {
        if (client == null) client = Minecraft.getInstance();
        Camera camera = event.getCamera();
        float partialTick = (float) event.getPartialTick();
        var type = client.options.getCameraType();
        Entity entity = camera.getEntity();
        if (((IEntityMixin) entity).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
            Quaternionf seatRot = new Quaternionf();
            seat.getSubPart().getWorldPositionMatrix(partialTick).getNormalizedRotation(seatRot);
            if (!type.isFirstPerson() && seat.attr.staticAttribute.views.focusOnCenter()) {
                VehicleCore vehicle = seat.getOwner().getSubPart().getPart().getVehicle();
                event.setCameraPos(vehicle.getPosition().scale(partialTick).add(vehicle.getOldPosition().scale(1 - partialTick))
                        .add(SparkMathKt.toVec3(MMMath.localVectorToWorldVector(
                                PhysicsHelperKt.toBVector3f(seat.attr.staticAttribute.views.thirdPersonOffset()),
                                SparkMathKt.toBQuaternion(seatRot)))));
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
        if (((IEntityMixin) entity).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
            VehicleCore vehicle = seat.getOwner().getSubPart().getPart().getVehicle();
            //根据速度调整相机距离
            speedDistanceFactor = 0.8f * speedDistanceFactor + 0.2f * (float) (2 * MMMath.sigmoid(0.1 * vehicle.getVelocity().length()) - 1);
            float newDistance = (float) ((seat.attr.staticAttribute.views.distanceScale() + 0.4 * speedDistanceFactor) * vehicle.cameraDistance);
            event.setDistance(newDistance);
        }
    }

    /**
     * 临时变量
     */
    private static final Transform tmpViewTransform = Transform.IDENTITY.clone();

    @SubscribeEvent
    public static void updateCameraRot(ViewportEvent.ComputeCameraAngles event) {
        if (client == null) client = Minecraft.getInstance();
        Camera camera = event.getCamera();
        CameraType type = client.options.getCameraType();
        Entity entity = camera.getEntity();
        float partialTick = (float) event.getPartialTick();

        // 初始化角度，避免刚进游戏和刚上车时从0开始插值
        if (!anglesInitialized) {
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

        //更新计算相机相对其所处坐标系的旋转
        float lerp = 0.25f;
        pitch = (1 - lerp) * pitch + lerp * targetViewPitch;
        yaw = (1 - lerp) * yaw + lerp * targetViewYaw;
        roll = (1 - lerp) * roll + lerp * targetViewRoll;
        AbstractControllableSubsystem subsystem = ((IEntityMixin) entity).machine_Max$getControllingSubsystem();
        if (subsystem instanceof SeatSubsystem seat && (type.isFirstPerson() || ControlPreference.shouldFollowPose(seat))) {
            //基于附体坐标系旋转相机
            Transform extra = SparkMathKt.lerp(oldExtraTransform, extraTransform, partialTick);
            //TODO: combine的TempVars.get()会在未找到座椅连接点时IndexOutOfBoundsException，检查逻辑
            MyMath.combine(new Transform(Vector3f.ZERO, SparkMathKt.toBQuaternion(new Quaternionf().rotateZYX(
                            (float) Math.toRadians(roll),
                            (float) Math.toRadians(-yaw),
                            (float) Math.toRadians(pitch)))),
                    extra, tmpViewTransform);
            //计算对应欧拉角
            org.joml.Vector3f rot = new org.joml.Vector3f();
            SparkMathKt.toQuaternionf(tmpViewTransform.getRotation()).getEulerAnglesYXZ(rot);
            //计算相机瞄准方向向量
            aimDirection = new Vec3(Math.cos(rot.x) * Math.sin(rot.y), Math.sin(rot.x), Math.cos(rot.x) * Math.cos(rot.y));
            rot.mul((float) (180 / Math.PI));
            //应用旋转
            event.setPitch(rot.x);
            event.setYaw(-rot.y);
            event.setRoll(rot.z);
        } else {
            //基于世界坐标系旋转相机 TODO: 玩家朝向有bug
            event.setPitch(pitch);
            event.setYaw(yaw);
            event.setRoll(roll);
            aimDirection = new Vec3(Math.cos(aimPitch) * Math.sin(aimYaw), Math.sin(aimPitch), Math.cos(aimPitch) * Math.cos(aimYaw));
        }
        //非自由视角模式下，逐渐回正视角
        if (!RawInputHandler.freeCam) {
            if (subsystem instanceof SeatSubsystem seat) {
                if (!onBoard) {
                    onBoard = true;
                    justLeft = false;
                    aimYaw = 180;
                    targetViewYaw = 180;
                }
                //回到保存记录的位置
                if (seat.getOwner().getSubPart().getEntity() instanceof MMPartEntity partEntity) {
                    entity.setXRot(aimPitch);
                    entity.setYRot(aimYaw + 180 + partEntity.getYRot());
                }
            } else {
                if (onBoard) {
                    onBoard = false;
                    justLeft = true;
                    anglesInitialized = false;
                }
                //回到实体实时视角
                aimPitch = entity.getViewXRot(partialTick);
                aimYaw = entity.getViewYRot(partialTick);
                aimRoll = 0F;
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
     * 避免水平角度于±180°跳变导致第一人称手臂位置跳变，乘坐载具时取消手臂随动旋转
     * 另外座椅不允许使用物品时，直接禁止手持物品的渲染
     */
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
        double scale = 1.0;
        //TODO:视情况调整放大倍率
        double rawFov = event.getFOV();
        event.setFOV(rawFov / scale);
    }

    public static void turnCamera(double yRot, double xRot) {
        //保持与默认旋转视角相同的缩放量（为什么会有缩放？）
        float f = (float) xRot * 0.15F;
        float f1 = (float) yRot * 0.15F;
        LocalPlayer player = client.player;
        if (player == null) return;
        AbstractControllableSubsystem subsystem = ((IEntityMixin) player).machine_Max$getControllingSubsystem();
        if (subsystem instanceof SeatSubsystem seat) {
            if (!RawInputHandler.freeCam) {
                // 俯仰角限制：零位（水平方向）对应0度，-90为仰头至最高，90为俯视至最低，因此需要调整正负号
                float minPitch = -seat.attr.staticAttribute.views.minPitch();
                float maxPitch = -seat.attr.staticAttribute.views.maxPitch();
                // 偏航角限制：由于底层坐标系限制，零位（正前方）对应180度
                // 因此yaw限制范围为 [180 - yawLimit/2, 180 + yawLimit/2]
                float yawLimit = seat.attr.staticAttribute.views.yawLimit() / 2;
                float minYaw = 180 - yawLimit;
                float maxYaw = 180 + yawLimit;
                if (ControlPreference.shouldFollowPose(seat) || client.options.getCameraType().isFirstPerson()) {
                    targetViewPitch = Math.clamp(targetViewPitch + f, maxPitch, minPitch);
                    targetViewYaw = Math.clamp(targetViewYaw + f1, minYaw, maxYaw);
                    aimPitch = Math.clamp(aimPitch + f, maxPitch, minPitch);
                    aimYaw = Math.clamp(aimYaw + f1, minYaw, maxYaw);
                } else {
                    float yaw = seat.getSubPart().getYaw();
                    float pitch = seat.getSubPart().getPitch();
                    //TODO: 根据当前yaw和pitch钳制范围
                    targetViewPitch = targetViewPitch + f;
                    targetViewYaw = targetViewYaw + f1;
                    aimPitch = aimPitch + f;
                    aimYaw = aimYaw + f1;
                }
            } else {
                targetViewPitch += f;
                targetViewYaw += f1;
            }
        } else {
            targetViewPitch += f;
            targetViewYaw += f1;
            if (!RawInputHandler.freeCam) {
                aimPitch += f;
                aimYaw += f1;
            }
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (client == null) client = Minecraft.getInstance();
        if (client.player == null) return;
        AbstractControllableSubsystem subsystem = ((IEntityMixin) client.player).machine_Max$getControllingSubsystem();
        if (subsystem instanceof SeatSubsystem seat) {
            //根据座椅设置切换可用视角
            while ((!seat.attr.staticAttribute.views.enableFirstPerson() && client.options.getCameraType() == CameraType.FIRST_PERSON) ||
                    (!seat.attr.staticAttribute.views.enableThirdPerson() && (client.options.getCameraType() == CameraType.THIRD_PERSON_BACK
                            || client.options.getCameraType() == CameraType.THIRD_PERSON_FRONT))) {
                client.options.setCameraType(client.options.getCameraType().cycle());
                client.levelRenderer.needsUpdate();
            }
            //更新附体坐标系的旋转
            oldExtraTransform = extraTransform;
            Transform newExtraTransform = seat.getOwner().getSubPart().getLerpedLocatorWorldTransform(seat.attr.locator, 1);
            extraTransform = SparkMathKt.lerp(extraTransform, newExtraTransform, 0.15f);
        }
        //TODO:传输相机控制量
//            boolean isPassenger = client.player.isPassenger();
//            Entity vehicle = client.player.getVehicle();
//            IEntityMixin mixin = (IEntityMixin) client.player;
//            MachineMax.LOGGER.debug("isPassenger:{}, vehicle:{}, subSystem:{}", isPassenger, vehicle, mixin.machine_Max$getRidingSubsystem());
    }

    @SubscribeEvent
    public static void modifySensitivity(CalculatePlayerTurnEvent event) {
        double raw = event.getMouseSensitivity();
        //根据是否处于瞄准等因素调整灵敏度
        if (RawInputHandler.freeCam) raw *= 0.5;
        event.setMouseSensitivity(raw);
    }

}
