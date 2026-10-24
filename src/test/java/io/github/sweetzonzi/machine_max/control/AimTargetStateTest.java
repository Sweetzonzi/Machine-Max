package io.github.sweetzonzi.machine_max.control;

import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimAxisLimit;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimGeometrySnapshot;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimMath;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimPose;
import io.github.sweetzonzi.machine_max.common.mech.control.aim.AimTargetState;
import io.github.sweetzonzi.machine_max.common.mech.control.view.ViewMath;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static io.github.sweetzonzi.machine_max.control.AimGeometryFixtures.deg;
import static org.junit.jupiter.api.Assertions.*;

class AimTargetStateTest {
    private static final AimPose SIGHT = AimPose.IDENTITY;
    private static final Vec3 FORWARD_POINT = new Vec3(0, 0, -AimMath.AIM_DISTANCE_METERS);

    private static AimGeometrySnapshot geometry(AimPose root, Vec2 actual) {
        return new AimGeometrySnapshot(root, new Transform(), new Vector3f(0, -1, 0),
                new Transform(), Vector3f.UNIT_X, new Transform(),
                AimAxisLimit.CONTINUOUS, AimAxisLimit.CONTINUOUS, actual);
    }

    private static void assertAngles(Vec2 expected, Vec2 actual) {
        assertEquals(expected.x, actual.x, 1e-6);
        assertEquals(expected.y, actual.y, 1e-6);
    }

    @Test
    void fullTargetsOverwriteWithoutAccumulating() {
        for (AimTargetState.Mode mode : AimTargetState.Mode.values()) {
            var state = new AimTargetState(mode, FORWARD_POINT, Vec2.ZERO);
            for (int yaw : new int[]{1, 2, 2, 3}) {
                for (int repeat = 0; repeat < 20; repeat++) {
                    state.setTarget(mode, FORWARD_POINT, deg(12, yaw));
                }
            }
            assertAngles(deg(12, 3), state.getRelativeAim());
            assertEquals(FORWARD_POINT, state.getWorldPoint());
        }
    }

    @Test
    void horizontalAndVerticalInputStayIndependentWithARolledSight() {
        AimPose rolled = new AimPose(Vec3.ZERO, new Quaternion().fromAngles(0, 0, (float) Math.PI / 2));
        for (AimTargetState.Mode mode : AimTargetState.Mode.values()) {
            var state = new AimTargetState(mode, FORWARD_POINT, Vec2.ZERO);
            state.applyMouse(rolled, geometry(rolled, Vec2.ZERO), deg(2, 0));
            state.applyMouse(rolled, geometry(rolled, Vec2.ZERO), deg(0, 3));
            assertEquals(deg(2, 3).x, state.getRelativeAim().x, 1e-6);
            assertEquals(deg(2, 3).y, state.getRelativeAim().y, 1e-6);
            Vec2 local = ViewMath.inherit(rolled.rotation(), rolled.position(), state.getWorldPoint());
            assertEquals(deg(2, 3).x, local.x, 1e-6);
            assertEquals(deg(2, 3).y, local.y, 1e-6);
        }
    }

    @Test
    void zeroInputAndDisplayReadsPreserveFiniteWorldPoint() {
        Vec3 point = new Vec3(20_000_000.125, 30, -20_000_000.375);
        AimPose moved = new AimPose(point.add(10, 20, 30), new Quaternion().fromAngles(.2f, .3f, .1f));
        for (AimTargetState.Mode mode : AimTargetState.Mode.values()) {
            var state = new AimTargetState(mode, point, deg(10, 20));
            for (int repeat = 0; repeat < 100; repeat++) {
                state.applyMouse(moved, geometry(SIGHT, Vec2.ZERO), Vec2.ZERO);
                assertEquals(point, state.getWorldPoint());
                assertAngles(deg(10, 20), state.getRelativeAim());
            }
        }
    }

    @Test
    void stableLeadRejectsOnlyFurtherInputAndAllowsImmediateReverse() {
        Vec3 point = ViewMath.follow(Quaternion.IDENTITY, deg(0, 28), Vec3.ZERO).worldPoint();
        var state = new AimTargetState(AimTargetState.Mode.WORLD, point, Vec2.ZERO);
        assertEquals(deg(0, 2).y, state.applyMouse(SIGHT, null, deg(0, 5)).y, 1e-6);
        Vec3 atLimit = state.getWorldPoint();
        assertEquals(0, state.applyMouse(SIGHT, null, deg(0, 5)).y, 1e-6);
        assertTrue(atLimit.distanceTo(state.getWorldPoint()) < .001);
        assertEquals(deg(0, -5).y, state.applyMouse(SIGHT, null, deg(0, -5)).y, 1e-6);
        assertEquals(deg(0, -3).y, state.getRelativeAim().y, 1e-6);
    }

