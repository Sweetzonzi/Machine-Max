package io.github.sweetzonzi.machine_max.track;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 开环拉杆制动的纯数学验证：W/S 加速/制动意图、A/D 拉杆、制动偏置与坦克式侧别。
 */
class TrackLeverBrakeTest {
    private static final float EPS = 1e-5f;

    @Test
    void brakeIntentRequiresOppositeTravelOutsideTheLowSpeedRegion() {
        assertTrue(TrackMath.isBrakeIntent(-0.1f, 2f));
        assertTrue(TrackMath.isBrakeIntent(0.1f, -2f));
        assertTrue(TrackMath.isBrakeIntent(-1f, 0.5f));
        assertFalse(TrackMath.isBrakeIntent(0.1f, 2f));
        assertFalse(TrackMath.isBrakeIntent(-0.1f, -2f));
        assertFalse(TrackMath.isBrakeIntent(-0.1f, 0.4f));
        assertFalse(TrackMath.isBrakeIntent(-0.01f, 2f));
        assertFalse(TrackMath.isBrakeIntent(0f, 2f));
    }

    @Test
    void accelerationIntentCombinesSteeringButYieldsToBraking() {
        assertEquals(1f, TrackMath.accelerationIntent(0f, 1f, 2f), EPS);
        assertEquals(-1f, TrackMath.accelerationIntent(0f, 1f, -2f), EPS);
        assertEquals(1f, TrackMath.accelerationIntent(0f, 1f, 0f), EPS);

        assertEquals(1f, TrackMath.accelerationIntent(0.1f, 1f, 2f), EPS);
        assertEquals(0f, TrackMath.accelerationIntent(0.1f, 1f, -2f), EPS);
        assertEquals(0f, TrackMath.accelerationIntent(-0.1f, 1f, 2f), EPS);
        assertEquals(-1f, TrackMath.accelerationIntent(-0.1f, 1f, -2f), EPS);

        assertEquals(0.1f, TrackMath.accelerationIntent(0.1f, 0f, 2f), EPS);
        assertEquals(0.5f, TrackMath.accelerationIntent(0f, 0.5f, 2f), EPS);
        assertEquals(1f, TrackMath.accelerationIntent(0.03f, 1f, 2f), EPS);
    }

    @Test
    void brakeIntentUsesOnlyTheLongitudinalAxis() {
        assertEquals(-0.1f, TrackMath.brakeIntent(-0.1f, 2f), EPS);
        assertEquals(0.1f, TrackMath.brakeIntent(0.1f, -2f), EPS);
        assertEquals(0f, TrackMath.brakeIntent(-0.1f, -2f), EPS);
        assertEquals(0f, TrackMath.brakeIntent(0.1f, 2f), EPS);
        assertEquals(0f, TrackMath.brakeIntent(-0.1f, 0.4f), EPS);
        assertEquals(0f, TrackMath.brakeIntent(0f, 2f), EPS);
    }

    @Test
    void leverBrakeHasDeadzoneAndReachesFullTravelAtFullSteering() {
        assertEquals(0f, TrackMath.leverBrake(0f), EPS);
        assertEquals(0f, TrackMath.leverBrake(0.05f), EPS);
        assertEquals((0.5f - 0.05f) / 0.95f, TrackMath.leverBrake(0.5f), EPS);
        assertEquals(1f, TrackMath.leverBrake(1f), EPS);
        assertEquals(TrackMath.leverBrake(0.5f), TrackMath.leverBrake(-0.5f), EPS);
    }

