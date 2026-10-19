package io.github.sweetzonzi.machine_max.common.mech.physics_test;

import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.render.gui.world.Anchor;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;


@Getter
public final class JoinPosition extends Anchor {
    private final double scale;
    private final Vec3 position;
    private final Vec3 lookAt;
    private final Transform transform;

    public JoinPosition(double scale, Vec3 position, Vec3 lookAt, Transform transform) {
        this.scale = scale;
        this.position = position;
        this.lookAt = lookAt;
        this.transform = transform;
    }

    @Override
    protected ResourceLocation getPictureLocation() {
        return ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "textures/gui/anchor_point.png");
    }
}
