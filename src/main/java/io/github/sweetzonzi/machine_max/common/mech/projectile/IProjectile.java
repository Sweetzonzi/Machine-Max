package io.github.sweetzonzi.machine_max.common.mech.projectile;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageApi;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;
import io.github.sweetzonzi.ballistics_framework.api.BFHurtTarget;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 投射物公共接口。
 * <p>
 * 定义质点投射物（{@link PointProjectile}）和刚体投射物（{@link RigidProjectile}）
 * 共有的物理状态协议、弹道参数协议和速度-伤害模型。
 * 所有弹道参数的默认实现均委托至 {@link ProjectileType}，实现数据驱动的设计。
 * <p>
 * 速度-伤害模型采用幂函数（德马尔式）：
 * <pre>
 * effective = base × (currentSpeed / baseVelocity)^coefficient
 * </pre>
 * 系数为 0 时退化为常数值（与速度无关）。
 * <p>
 * 命中时调用 {@link #dealDamage(BFHurtTarget, Vec3, Vec3)} 直接向
 * BallisticsFramework 协议目标发起伤害，无需中间 Entity 层桥接。
 */
public interface IProjectile {

    /**
     * @return 投射物当前世界坐标（JME Vector3f）
     */
    Vector3f getPosition();

    /**
     * @return 投射物当前速度矢量（JME Vector3f，单位 m/s）
     */
    Vector3f getVelocity();

    /**
     * @return 投射物前方向量（模型朝向）
     */
    Vector3f getFrontVector();

    /**
     * @return 当前速率（速度矢量的模长，单位 m/s）
     */
    default float getSpeed() {
        return getVelocity().length();
    }

    /**
     * 获取归一化的运动方向。
     * 当速度接近零时回退到 {@link #getFrontVector()} 作为方向。
     *
     * @return 归一化方向矢量
     */
    default Vector3f getDirection() {
        Vector3f vel = getVelocity();
        if (vel.lengthSquared() < 1e-12f) return getFrontVector();
        return vel.normalize();
    }

    /**
     * @return 此投射物关联的类型定义（含全部弹道参数）
     */
    ProjectileType getProjectileType();

    // ========== 弹道参数快捷委托（全部委托至 ProjectileType） ==========

    default float getMass()            { return getProjectileType().getMass(); }
    default float getGravityFactor()   { return getProjectileType().getGravityFactor(); }
    default float getDragFactor()      { return getProjectileType().getDragFactor(); }
    default float getBaseVelocity()    { return getProjectileType().getBaseVelocity(); }
    default float getBasePenetration() { return getProjectileType().getBasePenetration(); }
    default float getBaseDamage()      { return getProjectileType().getBaseDamage(); }
    default float getBaseAccuracyMil() { return getProjectileType().getBaseAccuracyMil(); }
    default float getPenetrationVelocityCoefficient() {
        return getProjectileType().getPenetrationVelocityCoefficient();
    }
    default float getDamageVelocityCoefficient() {
        return getProjectileType().getDamageVelocityCoefficient();
    }
    default float getRadius()          { return getProjectileType().getRadius(); }

    // ========== 生命周期 ==========

    /**
     * @return 投射物是否仍活跃（未命中、未超时、未移除）
     */
    boolean isAlive();

    /**
     * @return 当前剩余存活 tick 数
     */
    int getLifetime();

    /**
     * @return 最大存活 tick 数（来自 {@link ProjectileType#getMaxLifetimeTicks()}）
     */
    int getMaxLifetime();

    /**
     * 标记投射物已命中。
     * 调用后 {@link #isAlive()} 应返回 false，管理器将在下次 tick 中清理此投射物。
     */
    void markHit();

    Level getLevel();

    // ========== 速度-伤害模型 ==========

    /**
     * 计算当前速度下的有效穿深（mm RHA）。
     * <p>
     * 公式：{@code effective = basePenetration × (speed / baseVelocity)^coefficient}
     * <p>
     * 当 {@code penetrationVelocityCoefficient} 为 0 时退化到基准值。
     *
     * @return 有效穿深（mm RHA）
     */
    default float calculateCurrentPenetration() {
        float coeff = getPenetrationVelocityCoefficient();
        if (Math.abs(coeff) < 1e-6f) return getBasePenetration();
        float baseV = Math.max(getBaseVelocity(), 1e-6f);
        return getBasePenetration() * (float) Math.pow(getSpeed() / baseV, coeff);
    }

    /**
     * 计算当前速度下的有效伤害值。
     * <p>
     * 公式：{@code effective = baseDamage × (speed / baseVelocity)^coefficient}
     * <p>
     * 当 {@code damageVelocityCoefficient} 为 0 时退化到基准值。
     *
     * @return 有效伤害值
     */
    default float calculateCurrentDamage() {
        float coeff = getDamageVelocityCoefficient();
        if (Math.abs(coeff) < 1e-6f) return getBaseDamage();
        float baseV = Math.max(getBaseVelocity(), 1e-6f);
        return getBaseDamage() * (float) Math.pow(getSpeed() / baseV, coeff);
    }

    // ========== 伤害发起 ==========

    /**
     * 向 {@link BFHurtTarget} 直接发起 BallisticsFramework 协议伤害。
     * <p>
     * 由于 {@code DestroyableObject} 自身实现了 {@code BFHurtTarget}，
     * 命中 SubPart 时可直接将其作为 {@code target} 调用，
     * BallisticsFramework 自动走穿甲判定管线（getRHA → modifyPenetration →
     * resolvePenetration → calculateFinalDamage → hurt）。
     * <p>
     * 阶段一使用 {@code target.getBFEntity().damageSources().generic()} 构造 DamageSource，
     * 不追踪发射者信息。
     *
     * @param target    协议伤害目标（SubPart / Entity 等 BFHurtTarget 实现）
     * @param hitPoint  命中点世界坐标（MC Vec3）
     * @param hitNormal 命中面法线（MC Vec3）
     * @return 实际造成的伤害量（协议层计算值，可能被原版护甲二次减免）
     */
    default float dealDamage(BFHurtTarget target, Vec3 hitPoint, Vec3 hitNormal) {
        DamageSource source = getLevel().damageSources().generic();
        BFDamageContext ctx = BFDamageContext.builder()
            .source(source)
            .baseDamage(calculateCurrentDamage())
            .penetration(calculateCurrentPenetration())
            .hitVelocity(new Vec3(getVelocity().x, getVelocity().y, getVelocity().z))
            .hitPoint(hitPoint)
            .hitNormal(hitNormal)
            .build();
        return BFDamageApi.hurt(target, ctx);
    }
}
