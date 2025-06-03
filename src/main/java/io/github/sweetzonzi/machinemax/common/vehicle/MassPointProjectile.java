package io.github.sweetzonzi.machinemax.common.vehicle;

import com.jme3.math.Vector3f;
import lombok.Getter;
import lombok.Setter;

@Getter
public class MassPointProjectile implements IPhysicsProjectile {
    @Setter
    public Vector3f position;
    @Setter
    public Vector3f velocity;
    public boolean inWorld;
    public boolean launched = false;

}
