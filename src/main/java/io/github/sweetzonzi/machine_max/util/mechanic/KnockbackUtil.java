package io.github.sweetzonzi.machine_max.util.mechanic;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * 把物理冲量施加为原版实体的击退，统一三条通路（爆炸、投射物命中、载具碰撞）的单位口径与抗性处理。
 *
 * <p><b>单位口径</b>：冲量 {@code J} 量纲为 N·s（= kg·m/s），实体质量 {@code m} 为 kg，
 * 故 {@code Δv = J / m} 得到 m/s；而 {@code Entity#push} / {@code LivingEntity#knockback} 的入参是
 * delta-movement 的<b>每 tick 增量</b>（方块/tick，主线程 20 tps），因此再乘 {@link #PER_TICK}（1/20）折算。
 * 物理侧刚体（Bullet）走 SI 的 m/s，不经过本类。</p>
 *
 * <p><b>抗性</b>：生物按原版 {@code LivingEntity#knockback} 的口径乘上
 * {@code (1 - KNOCKBACK_RESISTANCE)}。这一步收在 {@link #applyImpulse}：
 * {@link #applyVelocity} 只做纯速度施加，不碰质量也不碰抗性。与原版唯一的差别是方向取完整三维，
 * 而非只有水平分量。</p>
 *
 * <p><b>刻意不照搬的两点</b>：原版 {@code LivingEntity#knockback} 还会把既有水平速度减半、
 * 并在着地时对竖直分量取 {@code min(0.4, ...)}——那是近战击退的语义，用于冲量模型会让
 * 竖直分量失真，故不采用。</p>
 *
 * <p><b>线程</b>：主线程（直接写 delta-movement）。</p>
 */
public final class KnockbackUtil {

    /** m/s → 方块/tick 的换算（主线程 20 tps）。 */
    public static final double PER_TICK = 1.0 / 20.0;

    /** 工具类，不实例化。 */
    private KnockbackUtil() {
    }

    /**
     * 把冲量 {@code J}（N·s）沿 {@code direction} 施加到实体上。
     *
     * <p>质量由 {@link MassUtil#getEntityMass(Entity)} 从 AABB 体积折算，
     * 并在此处应用生物的击退抗性折减，最后交给 {@link #applyVelocity} 做纯速度施加。</p>
     *
     * @param entity    目标实体
     * @param impulse   冲量大小（N·s），≤ 0 时不施加
     * @param direction 施加方向，不必单位化；长度 ≈ 0 时不施加
     */
    public static void applyImpulse(Entity entity, double impulse, Vec3 direction) {
        if (!(impulse > 0.0)) return;
        double mass = MassUtil.getEntityMass(entity);
        if (!(mass > 0.0)) return;
        // 物理速度增量 Δv = J / m（m/s），再折算为 delta-movement 的每 tick 增量
        double speed = impulse / mass * PER_TICK;
        // 原版 LivingEntity#knockback 的抗性折减：只有生物持有该属性
        if (entity instanceof LivingEntity living) {
            speed *= 1.0 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        }
        applyVelocity(entity, speed, direction);
    }

    /**
     * 直接施加一个速度增量（方块/tick）到 delta-movement，<b>不做质量与抗性修正</b>。
     *
     * @param speed     速度增量大小（方块/tick），≤ 0 时不施加
     * @param direction 方向，不必单位化；长度 ≈ 0 时不施加
     */
    public static void applyVelocity(Entity entity, double speed, Vec3 direction) {
        if (!(speed > 0.0)) return;
        if (direction.lengthSqr() < 1e-8) return;
        entity.setDeltaMovement(entity.getDeltaMovement().add(direction.normalize().scale(speed)));
        entity.hasImpulse = true;
    }
}
