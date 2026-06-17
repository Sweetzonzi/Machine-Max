package io.github.sweetzonzi.machine_max.common.mech.projectile;

import cn.solarmoon.spark_core.animation.model.ModelController;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageHandler;
import io.github.sweetzonzi.ballistics_framework.api.BFHurtTarget;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

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
 * 扩展 {@link BFDamageHandler}，将穿甲判定后的命中行为（击穿/跳弹/停止）
 * 封装为 {@link AfterHitResult}，由 {@link ProjectileManager} 消费后执行 SoA 操作。
 * 具体弹种（引信、战斗部、子系统等）只需覆写对应回调方法，无需修改 Manager。
 */
public interface IProjectile extends BFDamageHandler {

    // ==================== 命中结果 ====================

    /**
     * 一次命中的最终结果。
     * <p>
     * 由 {@link BFDamageHandler} 回调写入，{@link ProjectileManager} 消费。
     *
     * @param destroyed   投射物是否应销毁
     * @param newVelocity 若未销毁，命中后的新速度矢量（JME）
     */
    record AfterHitResult(boolean destroyed, Vector3f newVelocity) {

        /** 销毁 */
        static final AfterHitResult DESTROYED = new AfterHitResult(true, Vector3f.ZERO);

        /** 穿过后以指定保留率继续飞行 */
        static AfterHitResult passThrough(float speedRetention, Vector3f currentVelocity) {
            return new AfterHitResult(false, new Vector3f(currentVelocity).multLocal(speedRetention));
        }

        /** 跳弹：按法线反射后乘以能量保持率 */
        static AfterHitResult ricochet(float retention, Vector3f velocity, Vector3f normal) {
            Vector3f reflected = new Vector3f(velocity);
            float dot = reflected.dot(normal);
            Vector3f correction = new Vector3f(normal).multLocal(2 * dot);
            reflected.subtractLocal(correction);
            reflected.multLocal(retention);
            return new AfterHitResult(false, reflected);
        }
    }

    // ==================== 穿透速度工具 ====================

    /**
     * 计算穿透目标后的速度衰减（基于穿深残量反算新速率）。
     * <pre>
     * residualRatio = max(0, (pen - targetArmor) / pen)
     * coeff ≠ 0 → newSpeed = speed × residualRatio^(1/coeff)
     * coeff = 0 → newSpeed = speed × √residualRatio
     * </pre>
     *
     * @param currentSpeed  当前速率（m/s）
     * @param currentPen    当前穿深（mm RHA）
     * @param targetArmor   目标等效护甲（mm RHA）
     * @param penCoeff      穿深速度系数
     * @return 穿透后的新速率 ≥ 0
     */
    static float speedAfterPenetration(float currentSpeed, float currentPen, float targetArmor, float penCoeff) {
        float residualPen = Math.max(0, currentPen - targetArmor);
        float residualRatio = residualPen / Math.max(currentPen, 0.001f);
        if (Math.abs(penCoeff) > 1e-6f) {
            return currentSpeed * (float) Math.pow(residualRatio, 1.0f / penCoeff);
        } else {
            // 系数为 0 时退化为平方根衰减
            return currentSpeed * (float) Math.sqrt(residualRatio);
        }
    }

    // ==================== 命中结果桥接（回调 ↔ Manager） ====================

    /** 是否正等待主线程返回命中结果（物理线程暂停其积分） */
    boolean isHitPending();
    void setHitPending(boolean pending);

    /** 读取当前命中结果（写入后由 Manager 通过 consume 消费） */
    @Nullable
    AfterHitResult getPendingHitResult();

    /** 写入命中结果（由 BFDamageHandler 回调写入，可在物理线程或主线程调用） */
    void setPendingHitResult(@Nullable AfterHitResult result);

    /** 消费命中结果（物理线程调用，消费后清空） */
    default @Nullable AfterHitResult consumePendingHitResult() {
        AfterHitResult r = getPendingHitResult();
        setPendingHitResult(null);
        return r;
    }

    // ==================== BFDamageHandler 回调默认实现 ====================

