package io.github.sweetzonzi.machine_max.control;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Quaternion;
import io.github.sweetzonzi.machine_max.client.input.ThirdPersonViewpoint;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimAxisLimit;
import io.github.sweetzonzi.machine_max.common.mech.control.view.ViewMath;
import io.github.sweetzonzi.machine_max.util.RotationDamper;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证回正只影响显示，且跨帧率、角度跨周和输入变化时保持连续。 */
class ThirdPersonViewpointTest {
    private static final long START = 1_000_000_000L;

    @Test
    void returnStartsAtTheDisplayedPoseAndEndsExactlyAtTheDesiredPose() {
        var view = new ThirdPersonViewpoint();
        var desired = new Quaternionf().rotateYXZ(.3f, -.2f, .4f);
        var displayed = new Quaternionf().rotateYXZ(-1.2f, .7f, -.6f);
        view.beginReturn(displayed);
        assertRotation(displayed, view.sampleDisplayRotation(desired, START));
        assertRotation(new Quaternionf(displayed).slerp(desired, .5f),
                view.sampleDisplayRotation(desired, START + 100_000_000));
        assertRotation(desired, view.sampleDisplayRotation(desired, START + 200_000_000));
        var next = new Quaternionf(desired).rotateY(.2f);
        assertRotation(next, view.sampleDisplayRotation(next, START + 210_000_000));
    }

    @Test
    void returnDependsOnElapsedTimeAndUsesTheShortestArcAcrossYawWrap() {
        var desired = new Quaternionf().rotateY((float) Math.toRadians(-179));
        var displayed = new Quaternionf().rotateY((float) Math.toRadians(179));
        for (int fps : new int[]{30, 60, 144}) {
            var view = new ThirdPersonViewpoint();
            view.beginReturn(displayed);
            view.sampleDisplayRotation(desired, START);
            for (long elapsed = 1_000_000_000L / fps; elapsed < 100_000_000; elapsed += 1_000_000_000L / fps) {
                view.sampleDisplayRotation(desired, START + elapsed);
            }
            assertRotation(new Quaternionf().rotateY((float) Math.PI),
                    view.sampleDisplayRotation(desired, START + 100_000_000));
        }
    }

    @Test
    void movingMountAndMouseInputRemainImmediateWithoutChangingTheControlTarget() {
        var view = new ThirdPersonViewpoint();
        view.enter(true, Quaternion.IDENTITY, Vec3.ZERO, new Vec3(0, 0, -1000), 0);
        view.beginReturn(new Quaternionf().rotateY((float) Math.toRadians(-60)));
        view.sampleDisplayRotation(new Quaternionf(), START);
        var reference = ViewMath.lookRotation(new Vec2(0, (float) Math.toRadians(20)));
        var control = view.update(reference, Vec3.ZERO, new Vec2(0, (float) Math.toRadians(5)),
                AimAxisLimit.CONTINUOUS, AimAxisLimit.CONTINUOUS);
        var desired = SparkMathKt.toQuaternionf(control.pose().rotation());
        var display = view.sampleDisplayRotation(desired, START + 100_000_000);
        assertRotation(new Quaternionf(desired).rotateY((float) Math.toRadians(-30)), display);
        assertEquals(control.focusPoint(), view.focusPoint());
        assertEquals(control, view.sample(reference, Vec3.ZERO));
        assertRotation(desired, view.sampleDisplayRotation(desired, START + 200_000_000));
    }

    @Test
    void mouseDeltaIsAddedDirectlyToRelativeLook() {
        Quaternion reference = ViewMath.lookRotation(new Vec2((float) Math.toRadians(18),
                (float) Math.toRadians(-32)));
        Vec3 origin = new Vec3(12, 4, -9);
        Vec2 initialLook = new Vec2((float) Math.toRadians(7), (float) Math.toRadians(-11));
        Vec3 inherited = ViewMath.follow(reference, initialLook, origin).focusPoint();
        Vec2 mouseDelta = new Vec2((float) Math.toRadians(-3.5), (float) Math.toRadians(8));
        var view = new ThirdPersonViewpoint();
        view.enter(true, reference, origin, inherited, 0);

        view.update(reference, origin, mouseDelta, AimAxisLimit.CONTINUOUS, AimAxisLimit.CONTINUOUS);

        assertEquals(initialLook.x + mouseDelta.x, view.relativeLook().x, 1e-5);
        assertEquals(initialLook.y + mouseDelta.y, view.relativeLook().y, 1e-5);
        assertEquals(0, ViewMath.follow(reference, initialLook.add(mouseDelta), origin).focusPoint()
                .distanceTo(view.focusPoint()), 1e-9);
    }

