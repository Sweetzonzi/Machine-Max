package io.github.sweetzonzi.machine_max.control;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Quaternion;
import io.github.sweetzonzi.machine_max.client.input.ThirdPersonViewpoint;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimAxisLimit;
import io.github.sweetzonzi.machine_max.common.mech.control.view.ViewMath;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        view.enter(true, Quaternion.IDENTITY, Vec3.ZERO, new Vec3(0, 0, -1000));
        view.beginReturn(new Quaternionf().rotateY((float) Math.toRadians(-60)));
        view.sampleDisplayRotation(new Quaternionf(), START);
        var reference = ViewMath.lookRotation(new Vec2(0, (float) Math.toRadians(20)));
        var control = view.update(reference, Vec3.ZERO, new Vec2(0, (float) Math.toRadians(5)),
                AimAxisLimit.CONTINUOUS, AimAxisLimit.CONTINUOUS);
        var desired = SparkMathKt.toQuaternionf(control.pose().rotation());
        var display = view.sampleDisplayRotation(desired, START + 100_000_000);
        assertRotation(new Quaternionf(desired).rotateY((float) Math.toRadians(-30)), display);
        assertEquals(control.worldPoint(), view.worldPoint());
        assertEquals(control, view.sample(reference, Vec3.ZERO));
        assertRotation(desired, view.sampleDisplayRotation(desired, START + 200_000_000));
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
        view.enter(false, Quaternion.IDENTITY, Vec3.ZERO, new Vec3(0, 0, -1000));
        assertRotation(desired, view.sampleDisplayRotation(desired, START + 160_000_000));
    }

    private static void assertRotation(Quaternionf expected, Quaternionf actual) {
        for (Vector3f axis : new Vector3f[]{new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)}) {
            assertEquals(0, expected.transform(new Vector3f(axis)).distance(actual.transform(new Vector3f(axis))), 2e-6);
        }
    }
}
