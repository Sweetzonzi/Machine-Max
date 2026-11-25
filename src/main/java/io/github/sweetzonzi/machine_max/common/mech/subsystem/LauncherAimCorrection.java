package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import jme3utilities.math.MyQuaternion;

/** 单发射器局部空间内的瞄准修正，限制最大偏角与最大转动角速度。 */
final class LauncherAimCorrection {
    /** locator 局部空间中的炮轴前向。 */
    private static final Vector3f BORE_FORWARD = new Vector3f(0, 0, -1);
    /** 当前实际修正方向，始终以 locator 局部空间的单位向量保存。 */
    private Vector3f direction = BORE_FORWARD.clone();

    /**
     * 将期望方向限制在炮轴圆锥内，再按角速度上限推进当前修正方向。
     *
     * @param desiredDirection 期望出射方向，发射器局部空间
     * @param maxAngleDeg 允许偏离炮轴的最大角度，单位为度
     * @param maxSpeedDegPerSec 修正角速度上限，单位为度/秒
     * @param dt 本次物理步的时长，单位为秒
     * @return 更新后的单位方向，发射器局部空间
     */
    Vector3f update(Vector3f desiredDirection, float maxAngleDeg, float maxSpeedDegPerSec, float dt) {
        Vector3f target = clampToCone(BORE_FORWARD, desiredDirection, maxAngleDeg);
        float maxStep = (float) Math.toRadians(Math.max(0f, maxSpeedDegPerSec) * Math.max(0f, dt));
        direction = rotateToward(direction, target, maxStep);
        return direction.clone();
    }

    /** 将期望方向投影到以炮轴为中心、半角不超过配置上限的圆锥边界。 */
    private static Vector3f clampToCone(Vector3f forward, Vector3f preferred, float maxAngleDeg) {
        Vector3f normalizedForward = normalizedOrForward(forward);
        Vector3f normalizedPreferred = normalizedOrForward(preferred);
        float angle = angleBetween(normalizedForward, normalizedPreferred);
        float maxAngle = (float) Math.toRadians(Math.clamp(maxAngleDeg, 0f, 180f));
        return angle <= maxAngle ? normalizedPreferred : rotateToward(normalizedForward, normalizedPreferred, maxAngle);
    }

    /** 沿两方向之间的最短大圆弧转动，最多前进指定弧度。 */
    private static Vector3f rotateToward(Vector3f from, Vector3f to, float maxAngle) {
        Vector3f normalizedFrom = normalizedOrForward(from);
        Vector3f normalizedTo = normalizedOrForward(to);
        float angle = angleBetween(normalizedFrom, normalizedTo);
        if (angle <= maxAngle || angle < 1e-7f) return normalizedTo;

        Vector3f axis = normalizedFrom.cross(normalizedTo, null);
        if (axis.lengthSquared() < 1e-10f) {
            Vector3f reference = Math.abs(normalizedFrom.x) < 0.9f
                    ? new Vector3f(1, 0, 0)
                    : new Vector3f(0, 1, 0);
            axis = normalizedFrom.cross(reference, null);
        }
        axis = axis.normalize();
        Quaternion rotation = new Quaternion().fromAngleNormalAxis(maxAngle, axis);
        return normalizedOrForward(MyQuaternion.rotate(rotation, normalizedFrom, null));
    }

    /** 计算两个单位方向之间的夹角，并钳制点积以避免浮点误差越过反余弦定义域。 */
    private static float angleBetween(Vector3f first, Vector3f second) {
        return (float) Math.acos(Math.clamp(first.dot(second), -1f, 1f));
    }

    /** 归一化有效向量；零向量退回炮轴方向，避免产生 NaN。 */
    private static Vector3f normalizedOrForward(Vector3f value) {
        if (value == null || value.lengthSquared() < 1e-12f) return BORE_FORWARD.clone();
        return value.normalize();
    }
}
