package io.github.sweetzonzi.machine_max.util;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证跟随低通的衰减只取决于经过时间，且采样不推进内部状态。 */
class RotationDamperTest {
    private static final double SECONDS = .12;
    private static final long START = 1_000_000_000L;

    /** 姿态夹角，弧度；四元数取双覆盖下的最短弧。 */
    private static double angle(Quaternionf a, Quaternionf b) {
        double dot = Math.abs(a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w);
        return 2 * Math.acos(Math.min(1, dot));
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
    void zeroElapsedTimeKeepsThePoseAndLongPausesAreClamped() {
        var target = new Quaternionf().rotateX((float) Math.toRadians(90));
        var damper = new RotationDamper(SECONDS);
        damper.reset(new Quaternionf(), START);
        damper.advance(target, START + 500_000_000L);
        var afterHalfSecond = new Quaternionf(damper.sample(1));
        damper.advance(target, START + 500_000_000L);
        assertEquals(angle(afterHalfSecond, damper.sample(1)), 0, 1e-7);

        // 超过上限的间隔按 0.25 秒推进，不会一次跳满。
        var paused = new RotationDamper(SECONDS);
        paused.reset(new Quaternionf(), START);
        paused.advance(target, START + 30_000_000_000L);
        double clamped = Math.toRadians(90) * (1 - Math.exp(-.25 / SECONDS));
        assertEquals(clamped, angle(new Quaternionf(), paused.sample(1)), 1e-4);
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

        assertEquals(first.x, damper.sample(0).x, 1e-6);
        assertEquals(second.x, damper.sample(1).x, 1e-6);
        assertEquals(new Quaternionf(first).slerp(second, .5f).x, damper.sample(.5f).x, 1e-6);
        // 采样不推进状态。
        assertEquals(second.x, damper.sample(1).x, 1e-6);
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
