package io.github.sweetzonzi.machine_max.common.mech.physics_test;

import com.jme3.math.Vector3f;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PhysicsRigidBodyDataHolder {
    Vector3f linearVelocity;
    Vector3f angularVelocity;
    Vector3f linearFactor;
    Vector3f angularFactor;
}
