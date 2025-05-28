package io.github.sweetzonzi.machinemax.common.vehicle;

import com.jme3.math.Vector3f;

public interface IPhysicsProjectile {

    default boolean isProjectile() {
        return true;
    }

    boolean isLaunched();

    boolean isInWorld();

    Vector3f getPosition();

    void setPosition(Vector3f position);

    Vector3f getVelocity();

    void setVelocity(Vector3f velocity);
}