    @Test
    void stableLeadAtTheLimitUsesTheSameOpticalTimeAsItsScreenProjection() {
        for (boolean yaw : new boolean[]{true, false}) {
            for (int direction : new int[]{-1, 1}) {
                // 90°/s 的机构每 tick 转 4.5°，120 fps 每 tick 采样六次。
                // tick 末光轴限幅、帧内光轴投影会产生 3.75°锯齿；同一帧参考应保持 30°。
                assertEquals(3.75, stableLeadSpan(yaw, direction, false), 1e-4);
                assertEquals(0, stableLeadSpan(yaw, direction, true), 1e-4);
            }
        }
    }

    private static double stableLeadSpan(boolean yaw, int direction, boolean visibleReference) {
        Vec2 limit = yaw ? deg(0, direction * 30) : deg(direction * 30, 0);
        Vec3 point = ViewMath.follow(Quaternion.IDENTITY, limit, Vec3.ZERO).worldPoint();
        var state = new AimTargetState(AimTargetState.Mode.WORLD, point, Vec2.ZERO);
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        for (int tick = 1; tick <= 4; tick++) {
            for (int frame = 1; frame <= 6; frame++) {
                double visibleAngle = direction * 4.5 * (tick - 1 + frame / 6.0);
                double inputAngle = visibleReference ? visibleAngle : direction * 4.5 * tick;
                AimPose visible = ViewMath.follow(Quaternion.IDENTITY,
                        yaw ? deg(0, visibleAngle) : deg(visibleAngle, 0), Vec3.ZERO).pose();
                AimPose input = ViewMath.follow(Quaternion.IDENTITY,
                        yaw ? deg(0, inputAngle) : deg(inputAngle, 0), Vec3.ZERO).pose();
                state.applyMouse(input, null, yaw ? deg(0, direction * 10) : deg(direction * 10, 0));
                Vec2 offset = ViewMath.inherit(visible.rotation(), visible.position(), state.getWorldPoint());
                double lead = Math.toDegrees(yaw ? offset.y : offset.x) * direction;
                minimum = Math.min(minimum, lead);
                maximum = Math.max(maximum, lead);
            }
        }
        return maximum - minimum;
    }

    @Test
    void relativeAxesAccumulateWithoutInferringMechanicalLimitsFromOpticalError() {
        Vec3 distant = ViewMath.follow(Quaternion.IDENTITY, deg(50, 120), Vec3.ZERO).worldPoint();
        for (AimTargetState.Mode mode : AimTargetState.Mode.values()) {
            var state = new AimTargetState(mode, distant, Vec2.ZERO);
            Vec2 accepted = state.applyMouse(SIGHT, geometry(SIGHT, Vec2.ZERO), deg(5, 5));
            assertEquals(mode.pitchStable ? 0 : deg(5, 5).x, accepted.x, 1e-6);
            assertEquals(mode.yawStable ? 0 : deg(5, 5).y, accepted.y, 1e-6);
            assertAngles(accepted, state.getRelativeAim());
        }
    }

    @Test
    void switchingModesAndSourcesPreservesTheOperatorsAccumulatedAngles() {
        var first = new AimTargetState(AimTargetState.Mode.RELATIVE, FORWARD_POINT, Vec2.ZERO);
        var second = new AimTargetState(AimTargetState.Mode.RELATIVE, FORWARD_POINT, Vec2.ZERO);
        first.applyMouse(SIGHT, geometry(SIGHT, Vec2.ZERO), deg(2, 3));
        for (AimTargetState.Mode mode : AimTargetState.Mode.values()) {
            Vec3 inherited = FORWARD_POINT.add(50, 10, 0);
            first.setTarget(mode, inherited, first.getRelativeAim());
            assertAngles(deg(2, 3), first.getRelativeAim());
            assertEquals(inherited, first.getWorldPoint());
        }
        assertAngles(Vec2.ZERO, second.getRelativeAim());
        assertEquals(FORWARD_POINT, second.getWorldPoint());
    }

    @Test
    void initializationAndBaseMotionUseTheMechanismReference() {
        Vec2 initial = deg(15, 40);
        var sample = geometry(SIGHT, initial);
        var state = new AimTargetState(AimTargetState.Mode.RELATIVE, FORWARD_POINT, sample.actualAngles());
        state.updateGeometry(sample);
        assertAngles(initial, state.getRelativeAim());
        assertEquals(AimMath.buildAimPoint(sample, initial), state.getWorldPoint());

        AimPose moved = new AimPose(new Vec3(10, 2, 30), ViewMath.lookRotation(deg(0, 50)));
        var movedSample = geometry(moved, initial);
        state.updateGeometry(movedSample);
        assertEquals(AimMath.buildAimPoint(movedSample, initial), state.getWorldPoint());
        assertAngles(initial, state.getRelativeAim());
    }

