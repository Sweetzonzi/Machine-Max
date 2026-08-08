package io.github.sweetzonzi.machine_max.util;

import lombok.Getter;
import net.minecraft.world.phys.Vec3;

/**
 * 通用二阶弹簧阻尼器（mass=1 简化模型）。
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li>{@link #applyImpulse} — 外部冲量（投射物命中），直接加在 velocity 上</li>
 *   <li>{@link #setTargetPosition} — 设置追赶目标（载具相机），目标移动时自动转化惯性</li>
 * </ul>
 * <p>
 * 模型：offset'' = -k·offset - d·offset' （mass=1 约简，impulse 语义为速度增量），
 * 积分使用半隐式欧拉法。
 * <p>
 * dt 使用 tickCount + partialTick 差分计算，保证游戏暂停时 dt=0，避免弹簧突变。
 */
public class SpringDamper {

    private final float k;                // 刚度
    private final float d;                // 阻尼系数

    @Getter
    private Vec3 offset = Vec3.ZERO;      // 当前偏移量
    private Vec3 velocity = Vec3.ZERO;    // 当前速度

    // 追赶模式状态
    private Vec3 lastTargetPos = null;
    private float maxLag = Float.MAX_VALUE;

    /**
     * 构造弹簧阻尼器。d == 2·sqrt(k) 时为临界阻尼（无余振）。
     *
     * @param stiffness 刚度 k
     * @param damping   阻尼系数 d
     */
    public SpringDamper(float stiffness, float damping) {
        this.k = stiffness;
        this.d = damping;
    }

    /**
     * 施加脉冲冲量（投射物命中），直接加在 velocity 上。
     * mass=1 简化模型下 impulse 即速度增量。
     */
    public void applyImpulse(Vec3 impulse) {
        velocity = velocity.add(impulse);
    }

    /**
     * 设置追赶目标位置（载具相机惯性追赶）。
     * 目标移动时自动将位移差转化为 offset 增量——目标前进，相机滞后。
     * 无 dt 参数，纯粹记录状态。
     *
     * @param worldPos 目标世界位置
     * @param maxLag   最大滞后距离
     */
    public void setTargetPosition(Vec3 worldPos, float maxLag) {
        if (lastTargetPos != null) {
            // 目标移动 → 相机落后 → offset 向反方向增长
            Vec3 delta = worldPos.subtract(lastTargetPos);
            offset = offset.subtract(delta);
        }
        lastTargetPos = worldPos;
        this.maxLag = maxLag;
    }

    /**
     * 每帧积分推进。应在外部调用 {@link #setTargetPosition} 之后执行，
     * 确保追赶目标位移已转化为 offset 后再做弹簧力计算。
     *
     * @param dt 本帧时间步长（秒）
     */
    public void tick(float dt) {
        // 钳制最大滞后距离
        double len = offset.length();
        if (len > maxLag && len > 0) {
            offset = offset.scale(maxLag / len);
        }
        // 弹簧恢复力 + 阻尼：acceleration = -k·offset - d·velocity
        Vec3 acceleration = offset.scale(-k).add(velocity.scale(-d));
        velocity = velocity.add(acceleration.scale(dt));
        offset = offset.add(velocity.scale(dt));
    }

    /**
     * 重置弹簧状态为零。离开载具时必须调用，防止下次上车时残留 offset 导致瞬跳。
     */
    public void reset() {
        offset = Vec3.ZERO;
        velocity = Vec3.ZERO;
        lastTargetPos = null;
    }
}