    @Test
    void innerAndOuterBrakeFollowTheBiasTable() {
        assertEquals(0f, TrackMath.innerBrake(0f, 0f), EPS);
        assertEquals(0f, TrackMath.outerBrake(0f, 0f), EPS);
        assertEquals(0.5f, TrackMath.innerBrake(0f, 0.5f), EPS);
        assertEquals(0f, TrackMath.outerBrake(0f, 0.5f), EPS);
        assertEquals(0.5f, TrackMath.innerBrake(0.5f, 0f), EPS);
        assertEquals(0.5f, TrackMath.outerBrake(0.5f, 0f), EPS);
        assertEquals(0.75f, TrackMath.innerBrake(0.5f, 0.5f), EPS);
        assertEquals(0.25f, TrackMath.outerBrake(0.5f, 0.5f), EPS);
        assertEquals(1f, TrackMath.innerBrake(1f, 1f), EPS);
        assertEquals(0f, TrackMath.outerBrake(1f, 1f), EPS);

        assertEquals(1f, TrackMath.innerBrake(0.1f, 1f), EPS);
        assertEquals(0f, TrackMath.outerBrake(0.1f, 1f), EPS);
        assertEquals(0.55f, TrackMath.innerBrake(0.1f, 0.5f), EPS);
        assertEquals(0.05f, TrackMath.outerBrake(0.1f, 0.5f), EPS);
        assertEquals(0.19f, TrackMath.innerBrake(0.1f, 0.1f), EPS);
        assertEquals(0.09f, TrackMath.outerBrake(0.1f, 0.1f), EPS);
    }

    @Test
    void steeringClutchDropsTheInnerTrackOnlyPastTheLeverDeadzone() {
        assertEquals(1f, TrackMath.steeringClutch(0f), EPS);
        assertEquals(1f, TrackMath.steeringClutch(0.1f), EPS);
        assertEquals(0f, TrackMath.steeringClutch(0.11f), EPS);
        assertEquals(0f, TrackMath.steeringClutch(0.5f), EPS);
        assertEquals(0f, TrackMath.steeringClutch(1f), EPS);
    }

    @Test
    void lowSpeedSteeringHoldsTheLowGearUntilTheVehicleIsMoving() {
        assertTrue(TrackMath.holdLowGearForSteering(0.5f, 0.9f));
        assertTrue(TrackMath.holdLowGearForSteering(0.5f, 2.4f));
        assertFalse(TrackMath.holdLowGearForSteering(0.5f, 2.6f));
        assertFalse(TrackMath.holdLowGearForSteering(0f, 0.9f));
        assertTrue(TrackMath.holdLowGearForSteering(0.5f, -0.9f));
    }

    @Test
    void travelDirectionPrefersThrottleThenSpeedThenThePreviousDirection() {
        assertEquals(1f, TrackMath.travelDirection(0.3f, -5f, -1f), EPS);
        assertEquals(-1f, TrackMath.travelDirection(-0.3f, 5f, 1f), EPS);
        assertEquals(-1f, TrackMath.travelDirection(0f, -5f, 1f), EPS);
        assertEquals(1f, TrackMath.travelDirection(0f, 0.2f, 1f), EPS);
        assertEquals(-1f, TrackMath.travelDirection(0f, 0.2f, -1f), EPS);
    }

    @Test
    void leverBrakesTheTankLikeInnerSideInForwardAndReverse() {
        assertTrue(TrackMath.leverBrakesLeft(1f, 1f));
        assertFalse(TrackMath.leverBrakesLeft(1f, -1f));
        assertFalse(TrackMath.leverBrakesLeft(-1f, 1f));
        assertTrue(TrackMath.leverBrakesLeft(-1f, -1f));
        assertFalse(TrackMath.leverBrakesLeft(0f, 1f));
    }

    @Test
    void fullLeverLocksTheInnerTrackRegardlessOfServiceBrake() {
        assertEquals(1f, TrackMath.innerBrake(0.1f, 1f), EPS);
        assertEquals(0f, TrackMath.outerBrake(0.1f, 1f), EPS);
        assertEquals(1f, TrackMath.innerBrake(0.5f, 1f), EPS);
        assertEquals(0f, TrackMath.outerBrake(0.5f, 1f), EPS);
    }
}
