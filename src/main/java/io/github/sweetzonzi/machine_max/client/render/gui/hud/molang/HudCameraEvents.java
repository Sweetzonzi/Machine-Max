package io.github.sweetzonzi.machine_max.client.render.gui.hud.molang;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = "machine_max", value = Dist.CLIENT)
public final class HudCameraEvents {
    private static double worldFov = 70;
    private static double roll;
    private HudCameraEvents() {}
    public static double worldFov() { return worldFov; }
    public static double roll() { return roll; }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void captureRoll(ViewportEvent.ComputeCameraAngles event) {
        roll = event.getRoll();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void captureFov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov()) worldFov = event.getFOV();
    }
}