    /** 按穿深与 RHA 的残余比例计算 passThrough 结果 */
    private AfterHitResult passThroughByResidual(float pen, float rha) {
        float residual = (pen - rha) / Math.max(pen, 0.001f);
        residual = Math.max(0.1f, Math.min(1.0f, residual));
        return AfterHitResult.passThrough((float) Math.sqrt(residual), getVelocity());
    }

    @Override
    default void onPenetrated(BFHurtTarget target, BFDamageContext ctx) {
        setPendingHitResult(passThroughByResidual(ctx.penetration(), target.getRHA(ctx)));
    }

    @Override
    default void onBlocked(BFHurtTarget target, BFDamageContext ctx) {
        setPendingHitResult(AfterHitResult.DESTROYED);
    }

    @Override
    default void onRicochet(BFHurtTarget target, BFDamageContext ctx) {
        Vec3 normalMc = ctx.hitNormal();
        Vector3f normal = new Vector3f((float) normalMc.x, (float) normalMc.y, (float) normalMc.z);
        setPendingHitResult(AfterHitResult.ricochet(0.8f, getVelocity(), normal));
    }

    @Override
    default void onOvermatch(BFHurtTarget target, BFDamageContext ctx) {
        setPendingHitResult(passThroughByResidual(ctx.penetration(), target.getRHA(ctx)));
    }

    @Override
    default void onSpall(BFHurtTarget target, BFDamageContext ctx) {
        setPendingHitResult(passThroughByResidual(ctx.penetration(), target.getRHA(ctx)));
    }

    /**
     * 普通实体命中回调：根据原版属性估算等效 RHA 以决定穿透或销毁。
     * <p>
     * 非协议实体没有 {@link BFHurtTarget#getRHA}，改为从原版属性构造等效 RHA：
     * <ul>
     *   <li>{@link LivingEntity}: {@code 1 HP + 1 护甲 + 2 韧性}（mm）</li>
     *   <li>{@link AbstractMinecart}: 固定 20mm</li>
     *   <li>{@link Boat}: 固定 5mm</li>
     *   <li>其他: 固定 2mm</li>
     * </ul>
     * 穿深远小于等效 RHA（pen ≤ 0.15 × effectiveRha）时销毁，
     * 否则按残余比例衰减速度，公式与 {@link #onPenetrated} 一致。
     */
    @Override
    default void onNormalEntityHit(Entity entity, BFDamageContext ctx,
                             float baseDamage, boolean success) {
        float effectiveRha = switch (entity) {
            case LivingEntity living -> living.getMaxHealth()
                    + living.getArmorValue() * 1.0f
                    + (float) living.getAttributeValue(Attributes.ARMOR_TOUGHNESS) * 2.0f;
            case AbstractMinecart ignored1 -> 20f;
            case Boat ignored -> 5f;
            case null, default -> 2f;
        };

        float pen = calculateCurrentPenetration();
        if (pen <= effectiveRha * 0.15f) {
            setPendingHitResult(AfterHitResult.DESTROYED);
        } else {
            setPendingHitResult(passThroughByResidual(pen, effectiveRha));
        }
    }

    // ==================== 物理状态协议 ====================

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

    /**
     * @return 投射物模型控制器，可能为 null（无模型时不渲染）。
     * 实现类（如 {@link PointProjectile}、{@link RigidProjectile}）应
     * 实现 {@link cn.solarmoon.spark_core.animation.IAnimatable} 接口
     * 并返回真实的 {@link ModelController}。
     */
    default ModelController getModelController() { return null; }

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
     * 向 {@link BFHurtTarget} 发起协议伤害，并将自身注入为 {@link BFDamageHandler}。
     * <p>
     * 穿甲管线（getRHA → modifyPenetration → resolvePenetration → calculateFinalDamage → hurt）
     * 完成后，BallisticsFramework 自动回调 {@link #onPenetrated} / {@link #onBlocked} /
     * {@link #onRicochet} 等，将命中结果写入 {@link #setPendingHitResult(AfterHitResult)}。
     * <p>
     * 相比旧版，穿透判定不再由此方法外部的 Manager 自行计算——
     * BallisticsFramework 管线是穿透判定的唯一权威来源。
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
        return BFDamageHandler.super.dealDamage(target, ctx);
    }
}
