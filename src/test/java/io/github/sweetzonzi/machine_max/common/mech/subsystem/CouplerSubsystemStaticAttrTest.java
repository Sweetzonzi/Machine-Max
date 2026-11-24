package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.BasicSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CouplerSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.util.data.Axis;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 锁扣型号字段的边界：非法组合在构造期拒绝，不等到物理步。
 */
class CouplerSubsystemStaticAttrTest {

    private static final BasicSubsystemStaticAttr.BasicAttr BASIC =
            new BasicSubsystemStaticAttr.BasicAttr(20f, true, false, false);

    private static CouplerSubsystemStaticAttr attr(float capture, float lock, float force,
                                                   float angle, float speed, float rearm) {
        return attr(capture, lock, force, angle, speed, rearm,
                CouplerSubsystemStaticAttr.DEFAULT_SEPARATION_IMPULSE, null, false, false);
    }

    private static CouplerSubsystemStaticAttr attr(float capture, float lock, float force,
                                                   float angle, float speed, float rearm,
                                                   float separationImpulse, Axis separationDirection,
                                                   boolean commandOnly, boolean applyOnCenter) {
        return new CouplerSubsystemStaticAttr(BASIC, capture, lock, force, angle, speed, rearm,
                separationImpulse, separationDirection, commandOnly, applyOnCenter,
                CouplerSubsystemStaticAttr.CouplerSoundAttr.DEFAULT);
    }

    private static DataResult<CouplerSubsystemStaticAttr> parse(String json) {
        return CouplerSubsystemStaticAttr.CODEC.codec()
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json));
    }

    private static void assertAccepted(DataResult<CouplerSubsystemStaticAttr> result) {
        assertTrue(result.result().isPresent(),
                () -> result.error().map(DataResult.Error::message).orElse(""));
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

    @Test
    void separationDefaultsToAnAutomaticDirectionOnRelease() {
        DataResult<CouplerSubsystemStaticAttr> result = parse(
                "{\"capture_distance\": 0.04, \"lock_distance\": 0.015, \"lock_angle_tolerance\": 3.0}");
        assertAccepted(result);
        CouplerSubsystemStaticAttr attr = result.result().orElseThrow();
        assertEquals(CouplerSubsystemStaticAttr.DEFAULT_SEPARATION_IMPULSE, attr.getSeparationImpulse());
        // 省略方向 = 按“宿主质心指向分离点”自动取；两档都是默认开
        assertNull(attr.getSeparationDirection());
        assertFalse(attr.isSeparationCommandOnly());
        assertFalse(attr.isSeparationApplyOnCenter());
    }

    @Test
    void zeroSeparationImpulseSwitchesTheReleaseOff() {
        DataResult<CouplerSubsystemStaticAttr> result = parse(
                "{\"capture_distance\": 0.04, \"lock_distance\": 0.015, \"lock_angle_tolerance\": 3.0,"
                        + " \"separation_impulse\": 0}");
        assertAccepted(result);
        assertEquals(0f, result.result().orElseThrow().getSeparationImpulse());
    }

    @Test
    void separationDirectionIsReadAsADirectionalAxis() {
        DataResult<CouplerSubsystemStaticAttr> result = parse(
                "{\"capture_distance\": 0.04, \"lock_distance\": 0.015, \"lock_angle_tolerance\": 3.0,"
                        + " \"separation_direction\": \"yp\", \"separation_command_only\": true,"
                        + " \"separation_apply_on_center\": true}");
        assertAccepted(result);
        CouplerSubsystemStaticAttr attr = result.result().orElseThrow();
        assertEquals(Axis.YP, attr.getSeparationDirection());
        assertTrue(attr.isSeparationCommandOnly());
        assertTrue(attr.isSeparationApplyOnCenter());
    }

    @Test
    void rejectsNegativeOrNonFiniteSeparationImpulse() {
        assertThrows(IllegalArgumentException.class,
                () -> attr(0.02f, 0.01f, 0f, 3f, 0.25f, 2f, -1f, null, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> attr(0.02f, 0.01f, 0f, 3f, 0.25f, 2f, Float.NaN, null, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> attr(0.02f, 0.01f, 0f, 3f, 0.25f, 2f, Float.POSITIVE_INFINITY, null, false, false));
    }

    @Test
    void rejectsARotationalAxisAsSeparationDirection() {
        // XR / YR / ZR 定不出方向，方向轴之外一律在装载期拒绝
        assertThrows(IllegalArgumentException.class,
                () -> attr(0.02f, 0.01f, 0f, 3f, 0.25f, 2f, 8f, Axis.XR, false, false));
        assertThrows(IllegalArgumentException.class,
                () -> parse("{\"capture_distance\": 0.04, \"lock_distance\": 0.015,"
                        + " \"lock_angle_tolerance\": 3.0, \"separation_direction\": \"zr\"}"));
    }
}
