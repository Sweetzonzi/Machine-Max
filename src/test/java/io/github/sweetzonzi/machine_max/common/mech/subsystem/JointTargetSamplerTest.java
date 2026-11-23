package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.animation.anim.origin.Loop;
import com.google.gson.JsonPrimitive;
import com.jme3.bullet.RotationOrder;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.machine_max.util.data.Axis;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JointTargetSamplerTest {
    private static final float EPSILON = 1e-5f;

    @Test
    void onlyOnceReleasesAtTheEndOfTheClip() {
        assertDriveWindow(new JsonPrimitive(false), Loop.ONCE, false);
        assertDriveWindow(new JsonPrimitive("once"), Loop.ONCE, false);
        assertDriveWindow(new JsonPrimitive("hold_on_last_frame"), Loop.HOLD_ON_LAST_FRAME, true);
        assertDriveWindow(new JsonPrimitive(true), Loop.TRUE, true);
    }

    private static void assertDriveWindow(JsonPrimitive json, Loop expectedMode, boolean drivesAtEnd) {
        Loop mode = Loop.getCODEC().parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(expectedMode, mode);
        assertTrue(JointTargetSampler.hasDriveTarget(mode, 0.9f, 1f, 1f));
        assertEquals(drivesAtEnd, JointTargetSampler.hasDriveTarget(mode, 1f, 1f, 1f));
        assertEquals(drivesAtEnd, JointTargetSampler.hasDriveTarget(mode, 1.1f, 1f, 1f));
        assertFalse(JointTargetSampler.hasDriveTarget(mode, 0.9f, 1f, 0f));
    }

    @Test
    void unchangedLocatorProducesZeroTarget() {
        Transform locator = new Transform(new Vector3f(1f, 2f, 3f), new Quaternion());

        JointTargetSampler.Target target = JointTargetSampler.sample(
                locator, locator.clone(), new Quaternion(), RotationOrder.XYZ);

        assertVectorEquals(Vector3f.ZERO, target.translation());
        assertVectorEquals(Vector3f.ZERO, target.rotation());
    }

    @Test
    void translationDeltaIsExpressedInTheJointFrame() {
        Transform rest = new Transform(new Vector3f(1f, 2f, 3f), new Quaternion());
        Transform animated = new Transform(new Vector3f(2f, 2f, 3f), new Quaternion());
        Quaternion jointFrame = new Quaternion().fromAngles(0f, 0f, (float) Math.PI / 2f);

        JointTargetSampler.Target target = JointTargetSampler.sample(
                rest, animated, jointFrame, RotationOrder.XYZ);

        assertVectorEquals(new Vector3f(0f, -1f, 0f), target.translation());
        assertVectorEquals(Vector3f.ZERO, target.rotation());
    }

    @Test
    void rotationDeltaIsExpressedInTheJointFrame() {
        Quaternion jointFrame = new Quaternion().fromAngles(0f, 0f, (float) Math.PI / 2f);
        Quaternion localDelta = new Quaternion().fromAngles(0.3f, 0f, 0f);
        Transform rest = new Transform(Vector3f.ZERO, jointFrame);
        Transform animated = new Transform(Vector3f.ZERO, jointFrame.mult(localDelta));

        JointTargetSampler.Target target = JointTargetSampler.sample(
                rest, animated, jointFrame, RotationOrder.XYZ);

        assertVectorEquals(Vector3f.ZERO, target.translation());
        assertEquals(0.3f, target.rotation().x, EPSILON);
        assertEquals(0f, target.rotation().y, EPSILON);
        assertEquals(0f, target.rotation().z, EPSILON);
    }

    @Test
    void eulerExtractionMatchesEverySupportedRotationOrder() {
        Vector3f expected = new Vector3f(0.2f, -0.35f, 0.4f);
        for (RotationOrder order : RotationOrder.values()) {
            Quaternion combined = new Quaternion();
            for (char axis : order.name().toCharArray()) {
                Quaternion step = switch (axis) {
                    case 'X' -> new Quaternion().fromAngles(expected.x, 0f, 0f);
                    case 'Y' -> new Quaternion().fromAngles(0f, expected.y, 0f);
                    case 'Z' -> new Quaternion().fromAngles(0f, 0f, expected.z);
                    default -> throw new IllegalStateException("Unexpected rotation axis: " + axis);
                };
                combined = step.mult(combined);
            }

            JointTargetSampler.Target target = JointTargetSampler.sample(
                    new Transform(), new Transform(Vector3f.ZERO, combined), new Quaternion(), order);

            assertVectorEquals(expected, target.rotation());
        }
    }

    @Test
    void servoTargetsMatchTranslationAndConnectorRotationConventions() {
        JointTargetSampler.Target target = new JointTargetSampler.Target(
                new Vector3f(1f, -2f, 3f), new Vector3f(0.1f, -0.2f, 0.3f));

        assertEquals(1f, JointTargetSampler.servoTarget(target, Axis.X));
        assertEquals(-2f, JointTargetSampler.servoTarget(target, Axis.Y));
        assertEquals(-0.2f, JointTargetSampler.servoTarget(target, Axis.YR));
        assertEquals(0.2f, JointTargetSampler.servoTarget(
                new JointTargetSampler.Target(Vector3f.ZERO, new Vector3f(0f, 0.2f, 0f)), Axis.YR));
        assertEquals(-0.1f, JointTargetSampler.servoTarget(target, Axis.XR));
        assertEquals(-0.3f, JointTargetSampler.servoTarget(target, Axis.ZR));
    }

    private static void assertVectorEquals(Vector3f expected, Vector3f actual) {
        assertEquals(expected.x, actual.x, EPSILON);
        assertEquals(expected.y, actual.y, EPSILON);
        assertEquals(expected.z, actual.z, EPSILON);
    }
}
