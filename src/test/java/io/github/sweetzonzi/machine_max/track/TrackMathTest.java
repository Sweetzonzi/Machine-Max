package io.github.sweetzonzi.machine_max.track;

import io.github.sweetzonzi.machine_max.common.mech.signal.TrackControlSignal;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 履带控制/驱动的纯数学验证，不触碰物理世界。
 */
class TrackMathTest {

    @Test
    void singleInputFeedbackReturnsMeasuredSpeed() {
        assertEquals(7.5f, TrackMath.feedbackForProducer(7.5f, 12f, 12f, 12f, 120f), 1e-5f);
        assertEquals(-7.5f, TrackMath.feedbackForProducer(-7.5f, -12f, -12f, 12f, 120f), 1e-5f);
    }

    @Test
    void multiInputFeedbackSumsToMeasuredSpeed() {
        float actual = 9f;
        float total = 6f + 3f;
        float absTotal = 9f;
        float a = TrackMath.feedbackForProducer(actual, 6f, total, absTotal, 120f);
        float b = TrackMath.feedbackForProducer(actual, 3f, total, absTotal, 120f);
        assertEquals(actual, a + b, 1e-5f);
        assertEquals(6f, a, 1e-5f);
        assertEquals(3f, b, 1e-5f);
    }

    @Test
    void opposingInputsFallBackToCommandWithoutNaN() {
        float a = TrackMath.feedbackForProducer(4f, 5f, 0f, 10f, 120f);
        float b = TrackMath.feedbackForProducer(4f, -5f, 0f, 10f, 120f);
        assertEquals(5f, a, 1e-5f);
        assertEquals(-5f, b, 1e-5f);
        assertTrue(Float.isFinite(a) && Float.isFinite(b));
    }

    @Test
    void feedbackIsClampedToMaxSpeed() {
        float value = TrackMath.feedbackForProducer(1e6f, 1f, 1f, 1f, 120f);
        assertEquals(120f, value, 1e-5f);
    }

    @Test
    void singleFlowBrakesInnerTrackAndLeavesOuterFree() {
        assertEquals(5f, TrackMath.singleFlowAchieved(8f, 5f), 1e-5f);
        assertEquals(2f, TrackMath.singleFlowAchieved(2f, 5f), 1e-5f);
        assertEquals(1f, TrackMath.singleFlowBrake(5f, 0f, 0f), 1e-5f);
        assertEquals(0f, TrackMath.singleFlowBrake(5f, 5f, 0f), 1e-5f);
        assertEquals(0.8f, TrackMath.singleFlowBrake(5f, 1f, 0.4f), 1e-5f);
    }

    @Test
    void reverseSingleFlowClampsBetweenBaseAndZero() {
        assertEquals(-5f, TrackMath.singleFlowAchieved(-8f, -5f), 1e-5f);
        assertEquals(-2f, TrackMath.singleFlowAchieved(-2f, -5f), 1e-5f);
        assertEquals(1f, TrackMath.singleFlowBrake(-5f, 0f, 0f), 1e-5f);
    }

    @Test
    void yawRateIsLimitedBySpeedTableLateralAccelerationAndRadius() {
        float limited = TrackMath.maxYawRate(1.0f, 6f, 4f, 20f);
        assertEquals(6f / 20f, limited, 1e-5f);

        float radiusLimited = TrackMath.maxYawRate(1.0f, 100f, 4f, 2f);
        assertEquals(0.5f, radiusLimited, 1e-5f);

        assertEquals(0f, TrackMath.maxYawRate(1.0f, 6f, 4f, 0f), 1e-5f);
    }

    @Test
    void controlSignalClampsAllFields() {
        TrackControlSignal signal = new TrackControlSignal(2f, -1f, 0.5f);
        assertEquals(1f, signal.brakeControl(), 1e-5f);
        assertEquals(0f, signal.handBrakeControl(), 1e-5f);
        assertEquals(0.5f, signal.clutchEngagement(), 1e-5f);
    }
}
