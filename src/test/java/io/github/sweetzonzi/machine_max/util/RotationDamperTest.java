package io.github.sweetzonzi.machine_max.util;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证跟随低通的衰减只取决于经过时间，且采样不推进内部状态。 */
class RotationDamperTest {
    private static final double SECONDS = .12;
    private static final long START = 1_000_000_000L;

    /** 姿态夹角，弧度；四元数取双覆盖下的最短弧。 */
    private static double angle(Quaternionf a, Quaternionf b) {
        Quaternionf relative = new Quaternionf(a).normalize().conjugate()
                .mul(new Quaternionf(b).normalize()).normalize();
        double vectorLength = Math.sqrt(relative.x * relative.x + relative.y * relative.y
                + relative.z * relative.z);
        return 2 * Math.atan2(vectorLength, Math.abs(relative.w));
    }

    @Test
    void residualDecaysWithElapsedTimeRegardlessOfStepSize() {
        var target = new Quaternionf().rotateY((float) Math.toRadians(40));
        var single = new RotationDamper(SECONDS);
        single.reset(new Quaternionf(), START);
        single.advance(target, START + 100_000_000L);

        var stepped = new RotationDamper(SECONDS);
        stepped.reset(new Quaternionf(), START);
        for (long elapsed = 10_000_000L; elapsed <= 100_000_000L; elapsed += 10_000_000L) {
            stepped.advance(target, START + elapsed);
        }

        // 单次推进后已转过的角度为初始夹角乘以 1-e^(-Δt/τ)，剩余部分仍按指数衰减。
        double applied = Math.toRadians(40) * (1 - Math.exp(-.1 / SECONDS));
        assertEquals(applied, angle(new Quaternionf(), single.sample(1)), 1e-4);
        assertEquals(Math.toRadians(40) - applied, angle(target, single.sample(1)), 1e-4);
        assertEquals(single.sample(1).x, stepped.sample(1).x, 1e-5);
        assertEquals(single.sample(1).w, stepped.sample(1).w, 1e-5);
    }

    @Test
    void equivalentQuaternionSignsFollowTheShortestArcAcrossYawWrap() {
        Quaternionf start = new Quaternionf().rotateY((float) Math.toRadians(179));
        Quaternionf target = new Quaternionf().rotateY((float) Math.toRadians(-179));
        Quaternionf oppositeSign = new Quaternionf(-target.x, -target.y, -target.z, -target.w);
        var positive = new RotationDamper(SECONDS);
        var negative = new RotationDamper(SECONDS);
        positive.reset(start, START);
        negative.reset(start, START);

        long elapsed = Math.round(SECONDS * 1e9);
        positive.advance(target, START + elapsed);
        negative.advance(oppositeSign, START + elapsed);

        Quaternionf result = positive.sample(1);
        assertEquals(0, angle(result, negative.sample(1)), 1e-6);
        assertEquals(Math.toRadians(2) * (1 - Math.exp(-1)), angle(start, result), 1e-4);
        assertEquals(Math.toRadians(2) * Math.exp(-1), angle(target, result), 1e-4);
    }

    @Test
    void changingCombinedYawPitchAndRollStaysNormalizedAndFollowsQuaternionSlerp() {
        Quaternionf current = new Quaternionf().rotateYXZ(.4f, -.25f, .3f);
        var damper = new RotationDamper(SECONDS);
        damper.reset(current, START);
        long now = START;
        long[] steps = {8_000_000L, 17_000_000L, 11_000_000L, 23_000_000L};
        float[][] angles = {
                {.5f, -.2f, .4f},
                {.3f, -.1f, .55f},
                {.15f, .05f, .6f},
                {-.1f, .2f, .5f}
        };

        for (int i = 0; i < steps.length; i++) {
            Quaternionf target = new Quaternionf().rotateYXZ(angles[i][0], angles[i][1], angles[i][2]);
            double elapsed = steps[i] / 1e9;
            float alpha = (float) (1 - Math.exp(-elapsed / SECONDS));
            Quaternionf expected = new Quaternionf(current).slerp(target, alpha);
            now += steps[i];
            damper.advance(target, now);

            Quaternionf actual = damper.sample(1);
            assertEquals(0, angle(expected, actual), 1e-5);
            assertEquals(1, actual.lengthSquared(), 1e-5);
            assertTrue(Float.isFinite(actual.x) && Float.isFinite(actual.y)
                    && Float.isFinite(actual.z) && Float.isFinite(actual.w));
            current = actual;
        }
    }

