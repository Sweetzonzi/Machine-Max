package io.github.sweetzonzi.machine_max.common.mech.projectile;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.physics.PenetrationKey;
import cn.solarmoon.spark_core.util.PPhase;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.ballistics_framework.api.ArmorLevel;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageApi;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageExtensions;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageHandler;
import io.github.sweetzonzi.ballistics_framework.api.BFHitResolveResult;
import io.github.sweetzonzi.ballistics_framework.api.BFHurtTarget;
import io.github.sweetzonzi.ballistics_framework.api.PenetrationResult;
import io.github.sweetzonzi.machine_max.common.MMServerConfig;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.component.effect.WorldEffect;
import io.github.sweetzonzi.machine_max.common.mech.projectile.type.BallisticProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.projectile.type.BallisticProjectileType.VulnerabilityProperties;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.MMDamageExtensions;
import io.github.sweetzonzi.machine_max.util.mechanic.ArmorUtil;
import io.github.sweetzonzi.machine_max.util.mechanic.DamageUtil;
import io.github.sweetzonzi.machine_max.util.mechanic.MassUtil;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 弹道投射物（JSON {@code "type": "ballistic"}）。
 * <p>
 * 适用于小口径穿甲弹、APFSDS 长杆弹、榴弹等按弹道飞行的实体弹丸。
 * 没有 JME 物理刚体，不受 Bullet 管理，运动由 {@link ProjectileManager} 的 SoA 批量积分驱动。
 * <p>
 * 继承 {@link DestroyableObject} 以复用其生命周期管理（自动注册/注销于
 * {@link ObjectManager#levelDestroyableObjects}），但覆写了所有摧毁倒计时相关方法
 * （投射物命中即消失，无需倒计时）。
 * <p>
 * 本类同时承担"伤害发起方"与"伤害目标"两个角色：
 * <ul>
 *   <li>发起方——实现 {@link BFDamageHandler}，把穿甲判定后的命中行为
 *       （击穿/跳弹/停止）封装为 {@link AfterHitResult}，由 {@link ProjectileManager}
 *       消费后执行 SoA 操作；</li>
 *   <li>目标——作为 {@link BFHurtTarget}，护甲等效厚度与结构血量由内容包的
 *       {@code vulnerability} 块定义：命中走精确 RHA 比较，打穿后按耐久累加伤害，
 *       耐久归零即损毁；未配置该块的弹种不可被击毁。</li>
 * </ul>
 * <p>
 * 碰撞检测在 {@link ProjectileManager#updateProjectiles} 中通过 JME rayTest 完成。
 * <p>
 * 速度-伤害模型采用幂函数（德马尔式）：
 * <pre>
 * effective = base × (currentSpeed / baseVelocity)^coefficient
 * </pre>
 * 系数为 0 时退化为常数值（与速度无关）。
 */
public class BallisticProjectile extends DestroyableObject
        implements BFDamageHandler, IAnimatable<BallisticProjectile> {

    // ==================== 命中结果 ====================

    /**
     * 一次命中的最终结果。
     * <p>
     * 由 {@link BFDamageHandler} 回调写入，{@link ProjectileManager} 消费。
     *
     * @param destroyed   投射物是否应销毁
     * @param newVelocity 若未销毁，命中后的新速度矢量（JME）
     */
    public record AfterHitResult(boolean destroyed, Vector3f newVelocity) {

        /** 销毁 */
        public static final AfterHitResult DESTROYED = new AfterHitResult(true, Vector3f.ZERO);

        /** 穿过后以指定保留率继续飞行 */
        public static AfterHitResult passThrough(float speedRetention, Vector3f currentVelocity) {
            return new AfterHitResult(false, new Vector3f(currentVelocity).multLocal(speedRetention));
        }

        /** 跳弹：按法线反射后乘以能量保持率 */
        public static AfterHitResult ricochet(float retention, Vector3f velocity, Vector3f normal) {
            Vector3f reflected = new Vector3f(velocity);
            float dot = reflected.dot(normal);
            Vector3f correction = new Vector3f(normal).multLocal(2 * dot);
            reflected.subtractLocal(correction);
            reflected.multLocal(retention);
            return new AfterHitResult(false, reflected);
        }
    }

    // ==================== 状态 ====================

    private final BallisticProjectileType projectileType;

    /**
     * 是否已命中。
     * <p>
     * 写入方可能是物理线程（消费命中结果）或主线程（耐久归零），
     * 读取方在两端都有，因此用 volatile 保证可见性。
     */
    private volatile boolean hasHit = false;

    /** 是否正等待主线程返回命中结果（物理线程暂停其积分） */
    @Getter
    @Setter
    private volatile boolean hitPending = false;

    /** 待处理的命中结果（由 BFDamageHandler 回调写入，Manager 在物理线程消费） */
    @Getter
    @Setter
    @Nullable private AfterHitResult pendingHitResult;

    // ========== IAnimatable 实现 ==========
    // 模型/动画控制器使用懒加载，确保构造完成后再初始化
    private AnimController animController;
    private ModelController modelController;
    private final Map<String, Object> variables = HashMap.newHashMap(1);

    /**
     * 缓存寿命副本，由 {@link ProjectileManager#tickAndPreTick()} 在调用 preTick() 前设置。
     * 避免 getLifetime() 在 preTick() → checkDestroyed() 链条中进行 O(n) 线性扫描。
     */
    int cachedLifetime = 0;

    /**
     * 运动学触发体——本弹在物理世界中的可探测体积。
     * <p>
     * 仅服务端、且仅对配置了 {@code vulnerability} 的弹种创建；销毁时摘除。
     */
    @Nullable
    private volatile ProjectileHitBox hitBox;

    /**
     * 创建一个弹道投射物。
     * <p>
     * 服务端：注册到 {@link ObjectManager} 和 {@link ProjectileManager} 的 SoA 数组。
     * 客户端：仅创建实例等待服务端同步。
     *
     * @param level    维度
     * @param type     投射物类型定义
     * @param position 初始世界坐标（JME）
     * @param velocity 初始速度矢量（JME，单位 m/s）
     */
    public BallisticProjectile(Level level, BallisticProjectileType type, Vector3f position, Vector3f velocity) {
        super(level);
        this.projectileType = type;
        setPosition(position);
        setLinearVelocity(velocity);
        transform = new Transform(position, Quaternion.IDENTITY);
        oldTransform = transform.clone();
        // 结构血量的容量来自内容包，而 DATA_DURABILITY_ID 自带 20 的默认值，
        // 与配置无关，因此必须在此显式写入一次初值
        setDurability(getMaxDurability());
    }

    /**
     * 将投射物注册到世界（两端的统一入口）。
     * <p>
     * {@link DestroyableObject#addToLevel()} → 注册到 {@link ObjectManager#levelDestroyableObjects}
     * → 注册到 {@link ProjectileManager} SoA 数组。
     * <p>
     * <b>服务端：</b>Entity 创建与网络广播已移至
     * {@link ProjectileManager#flushProjectileEntities()}（主线程 preTick），
     * 改由批量包 {@code ProjectilesSpawnPayload} 发送。<br>
     * <b>客户端：</b>直接播放开火音效。
     * <p>
     * <b>调用线程：</b>物理线程（由 {@link BallisticProjectileType#create} → addToLevel 链调用）。
     */
    @Override
    public void addToLevel() {
        super.addToLevel();
        ProjectileManager pm = ObjectManager.getOrCreateProjectileManager(level);
        pm.addProjectile(this);

        // 触发体只在服务端参与判定，客户端建出来的刚体位姿无人刷新、判定无人查询
        if (!level.isClientSide() && isInterceptable()) {
            ProjectileHitBox box = new ProjectileHitBox(this);
            this.hitBox = box;
            // 预登记触发体自身的密钥，让攻击弹从出膛起就忽略自己的触发体
            // （与 LauncherSubsystem 预登记发射器零件同一手法）。属主是 ProjectileHitBox、
            // 区域标识取默认实现的 null，因此密钥恒为 PenetrationKey(box, null)
            pm.markPenetrated(getId(), new PenetrationKey(box, null));
        }
    }

    /**
     * 本弹种是否可被拦截——即是否持有运动学触发体。
     * <p>
     * 判据只有一条：内容包是否配置了 {@code vulnerability}。触发体创建、HUD 与验证
     * 都走这个入口，避免"是否持有触发体"与"是否配了 vulnerability"两处判据漂移。
     *
     * @return true 表示本弹可被其他投射物或外部攻击命中
     */
    public boolean isInterceptable() {
        return projectileType.getVulnerability() != null;
    }

    /** @return 运动学触发体；未配置 {@code vulnerability} 的弹种与客户端为 null */
    @Nullable
    public ProjectileHitBox getHitBox() {
        return hitBox;
    }

    public BallisticProjectileType getProjectileType() {
        return projectileType;
    }

    public Vector3f getVelocity() {
        return getLinearVelocity();
    }

    public boolean isAlive() {
        return !isRemoved && !hasHit;
    }

    /**
     * 返回剩余存活 tick 数。
     * <p>
     * 寿命的权威来源是 {@link ProjectileManager} 的 SoA 数组，
     * {@link ProjectileManager#tickAndPreTick()} 在每 tick 将 SoA 中的寿命
     * 写入 {@link #cachedLifetime}，避免在此处进行 O(n) 线性扫描。
     */
    public int getLifetime() {
        return cachedLifetime;
    }

    public int getMaxLifetime() {
        return projectileType.getMaxLifetimeTicks();
    }

    public void markHit() {
        this.hasHit = true;
    }

    /**
     * 覆写：先摘除运动学触发体，再从 {@link ProjectileManager} 的 SoA 中标记摘除，
     * 最后走基类销毁。
     * <p>
     * 命中销毁、寿命到期、超时清理三条路径最终都会经过这里，
     * 因此 SoA 不会残留死条目，物理世界也不会残留可被射线命中的幽灵触发体。
     */
    @Override
    public void destroy() {
        ProjectileHitBox box = hitBox;
        if (box != null) {
            box.remove();
            hitBox = null;
        }
        ProjectileManager pm = ObjectManager.levelProjectileManagers.get(level);
        if (pm != null) pm.removeProjectile(getId());
        super.destroy();
    }

    // ========== 覆写 DestroyableObject 生命周期 ==========

    @Override
    public void preTick() {
        if (isRemoved) return;
        tickCount++;
        if (hurtTime > 0) hurtTime--;
        if (!level.isClientSide()) {
            handleAccumulatedDamage();   // 伤害队列唯一的消费点：先结算伤害，再判定摧毁
            if (checkDestroyed()) {
                setDestroyed();
            }
        }
    }

    /**
     * 覆写：跳过逐 tick syncToClient。
     * <p>
     * 投射物网络同步采用关键事件模式（创建/命中/超时），
     * 不每 tick 同步。仅检查摧毁后立即清理。
     */
    @Override
    public void postTick() {
        if (isDestroyed() && getDestroyTime() <= 0) {
            this.destroy();
        }
    }

    @Override
    public void prePhysicsTick() {
        if (isRemoved) return;
        physicsTickCount++;
    }

    @Override
    public void postPhysicsTick() {
    }

    /**
     * 覆写：基于 hasHit / SoA 寿命 / 结构血量判断摧毁。
     * 寿命权威来源为 {@link ProjectileManager} SoA 数组，结构血量来自基类 {@code durability}。
     */
    @Override
    protected boolean checkDestroyed() {
        return !isDestroyed() && (hasHit || getLifetime() <= 0 || getDurability() <= 0);
    }

    /**
     * 覆写：跳过摧毁倒计时，立即标记为已摧毁。
     * 投射物不需要像 SubPart 那样有销毁动画/倒计时。
     */
    @Override
    protected void setDestroyed() {
        getSyncedData().set(DATA_DESTROYED_ID, true);
        getSyncedData().set(DESTROY_TIME_ID, 0);
    }

    /** 覆写为空操作：投射物不需要摧毁倒计时推进 */
    @Override
    protected void tickDestroyTimer(int tick) {
    }

    /**
     * 结算伤害队列（主线程，由 {@link #preTick()} 调用）。
     * <p>
     * 伤害由任意线程经 {@link #accumulateDamage} 入队，此处是唯一消费点。
     * 结构血量归零时在最后一次命中的命中点执行战斗部，
     * 随后的 {@link #checkDestroyed()} 会因耐久归零而标记销毁。
     */
    @Override
    protected void handleAccumulatedDamage() {
        float total = 0f;
        BFDamageContext last = null;
        while (!accumulatedDamage.isEmpty()) {
            var pair = accumulatedDamage.poll();
            total += pair.getFirst();
            last = pair.getSecond();
        }
        if (total <= 0f) return;
        setDurability(getDurability() - total);
        if (getDurability() > 0f) return;

        // 结构耗尽：在命中点执行战斗部
        Vec3 hitPoint = last != null ? last.hitPoint()
                : new Vec3(getPosition().x, getPosition().y, getPosition().z);
        ObjectManager.getOrCreateProjectileManager(level).enqueueWarheadDetonation(this, hitPoint);
        markHit();
    }

    /**
     * 伤害入队。
     * <p>
     * 未配置 {@code vulnerability} 的弹种按不可被击毁处理，直接丢弃伤害；
     * 其余交给基类累加器，由主线程 {@link #handleAccumulatedDamage()} 结算。
     */
    @Override
    public void accumulateDamage(float damage, BFDamageContext ctx) {
        if (projectileType.getVulnerability() == null) return;
        super.accumulateDamage(damage, ctx);
    }

    @Override
    public float getMaxDurability() {
        VulnerabilityProperties v = projectileType.getVulnerability();
        return v == null ? 1f : v.durability();
    }

    /**
     * 弹道投射物无物理刚体，调用此方法将抛出异常。
     */
    @Override
    public @NotNull PhysicsLevel getPhysicsLevel() {
        throw new UnsupportedOperationException("BallisticProjectile has no physics body");
    }

    @Override
    protected void defineSyncedData(SynchedEntityData.Builder builder) {
    }

    // ========== IAnimatable 实现 ==========

    @Override
    public BallisticProjectile getAnimatable() {
        return this;
    }

    @Override
    public Level getAnimLevel() {
        return level;
    }

    private ModelIndex defaultModelIndex;

    @Override
    public @NotNull ModelIndex getDefaultModelIndex() {
        if (defaultModelIndex == null) {
            ResourceLocation key = projectileType.getRegistryKey();
            defaultModelIndex = new ModelIndex("projectile", key != null ? key
                    : ResourceLocation.fromNamespaceAndPath("machine_max", "ballistic_default"));
        }
        return defaultModelIndex;
    }

    @Override
    public @NotNull AnimController getAnimController() {
        if (animController == null) {
            animController = new AnimController(this);
        }
        return animController;
    }

    @Override
    public @NotNull ModelController getModelController() {
        if (modelController == null) {
            modelController = new ModelController(this);
        }
        return modelController;
    }

    @Override
    public @NotNull Map<String, Object> getVariables() {
        return variables;
    }

    // ========== BFHurtTarget 实现 ==========
    //
    // 护甲与结构血量的语义由内容包的 vulnerability 块定义：
    //   vulnerability 缺省 → 本弹种不可被击毁（hurt 恒 false、护甲按 0.5mm 处理、耐久容量 1）
    //   vulnerability 存在 → 命中走精确 RHA 比较，打穿则按耐久累加伤害

    /**
     * 命中等效厚度（mm RHA）。未配置 {@code vulnerability} 的弹种按
     * {@link ArmorLevel#UNARMORED_1} 的中位值处理。
     */
    @Override
    public float getRHA(BFDamageContext ctx) {
        VulnerabilityProperties v = projectileType.getVulnerability();
        return v == null ? ArmorLevel.UNARMORED_1.medianRha() : v.rha();
    }

    /** 护甲等级与精确 RHA 保持一致，供 HUD 与等级语义使用 */
    @Override
    public ArmorLevel getArmorLevel(BFDamageContext ctx) {
        return ArmorLevel.fromRha(getRHA(ctx));
    }

    /**
     * 精确穿深比较。
     * <p>
     * 默认的离散等级比较会把 11mm 穿深判为击穿 19mm 装甲；
     * 精确比较与 {@code SubPart} 的口径一致。
     */
    @Override
    public boolean isArmorPenetrated(BFDamageContext ctx) {
        return modifyPenetration(ctx) > getRHA(ctx);
    }

    /**
     * 接受协议伤害。
     * <p>
     * 只负责把伤害送进基类累加器，不做耐久运算——命中管线先判穿甲，
     * 未击穿时 {@code calculateFinalDamage} 返回 0，在 {@code amount <= 0f} 处早退，
     * 因此"未击穿不扣耐久"由管线自身保证。
     * <p>
     * <b>调用线程：</b>任意线程（物理线程或主线程的伤害任务），
     * 耐久写入统一发生在主线程 {@link #handleAccumulatedDamage()}。
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (getLevel().isClientSide()) return false;
        if (getProjectileType().getVulnerability() == null || amount <= 0f) return false;
        // 命中几何从上下文栈取回；取不到时入队 null，起爆点由 handleAccumulatedDamage 用当前位置兜底
        accumulateDamage(amount, BFDamageApi.getContextFor(this));
        return true;
    }

    /**
     * 协议外伤害的上下文入口。
     * <p>
     * 原版与其他模组的伤害（箭矢、近战、爆炸）经 mixin 拦截后由此构造上下文，
     * 返回 null 即视为"该目标不接受协议外伤害"而交回原版流程。
     * <p>
     * {@code penetration} 取 {@code amount}：协议外伤害没有弹道模型，伤害值同时充当穿深，
     * 因此原版箭矢这类低伤害来源会被 {@link #getRHA} 挡在门外。
     */
    @Override
    @Nullable
    public BFDamageContext createContextFromVanilla(DamageSource source, float amount) {
        if (getProjectileType().getVulnerability() == null) return null;
        return BFDamageContext.builder()
                .source(source)
                .baseDamage(amount)
                .penetration(amount)
                .build();
    }

    // ========== 稳定性状态（SoA 数组支持） ==========

    public float getRemainingStableDistance() {
        ProjectileManager pm = ObjectManager.levelProjectileManagers.get(level);
        if (pm == null) return 0;
        int idx = pm.findIndexByObjId(getId());
        return (idx >= 0) ? pm.remainingStableDistance[idx] : 0;
    }

    public void setRemainingStableDistance(float v) {
        ProjectileManager pm = ObjectManager.levelProjectileManagers.get(level);
        if (pm == null) return;
        int idx = pm.findIndexByObjId(getId());
        if (idx >= 0) pm.remainingStableDistance[idx] = v;
    }

    // ==================== 穿透速度工具 ====================

    /**
     * 能量法计算穿透后的剩余速率。
     * <p>
     * 从穿深公式反推刚好穿透目标护甲所需的速度 v_threshold，
     * 再从当前动能中扣除穿透耗能，由剩余动能反推新速度。
     * <pre>
     * v_threshold = v × (targetArmor / currentPen)^(1/k)
     * v_new = sqrt(max(0, v² - v_threshold²))
     * </pre>
     * k=0 时穿深与速度无关，不损失能量，返回原速度。
     *
     * @param currentSpeed 当前速率（m/s）
     * @param currentPen   当前穿深（mm RHA）
     * @param targetArmor  目标等效护甲（mm RHA）
     * @param penCoeff     穿深速度系数 k
     * @return 穿透后的新速率；动能不足以穿透时返回 0
     */
    private static float speedAfterPenetration(float currentSpeed, float currentPen, float targetArmor, float penCoeff) {
        if (Math.abs(penCoeff) < 1e-6f) return currentSpeed; // k=0 不损失能量
        float ratio = targetArmor / Math.max(currentPen, 1e-6f);
        float vThreshold = currentSpeed * (float) Math.pow(ratio, 1.0f / penCoeff);
        float vNewSq = currentSpeed * currentSpeed - vThreshold * vThreshold;
        if (vNewSq <= 0) return 0;
        return (float) Math.sqrt(vNewSq);
    }

    // ==================== 命中结果桥接（回调 ↔ Manager） ====================

    /** 消费命中结果（物理线程调用，消费后清空） */
    public @Nullable AfterHitResult consumePendingHitResult() {
        AfterHitResult r = getPendingHitResult();
        setPendingHitResult(null);
        return r;
    }

    // ==================== BFDamageHandler 回调实现 ====================

    @Override
    public void onPenetrated(BFHurtTarget target, BFDamageContext ctx) {
        decrementStableDistance(target, ctx);   // 穿透成功则递减稳定距离
        resolvePenetrationSpeed(target, ctx);
    }

    @Override
    public void onBlocked(BFHurtTarget target, BFDamageContext ctx) {
        setPendingHitResult(AfterHitResult.DESTROYED);
    }

    @Override
    public void onRicochet(BFHurtTarget target, BFDamageContext ctx) {
        Vec3 normalMc = ctx.hitNormal();
        Vector3f normal = new Vector3f((float) normalMc.x, (float) normalMc.y, (float) normalMc.z);
        setPendingHitResult(AfterHitResult.ricochet(0.8f, getVelocity(), normal));
    }

    @Override
    public void onOvermatch(BFHurtTarget target, BFDamageContext ctx) {
        resolvePenetrationSpeed(target, ctx);
    }

    @Override
    public void onSpall(BFHurtTarget target, BFDamageContext ctx) {
        resolvePenetrationSpeed(target, ctx);
    }

    /**
     * 碾压判定：穿深或口径远超装甲厚度时弹体保持完整。
     * <p>
     * 相比默认实现仅检查穿深维度，此处增加口径维度：
     * 大口径弹丸（口径 &gt; 2× 装甲RHA）打薄板时同样视为碾压。
     *
     * @param target 伤害目标
     * @param ctx    命中上下文
     * @param result 穿甲结果
     * @return true 表示碾压——弹体完整穿透，不触发破片
     */
    @Override
    public boolean isOvermatch(BFHurtTarget target, BFDamageContext ctx, PenetrationResult result) {
        if (result != PenetrationResult.PENETRATED) return false;
        float rha = target.getRHA(ctx);
        float modifiedPen = target.modifyPenetration(ctx);
        return ArmorUtil.isOvermatched(getCaliber(), modifiedPen, rha);
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
    public void onNormalEntityHit(Entity entity, BFDamageContext ctx,
                             float baseDamage, boolean success) {
        float effectiveRha = computeEntityEffectiveRha(entity);
        float pen = calculateCurrentPenetration();
        if (pen <= effectiveRha * 0.15f) {
            setPendingHitResult(AfterHitResult.DESTROYED);
        } else {
            float newSpeed = speedAfterPenetration(getSpeed(), pen, effectiveRha, getPenetrationVelocityCoefficient());
            if (newSpeed <= 0) {
                setPendingHitResult(AfterHitResult.DESTROYED);
            } else {
                setPendingHitResult(AfterHitResult.passThrough(newSpeed / Math.max(getSpeed(), 0.001f), getVelocity()));
            }
        }
    }

    // ==================== 伤害前回调：在 hurt() 前写入冲量 ====================
    // 利用 BF pipeline 的 before* 回调（resolvePenetration 后、hurt 前触发），
    // 在此时计算冲量并写入扩展容器，确保 SubPart.hurt() 的延迟任务在读取时值已就绪。

    @Override
    public void beforePenetrated(BFHurtTarget target, BFDamageContext ctx) {
        float effectivePen = target.modifyPenetration(ctx);
        float rha = target.getRHA(ctx);
        ctx.extensions().set(BFDamageExtensions.IMPULSE,
                computePenetrationImpulse(effectivePen, rha, (float) ctx.hitVelocity().length()));
    }

    @Override
    public void beforeBlocked(BFHurtTarget target, BFDamageContext ctx) {
        ctx.extensions().set(BFDamageExtensions.IMPULSE,
                getMass() * (float) ctx.hitVelocity().length());
    }

    @Override
    public void beforeRicochet(BFHurtTarget target, BFDamageContext ctx) {
        // 跳弹冲量：弹体以 0.8 倍速率反射，转移约 20% 动量
        ctx.extensions().set(BFDamageExtensions.IMPULSE,
                getMass() * (float) ctx.hitVelocity().length() * 0.2f);
    }

    @Override
    public void beforeOvermatch(BFHurtTarget target, BFDamageContext ctx) {
        float effectivePen = target.modifyPenetration(ctx);
        float rha = target.getRHA(ctx);
        ctx.extensions().set(BFDamageExtensions.IMPULSE,
                computePenetrationImpulse(effectivePen, rha, (float) ctx.hitVelocity().length()));
    }

    @Override
    public void beforeSpall(BFHurtTarget target, BFDamageContext ctx) {
        // 破片场景：parent beforePenetrated/Blocked 已写冲量，此处无需额外操作
    }

    @Override
    public void beforeNormalEntityHit(Entity entity, BFDamageContext ctx, float baseDamage) {
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
     * 由 Manager 在射线检测路径调用。
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
    public AfterHitResult onTerrainHit(
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
            // 有战斗部 → 视作无穿透，命中即停止（起爆由 ProjectileManager 入队）
            if (hasWarheads()) return AfterHitResult.DESTROYED;

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
     * @param hitBox              命中的碰撞箱（SubPart 命中时有值，否则 null）
     * @return AfterHitResult — passThrough / DESTROYED / ricochet
     */
    public AfterHitResult onPartHit(
        Level level, BFHurtTarget target,
        float currentPenetration, float currentDamage,
        Vec3 hitPoint, Vec3 hitNormal,
        @Nullable io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox hitBox
    ) {
        BFDamageExtensions exts = getProjectileType().getBaseExtensions();
        Vector3f vel = getVelocity();
        Vec3 hitVel = new Vec3(vel.x, vel.y, vel.z);

        if (hitBox != null) {
            exts.set(MMDamageExtensions.HIT_BOX, hitBox);
            exts.set(MMDamageExtensions.HIT_PHYSICAL_THICKNESS, hitBox.getAttr().getThickness());
        }

        BFDamageContext ctx = buildHurtContext(getLevel(), currentDamage,
                hitVel, hitPoint, hitNormal, exts);
        dealDamage(target, ctx);
        // 有战斗部 → 视作无穿透，动能判定完成后停止（起爆由 ProjectileManager 入队）
        if (hasWarheads()) return AfterHitResult.DESTROYED;
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
    public AfterHitResult onEntityHit(
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
                BFDamageExtensions exts = getProjectileType().getBaseExtensions();
                BFDamageContext ctx = buildHurtContext(level, currentDamage,
                        hitVel, resolved.correctedHitPoint(), resolved.correctedHitNormal(), exts);
                dealDamage(rt, ctx);
                // 有战斗部 → 视作无穿透，动能判定完成后停止（起爆由 ProjectileManager 入队）
                if (hasWarheads()) return AfterHitResult.DESTROYED;
                return consumePendingHitResult();
            }
        }

        // 异步管线：捕获当前弹道参数，提交主线程构造上下文并执行伤害
        setHitPending(true);
        float capturedDmg = calculateCurrentDamage();
        SparkLevel.submitImmediateTask(level, PPhase.POST,
                () -> {
                    if (entity instanceof LivingEntity livingEntity)
                        livingEntity.invulnerableTime = 0;

                    BFDamageExtensions exts = getProjectileType().getBaseExtensions();
                    exts.set(MMDamageExtensions.HIT_PHYSICAL_THICKNESS, entity.getBbWidth() * 1000f);
                    BFDamageContext ctx = buildHurtContext(level, capturedDmg,
                            hitVel, hitPoint, hitNormal, exts);
                    dealDamage(entity, ctx);

                    // 有战斗部 → 视作无穿透：先把起爆请求追加在本次动能伤害之后，再强制销毁
                    if (hasWarheads()) {
                        setPendingHitResult(AfterHitResult.DESTROYED);
                        ObjectManager.getOrCreateProjectileManager(level)
                                .enqueueWarheadDetonation(this, hitPoint);
                    }
                });
        return null;
    }

    // ==================== 弹道参数快捷委托（全部委托至 ProjectileType） ====================

    /** @return 本弹种的战斗部效果列表（空列表 = 纯动能弹） */
    public List<WorldEffect> getWarheads() {
        return getProjectileType().getWarheads();
    }

    /**
     * 本弹种是否携带战斗部效果。
     * <p>
     * 有战斗部时命中结果一律视作<b>无穿透</b>：先照常完成动能击穿 / 伤害判定，
     * 随后在命中点执行全部战斗部效果，最后销毁投射物（起爆由
     * {@link ProjectileManager} 入队、主线程冲刷）。
     *
     * @return true 表示至少有一个战斗部效果
     */
    public boolean hasWarheads() {
        return !getWarheads().isEmpty();
    }

    public float getMass()            { return getProjectileType().getMass(); }
    public float getGravityFactor()   { return getProjectileType().getGravityFactor(); }
    public float getDragFactor()      { return getProjectileType().getDragFactor(); }
    public float getBaseVelocity()    { return getProjectileType().getBaseVelocity(); }
    public float getBasePenetration() { return getProjectileType().getBasePenetration(); }
    public float getBaseDamage()      { return getProjectileType().getBaseDamage(); }
    public float getBaseAccuracyMil() { return getProjectileType().getBaseAccuracyMil(); }
    public float getPenetrationVelocityCoefficient() {
        return getProjectileType().getPenetrationVelocityCoefficient();
    }
    public float getDamageVelocityCoefficient() {
        return getProjectileType().getDamageVelocityCoefficient();
    }
    public float getRadius()          { return getProjectileType().getRadius(); }

    /** 口径（mm），供跳弹/碾压判定等使用 */
    public float getCaliber()         { return getProjectileType().getCaliber(); }

    /** 稳定距离（mm），0 = 无限稳定 */
    public float getStableDistance()   { return getProjectileType().getStableDistance(); }
    /** 失稳后穿深保留因子（0~1） */
    public float getUnstablePenFactor(){ return getProjectileType().getUnstablePenFactor(); }

    /** 当前是否已失稳（稳定距离耗尽） */
    public boolean isCurrentlyUnstable() {
        return getStableDistance() > 0 && getRemainingStableDistance() <= 0;
    }

    /**
     * @return 当前速率（速度矢量的模长，单位 m/s）
     */
    public float getSpeed() {
        return getVelocity().length();
    }

    /**
     * 获取归一化的运动方向。
     * 当速度接近零时回退到 {@link #getFrontVector()} 作为方向。
     *
     * @return 归一化方向矢量
     */
    public Vector3f getDirection() {
        Vector3f vel = getVelocity();
        if (vel.lengthSquared() < 1e-12f) return getFrontVector();
        return vel.normalize();
    }

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
    public float calculateCurrentPenetration() {
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
    public float calculateCurrentDamage() {
        float coeff = getDamageVelocityCoefficient();
        if (Math.abs(coeff) < 1e-6f) return getBaseDamage();
        float baseV = Math.max(getBaseVelocity(), 1e-6f);
        return getBaseDamage() * (float) Math.pow(getSpeed() / baseV, coeff);
    }

    // ========== 伤害发起 ==========

    /**
     * 向目标发起协议伤害，并将自身注入为 {@link BFDamageHandler}。
     * <p>
     * 上下文由调用方在调用前构造（含 HIT_BOX、物理厚度、稳定性判定等），
     * 此方法仅执行 BF 管线 + 击退 + 玩家命中冲击包。
     * <p>
     * 穿甲管线完成后，BallisticsFramework 自动回调 {@link #onPenetrated} / {@link #onBlocked} /
     * {@link #onRicochet} 等，将命中结果写入 {@link #setPendingHitResult(AfterHitResult)}。
     *
     * @param target 伤害目标（BFHurtTarget / Entity 等）
     * @param ctx    已构造的命中上下文（含稳定性折减、HIT_BOX、物理厚度等）
     * @return 实际造成的伤害量
     */
    public float dealDamage(Object target, BFDamageContext ctx) {
        // 执行伤害管线（before* 回调已在 hurt 前写入 IMPULSE）
        float dmg = BFDamageHandler.super.dealDamage(target, ctx);

        // 对实体直接施加击退（SubPart 由延迟任务读取 IMPULSE 自处理）
        if (target instanceof Entity entity) {
            float impulse = ctx.extensions().get(BFDamageExtensions.IMPULSE);
            if (impulse > 1e-6f) {
                Vec3 dir = ctx.hitVelocity().normalize();
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
     * 构造带 handler 的 {@link BFDamageContext}，内含稳定性判定与原始穿深保留。
     * <p>
     * 穿深通过 {@link #calculateCurrentPenetration()} 实时计算，
     * 失稳时 ×unstablePenFactor 写入上下文，原始值存入扩展供能量法使用。
     *
     * @param level     维度（用于获取通用 DamageSource）
     * @param damage    伤害量（已按速度衰减的当前值）
     * @param hitVel    命中速度矢量（MC Vec3，m/s）
     * @param hitPoint  命中点世界坐标
     * @param hitNormal 命中面法线
     * @param exts      扩展容器
     * @return 已注入当前投射物为 handler 的上下文
     */
    public BFDamageContext buildHurtContext(Level level, float damage,
                                             Vec3 hitVel, Vec3 hitPoint, Vec3 hitNormal,
                                             BFDamageExtensions exts) {
        // 稳定性判定：失稳时上下文穿深打折，但保留原始穿深供能量法使用
        float originalPen = calculateCurrentPenetration();
        float effectivePen = isCurrentlyUnstable()
                ? originalPen * getUnstablePenFactor()
                : originalPen;
        exts.set(MMDamageExtensions.ORIGINAL_PENETRATION, originalPen);

        return BFDamageContext.builder()
                .source(level.damageSources().generic())
                .baseDamage(damage)
                .penetration(effectivePen)
                .hitVelocity(hitVel)
                .hitPoint(hitPoint)
                .hitNormal(hitNormal)
                .extensions(exts)
                .build()
                .withHandler(this);
    }

    /**
     * 应用目标的穿深修正与稳定性折减，计算穿透后速率并写入命中结果。
     * <p>
     * 两步处理：
     * <ol>
     *   <li>通过 {@link BFHurtTarget#modifyPenetration} 获取目标侧修正后的有效穿深
     *       （如爆反拦截、间隙衰减等真实物理过程）</li>
     *   <li>用上下文中的原始穿深（{@link MMDamageExtensions#ORIGINAL_PENETRATION}）
     *       乘以目标修正比例，得到既不含稳定性打折、又经过目标修正的能量计算用穿深</li>
     * </ol>
     * 最终代入能量法公式计算穿透后的剩余速率，结果写入 {@link #setPendingHitResult}。
     * 供 {@link #onPenetrated} / {@link #onOvermatch} / {@link #onSpall} 共享。
     *
     * @param target 命中目标（用于获取穿深修正与 RHA）
     * @param ctx    命中上下文
     */
    private void resolvePenetrationSpeed(BFHurtTarget target, BFDamageContext ctx) {
        float modifiedPen = target.modifyPenetration(ctx);  // 目标侧修正（爆反/间隙衰减等）
        float rha = target.getRHA(ctx);
        float speed = getSpeed();

        // 能量法使用修正后的原始穿深：原始穿深 × (目标修正后 / 上下文穿深) 的比例
        // 这样既保留了 ERA 等真实减效的影响，又排除了稳定性打折（unstablePenFactor）的人为折扣
        float originalPen = ctx.extensions().get(MMDamageExtensions.ORIGINAL_PENETRATION);
        if (originalPen <= 0) originalPen = ctx.penetration();
        float modRatio = ctx.penetration() > 0 ? modifiedPen / ctx.penetration() : 1f;
        float energyPen = originalPen * modRatio;

        float newSpeed = speedAfterPenetration(speed, energyPen, rha, getPenetrationVelocityCoefficient());
        if (newSpeed <= 0) {
            setPendingHitResult(AfterHitResult.DESTROYED);
        } else {
            setPendingHitResult(AfterHitResult.passThrough(newSpeed / Math.max(speed, 0.001f), getVelocity()));
        }
    }

    /** 能量法计算穿透冲量：m × (v - v_new)，v_new 由 {@link #speedAfterPenetration} 得出 */
    private float computePenetrationImpulse(float pen, float rha, float impactSpeed) {
        float newSpeed = speedAfterPenetration(impactSpeed, pen, rha, getPenetrationVelocityCoefficient());
        return getMass() * (impactSpeed - newSpeed); // newSpeed=0 即全部动量转移
    }

    /**
     * 根据命中上下文递减弹头的剩余稳定距离。
     * <p>
     * 从上下文中读取物理厚度（{@link MMDamageExtensions#HIT_PHYSICAL_THICKNESS}），
     * 若不可用则回退到 {@link BFHurtTarget#getRHA} 估算。
     * 结合命中方向与法线计算视厚度（LOS = 物理厚度 / cosθ），
     * 从剩余稳定距离中扣除。若稳定距离耗尽则弹头将在下一命中时以打折穿深判定。
     * <p>
     * 仅在 {@link #onPenetrated} 中调用，{@link #onOvermatch} 跳过此递减——
     * 碾压穿甲时弹体保持完整，不消耗稳定距离。
     *
     * @param target 命中目标（用于 RHA 回退）
     * @param ctx    命中上下文（含命中方向与法线）
     */
    private void decrementStableDistance(BFHurtTarget target, BFDamageContext ctx) {
        float remaining = getRemainingStableDistance();
        if (remaining <= 0) return; // 已失稳，无需递减

        float physicalThickness = ctx.extensions().get(MMDamageExtensions.HIT_PHYSICAL_THICKNESS);
        if (physicalThickness <= 0) {
            // 回退：非 SubPart/Entity 的 BFHurtTarget 用 RHA 估算物理厚度
            physicalThickness = target.getRHA(ctx);
        }
        if (physicalThickness <= 0) return;

        // 计算视厚度（LOS）：物理厚度 / cos(入射角)
        Vec3 hitVel = ctx.hitVelocity();
        Vec3 hitNormal = ctx.hitNormal();
        double speed = hitVel.length();
        if (speed < 1e-6) return;
        double cosTheta = Math.abs(hitVel.dot(hitNormal)) / speed;
        float losThickness = (float)(physicalThickness / Math.max(cosTheta, 0.01));

        remaining = Math.max(0, remaining - losThickness);
        setRemainingStableDistance(remaining);
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
