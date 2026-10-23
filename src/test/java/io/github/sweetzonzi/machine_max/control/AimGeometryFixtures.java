package io.github.sweetzonzi.machine_max.control;

import net.minecraft.world.phys.Vec2;

import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.*;

final class AimGeometryFixtures {
    static AimGeometrySnapshot rig(AimPose root, boolean separatePivots) {
        return new AimGeometrySnapshot(root,
                new Transform(new Vector3f(.2f, 1.3f, -.1f),Quaternion.IDENTITY), new Vector3f(0, -1, 0),
                new Transform(separatePivots ? new Vector3f(.3f, .4f, -.8f) : Vector3f.ZERO,Quaternion.IDENTITY),
                new Vector3f(1, 0, 0), new Transform(new Vector3f(.12f, .18f, -1.6f),Quaternion.IDENTITY),
                AimAxisLimit.CONTINUOUS, new AimAxisLimit(false, Math.toRadians(-80), Math.toRadians(80)));
    }
    static Quaternion rotation(Vector3f axis, double angle) {
        return new Quaternion().fromAngleNormalAxis((float) angle, axis.normalize());
    }
    static Vec2 deg(double pitch, double yaw) {
        return new Vec2((float) Math.toRadians(pitch), (float) Math.toRadians(yaw));
    }
}
