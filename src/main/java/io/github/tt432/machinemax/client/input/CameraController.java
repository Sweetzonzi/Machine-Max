package io.github.tt432.machinemax.client.input;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.client.event.ComputeCameraPosEvent;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
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
    private static MouseHandler mouseHandler;
    private static float pitch = 0;
    private static float yaw = 0;
    private static float roll = 0;

    public static void init(FMLClientSetupEvent event) {
        client = Minecraft.getInstance();
        mouseHandler = Minecraft.getInstance().mouseHandler;
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
        if (!RawInputHandler.freeCam) {
            pitch = 0.9f * pitch + 0.1f * camera.getEntity().getViewXRot(partialTick);
            yaw = 0.9f * yaw + 0.1f * camera.getEntity().getViewYRot(partialTick);
        }
    }

    public static void turnCamera(double yRot, double xRot) {
        float f = (float)xRot * 0.15F;
        float f1 = (float)yRot * 0.15F;
        pitch += f;
        yaw += f1;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        //TODO:传输相机控制量
    }

    @SubscribeEvent
    public static void modifySensitivity(CalculatePlayerTurnEvent event) {
        double raw = event.getMouseSensitivity();
        //根据是否处于瞄准等因素调整灵敏度
        event.setMouseSensitivity(raw * 0.1);
    }
}