    @Test
    void zeroElapsedTimeKeepsThePoseAndLongPausesAreClamped() {
        var target = new Quaternionf().rotateX((float) Math.toRadians(90));
        var damper = new RotationDamper(SECONDS);
        damper.reset(new Quaternionf(), START);
        damper.advance(target, START + 500_000_000L);
        var afterHalfSecond = new Quaternionf(damper.sample(1));
        var midpointBeforeZeroStep = new Quaternionf(damper.sample(.5f));
        damper.advance(target, START + 500_000_000L);
        assertEquals(0, angle(afterHalfSecond, damper.sample(1)), 1e-7);
        assertEquals(0, angle(midpointBeforeZeroStep, damper.sample(.5f)), 1e-7);

        // 超过上限的间隔按 0.25 秒推进，不会一次跳满。
        var paused = new RotationDamper(SECONDS);
        paused.reset(new Quaternionf(), START);
        paused.advance(target, START + 30_000_000_000L);
        double clamped = Math.toRadians(90) * (1 - Math.exp(-.25 / SECONDS));
        assertEquals(clamped, angle(new Quaternionf(), paused.sample(1)), 1e-4);
    }

    @Test
    void regressingTimestampDoesNotMoveTheElapsedTimeAnchorBackward() {
        var target = new Quaternionf().rotateY((float) Math.toRadians(90));
        var damper = new RotationDamper(SECONDS);
        damper.reset(new Quaternionf(), START);
        damper.advance(target, START + 100_000_000L);
        Quaternionf afterFirstStep = damper.sample(1);
        Quaternionf interpolatedBeforeRegression = damper.sample(.5f);

        damper.advance(target, START + 90_000_000L);
        assertEquals(0, angle(afterFirstStep, damper.sample(1)), 1e-7);
        assertEquals(0, angle(interpolatedBeforeRegression, damper.sample(.5f)), 1e-7);
        damper.advance(target, START + 110_000_000L);

        double expected = Math.toRadians(90) * (1 - Math.exp(-.11 / SECONDS));
        assertEquals(expected, angle(new Quaternionf(), damper.sample(1)), 1e-4);
    }

    @Test
    void sampleInterpolatesBetweenTheTwoMostRecentAdvances() {
        var target = new Quaternionf().rotateZ((float) Math.toRadians(60));
        var damper = new RotationDamper(SECONDS);
        damper.reset(new Quaternionf(), START);
        damper.advance(target, START + 50_000_000L);
        var first = new Quaternionf(damper.sample(1));
        damper.advance(target, START + 100_000_000L);
        var second = new Quaternionf(damper.sample(1));
        Quaternionf midpoint = new Quaternionf(first).slerp(second, .5f);

        assertEquals(0, angle(first, damper.sample(0)), 1e-6);
        assertEquals(0, angle(second, damper.sample(1)), 1e-6);
        assertEquals(0, angle(midpoint, damper.sample(.5f)), 1e-6);
        for (int i = 0; i < 5; i++) {
            damper.sample(i / 4f);
        }
        assertEquals(0, angle(second, damper.sample(1)), 1e-6);
    }

    @Test
    void resetSnapsToTheTargetWithoutResidual() {
        var damper = new RotationDamper(SECONDS);
        damper.reset(new Quaternionf(), START);
        damper.advance(new Quaternionf().rotateY((float) Math.toRadians(80)), START + 100_000_000L);
        var target = new Quaternionf().rotateX((float) Math.toRadians(-25));
        damper.reset(target, START + 200_000_000L);
        assertEquals(0, angle(target, damper.sample(0)), 1e-7);
        assertEquals(0, angle(target, damper.sample(1)), 1e-7);
    }
}
