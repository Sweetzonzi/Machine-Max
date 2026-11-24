package io.github.sweetzonzi.machine_max.common.mech.subsystem;

/**
 * 锁扣的纯判据：锁止窗口与合拢力。
 *
 * <p>只接收标量，不读世界、不读同步数据，因此可以在纯逻辑测试里覆盖边界；物理侧负责把端点
 * 几何换算成这里的距离、朝向误差与速度。</p>
 *
 * <p>不放进 {@link CouplerSubsystem}：该类经 {@link AbstractSubsystem} 的静态初始化注册
 * {@code SynchedEntityData} 访问器，加载即触发 Minecraft 注册表；一旦判据住在那里，普通 JUnit
 * 连调用静态方法都会在 {@code <clinit>} 阶段失败（{@code Not bootstrapped}），边界只能退化成
 * GameTest。</p>
 */
final class CouplerCriteria {

    private CouplerCriteria() {
    }

    /**
     * 当前样本是否允许提交锁止。
     *
     * @param distance       两端点距离（米）
     * @param angleError     相向法线误差（弧度）
     * @param relativeSpeed  两端点相对速率（米/秒）
     * @param separationSpeed 沿端点连线的分离速率（米/秒，正值表示相互远离）
     * @param lockDistance   锁止窗口（米）
     * @param angleTolerance 允许的朝向误差（弧度）
     * @param maxSpeed       允许的相对速率（米/秒）
     * @return 非有限样本、超出任一门槛或明显向外反弹时为 {@code false}
     */
    static boolean acceptsLock(float distance, float angleError, float relativeSpeed, float separationSpeed,
                               float lockDistance, float angleTolerance, float maxSpeed) {
        if (!Float.isFinite(distance) || !Float.isFinite(angleError)
                || !Float.isFinite(relativeSpeed) || !Float.isFinite(separationSpeed)) {
            return false;
        }
        if (distance > lockDistance) return false;
        if (angleError > angleTolerance) return false;
        if (relativeSpeed > maxSpeed) return false;
        // 数值噪声内的分离不算反弹；上限取 min(0.01 m/s, max_lock_speed)。
        float noise = Math.min(0.01f, maxSpeed);
        return separationSpeed <= noise;
    }

    /**
     * 沿端点连线的合拢力大小。
     *
     * @param distance        两端点距离（米）
     * @param captureDistance 吸合范围（米）
     * @param maxForce        合拢力上限（N）
     * @return 距离在范围外、参数非法或上限为零时为 {@code 0}
     */
    static float captureForce(float distance, float captureDistance, float maxForce) {
        if (!Float.isFinite(distance) || !Float.isFinite(captureDistance) || !Float.isFinite(maxForce)) return 0f;
        if (captureDistance <= 0f || maxForce <= 0f) return 0f;
        if (distance < 0f || distance >= captureDistance) return 0f;
        return maxForce * Math.clamp(1f - distance / captureDistance, 0f, 1f);
    }
}
