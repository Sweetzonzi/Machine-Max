package io.github.sweetzonzi.machine_max.probe;

import com.jme3.bullet.CollisionConfiguration;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.RotationOrder;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.joints.motors.MotorParam;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 校准动画关节执行器的平动与转动伺服目标符号。 */
class JointDriverServoProbe {
    private static final float STEP = 0.01f;

    @BeforeAll
    static void loadNative() {
        NativeLibraryLoader.load();
    }

    @Test
    void positiveTranslationTargetMovesBodyBAlongPositiveJointAxis() {
        try (Rig rig = new Rig()) {
            rig.translationTarget(0, 0.25f);
            rig.step(200);

            assertEquals(0.25f, rig.bodyB.getPhysicsLocation(null).x, 0.02f);
        }
    }

    @Test
    void negativeRotationTargetProducesNegativeJointAngle() {
        try (Rig rig = new Rig()) {
            rig.rotationTarget(1, -0.35f);
            rig.step(200);

            assertEquals(-0.35f, rig.joint.getAngles(null).y, 0.02f);
        }
    }

    private static final class Rig implements AutoCloseable {
        private final PhysicsSpace space = new PhysicsSpace(
                new Vector3f(-10f, -10f, -10f), new Vector3f(10f, 10f, 10f),
                PhysicsSpace.BroadphaseType.DBVT, 1, new CollisionConfiguration(256, 1));
        private final PhysicsRigidBody bodyA = new PhysicsRigidBody(new BoxCollisionShape(0.2f), 0f);
        private final PhysicsRigidBody bodyB = new PhysicsRigidBody(new BoxCollisionShape(0.2f), 1f);
        private final New6Dof joint;

        private Rig() {
            space.setGravity(Vector3f.ZERO);
            space.addCollisionObject(bodyA);
            space.addCollisionObject(bodyB);
            joint = new New6Dof(bodyA, bodyB, Vector3f.ZERO, Vector3f.ZERO,
                    Matrix3f.IDENTITY, Matrix3f.IDENTITY, RotationOrder.XYZ);
            joint.setCollisionBetweenLinkedBodies(false);
            for (int axis = 0; axis < 6; axis++) {
                joint.set(MotorParam.LowerLimit, axis, 1f);
                joint.set(MotorParam.UpperLimit, axis, -1f);
            }
            space.addJoint(joint);
        }

        private void translationTarget(int axis, float target) {
            var motor = joint.getTranslationMotor();
            motor.setMotorEnabled(axis, true);
            motor.setServoEnabled(axis, true);
            Vector3f values = new Vector3f();
            values.set(axis, target);
            motor.set(MotorParam.ServoTarget, values);
            values.set(0f, 0f, 0f);
            values.set(axis, 2f);
            motor.set(MotorParam.TargetVelocity, values);
            values.set(0f, 0f, 0f);
            values.set(axis, 100f);
            motor.set(MotorParam.MaxMotorForce, values);
        }

        private void rotationTarget(int axis, float target) {
            var motor = joint.getRotationMotor(axis);
            motor.setMotorEnabled(true);
            motor.setServoEnabled(true);
            motor.set(MotorParam.ServoTarget, target);
            motor.set(MotorParam.TargetVelocity, 2f);
            motor.set(MotorParam.MaxMotorForce, 100f);
        }

        private void step(int count) {
            for (int tick = 0; tick < count; tick++) space.update(STEP, 0);
        }

        @Override
        public void close() {
            space.removeJoint(joint);
            space.removeCollisionObject(bodyB);
            space.removeCollisionObject(bodyA);
            space.destroy();
        }
    }
}
