package io.github.sweetzonzi.machine_max.common.mech.control.aim;

/**
 * 单轴角度边界，单位为弧度。
 * 连续轴保留累计圈数；有限行程轴在边界截断，不能跨过止挡绕回另一端。
 *
 * @param continuous 是否允许连续回转
 * @param min 有限行程轴的下限
 * @param max 有限行程轴的上限
 */
public record AimAxisLimit(boolean continuous, double min, double max) {
    public static final AimAxisLimit CONTINUOUS = new AimAxisLimit(true, -Math.PI, Math.PI);
    public AimAxisLimit {
        if (!Double.isFinite(min) || !Double.isFinite(max) || min > max)
            throw new IllegalArgumentException("Invalid axis limits");
    }
    public double apply(double angle) { return continuous ? angle : Math.clamp(angle, min, max); }
    /** 连续轴取邻近参考角的等价分支；有限行程轴直接按机械边界截断。 */
    public double near(double angle, double branch) {
        return apply(continuous ? branch + Math.IEEEremainder(angle - branch, 2 * Math.PI) : angle);
    }
}
