package io.github.sweetzonzi.machine_max.client.render.gui.world;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

public interface WorldRenderer2DElement {
    double getScale();
    RenderType getRenderType();
    Vec3 getPosition();
}
