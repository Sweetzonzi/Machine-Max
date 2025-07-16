package io.github.sweetzonzi.machinemax.client.input;

import cn.solarmoon.spark_core.physics.SparkMathKt;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.client.event.ComputeCameraPosEvent;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machinemax.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machinemax.util.MMMath;
import jme3utilities.math.MyMath;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Math;
import org.joml.Quaternionf;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = MachineMax.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class CameraController {
    private static Minecraft client = null;
    private static float pitch = 0;
    private static float yaw = 0;
    private static float roll = 0;
    private static Transform extraTransform = new Transform();
    private static Transform oldExtraTransform = new Transform();
    private static float targetViewPitch = 0;
    private static float targetViewYaw = 0;
    private static float targetViewRoll = 0;
    private static float aimPitch = 0;
    private static float aimYaw = 0;
    private static float aimRoll = 0;
    public static Vec3 aimDirection = new Vec3(1, 0, 0);
    private static float speedDistanceFactor = 0.0f;

    public static void init() {
        if (client == null) client = Minecraft.getInstance();
    }

    @SubscribeEvent
    public static void updateCameraPos(ComputeCameraPosEvent event) {
        init();
        Camera camera = event.getCamera();
        float partialTick = (float) event.getPartialTick();
        var type = client.options.getCameraType();
        Entity entity = camera.getEntity();
        if (((IEntityMixin) entity).machine_Max$getRidingSubsystem() instanceof SeatSubsystem seat) {
            if (!type.isFirstPerson() && seat.attr.views.focusOnCenter()) {
                VehicleCore vehicle = seat.getPart().getVehicle();
                event.setCameraPos(vehicle.getPosition().scale(partialTick).add(vehicle.getOldPosition().scale(1 - partialTick)));
            } else {
                Transform transform = seat.getPart().getLerpedLocatorWorldTransform(seat.attr.locator, new Transform().setTranslation(new Vector3f(0, 1.1f, 0)), partialTick);
                event.setCameraPos(SparkMathKt.toVec3(transform.getTranslation()));
            }
        }
    }

    @SubscribeEvent
    public static void updateCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        init();
        Camera camera = event.getCamera();
        Entity entity = camera.getEntity();
        if (((IEntityMixin) entity).machine_Max$getRidingSubsystem() instanceof SeatSubsystem seat) {
            VehicleCore vehicle = seat.getPart().getVehicle();
            //根据速度调整相机距离
            speedDistanceFactor = 0.9f * speedDistanceFactor + 0.1f * (float) MMMath.sigmoid(0.1 * vehicle.getVelocity().length());
            float newDistance = (float) ((seat.attr.views.distanceScale() + 0.25 * speedDistanceFactor) * vehicle.cameraDistance);
            event.setDistance(newDistance);
        }
    }

    @SubscribeEvent
    public static void updateCameraRot(ViewportEvent.ComputeCameraAngles event) {
        init();
        Camera camera = event.getCamera();
        CameraType type = client.options.getCameraType();
        Entity entity = camera.getEntity();
        float partialTick = (float) event.getPartialTick();
        //更新计算相机相对其所处坐标系的旋转
        pitch = 0.6f * pitch + 0.4f * targetViewPitch;
        yaw = 0.6f * yaw + 0.4f * targetViewYaw;
        roll = 0.6f * roll + 0.4f * targetViewRoll;
        try {
            SeatSubsystem seat = ((IEntityMixin) entity).machine_Max$getRidingSubsystem();
            if (seat == null) throw new NullPointerException();
            if (!type.isFirstPerson() && !seat.attr.views.followVehicle()) throw new RuntimeException();
            //基于附体坐标系旋转相机
            Transform extra = SparkMathKt.lerp(oldExtraTransform, extraTransform, partialTick);
            Transform viewTransform = MyMath.combine(new Transform(new Vector3f(), SparkMathKt.toBQuaternion(new Quaternionf().rotateZYX(
                            Math.toRadians(roll),
                            Math.toRadians(-yaw),
                            Math.toRadians(pitch)))),
                    extra, null);
            //计算对应欧拉角
            org.joml.Vector3f rot = new org.joml.Vector3f();
            SparkMathKt.toQuaternionf(viewTransform.getRotation()).getEulerAnglesYXZ(rot);
            //计算相机瞄准方向向量
            aimDirection = new Vec3(Math.cos(rot.x) * Math.sin(rot.y), Math.sin(rot.x), Math.cos(rot.x) * Math.cos(rot.y));
            rot.mul((float) (180 / Math.PI));
            //应用旋转
            event.setPitch(rot.x);
            event.setYaw(-rot.y);
            event.setRoll(rot.z);
        } catch (Exception e) {
            //基于世界坐标系旋转相机
            event.setPitch(pitch);
            event.setYaw(yaw);
            event.setRoll(roll);
            aimDirection = new Vec3(Math.cos(aimPitch) * Math.sin(aimYaw), Math.sin(aimPitch), Math.cos(aimPitch) * Math.cos(aimYaw));
        }
        //非自由视角模式下，逐渐回正视角
        if (!RawInputHandler.freeCam) {
            if (((IEntityMixin) entity).machine_Max$getRidingSubsystem() instanceof SeatSubsystem subsystem) {
                //回到保存记录的位置
            } else {
                //回到实体实时视角
                aimPitch = entity.getViewXRot(partialTick);
                aimYaw = entity.getViewYRot(partialTick);
                aimRoll = 0;
            }
            targetViewPitch = 0.9f * targetViewPitch + 0.1f * aimPitch;
            targetViewYaw = 0.9f * targetViewYaw + 0.1f * aimYaw;
            targetViewRoll = 0.9f * targetViewRoll + 0.1f * aimRoll;
        }
    }

    @SubscribeEvent
    public static void updateCameraScale(ViewportEvent.ComputeFov event) {
        init();
        double scale = 1.0;
        //TODO:视情况调整放大倍率
        double rawFov = event.getFOV();
        event.setFOV(rawFov / scale);
    }

    public static void turnCamera(double yRot, double xRot) {
        init();
        //保持与默认旋转视角相同的缩放量（为什么会有缩放？）
        float f = (float) xRot * 0.15F;
        float f1 = (float) yRot * 0.15F;
        targetViewPitch += f;
        targetViewYaw += f1;
        if (!RawInputHandler.freeCam) {
            aimPitch += f;
            aimYaw += f1;
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        init();
        if (client.player != null) {
            SeatSubsystem seat = ((IEntityMixin) client.player).machine_Max$getRidingSubsystem();
            if (seat != null) {
                //根据座椅设置切换可用视角
                while ((!seat.attr.views.enableFirstPerson() && client.options.getCameraType() == CameraType.FIRST_PERSON) ||
                        (!seat.attr.views.enableThirdPerson() && (client.options.getCameraType() == CameraType.THIRD_PERSON_BACK
                                || client.options.getCameraType() == CameraType.THIRD_PERSON_FRONT))) {
                    client.options.setCameraType(client.options.getCameraType().cycle());
                    client.levelRenderer.needsUpdate();
                }
                //更新附体坐标系的旋转
                oldExtraTransform = extraTransform;
                Transform newExtraTransform = seat.getPart().getLerpedLocatorWorldTransform(seat.attr.locator, 1);
                extraTransform = SparkMathKt.lerp(extraTransform, newExtraTransform, 0.15f);
            }
            //TODO:传输相机控制量
//            boolean isPassenger = client.player.isPassenger();
//            Entity vehicle = client.player.getVehicle();
//            IEntityMixin mixin = (IEntityMixin) client.player;
//            MachineMax.LOGGER.debug("isPassenger:{}, vehicle:{}, subSystem:{}", isPassenger, vehicle, mixin.machine_Max$getRidingSubsystem());
        }
    }

    @SubscribeEvent
    public static void modifySensitivity(CalculatePlayerTurnEvent event) {
        init();
        double raw = event.getMouseSensitivity();
        //根据是否处于瞄准等因素调整灵敏度
        if (RawInputHandler.freeCam) raw *= 0.5;
        event.setMouseSensitivity(raw);
    }

}
