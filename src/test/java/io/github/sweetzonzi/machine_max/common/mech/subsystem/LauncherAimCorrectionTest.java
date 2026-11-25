package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import com.jme3.math.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 校验发射器修正方向的偏角限制、角速度限制与回正行为。 */
class LauncherAimCorrectionTest {
    /** 计算方向相对局部炮轴的夹角，结果单位为度。 */
    private static float angleFromBore(Vector3f direction) {
        return (float) Math.toDegrees(Math.acos(Math.clamp(-direction.z, -1f, 1f)));
    }

    /** 目标超出偏角上限时，方向先受角速度限制再收敛到圆锥边界。 */
    @Test
    void correctionIsClampedAndTurnsAtTheConfiguredRate() {
        LauncherAimCorrection correction = new LauncherAimCorrection();
        Vector3f target = new Vector3f(1, 0, -1).normalize();

        Vector3f first = correction.update(target, 10f, 30f, 0.1f);
        assertEquals(3f, angleFromBore(first), 1e-3f);

        Vector3f settled = first;
        for (int tick = 0; tick < 10; tick++) {
            settled = correction.update(target, 10f, 30f, 0.1f);
        }
        assertEquals(10f, angleFromBore(settled), 1e-3f);
    }

    /** 零偏角关闭修正；撤去目标后，已有修正按配置角速度回到炮轴。 */
    @Test
    void zeroAngleDisablesCorrectionAndNoTargetReturnsAtTheSameRate() {
        LauncherAimCorrection correction = new LauncherAimCorrection();
        Vector3f target = new Vector3f(1, 0, -1).normalize();

        assertEquals(0f, angleFromBore(correction.update(target, 0f, 30f, 0.1f)), 1e-3f);
        Vector3f corrected = correction.update(target, 10f, 30f, 1f);
        assertEquals(10f, angleFromBore(corrected), 1e-3f);

        Vector3f returning = correction.update(new Vector3f(0, 0, -1), 10f, 30f, 0.1f);
        assertEquals(7f, angleFromBore(returning), 1e-3f);
    }

    /** 角速度上限为零时，修正方向保持在当前状态。 */
    @Test
    void zeroSpeedFreezesTheCurrentCorrection() {
        LauncherAimCorrection correction = new LauncherAimCorrection();
        Vector3f target = new Vector3f(1, 0, -1).normalize();
        Vector3f initial = correction.update(target, 10f, 30f, 0.1f);

        Vector3f frozen = correction.update(new Vector3f(0, 0, -1), 10f, 0f, 1f);
        assertEquals(initial.distance(frozen), 0f, 1e-6f);
    }
}
