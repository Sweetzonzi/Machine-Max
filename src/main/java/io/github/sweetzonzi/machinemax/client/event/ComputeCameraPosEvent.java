package io.github.sweetzonzi.machinemax.client.event;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;

@Setter
@Getter
public class ComputeCameraPosEvent extends ViewportEvent {
    private Vec3 cameraPos;

    public ComputeCameraPosEvent(Camera camera, float partialTick) {
        super(Minecraft.getInstance().gameRenderer, camera, partialTick);
        cameraPos = camera.getPosition();
    }

}