    @Test
    void relativeOffsetIsCappedAtSixtyDegreesAndReverseIsImmediate() {
        var sample = geometry(SIGHT, deg(10, 40));
        var state = new AimTargetState(AimTargetState.Mode.RELATIVE, FORWARD_POINT, sample.actualAngles());
        assertAngles(deg(60, 60), state.applyMouse(sample.forward(sample.actualAngles()), sample, deg(120, 120)));
        assertAngles(deg(70, 100), state.getRelativeAim());
        assertAngles(Vec2.ZERO, state.applyMouse(SIGHT, sample, deg(5, 5)));
        assertAngles(deg(-5, -5), state.applyMouse(SIGHT, sample, deg(-5, -5)));
        assertAngles(deg(65, 95), state.getRelativeAim());
        var caughtUp = geometry(SIGHT, state.getRelativeAim());
        assertAngles(deg(5, 5), state.applyMouse(SIGHT, caughtUp, deg(5, 5)));
    }

    @Test
    void continuousYawKeepsMultipleTurnsAcrossNativeBranches() {
        var state = new AimTargetState(AimTargetState.Mode.RELATIVE, FORWARD_POINT, deg(0, 170));
        for (int i = 0; i < 24; i++) {
            Vec2 actual = new Vec2(0, (float) Math.IEEEremainder(state.getRelativeAim().y, 2 * Math.PI));
            var sample = geometry(SIGHT, actual);
            assertAngles(deg(0, 30), state.applyMouse(sample.forward(actual), sample, deg(0, 30)));
        }
        assertEquals(Math.toRadians(890), state.getRelativeAim().y, 1e-5);
    }

    @Test
    void mixedModeReticleUsesUnstableOffsetAndCurrentStableAxis() {
        var state = new AimTargetState(AimTargetState.Mode.YAW_STABLE, FORWARD_POINT, deg(15, 40));
        var sample = geometry(SIGHT, deg(10, 60));
        Vec3 heldWorld = state.getWorldPoint();
        state.updateGeometry(sample);
        assertEquals(heldWorld, state.getWorldPoint());
        assertEquals(AimMath.buildAimPoint(sample, deg(15, 60)), state.relativePoint(sample));
        var caughtUp = geometry(SIGHT, deg(15, 80));
        assertEquals(AimMath.buildAimPoint(caughtUp, caughtUp.actualAngles()), state.relativePoint(caughtUp));
    }

    @Test
    void relativeReticleFollowsInterpolatedMechanismInsteadOfDiscretePhysicsError() {
        var state = new AimTargetState(AimTargetState.Mode.RELATIVE, FORWARD_POINT, deg(0, 30));
        for (int tick = 1; tick <= 4; tick++) {
            AimPose physicalBase = new AimPose(new Vec3(20_000_000 + tick, 2, 0),
                    ViewMath.lookRotation(deg(0, tick * 2)));
            var physical = geometry(physicalBase, deg(0, tick * 4.5));
            for (int frame = 1; frame <= 6; frame++) {
                double time = tick - 1 + frame / 6.0;
                AimPose base = new AimPose(new Vec3(20_000_000 + time, 2, 0),
                        ViewMath.lookRotation(deg(0, time * 2)));
                Vec2 actual = deg(0, time * 4.5);
                AimPose optic = geometry(base, actual).forward(actual);
                var rendered = physical.atPose(base, optic);
                assertAngles(actual, rendered.actualAngles());
                Vec3 point = state.relativePoint(rendered);
                Vec2 offset = ViewMath.inherit(optic.rotation(), optic.position(), point);
                assertEquals(30 - time * 4.5, Math.toDegrees(offset.y), 1e-4);

                // 同一帧参考同时用于 60°输入限幅和圈的绘制，触限时偏差保持恒定。
                var capped = new AimTargetState(AimTargetState.Mode.RELATIVE, FORWARD_POINT, actual);
                capped.applyMouse(optic, rendered, deg(0, 100));
                Vec2 cappedOffset = ViewMath.inherit(optic.rotation(), optic.position(), capped.relativePoint(rendered));
                assertEquals(60, Math.toDegrees(cappedOffset.y), 1e-4);
            }
        }
        assertAngles(deg(0, 30), state.getRelativeAim());
    }

    @Test
    void mixedReticleUsesTheRenderedActualAngleForItsStableAxis() {
        AimPose base = new AimPose(new Vec3(10, 2, -4), ViewMath.lookRotation(deg(4, 8)));
        Vec2 actual = deg(3, 7.5);
        AimPose optic = geometry(base, actual).forward(actual);
        var physical = geometry(SIGHT, deg(4, 8));
        var rendered = physical.atPose(base, optic);
        var state = new AimTargetState(AimTargetState.Mode.YAW_STABLE, FORWARD_POINT, deg(15, 20));
        assertEquals(0, state.relativePoint(rendered)
                .distanceTo(AimMath.buildAimPoint(geometry(base, actual), deg(15, 7.5))), .001);
        assertEquals(FORWARD_POINT, state.getWorldPoint());
    }
}
