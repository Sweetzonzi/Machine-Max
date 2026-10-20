package io.github.sweetzonzi.machine_max.common.mech.vehicle.track;

/**
 * 履带控制与驱动的纯数学部分，不依赖 Minecraft / Bullet 类型。
 *
 * <p>控制器和驱动共用这些公式，单元测试直接覆盖本类，避免为了验证数学而启动物理世界。</p>
 */
public final class TrackMath {
    /** 速度、功率分母的数值下限。 */
    public static final float EPSILON = 1e-4f;

    private TrackMath() {
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * 多输入驱动的反馈分摊：实测总转速按各 producer 最近一次输入转速的比例分配。
     *
     * @param actual       实测主动轮角速度
     * @param inputSpeed   该 producer 的输入转速
     * @param totalInput   所有参与汇合的 producer 输入转速之和
     * @param absTotal     所有参与汇合的 producer 输入转速绝对值之和
     * @param maxSpeed     反馈钳位上限
     */
    public static float feedbackForProducer(float actual,
                                            float inputSpeed,
                                            float totalInput,
                                            float absTotal,
                                            float maxSpeed) {
        if (!Float.isFinite(actual) || !Float.isFinite(inputSpeed)
                || !Float.isFinite(totalInput) || !Float.isFinite(absTotal)) {
            return 0f;
        }
        if (absTotal < EPSILON) {
            return 0f;
        }
        float feedback;
        if (Math.abs(totalInput) >= Math.max(EPSILON, 0.1f * absTotal)) {
            feedback = actual * inputSpeed / totalInput;
        } else {
            // 指令互相抵消（例如中心转向的两路），回退到指令分量，避免除零放大。
            feedback = inputSpeed;
        }
        return clamp(feedback, -maxSpeed, maxSpeed);
    }

    /**
     * 由功率和目标转速反推扭矩：P = τ × |ω|。
     */
    public static float torqueFromPower(float power, float speed, float minSpeedForTorque) {
        if (!Float.isFinite(power) || !Float.isFinite(speed)) {
            return 0f;
        }
        return power / Math.max(Math.abs(speed), Math.max(minSpeedForTorque, EPSILON));
    }

    /**
     * 单流制动差速：外侧保持基础速度，内侧按未达到的目标速度差制动。
     *
     * @param vBase       基础线速度
     * @param achieved    钳位后的可实现线速度
     * @param actualBrake 驾驶员制动量
     */
    public static float singleFlowBrake(float vBase, float achieved, float actualBrake) {
        float difference = Math.abs(vBase - achieved) / Math.max(Math.abs(vBase), 0.5f);
        return clamp(Math.max(actualBrake, difference), 0f, 1f);
    }

    /**
     * 单流线速度钳位。
     */
    public static float singleFlowAchieved(float desired, float vBase) {
        return vBase >= 0f
                ? clamp(desired, 0f, vBase)
                : clamp(desired, vBase, 0f);
    }

    /**
     * 横摆角速度上限：速度表、侧向加速度、最小转弯半径三者取小。
     *
     * @param tableYawRate          速度表给出的上限（rad/s）
     * @param maxLateralAcceleration 侧向加速度上限（m/s²）
     * @param minTurningRadius      最小转弯半径（m）
     * @param vBase                 基础线速度（m/s）
     */
    public static float maxYawRate(float tableYawRate,
                                   float maxLateralAcceleration,
                                   float minTurningRadius,
                                   float vBase) {
        float absBase = Math.abs(vBase);
        float lateralLimit = maxLateralAcceleration / Math.max(absBase, 0.5f);
        float radiusLimit = absBase / Math.max(minTurningRadius, EPSILON);
        return Math.max(0f, Math.min(tableYawRate, Math.min(lateralLimit, radiusLimit)));
    }
}