    @Test
    void logicalFocusMovesImmediatelyWhileDisplayedCameraRotationCatchesUp() {
        var view = new ThirdPersonViewpoint();
        view.enter(true, Quaternion.IDENTITY, Vec3.ZERO, null, 0);
        var initial = view.update(Quaternion.IDENTITY, Vec3.ZERO, Vec2.ZERO,
                AimAxisLimit.CONTINUOUS, AimAxisLimit.CONTINUOUS);
        Quaternionf initialRotation = SparkMathKt.toQuaternionf(initial.pose().rotation());
        var displayFollow = new RotationDamper(.12);
        displayFollow.reset(initialRotation, START);

        Quaternion mount = ViewMath.lookRotation(new Vec2(0, (float) Math.toRadians(60)));
        var updated = view.update(mount, Vec3.ZERO, Vec2.ZERO,
                AimAxisLimit.CONTINUOUS, AimAxisLimit.CONTINUOUS);
        Quaternionf desiredRotation = SparkMathKt.toQuaternionf(updated.pose().rotation());
        Vec3 logicalFocus = ViewMath.follow(mount, Vec2.ZERO, Vec3.ZERO).focusPoint();

        displayFollow.advance(desiredRotation, START + 50_000_000L);
        Quaternionf displayedRotation = displayFollow.sample(1);
        Vector3f initialForward = initialRotation.transform(new Vector3f(0, 0, -1));
        Vector3f desiredForward = desiredRotation.transform(new Vector3f(0, 0, -1));
        Vector3f displayedForward = displayedRotation.transform(new Vector3f(0, 0, -1));
        Vector3f cameraPosition = new Vector3f(displayedForward).mul(-4);
        Vector3f targetDirection = new Vector3f(
                (float) logicalFocus.x - cameraPosition.x,
                (float) logicalFocus.y - cameraPosition.y,
                (float) logicalFocus.z - cameraPosition.z).normalize();

        assertTrue(initial.focusPoint().distanceTo(logicalFocus) > 100);
        assertEquals(0, logicalFocus.distanceTo(view.focusPoint()), 1e-8);
        assertTrue(initialForward.distance(displayedForward) > .1f);
        assertTrue(displayedForward.distance(desiredForward) > .1f);
        assertTrue(targetDirection.dot(displayedForward) < .99f);
    }

    @Test
    void interruptedReturnCanResumeFromTheCurrentDisplayAndEnteringClearsIt() {
        var view = new ThirdPersonViewpoint();
        var desired = new Quaternionf();
        view.beginReturn(new Quaternionf().rotateY(1));
        view.sampleDisplayRotation(desired, START);
        var midway = view.sampleDisplayRotation(desired, START + 100_000_000);
        view.cancelReturn();
        view.beginReturn(midway);
        assertRotation(midway, view.sampleDisplayRotation(desired, START + 150_000_000));
        view.enter(false, Quaternion.IDENTITY, Vec3.ZERO, new Vec3(0, 0, -1000), 0);
        assertRotation(desired, view.sampleDisplayRotation(desired, START + 160_000_000));
    }

    @Test
    void renderSamplesDoNotMutateTheLogicalAimState() {
        float bias = (float) Math.toRadians(15);
        Quaternion reference = ViewMath.lookRotation(new Vec2((float) Math.toRadians(12), (float) Math.toRadians(-28)));
        Vec3 anchor = new Vec3(15, 8, -30);
        var view = new ThirdPersonViewpoint();
        view.enter(true, reference, anchor, null, bias);
        view.update(reference, anchor, new Vec2((float) Math.toRadians(4), (float) Math.toRadians(7)),
                AimAxisLimit.CONTINUOUS, AimAxisLimit.CONTINUOUS);
        Vec2 logicalLook = view.relativeLook();
        Vec3 logicalPoint = view.focusPoint();

        for (float partial : new float[]{0, .25f, .5f, .75f, 1}) {
            Quaternion sampledReference = ViewMath.lookRotation(new Vec2(
                    (float) Math.toRadians(12 + partial), (float) Math.toRadians(-28 + partial * 2)));
            view.sample(sampledReference, anchor.add(partial, 0, -partial));
        }

        assertEquals(logicalLook, view.relativeLook());
        assertEquals(logicalPoint, view.focusPoint());
    }

    private static void assertRotation(Quaternionf expected, Quaternionf actual) {
        for (Vector3f axis : new Vector3f[]{new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)}) {
            assertEquals(0, expected.transform(new Vector3f(axis)).distance(actual.transform(new Vector3f(axis))), 2e-6);
        }
    }
}
