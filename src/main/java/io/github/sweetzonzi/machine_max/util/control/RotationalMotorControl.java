package io.github.sweetzonzi.machine_max.util.control;

import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;

/** 限扭矩转动电机的目标计算，无可变状态。 */
public final class RotationalMotorControl {
    private RotationalMotorControl() {
    }

    /**
     * 牵引目标(rad/s)，沿输入轴速方向增加一个物理步的自由升速量。
     * torque 为非负扭矩预算，inverseInertia 为两端刚体在关节轴上的逆惯量之和。
     * 补偿与实测轮速无关；超出补偿后目标时由电机回拖。
     * 本方法只计算速度目标，扭矩预算、制动和设备限速由调用方处理。
     */
    public static float driveTarget(float speed, float torque, float inverseInertia, float timeStep) {
        if (!Float.isFinite(speed)) return 0f;
        if (!Float.isFinite(torque) || torque <= 0f
                || !Float.isFinite(inverseInertia) || inverseInertia <= 0f
                || !Float.isFinite(timeStep) || timeStep <= 0f) return speed;
        double target = speed + Math.signum(speed) * (double) torque * inverseInertia * timeStep;
        return (float) Math.clamp(target, -Float.MAX_VALUE, Float.MAX_VALUE);
    }

    /** 世界逆惯量张量在单位关节轴上的投影(1/(kg·m²))。 */
    public static float projectedInverseInertia(Matrix3f inverseInertia, Vector3f axis) {
        return axis.dot(inverseInertia.mult(axis, null));
    }
}
