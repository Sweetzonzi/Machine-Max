package io.github.tt432.machinemax.client.input;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.client.event.ComputeCameraPosEvent;
import io.github.tt432.machinemax.mixin_interface.IEntityMixin;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = MachineMax.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class CameraController {
    private static Minecraft client;
    private static float pitch = 0;
    private static float yaw = 0;
    private static float roll = 0;

    public static void init(FMLClientSetupEvent event) {
        client = Minecraft.getInstance();
    }

    @SubscribeEvent
    public static void updateCameraPos(ComputeCameraPosEvent event) {
        Camera camera = event.getCamera();
        Vec3 oldPos = camera.getPosition();
        event.setCameraPos(new Vec3(oldPos.x, oldPos.y, oldPos.z));
    }

    @SubscribeEvent
    public static void updateCameraRot(ViewportEvent.ComputeCameraAngles event) {
        Camera camera = event.getCamera();
        event.setPitch(pitch);
        event.setYaw(yaw);
        float partialTick = (float) event.getPartialTick();
        //非自由视角模式下，逐渐回正视角
        if (!RawInputHandler.freeCam) {
            pitch = 0.9f * pitch + 0.1f * camera.getEntity().getViewXRot(partialTick);
            yaw = 0.9f * yaw + 0.1f * camera.getEntity().getViewYRot(partialTick);
        }
    }
    @SubscribeEvent
    public static void updateCameraScale(ViewportEvent.ComputeFov event){
        double scale = 1.0;
        //TODO:视情况调整放大倍率
        double rawFov = event.getFOV();
        event.setFOV(rawFov/scale);
    }

    public static void turnCamera(double yRot, double xRot) {
        //保持与默认旋转视角相同的缩放量（为什么会有缩放？）
        float f = (float)xRot * 0.15F;
        float f1 = (float)yRot * 0.15F;
        pitch += f;
        yaw += f1;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (client.player != null) {
            //TODO:传输相机控制量
//            boolean isPassenger = client.player.isPassenger();
//            Entity vehicle = client.player.getVehicle();
//            IEntityMixin mixin = (IEntityMixin) client.player;
//            MachineMax.LOGGER.debug("isPassenger:{}, vehicle:{}, subSystem:{}", isPassenger, vehicle, mixin.machine_Max$getRidingSubsystem());
        }
    }

    @SubscribeEvent
    public static void modifySensitivity(CalculatePlayerTurnEvent event) {
        double raw = event.getMouseSensitivity();
        //根据是否处于瞄准等因素调整灵敏度
        if (RawInputHandler.freeCam) raw *= 0.5;
        event.setMouseSensitivity(raw);
    }
}
