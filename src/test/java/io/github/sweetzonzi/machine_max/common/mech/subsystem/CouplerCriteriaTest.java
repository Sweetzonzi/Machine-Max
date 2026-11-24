package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁扣纯判据的边界：阈值包含、非法样本、向外反弹与合拢力形状。
 */
class CouplerCriteriaTest {

    private static final float LOCK = 0.015f;
    private static final float ANGLE = (float) Math.toRadians(3.0);
    private static final float SPEED = 0.25f;

    private static boolean accepted(float distance, float angle, float relative, float separation) {
        return CouplerCriteria.acceptsLock(distance, angle, relative, separation, LOCK, ANGLE, SPEED);
    }

    @Test
    void acceptsSamplesExactlyOnTheThresholds() {
        assertTrue(accepted(LOCK, ANGLE, SPEED, 0f));
        assertTrue(accepted(0f, 0f, 0f, 0f));
    }

    @Test
    void rejectsEachThresholdBeyondItsLimit() {
        assertFalse(accepted(LOCK + 1e-4f, 0f, 0f, 0f));
        assertFalse(accepted(0f, ANGLE + 1e-4f, 0f, 0f));
        assertFalse(accepted(0f, 0f, SPEED + 1e-4f, 0f));
    }

    @Test
    void rejectsOutwardBounceBeyondTheNoiseFloor() {
        assertTrue(accepted(0f, 0f, 0f, 0.01f));       // 噪声上限内
        assertFalse(accepted(0f, 0f, 0f, 0.0101f));    // 明显向外
    }

    @Test
    void noiseFloorFollowsTheConfiguredSpeed() {
        // max_lock_speed = 0 时没有可接受的分离速度
        assertTrue(CouplerCriteria.acceptsLock(0f, 0f, 0f, 0f, LOCK, ANGLE, 0f));
        assertFalse(CouplerCriteria.acceptsLock(0f, 0f, 0f, 1e-4f, LOCK, ANGLE, 0f));
    }

    @Test
    void rejectsNonFiniteSamples() {
        assertFalse(accepted(Float.NaN, 0f, 0f, 0f));
        assertFalse(accepted(0f, Float.POSITIVE_INFINITY, 0f, 0f));
        assertFalse(accepted(0f, 0f, Float.NaN, 0f));
        assertFalse(accepted(0f, 0f, 0f, Float.NEGATIVE_INFINITY));
    }

    @Test
    void captureForceFallsLinearlyToZeroAtTheCaptureRadius() {
        assertEquals(100f, CouplerCriteria.captureForce(0f, 0.04f, 100f), 1e-4f);
        assertEquals(50f, CouplerCriteria.captureForce(0.02f, 0.04f, 100f), 1e-4f);
        assertEquals(0f, CouplerCriteria.captureForce(0.04f, 0.04f, 100f), 1e-6f);
        assertEquals(0f, CouplerCriteria.captureForce(0.05f, 0.04f, 100f), 1e-6f);
    }

    @Test
    void captureForceIsZeroForDisabledOrInvalidParameters() {
        assertEquals(0f, CouplerCriteria.captureForce(0f, 0.04f, 0f));
        assertEquals(0f, CouplerCriteria.captureForce(0f, 0f, 100f));
        assertEquals(0f, CouplerCriteria.captureForce(Float.NaN, 0.04f, 100f));
        assertEquals(0f, CouplerCriteria.captureForce(0f, 0.04f, Float.POSITIVE_INFINITY));
    }
}
