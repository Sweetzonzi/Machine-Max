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

    /** 意图模式：转向也请求动力；无纵向输入时采用前进支点转向。 */
    public static float singleFlowDriveIntent(float longitudinal, float steering) {
        float magnitude = Math.max(Math.abs(longitudinal), Math.abs(steering));
        if (magnitude < 0.01f) return 0f;
        return Math.copySign(Math.min(magnitude, 1f), longitudinal < -0.01f ? -1f : 1f);
    }

    /** 八方向与摇杆共用的差速比例：纯转向为 1，等量纵向与转向输入为 1/2。 */
    public static float singleFlowTurnRatio(float longitudinal, float steering) {
        float sum = Math.abs(longitudinal) + Math.abs(steering);
        return sum < 0.01f ? 0f : steering / sum;
    }

    /** 动力方向取主动轮输入轴的符号（包含倒挡）；无输入转速时依次使用车速和动力意图。 */
    public static float singleFlowDirection(float inputSpeed, float bodySpeed, float driveIntent) {
        if (Math.abs(inputSpeed) > 0.01f) return Math.signum(inputSpeed);
        if (Math.abs(bodySpeed) > 0.1f) return Math.signum(bodySpeed);
        return driveIntent < -0.01f ? -1f : 1f;
    }

    /** 外侧锚定的带速比例；turnRatio 表示实际偏航方向（正左转），direction 表示动力方向。 */
    public static float singleFlowSpeedRatio(float x, float minX, float maxX,
                                              float turnRatio, float direction) {
        float width = maxX - minX;
        if (width < 0.1f) return 1f;
        float inward = turnRatio * direction >= 0f ? (maxX - x) / width : (x - minX) / width;
        return clamp(1f - Math.abs(turnRatio) * inward, 0f, 1f);
    }

    /**
     * 单流差速比例上限。outerSpeed 是外侧带速，bodySpeed 用于侧向加速度约束。
     * 支点转向的几何半径是 width/2；纵向意图占优时使用配置的车体中心最小半径。
     */
    public static float singleFlowTurnLimit(float outerSpeed, float bodySpeed, float width,
                                            float tableYawRate, float maxLateralAcceleration,
                                            float minTurningRadius, float pivotWeight) {
        if (width < 0.1f) return 0f;
        float referenceSpeed = Math.max(Math.abs(outerSpeed), 0.5f);
        float radius = Math.max(width / 2f, minTurningRadius);
        radius += (width / 2f - radius) * clamp(pivotWeight, 0f, 1f);
        float yawLimit = Math.min(Math.max(tableYawRate, 0f),
                Math.max(maxLateralAcceleration, 0f) / Math.max(Math.abs(bodySpeed), 0.5f));
        return clamp(Math.min(width * yawLimit / referenceSpeed, width / (radius + width / 2f)), 0f, 1f);
    }

    /** 关节速度伺服的目标与扭矩预算；driveTorque 是有符号的驱动力矩输出。 */
    public record MotorCommand(float speed, float maxTorque, float driveTorque) {
    }

    /**
     * 单流主动轮伺服（角速度单位 rad/s，前进为正）。制动优先于驱动；
     * 转向限速超速时仅使用制动预算，低于目标时仅使用动力链预算。
     */
    public static MotorCommand singleFlowMotor(float sourceSpeed, float sourceTorque, float actualSpeed,
                                               float speedLimit, float brakeTorque, float steeringBrakeTorque) {
        float engineBrake = Math.max(-sourceTorque, 0f);
        if (brakeTorque > 0f || engineBrake > 0f) {
            return new MotorCommand(0f, brakeTorque + engineBrake, 0f);
        }
        if (Float.isFinite(speedLimit)) {
            if (Math.abs(speedLimit) < EPSILON) {
                return new MotorCommand(0f, Math.max(steeringBrakeTorque, 0f), 0f);
            }
            float direction = Math.signum(speedLimit);
            float target = direction * Math.min(Math.abs(speedLimit),
                    sourceSpeed * direction > 0f ? Math.abs(sourceSpeed) : 0f);
            if (actualSpeed * direction > Math.abs(speedLimit)) {
                return new MotorCommand(speedLimit, Math.max(steeringBrakeTorque, 0f), 0f);
            }
            return new MotorCommand(target, Math.max(sourceTorque, 0f),
                    Math.signum(target) * Math.max(sourceTorque, 0f));
        }
        return new MotorCommand(sourceSpeed, Math.max(sourceTorque, 0f),
                Math.signum(sourceSpeed) * Math.max(sourceTorque, 0f));
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

}
