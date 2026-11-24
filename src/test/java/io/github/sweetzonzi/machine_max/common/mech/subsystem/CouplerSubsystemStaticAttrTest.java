package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.BasicSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CouplerSubsystemStaticAttr;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 锁扣型号字段的边界：非法组合在构造期拒绝，不等到物理步。
 */
class CouplerSubsystemStaticAttrTest {

    private static final BasicSubsystemStaticAttr.BasicAttr BASIC =
            new BasicSubsystemStaticAttr.BasicAttr(20f, true, false, false);

    private static CouplerSubsystemStaticAttr attr(float capture, float lock, float force,
                                                   float angle, float speed, float rearm) {
        return new CouplerSubsystemStaticAttr(BASIC, capture, lock, force, angle, speed, rearm,
                CouplerSubsystemStaticAttr.CouplerSoundAttr.DEFAULT);
    }

    @Test
    void acceptsThresholdsInclusive() {
        CouplerSubsystemStaticAttr equal = attr(0.02f, 0.02f, 0f, 3f, 0.25f, 2f);
        assertEquals(0.02f, equal.getLockDistance());
        CouplerSubsystemStaticAttr boundaryAngle = attr(0.02f, 0.01f, 0f, 90f, 0.25f, 2f);
        assertEquals(90f, boundaryAngle.getLockAngleTolerance());
    }

    @Test
    void rejectsLockBeyondCapture() {
        assertThrows(IllegalArgumentException.class, () -> attr(0.01f, 0.02f, 0f, 3f, 0.25f, 2f));
    }

    @Test
    void rejectsNonPositiveDistances() {
        assertThrows(IllegalArgumentException.class, () -> attr(0f, 0f, 0f, 3f, 0.25f, 2f));
        assertThrows(IllegalArgumentException.class, () -> attr(0.02f, 0f, 0f, 3f, 0.25f, 2f));
    }

    @Test
    void rejectsNegativeForceAndSpeed() {
        assertThrows(IllegalArgumentException.class, () -> attr(0.02f, 0.01f, -1f, 3f, 0.25f, 2f));
        assertThrows(IllegalArgumentException.class, () -> attr(0.02f, 0.01f, 0f, 3f, -0.1f, 2f));
    }

    @Test
    void rejectsAngleOutsideOpenClosedInterval() {
        assertThrows(IllegalArgumentException.class, () -> attr(0.02f, 0.01f, 0f, 0f, 0.25f, 2f));
        assertThrows(IllegalArgumentException.class, () -> attr(0.02f, 0.01f, 0f, 90.1f, 0.25f, 2f));
    }

    @Test
    void rejectsRearmMultiplierAtOrBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> attr(0.02f, 0.01f, 0f, 3f, 0.25f, 1f));
        assertThrows(IllegalArgumentException.class, () -> attr(0.02f, 0.01f, 0f, 3f, 0.25f, 0.5f));
    }

    @Test
    void rejectsNonFiniteValues() {
        assertThrows(IllegalArgumentException.class,
                () -> attr(Float.NaN, 0.01f, 0f, 3f, 0.25f, 2f));
        assertThrows(IllegalArgumentException.class,
                () -> attr(0.02f, 0.01f, Float.POSITIVE_INFINITY, 3f, 0.25f, 2f));
    }

    @Test
    void rearmDistanceKeepsTheLargerOfCaptureAndMultipliedLock() {
        assertEquals(0.03f, attr(0.02f, 0.015f, 0f, 3f, 0.25f, 2f).rearmDistance(), 1e-6f);
        // 倍率乘出来小于捕获范围时以捕获范围为准
        assertEquals(0.04f, attr(0.04f, 0.01f, 0f, 3f, 0.25f, 2f).rearmDistance(), 1e-6f);
    }
}
