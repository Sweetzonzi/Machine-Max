package io.github.sweetzonzi.machine_max.track;

import com.jme3.bullet.CollisionConfiguration;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.RotationOrder;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.joints.motors.MotorParam;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Matrix3f;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.probe.NativeLibraryLoader;
import io.github.sweetzonzi.machine_max.util.control.RotationalMotorControl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 用真实 Bullet 验证限扭矩牵引、轴向投影与停车保持。 */
class RotationalDrivePhysicsTest {
    @BeforeAll
    static void loadNative() {
        NativeLibraryLoader.load();
    }

    @Test
    void followingADistantInputSpeedUsesTheAvailableTorqueAcrossMassOrientationAndStepSize() {
        for (float mass : new float[]{1f, 10f, 80f}) {
            for (float step : new float[]{.005f, .01f, .02f}) {
                for (float direction : new float[]{-1f, 1f}) {
                    for (float yaw : new float[]{0f, 1.1f}) {
                        try (Rig rig = new Rig(mass, yaw, true)) {
                            float torque = direction * 30f;
                            float speed = direction * 1000f;
                            for (int tick = 0; tick < 12; tick++) {
                                float before = rig.speed();
                                Vector3f axis = rig.joint.getAxis(0, null);
                                float inverse = RotationalMotorControl.projectedInverseInertia(rig.a.getInverseInertiaWorld(null), axis)
                                        + RotationalMotorControl.projectedInverseInertia(rig.b.getInverseInertiaWorld(null), axis);
                                float target = RotationalMotorControl.driveTarget(speed, Math.abs(torque), inverse, step);
                                rig.motor(target, Math.abs(torque));
                                rig.space.update(step, 0);
                                float impulse = (rig.speed() - before) / inverse;
                                assertEquals(torque * step, impulse, Math.abs(torque * step) * .002f,
                                        "远离输入轴速时应使用可用扭矩追随目标");
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void overspeedConvergesToTheBiasedInputTarget() {
        for (float direction : new float[]{-1f, 1f}) {
            try (Rig rig = new Rig(10f, .7f, false)) {
                rig.b.setAngularVelocity(rig.joint.getAxis(0, null).mult(direction * 10f));
                Vector3f axis = rig.joint.getAxis(0, null);
                float inverse = RotationalMotorControl.projectedInverseInertia(rig.b.getInverseInertiaWorld(null), axis);
                float target = RotationalMotorControl.driveTarget(direction * 5f, 30f, inverse, .01f);
                assertTrue(target * direction > 5f && target * direction < 10f);
                for (int tick = 0; tick < 50; tick++) {
                    rig.motor(target, 30f);
                    rig.space.update(.01f, 0);
                    assertTrue(rig.speed() * direction > 0f, "追随轴速目标不得主动反转");
                }
                assertEquals(target, rig.speed(), 1e-4f);
            }
        }
    }

    @Test
    void inputSpeedShiftsTheTargetWithoutChangingTheTractionMargin() {
        for (float direction : new float[]{-1f, 1f}) {
            float target = RotationalMotorControl.driveTarget(direction * 4f, 30f, 2f, .01f);
            float shifted = RotationalMotorControl.driveTarget(direction * 5f, 30f, 2f, .01f);
            assertTrue(target * direction > 4f);
            assertEquals(direction, shifted - target, 1e-5f);
        }
    }

    @Test
    void brakingStopsBothDirectionsWithoutStartingReverseMotion() {
        for (float direction : new float[]{-1f, 1f}) {
            try (Rig rig = new Rig(10f, .7f, false)) {
                Vector3f axis = rig.joint.getAxis(0, null);
                rig.b.setAngularVelocity(axis.mult(direction * .05f));
                rig.motor(0f, 30f);
                for (int tick = 0; tick < 50; tick++) {
                    rig.space.update(.01f, 0);
                    assertTrue(rig.speed() * direction >= -1e-5f, "制动不得主动跨零反转");
                }
                assertEquals(0f, rig.speed(), 1e-5f);
            }
        }
    }

    @Test
    void zeroTorqueAndLockedAxisCannotCreateTractionMargin() {
        assertEquals(4f, RotationalMotorControl.driveTarget(4f, 0f, 2f, .01f));
        assertEquals(-4f, RotationalMotorControl.driveTarget(-4f, 100f, 0f, .01f));
        assertEquals(0f, RotationalMotorControl.driveTarget(0f, 100f, 2f, .01f));
        assertTrue(Float.isFinite(RotationalMotorControl.driveTarget(Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, .01f)));
    }

    private static final class Rig implements AutoCloseable {
        final PhysicsSpace space = new PhysicsSpace(new Vector3f(-100, -100, -100),
                new Vector3f(100, 100, 100), PhysicsSpace.BroadphaseType.DBVT, 1,
                new CollisionConfiguration(256, 1));
        final PhysicsRigidBody a;
        final PhysicsRigidBody b;
        final New6Dof joint;

        Rig(float mass, float yaw, boolean dynamicA) {
            space.setGravity(Vector3f.ZERO);
            a = new PhysicsRigidBody(new BoxCollisionShape(new Vector3f(.6f, .4f, .2f)), dynamicA ? 30f : 0f);
            b = new PhysicsRigidBody(new BoxCollisionShape(new Vector3f(.2f, .35f, .5f)), mass);
            a.setPhysicsLocation(new Vector3f(-3, 0, 0));
            b.setPhysicsLocation(new Vector3f(3, 0, 0));
            Quaternion rotation = new Quaternion().fromAngles(.3f, yaw, .2f);
            a.setPhysicsRotation(rotation);
            b.setPhysicsRotation(rotation);
            space.addCollisionObject(a);
            space.addCollisionObject(b);
            joint = new New6Dof(a, b, Vector3f.ZERO, Vector3f.ZERO,
                    Matrix3f.IDENTITY, Matrix3f.IDENTITY, RotationOrder.XZY);
            for (int axis = 0; axis < 6; axis++) {
                joint.set(MotorParam.LowerLimit, axis, 1f);
                joint.set(MotorParam.UpperLimit, axis, -1f);
            }
            space.addJoint(joint);
        }

        float speed() {
            Vector3f velocityA = a.isDynamic() ? a.getAngularVelocity(null) : Vector3f.ZERO;
            return joint.getAxis(0, null).dot(b.getAngularVelocity(null).subtractLocal(velocityA));
        }

        void motor(float speed, float torque) {
            var motor = joint.getRotationMotor(0);
            motor.setMotorEnabled(torque > 0f);
            motor.set(MotorParam.MaxMotorForce, torque);
            motor.set(MotorParam.TargetVelocity, -speed);
        }

        @Override
        public void close() {
            space.removeJoint(joint);
            space.removeCollisionObject(b);
            space.removeCollisionObject(a);
            space.destroy();
        }
    }
}
