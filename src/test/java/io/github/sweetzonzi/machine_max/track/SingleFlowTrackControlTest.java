package io.github.sweetzonzi.machine_max.track;

import io.github.sweetzonzi.machine_max.common.mech.signal.TrackControlSignal;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleFlowTrackControlTest {
    private static final float EPS = 1e-5f;

    @Test
    void eightDirectionsBrakeTheSameSideInForwardAndReverse() {
        // longitudinal, steering (+left), signed power, left speed ratio, right speed ratio
        float[][] cases = {
                {1, 0, 1, 1, 1}, {1, -1, 1, 1, .5f}, {0, -1, 1, 1, 0},
                {-1, -1, -1, 1, .5f}, {-1, 0, -1, 1, 1},
                {-1, 1, -1, .5f, 1}, {0, 1, 1, 0, 1}, {1, 1, 1, .5f, 1}
        };
        for (float[] c : cases) {
            float power = TrackMath.singleFlowDriveIntent(c[0], c[1]);
            float turn = TrackMath.singleFlowTurnRatio(c[0], c[1]) * Math.signum(power);
            assertEquals(c[2], power, EPS);
            assertEquals(c[3], TrackMath.singleFlowSpeedRatio(-1f, -1f, 1f, turn, power), EPS);
            assertEquals(c[4], TrackMath.singleFlowSpeedRatio(1f, -1f, 1f, turn, power), EPS);
        }
        assertEquals(0f, TrackMath.singleFlowDriveIntent(0f, 0f));
        assertEquals(0f, TrackMath.singleFlowDriveIntent(0f, .001f));
    }

    @Test
    void reverseGearControlsSteeringDirectionEvenWithPositiveThrottle() {
        for (float steering : new float[]{-1f, 1f}) {
            for (boolean raw : new boolean[]{false, true}) {
                float previousYaw = 0f;
                for (float gearRatio : new float[]{2f, -2f}) {
                    float inputSpeed = 20f / gearRatio;
                    float direction = TrackMath.singleFlowDirection(inputSpeed, 0f, 1f);
                    assertEquals(Math.signum(gearRatio), direction, EPS);
                    float turn = (raw ? steering : TrackMath.singleFlowTurnRatio(1f, steering)) * direction;
                    float left = TrackMath.singleFlowSpeedRatio(-1f, -1f, 1f, turn, direction);
                    float right = TrackMath.singleFlowSpeedRatio(1f, -1f, 1f, turn, direction);
                    float slowRatio = raw ? 0f : .5f;
                    assertEquals(steering > 0f ? slowRatio : 1f, left, EPS);
                    assertEquals(steering < 0f ? slowRatio : 1f, right, EPS);
                    float yaw = inputSpeed * (right - left) / 2f;
                    if (gearRatio < 0f) assertEquals(-previousYaw, yaw, EPS);
                    previousYaw = yaw;
                }
            }
        }
        assertEquals(-1f, TrackMath.singleFlowDirection(0f, -2f, 1f), EPS);
        assertEquals(-1f, TrackMath.singleFlowDirection(0f, 0f, -1f), EPS);
    }

    @Test
    void joystickMagnitudeChangesPowerWithoutChangingTheTurnShape() {
        assertEquals(.3f, TrackMath.singleFlowDriveIntent(.3f, -.3f), EPS);
        assertEquals(-.5f, TrackMath.singleFlowTurnRatio(.3f, -.3f), EPS);
        float previous = 1f;
        for (int i = 0; i <= 100; i++) {
            float longitudinal = i / 100f;
            float ratio = Math.abs(TrackMath.singleFlowTurnRatio(longitudinal, -.5f));
            assertTrue(ratio <= previous + EPS);
            previous = ratio;
        }
    }

    @Test
    void outerAnchoringProducesTheFullRequestedSpeedDifference() {
        float outer = 4f;
        float turn = .75f;
        float left = outer * TrackMath.singleFlowSpeedRatio(-1f, -1f, 1f, -turn, 1f);
        float right = outer * TrackMath.singleFlowSpeedRatio(1f, -1f, 1f, -turn, 1f);
        assertEquals(4f, left, EPS);
        assertEquals(1f, right, EPS);
        assertEquals(outer * turn / 2f, (left - right) / 2f, EPS);
        assertEquals(.5f, TrackMath.singleFlowSpeedRatio(0f, -1f, 1f, -1f, 1f), EPS);
    }

    @Test
    void turnLimitsPermitStationaryLaunchAndRestrictFastTurns() {
        float yaw = (float) Math.toRadians(45f);
        assertEquals(1f, TrackMath.singleFlowTurnLimit(0f, 0f, 3f, yaw, 6f, 4f, 1f), EPS);
        assertEquals(0f, TrackMath.singleFlowTurnLimit(0f, 0f, 3f, 0f, 6f, 4f, 1f), EPS);
        assertEquals(0f, TrackMath.singleFlowTurnLimit(4f, 2f, 0f, yaw, 6f, 4f, 1f), EPS);
        assertEquals(3f / 5.5f, TrackMath.singleFlowTurnLimit(4f, 2f, 3f, 10f, 100f, 4f, 0f), EPS);
        assertEquals(.045f, TrackMath.singleFlowTurnLimit(20f, 20f, 3f, yaw, 6f, 4f, 1f), EPS);
    }

    @Test
    void steeringBrakeBudgetIsIndependentOfEngineDriveTorque() {
        for (float sign : new float[]{1f, -1f}) {
            TrackMath.MotorCommand slow = TrackMath.singleFlowMotor(20f * sign, 100_000f,
                    8f * sign, 4f * sign, 0f, 200f);
            assertEquals(4f * sign, slow.speed(), EPS);
            assertEquals(200f, slow.maxTorque(), EPS);
            assertEquals(0f, slow.driveTorque(), EPS);
            TrackMath.MotorCommand accelerate = TrackMath.singleFlowMotor(20f * sign, 100f,
                    2f * sign, 4f * sign, 0f, 200f);
            assertEquals(4f * sign, accelerate.speed(), EPS);
            assertEquals(100f, accelerate.maxTorque(), EPS);
            assertEquals(100f * sign, accelerate.driveTorque(), EPS);
        }
    }

    @Test
    void holdingAndServiceBrakingSuppressDriveAndCoastingCannotCreatePower() {
        TrackMath.MotorCommand hold = TrackMath.singleFlowMotor(20f, 100_000f, 0f, 0f, 0f, 200f);
        assertEquals(new TrackMath.MotorCommand(0f, 200f, 0f), hold);
        TrackMath.MotorCommand service = TrackMath.singleFlowMotor(20f, 100_000f, 8f, 4f, 50f, 200f);
        assertEquals(new TrackMath.MotorCommand(0f, 50f, 0f), service);
        TrackMath.MotorCommand coast = TrackMath.singleFlowMotor(0f, 0f, 2f, 4f, 0f, 200f);
        assertEquals(0f, coast.maxTorque(), EPS);
        TrackMath.MotorCommand free = TrackMath.singleFlowMotor(-20f, 100f, -8f, Float.NaN, 0f, 200f);
        assertEquals(new TrackMath.MotorCommand(-20f, 100f, -100f), free);
    }

    @Test
    void legacySignalsLeaveSpeedToThePowerTrainAndInvalidLimitsAreIgnored() {
        assertTrue(Float.isNaN(new TrackControlSignal(0f, 0f, 1f).targetSpeed()));
        assertTrue(Float.isNaN(new TrackControlSignal(0f, 0f, 1f, Float.POSITIVE_INFINITY).targetSpeed()));
        assertEquals(-3f, new TrackControlSignal(0f, 0f, 1f, -3f).targetSpeed(), EPS);
    }
}
