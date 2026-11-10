package io.github.sweetzonzi.machine_max.common.mech.signal;

import com.jme3.math.Vector3f;

/**
 * 三轴旋转信号，单位弧度。<br>
 * x = pitch(俯仰), y = yaw(偏航), z = roll(横滚)。<br>
 * 用于武器控制器子系统 → 炮塔驱动子系统的目标角度传递，以及炮塔驱动子系统的角度反馈。
 */
public class RotationSignal extends Signal<Vector3f> {

    public static final RotationSignal IDLE = new RotationSignal(0, 0, 0);

    /** 世界稳定轴的运动前馈，单位 rad/s，使用与目标角相同的关节原生符号。 */
    public final float pitchFeedforward;
    public final float yawFeedforward;

    public RotationSignal(Vector3f value) {
        this(value, 0f, 0f);
    }

    public RotationSignal(Vector3f value, float pitchFeedforward, float yawFeedforward) {
        super(value);
        this.pitchFeedforward = pitchFeedforward;
        this.yawFeedforward = yawFeedforward;
    }

    /**
     * @param pitch 俯仰角（弧度），正值为抬头
     * @param yaw   偏航角（弧度），正值为右转（俯视顺时针）
     * @param roll  横滚角（弧度），暂未使用
     */
    public RotationSignal(float pitch, float yaw, float roll) {
        this(new Vector3f(pitch, yaw, roll));
    }

    public float getPitch() {
        return value.x;
    }

    public float getYaw() {
        return value.y;
    }

    public float getRoll() {
        return value.z;
    }

    @Override
    public String toString() {
        return "RotationSignal{pitch=" + value.x + ", yaw=" + value.y + ", roll=" + value.z + "}";
    }
}
