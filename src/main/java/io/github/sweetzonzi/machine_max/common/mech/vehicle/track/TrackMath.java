package io.github.sweetzonzi.machine_max.common.mech.vehicle.track;

/**
 * 履带控制与驱动的纯数学部分，不依赖 Minecraft / Bullet 类型。
 *
 * <p>控制器和驱动共用这些公式，单元测试直接覆盖本类，避免为了验证数学而启动物理世界。</p>
 */
public final class TrackMath {
    /** 速度、功率分母的数值下限。 */
    public static final float EPSILON = 1e-4f;
    /** 意图输入的公共死区。 */
    public static final float INTENT_DEADZONE = 0.05f;
    /** A/D 拉杆的死区。 */
    public static final float LEVER_DEADZONE = 0.05f;
    /** 超过该拉杆行程后摘掉内侧动力，避免内侧制动与发动机互相拖拽。 */
    public static final float STEERING_CLUTCH_MIN_LEVER = 0.1f;
    /** 低于该车速的转向工况保持低挡，避免原地转向时被自动升挡打断。 */
    public static final float LOW_SPEED_SHIFT_HOLD = 2.5f;
    /** 低于该车速时，反向纵向输入视为换向请求而不是制动。 */
    public static final float BRAKE_INTENT_MIN_SPEED = 0.5f;
    /** 行进方向优先取油门符号的阈值。 */
    public static final float TRAVEL_THROTTLE_THRESHOLD = 0.05f;
    /** 行进方向在无油门时取车速符号的阈值。 */
    public static final float TRAVEL_SPEED_THRESHOLD = 0.3f;

    private TrackMath() {
    }

    /**
     * W/S 与当前车速反向且已超过低速换向区时，纵向轴表示制动意图。
     * 低速区反向输入仍按换向请求处理，与基类的 0.5 m/s 门限一致。
     */
    public static boolean isBrakeIntent(float longitudinal, float bodySpeed) {
        return Math.abs(longitudinal) > INTENT_DEADZONE
                && Math.abs(bodySpeed) >= BRAKE_INTENT_MIN_SPEED
                && longitudinal * bodySpeed < 0f;
    }

    /**
     * 加速意图：W/S 优先决定方向与模式，A/D 在加速时补足油门大小；制动意图返回 0。
     */
    public static float accelerationIntent(float longitudinal, float steering, float bodySpeed) {
        if (isBrakeIntent(longitudinal, bodySpeed)) return 0f;
        boolean hasLongitudinal = Math.abs(longitudinal) > INTENT_DEADZONE;
        float magnitude = hasLongitudinal
                ? Math.max(Math.abs(longitudinal), Math.abs(steering))
                : Math.abs(steering);
        if (magnitude < 0.01f) return 0f;
        float direction = hasLongitudinal
                ? Math.signum(longitudinal)
                : (Math.abs(bodySpeed) > 0.1f ? Math.signum(bodySpeed) : 1f);
        return Math.copySign(Math.min(magnitude, 1f), direction);
    }

    /** 制动意图只取 W/S 的大小和符号，A/D 不参与放大。 */
    public static float brakeIntent(float longitudinal, float bodySpeed) {
        return isBrakeIntent(longitudinal, bodySpeed) ? clamp(longitudinal, -1f, 1f) : 0f;
    }

    /** A/D 拉杆行程：0 中位，1 满拉杆；只取大小，不区分左右。 */
    public static float leverBrake(float steering) {
        if (!Float.isFinite(steering)) return 0f;
        float magnitude = Math.abs(steering);
        if (magnitude <= LEVER_DEADZONE) return 0f;
        return clamp((magnitude - LEVER_DEADZONE) / (1f - LEVER_DEADZONE), 0f, 1f);
    }

    /**
     * 转向离合：拉杆超过死区后内侧脱离动力路径，否则保持接合。
     *
     * <p>离合只有接合与断开两种状态，连续转向量由制动拉杆承担。</p>
     */
    public static boolean steeringClutchEngaged(float lever) {
        return lever <= STEERING_CLUTCH_MIN_LEVER;
    }

    /** 低速转向时保持低挡；直线行驶仍按原自动换挡逻辑。 */
    public static boolean holdLowGearForSteering(float lever, float bodySpeed) {
        return lever > 0f && Math.abs(bodySpeed) < LOW_SPEED_SHIFT_HOLD;
    }

    /** 行进方向：油门符号优先，其次车速，最后沿用上次方向。 */
    public static float travelDirection(float throttle, float bodySpeed, float lastDirection) {
        if (Math.abs(throttle) > TRAVEL_THROTTLE_THRESHOLD) return Math.signum(throttle);
        if (Math.abs(bodySpeed) > TRAVEL_SPEED_THRESHOLD) return Math.signum(bodySpeed);
        return lastDirection < -0.01f ? -1f : 1f;
    }

    /** 坦克式侧别：前进时 A 左、D 右；倒车时 A 右、D 左，A/D 的偏航方向不随挡位翻转。 */
    public static boolean leverBrakesLeft(float steering, float travelDirection) {
        return steering * travelDirection > 0f;
    }

    /** 基础制动在内侧的偏置。 */
    public static float innerBrake(float serviceBrake, float lever) {
        return clamp(serviceBrake + lever * (1f - serviceBrake), 0f, 1f);
    }

    /** 基础制动在外侧的释放。 */
    public static float outerBrake(float serviceBrake, float lever) {
        return clamp(serviceBrake * (1f - lever), 0f, 1f);
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
