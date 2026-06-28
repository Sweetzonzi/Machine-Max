package io.github.sweetzonzi.machine_max.common.mech.projectile;

import cn.solarmoon.spark_core.animation.model.ModelController;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageHandler;
import io.github.sweetzonzi.ballistics_framework.api.BFHurtTarget;
import io.github.sweetzonzi.machine_max.util.mechanic.MassUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageApi;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageExtensions;
import io.github.sweetzonzi.ballistics_framework.api.BFHitResolveResult;
import io.github.sweetzonzi.machine_max.common.MMServerConfig;
import io.github.sweetzonzi.machine_max.util.mechanic.ArmorUtil;
import io.github.sweetzonzi.machine_max.util.mechanic.DamageUtil;
import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.util.PPhase;

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
        float effectiveRha = computeEntityEffectiveRha(entity);
        float pen = calculateCurrentPenetration();
        if (pen <= effectiveRha * 0.15f) {
            setPendingHitResult(AfterHitResult.DESTROYED);
        } else {
            setPendingHitResult(passThroughByResidual(pen, effectiveRha));
        }
    }

    // ==================== 伤害前回调：在 hurt() 前写入冲量 ====================
    // 利用 BF pipeline 的 before* 回调（resolvePenetration 后、hurt 前触发），
    // 在此时计算冲量并写入扩展容器，确保 SubPart.hurt() 的延迟任务在读取时值已就绪。

    @Override
    default void beforePenetrated(BFHurtTarget target, BFDamageContext ctx) {
        float rha = target.getRHA(ctx);
        ctx.extensions().set(BFDamageExtensions.IMPULSE,
                computePenetrationImpulse(ctx.penetration(), rha, (float) ctx.hitVelocity().length()));
    }

    @Override
    default void beforeBlocked(BFHurtTarget target, BFDamageContext ctx) {
        ctx.extensions().set(BFDamageExtensions.IMPULSE,
                getMass() * (float) ctx.hitVelocity().length());
    }

    @Override
    default void beforeRicochet(BFHurtTarget target, BFDamageContext ctx) {
        // 跳弹冲量：弹体以 0.8 倍速率反射，转移约 20% 动量
        ctx.extensions().set(BFDamageExtensions.IMPULSE,
                getMass() * (float) ctx.hitVelocity().length() * 0.2f);
    }

    @Override
    default void beforeOvermatch(BFHurtTarget target, BFDamageContext ctx) {
        float rha = target.getRHA(ctx);
        ctx.extensions().set(BFDamageExtensions.IMPULSE,
                computePenetrationImpulse(ctx.penetration(), rha, (float) ctx.hitVelocity().length()));
    }

    @Override
    default void beforeSpall(BFHurtTarget target, BFDamageContext ctx) {
        // 破片场景：parent beforePenetrated/Blocked 已写冲量，此处无需额外操作
    }

    @Override
    default void beforeNormalEntityHit(Entity entity, BFDamageContext ctx, float baseDamage) {
        float impactSpeed = (float) ctx.hitVelocity().length();
        float pen = calculateCurrentPenetration();
        float effectiveRha = computeEntityEffectiveRha(entity);
        if (pen > effectiveRha) {
            ctx.extensions().set(BFDamageExtensions.IMPULSE,
                    computePenetrationImpulse(pen, effectiveRha, impactSpeed));
        } else {
            ctx.extensions().set(BFDamageExtensions.IMPULSE, getMass() * impactSpeed);
        }
    }

    // ==================== 地形/实体/零件命中解析 ====================

    /**
     * 处理地形命中。计算方块等效护甲，判定穿透/停住，可选方块破坏。
     * 由 Manager（质点 rayTest）或碰撞回调（刚体）调用。
     * <p>
     * 调用方应在调用此方法之前检查穿透密钥（通过
     * {@link ProjectileManager#hasPenetrated}），若已穿透则跳过；
     * 穿透后由调用方写入密钥。
     *
     * @param level               维度
     * @param blockPos            命中方块坐标
     * @param blockState          方块状态（调用方预先获取）
     * @param currentPenetration  当前速度下的穿深（mm RHA）
     * @param currentSpeed        当前速度（m/s）
     * @param hitPoint            命中点世界坐标（MC Vec3）
     * @param hitNormal           命中面法线（指向投射物）
     * @return AfterHitResult — passThrough(速率保留率) / DESTROYED
     */
    default AfterHitResult onTerrainHit(
        Level level, BlockPos blockPos, BlockState blockState,
        float currentPenetration, float currentSpeed,
        Vec3 hitPoint, Vec3 hitNormal
    ) {
        // 获取方块等效护甲（mm RHA）
        float blockArmor = ArmorUtil.getBlockArmor(level, blockState, BlockPos.ZERO);

        if (currentPenetration > blockArmor) {
            // 穿透：计算穿透后速度
            float penCoeff = getPenetrationVelocityCoefficient();
            float newSpeed = speedAfterPenetration(currentSpeed, currentPenetration, blockArmor, penCoeff);
            float velocityRetention = newSpeed / Math.max(currentSpeed, 0.001f);

            // 方块破坏判定（服务端）
            if (!level.isClientSide() && MMServerConfig.projectileDestroyBlocks()
                    && getProjectileType().getBlockDamageFactor() > 0) {
                float damage = calculateCurrentDamage();
                float blockDurability = DamageUtil.getMaxBlockDurability(
                        EmptyBlockGetter.INSTANCE, blockState, BlockPos.ZERO);
                if (blockDurability > 0 && getProjectileType().getBlockDamageFactor() * damage > blockDurability) {
                    SparkLevel.submitDeduplicatedTask(level, blockPos.toShortString(), PPhase.PRE,
                            () -> level.destroyBlock(blockPos, false));
                }
            }

            return AfterHitResult.passThrough(velocityRetention, getVelocity());
        }

        // 无法穿透 → 销毁
        return AfterHitResult.DESTROYED;
    }

    /**
     * 处理零件或 BFHurtTarget（非 Entity）命中。同步执行 BFDamageApi 管线。
     * <p>
     * 调用方负责在调用前过滤掉 {@code MMPartEntity} 和 {@code MMProjectileEntity}
     * （它们仅是渲染代理，不应触发命中）。
     *
     * @param level               维度
     * @param target              命中目标（SubPart 或其他 BFHurtTarget）
     * @param currentPenetration  当前穿深（mm RHA）
     * @param currentDamage       当前伤害
     * @param hitPoint            命中点世界坐标
     * @param hitNormal           命中面法线
     * @return AfterHitResult — passThrough / DESTROYED / ricochet
     */
    default AfterHitResult onPartHit(
        Level level, BFHurtTarget target,
        float currentPenetration, float currentDamage,
        Vec3 hitPoint, Vec3 hitNormal
    ) {
        dealDamage(target, hitPoint, hitNormal);
        return consumePendingHitResult();
    }

    /**
     * 处理实体命中。委托 BFDamageApi 管线，异步完成后由 BFDamageHandler 回调写入结果。
     * <p>
     * 先通过 {@link BFDamageApi#resolveHitTarget} 决议实际目标：
     * <ul>
     *   <li>决议到非实体 BFHurtTarget（如 SubPart）→ 同步管线，立即返回结果</li>
     *   <li>决议到 Entity 或非协议实体 → 异步管线，暂停投射物，提交主线程执行伤害</li>
     *   <li>决议失败 → 假阳性，返回 null（继续飞行）</li>
     * </ul>
     * <p>
     * 调用方应在下一帧通过 {@link #consumePendingHitResult()} 消费异步结果。
     *
     * @param level               维度
     * @param entity              命中实体
     * @param currentPenetration  当前穿深（mm RHA）
     * @param currentDamage       当前伤害
     * @param hitPoint            命中点世界坐标
     * @param hitNormal           命中面法线
     * @return AfterHitResult — 同步路径返回即时结果；异步路径返回 null，结果由回调链写入 pendingHitResult
     */
    @Nullable
    default AfterHitResult onEntityHit(
        Level level, Entity entity,
        float currentPenetration, float currentDamage,
        Vec3 hitPoint, Vec3 hitNormal
    ) {
        // 一次 JME→MC 转换，delta 通过缩放得到
        Vector3f velJme = getVelocity();
        Vec3 hitVel = new Vec3(velJme.x, velJme.y, velJme.z);

        // 协议实体先决议实际命中目标
        if (BFDamageApi.isProtocolAware(entity)) {
            BFHitResolveResult resolved = BFDamageApi.resolveHitTarget(
                    entity, hitPoint, hitVel.scale(1.0 / 20.0));
            if (resolved == null) return null; // 假阳性，继续飞行

            BFHurtTarget rt = resolved.actualTarget();

            // 决议到非实体 BFHurtTarget → 同步管线，立即返回
            if (!(rt instanceof Entity)) {
                dealDamage(rt, resolved.correctedHitPoint(), resolved.correctedHitNormal());
                return consumePendingHitResult();
            }
        }

        // 异步管线：提交主线程执行伤害，穿透/击退由 dealDamage 内部统一处理
        setHitPending(true);
        SparkLevel.submitImmediateTask(level, PPhase.POST,
                () -> {
                    if (entity instanceof LivingEntity livingEntity)
                        livingEntity.invulnerableTime = 0;
                    dealDamage(entity, hitPoint, hitNormal);
                });
        return null;
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
     * 向目标发起协议伤害，并将自身注入为 {@link BFDamageHandler}。
     * <p>
     * 穿甲管线（getRHA → modifyPenetration → resolvePenetration → calculateFinalDamage → hurt）
     * 完成后，BallisticsFramework 自动回调 {@link #onPenetrated} / {@link #onBlocked} /
     * {@link #onRicochet} 等，将命中结果写入 {@link #setPendingHitResult(AfterHitResult)}。
     * <p>
     * 目标可以是 {@link BFHurtTarget}（完整协议管线）、带护甲的实体（适配器管线）、
     * 或普通实体（回退原版 hurt）。穿透判定不由 Manager 自行计算——
     * BallisticsFramework 管线是穿透判定的唯一权威来源。
     *
     * @param target    伤害目标（BFHurtTarget / Entity 等，传入 {@link BFDamageApi#hurt}）
     * @param hitPoint  命中点世界坐标（MC Vec3）
     * @param hitNormal 命中面法线（MC Vec3）
     * @return 实际造成的伤害量（协议层计算值，可能被原版护甲二次减免）
     */
    default float dealDamage(Object target, Vec3 hitPoint, Vec3 hitNormal) {
        // 创建扩展容器，供穿透管线内外传递数据
        BFDamageExtensions exts = new BFDamageExtensions();
        Vector3f vel = getVelocity();
        Vec3 hitVel = new Vec3(vel.x, vel.y, vel.z);
        BFDamageContext ctx = buildHurtContext(getLevel(), calculateCurrentDamage(),
                calculateCurrentPenetration(), hitVel, hitPoint, hitNormal, exts);

        // 先执行伤害管线（before* 回调已在 hurt 前写入 IMPULSE）
        float dmg = BFDamageHandler.super.dealDamage(target, ctx);

        // 对实体直接施加击退（SubPart 由延迟任务读取 IMPULSE 自处理）
        if (target instanceof Entity entity) {
            float impulse = exts.get(BFDamageExtensions.IMPULSE);
            if (impulse > 1e-6f) {
                Vec3 dir = ctx.hitVelocity().normalize();
                // 冲量转换为速度变化
                if (entity instanceof LivingEntity livingEntity)
                    livingEntity.knockback(impulse / MassUtil.getEntityMass(entity), -dir.x, -dir.z);
                else
                    entity.setDeltaMovement(entity.getDeltaMovement().add(dir.scale(impulse / MassUtil.getEntityMass(entity))));
            }
        }
        return dmg;
    }

    // ========== 私有辅助 ==========

    /**
     * 构造带 handler 的 {@link BFDamageContext}，由 {@link #dealDamage} 调用。
     * <p>
     * 集中管理上下文构造逻辑，避免 builder 链在多处重复。
     *
     * @param level       维度（用于获取通用 DamageSource）
     * @param damage      伤害量（已按速度衰减的当前值）
     * @param penetration 穿深 mm RHA
     * @param hitVel      命中速度矢量（MC Vec3，m/s）
     * @param hitPoint    命中点世界坐标
     * @param hitNormal   命中面法线
     * @param exts        扩展容器
     * @return 已注入当前投射物为 handler 的上下文
     */
    private BFDamageContext buildHurtContext(Level level, float damage, float penetration,
                                             Vec3 hitVel, Vec3 hitPoint, Vec3 hitNormal,
                                             BFDamageExtensions exts) {
        return BFDamageContext.builder()
                .source(level.damageSources().generic())
                .baseDamage(damage)
                .penetration(penetration)
                .hitVelocity(hitVel)
                .hitPoint(hitPoint)
                .hitNormal(hitNormal)
                .extensions(exts)
                .build()
                .withHandler(this);
    }

    /** 根据穿深和 RHA 计算穿透后的动量转移冲量（N·s），公式与 {@link #passThroughByResidual} 一致 */
    private float computePenetrationImpulse(float pen, float rha, float impactSpeed) {
        float residual = (pen - rha) / Math.max(pen, 0.001f);
        residual = Math.max(0.1f, Math.min(1.0f, residual));
        float residualSpeed = impactSpeed * (float) Math.sqrt(residual);
        return Math.max(0f, getMass() * (impactSpeed - residualSpeed));
    }

    /**
     * 根据原版属性估算实体等效 RHA（mm），供普通实体（非协议感知）冲量计算使用。
     * <p>
     * {@link LivingEntity}: {@code 1 HP + 1 护甲 + 2 韧性}（mm）；
     * {@link AbstractMinecart}: 20mm；{@link Boat}: 5mm；其他: 2mm。
     */
    private static float computeEntityEffectiveRha(Entity entity) {
        return switch (entity) {
            case LivingEntity living -> living.getMaxHealth()
                    + living.getArmorValue() * 1.0f
                    + (float) living.getAttributeValue(Attributes.ARMOR_TOUGHNESS) * 2.0f;
            case AbstractMinecart ignored1 -> 20f;
            case Boat ignored -> 5f;
            case null, default -> 2f;
        };
    }
}
