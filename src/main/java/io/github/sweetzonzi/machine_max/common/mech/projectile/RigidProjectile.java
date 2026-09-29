package io.github.sweetzonzi.machine_max.common.mech.projectile;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.body.ManifoldPoint;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.PenetrationKey;
import cn.solarmoon.spark_core.physics.terrain.PhysicsChunkSection;
import cn.solarmoon.spark_core.physics.terrain.SectionSnapshot;
import cn.solarmoon.spark_core.physics.PhysicsHost;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.ManifoldPoints;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.entity.MMProjectileEntity;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.ballistics_framework.api.ArmorLevel;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageApi;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageExtensions;
import io.github.sweetzonzi.ballistics_framework.api.BFHurtTarget;
import io.github.sweetzonzi.ballistics_framework.api.BFHitResolveResult;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableRigidObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.component.guidance.GuidanceContext;
import io.github.sweetzonzi.machine_max.common.mech.projectile.type.KineticProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.MMDamageExtensions;
import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.util.PPhase;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * 刚体投射物。
 * <p>
 * 适用于大口径榴弹、火箭弹等大质量、可被拦截的投射物。
 * 拥有 JME {@link com.jme3.bullet.objects.PhysicsRigidBody}，受 Bullet 物理引擎管理，
 * 碰撞检测通过 {@link com.jme3.bullet.objects.PhysicsRigidBody#isColliding} 标志实现。
 * <p>
 * 继承 {@link DestroyableRigidObject} 复用刚体生命周期管理，
 * 覆写了所有摧毁倒计时相关方法（命中即消失）。
 * 空气阻力在 {@link #prePhysicsTick()} 中以中心力施加（{@code ½·ρ(h)·Cd·A·v²}，与质点弹同一模型）；
 * 重力系数在入世后覆盖刚体重力（{@code g = 世界重力 × gravity_factor}）。
 * <p>
 * <b>姿态：</b>角速度因子置零，Bullet 对本刚体不产生任何自转；位姿由代码每物理步
 * 按"弹轴 = 速度方向"的近似驱动（见 {@link #facingFromVelocity(Vector3f)}）。
 * 碰撞形状是球体，姿态不参与碰撞解算，因此覆写姿态是安全的，其作用是给出模型朝向
 * 与 {@code getFrontVector()} / {@code IProjectile#getDirection()} 的语义。
 * <p>
 * <b>制导：</b>类型配置了 {@code guidance} 时，弹上每物理步按 SoA 中的目标点解算指令加速度，
 * 并以中心力提交给 Bullet（见 {@link #applyGuidanceForce()}）。
 * <p>
 * 碰撞形状为固定半径球体。
 */
public class RigidProjectile extends DestroyableRigidObject implements IProjectile, IAnimatable<RigidProjectile> {

    private final KineticProjectileType projectileType;
    private volatile boolean hasHit = false;

    /** 制导解算输出缓冲（复用，避免物理步内重复分配） */
    private final Vector3f guidanceAccel = new Vector3f();

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
     * 创建一个刚体投射物。
     * <p>
     * 构造器仅初始化内部状态和刚体属性，不注册到任何管理器。
     * 调用方在构造后需手动调用 {@link #addToLevel()} 完成注册。
     *
     * @param level    维度
     * @param type     投射物类型定义
     * @param position 初始世界坐标（JME）
     * @param velocity 初始速度矢量（JME，单位 m/s）
     */
    public RigidProjectile(Level level, KineticProjectileType type, Vector3f position, Vector3f velocity) {
        super(level, createCollisionShape(type.getRadius()), type.getMass());
        this.projectileType = type;

        setPosition(position);
        setLinearVelocity(velocity);
        // 姿态基线：角速度因子置零并清除角速度，Bullet 不再产生自转，
        // 位姿由 applyVelocityFacing() 每物理步按速度方向驱动
        body.setAngularFactor(Vector3f.ZERO);
        body.setAngularVelocity(Vector3f.ZERO);
        Quaternion initialFacing = facingFromVelocity(velocity);
        setRotation(initialFacing);
        transform = new Transform(position, initialFacing);
        oldTransform = transform.clone();
        body.setFriction(0f);
        body.setRestitution(0f);
        body.setCcdMotionThreshold(0.01f);
        body.setCcdSweptSphereRadius(type.getRadius());
    }

    /**
     * 将投射物注册到世界（两端的统一入口）。
     * <p>
     * 服务端：设置刚体位姿/速度/所有者 → {@link DestroyableRigidObject#addToLevel()} 添加刚体到物理世界
     * → 注册到 {@link ProjectileManager} SoA。
     * <br>
     * 客户端：仅注册到 {@link ObjectManager#levelDestroyableObjects} 和 SoA，不添加刚体到物理世界
     * （客户端投射物渲染走 SoA 而非刚体）。
     * <p>
     * <b>服务端网络广播：</b>已移至
     * {@link ProjectileManager#flushProjectileEntities()}（主线程 preTick），
     * 改由批量包 {@code ProjectilesSpawnPayload} 发送。
     * <p>
     * <b>调用线程：</b>物理线程（由 {@link io.github.sweetzonzi.machine_max.common.mech.projectile.type.KineticProjectileType#create} → addToLevel 链调用）。
     */
    @Override
    public void addToLevel() {
        super.addToLevel();
        body.setPhysicsLocation(getPosition());
        body.setPhysicsRotation(getRotation());
        body.setLinearVelocity(getLinearVelocity());
        PhysicsBodyExtensionKt.setOwner(body, this);

        // 重力系数：刚体入世时其重力会被物理世界重力覆盖，故在入世之后的后继任务中改写，
        // 得到 g = 世界重力 × gravity_factor，与质点弹的 -gravityFactor·9.81 语义一致。
        // gamma = 1 时世界重力即所求，无需改写。
        float gravityFactor = getGravityFactor();
        if (Math.abs(gravityFactor - 1f) > 1e-6f) {
            getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
                body.setGravity(body.getGravity(null).mult(gravityFactor));
                return null;
            });
        }

        ObjectManager.addDestroyableObject(this);
        ProjectileManager pm = ObjectManager.getOrCreateProjectileManager(level);
        pm.addRigidProjectile(this);

        // 注册碰撞回调（物理线程）：命中分派由 handleBulletCollision 负责
        PhysicsBodyExtensionKt.onCollideProcessed(body, event -> {
            handleBulletCollision(
                event.getO1(), event.getO2(),
                event.getO1Point(), event.getO2Point()
            );
            return null; // Unit
        });
        // 预碰撞：对已排除的目标取消碰撞响应，与 SubPart 的 onCollidePre 同一入口
        PhysicsBodyExtensionKt.onCollidePre(body, event ->
            !shouldIgnoreContact(event.getO2(), event.getO1Point()));
    }

    /**
     * 判断本次接触是否应被排除（预碰撞阶段，Bullet 求解前）。
     * <p>
     * 排除两类目标：
     * <ul>
     *   <li>无属主的渲染代理（{@link MMPartEntity} / {@link MMProjectileEntity}）</li>
     *   <li>已登记在穿透去重表中的目标——发射时预登记的发射者自身，
     *       或此前已经处理过的零件/实体（见 {@link ProjectileManager#hasPenetrated}）</li>
     * </ul>
     * 排除时把接触冲量与距离一并处理掉，使弹体既不被自己的挂架弹开，
     * 也不会对已处理过的目标产生二次冲量。
     * <p>
     * <b>调用线程：</b>物理线程。
     *
     * @param other  对方物理体
     * @param point1 自身接触点
     * @return true 表示应取消本次碰撞
     */
    private boolean shouldIgnoreContact(PhysicsCollisionObject other, ManifoldPoint point1) {
        Object otherOwner = PhysicsBodyExtensionKt.getOwner(other);
        if (otherOwner instanceof MMPartEntity || otherOwner instanceof MMProjectileEntity) return true;

        PenetrationKey key = PenetrationKey.fromCollision(other, point1.getTriangleIndex());
        if (key == null) return false;
        ProjectileManager pm = ObjectManager.levelProjectileManagers.get(level);
        if (pm == null || !pm.hasPenetrated(getId(), key)) return false;

        long pointId = point1.getId();
        ManifoldPoints.setAppliedImpulse(pointId, 0f);
        ManifoldPoints.setDistance1(pointId, 500f);
        return true;
    }

    /** 创建固定半径球体碰撞形状 */
    private static CompoundCollisionShape createCollisionShape(float radius) {
        CompoundCollisionShape shape = new CompoundCollisionShape();
        shape.addChildShape(new SphereCollisionShape(radius), Vector3f.ZERO);
        return shape;
    }

    @Override
    public KineticProjectileType getProjectileType() {
        return projectileType;
    }

    @Override
    public Vector3f getVelocity() {
        return getLinearVelocity();
    }

    @Override
    public boolean isAlive() {
        return !isRemoved && !hasHit;
    }

    @Override
    public int getLifetime() {
        return cachedLifetime;
    }

    @Override
    public int getMaxLifetime() {
        return projectileType.getMaxLifetimeTicks();
    }

    @Override
    public void markHit() {
        this.hasHit = true;
    }

    // ========== 覆写 DestroyableRigidObject 生命周期 ==========

    @Override
    public void preTick() {
        if (isRemoved) return;
        tickCount++;
        if (hurtTime > 0) hurtTime--;
        if (!level.isClientSide() && checkDestroyed()) {
            setDestroyed();
        }
    }

    /**
     * 覆写：仅从刚体同步位姿，不逐 tick syncToClient。
     * <p>
     * 投射物网络同步采用关键事件模式（创建/命中/超时），
     * 摧毁后立即清理，不走倒计时。
     */
    @Override
    public void postTick() {
        // 服务端：从刚体同步位姿/速度到 SynchedEntityData
        if (!level.isClientSide() && body.isInWorld()) {
            if (body.isActive() || body.isKinematic()) {
                updateLock = true;
                setPosition(body.getPhysicsLocation(null));
                setRotation(body.getPhysicsRotation(null));
                setLinearVelocity(body.getLinearVelocity(null));
                setAngularVelocity(body.getAngularVelocity(null));
                updateLock = false;
                // 碰撞处理已由 onCollideProcessed 回调接管
            }
        }
        if (isDestroyed() && getDestroyTime() <= 0) {
            this.destroy();
        }
    }

    /**
     * 覆写：先从 {@link ProjectileManager} 的 SoA 中标记摘除，再走基类销毁（摘除物理体）。
     * <p>
     * 命中销毁、寿命到期、超时清理三条路径最终都会经过这里，
     * 因此 SoA 不会残留死条目。
     */
    @Override
    public void destroy() {
        ProjectileManager pm = ObjectManager.levelProjectileManagers.get(level);
        if (pm != null) pm.removeProjectile(getId());
        super.destroy();
    }

    // ==================== 碰撞回调（Bullet 接触入口） ====================

    /**
     * 刚体碰撞事件处理入口（由 {@code onCollideProcessed} 回调）。
     * <p>
     * 按碰撞组过滤后分派到对应的 IProjectile 命中解析方法。
     * 命中结算与伤害仅在服务端进行：客户端的刚体是运动学体、位姿来自权威快照，
     * 其接触不构成命中。
     *
     * @param o1     自身物理体
     * @param o2     对方物理体
     * @param point1 自身接触点（ManifoldPoint）
     * @param point2 对方接触点（ManifoldPoint）
     */
    private void handleBulletCollision(
        PhysicsCollisionObject o1, PhysicsCollisionObject o2,
        ManifoldPoint point1, ManifoldPoint point2
    ) {
        if (level.isClientSide()) return;
        if (hasHit || isDestroyed()) return;

        // ① 碰撞组过滤：仅处理 TERRAIN / PHYSICS_BODY / PAWN
        int group = o2.getCollisionGroup();
        if (group != CollisionGroups.TERRAIN
            && group != CollisionGroups.PHYSICS_BODY
            && group != CollisionGroups.PAWN) {
            return;
        }

        // ② 获取接触点与法线
        Vector3f hitPointJme = new Vector3f();
        Vector3f hitNormalJme = new Vector3f();
        point1.getPositionWorld(hitPointJme);
        point2.getNormalWorld(hitNormalJme);  // 法线指向 o1（投射物自身）
        Vec3 hitPointMc = new Vec3(hitPointJme.x, hitPointJme.y, hitPointJme.z);
        Vec3 hitNormalMc = new Vec3(hitNormalJme.x, hitNormalJme.y, hitNormalJme.z);

        float currentPen = calculateCurrentPenetration();
        float currentDamage = calculateCurrentDamage();
        float currentSpeed = getSpeed();
        Object otherOwner = PhysicsBodyExtensionKt.getOwner(o2);

        AfterHitResult result = null;
        BlockPos terrainBlockPos = null;
        PenetrationKey hitPenKey = null;
        ProjectileManager pm = ObjectManager.getOrCreateProjectileManager(level);

        // ③ 按碰撞组分派
        if (group == CollisionGroups.TERRAIN) {
            // 地形：穿透去重 → onTerrainHit → 标记穿透
            if (otherOwner instanceof PhysicsChunkSection terrain) {
                BlockPos blockPos = terrain.getBlockPosFromContactPoint(
                    hitPointJme, hitNormalJme, -0.01f);
                SectionSnapshot.BlockSnapshot blockSnap = terrain.getBlockSnapshot(blockPos);
                if (blockSnap == null || terrain.isRemoved(blockPos)) return;
                BlockState blockState = blockSnap.getState();

                // 穿透去重检查
                if (!pm.hasPenetrated(getId(), blockPos)) {
                    result = onTerrainHit(level, blockPos, blockState,
                        currentPen, currentSpeed, hitPointMc, hitNormalMc);
                    if (result != null && !result.destroyed()) {
                        pm.markPenetrated(getId(), blockPos, terrain);
                    }
                    terrainBlockPos = blockPos;
                }
            }
        } else if (group == CollisionGroups.PHYSICS_BODY) {
            // 零件：过滤渲染代理后分派（刚体碰撞无 triangleIndex，传 null HitBox）
            if (otherOwner instanceof BFHurtTarget target
                && !(otherOwner instanceof MMPartEntity)
                && !(otherOwner instanceof MMProjectileEntity)) {
                // 穿透去重：发射时预登记的发射者自身、以及此前已处理过的零件，一律跳过结算
                hitPenKey = PenetrationKey.fromCollision(o2, point1.getTriangleIndex());
                if (hitPenKey != null && pm.hasPenetrated(getId(), hitPenKey)) return;
                result = onPartHit(level, target,
                    currentPen, currentDamage, hitPointMc, hitNormalMc, null);
            }
        } else if (group == CollisionGroups.PAWN) {
            // 实体：命中即销毁并结算动能伤害
            if (otherOwner instanceof Entity entity) {
                hitPenKey = PenetrationKey.fromCollision(o2, point1.getTriangleIndex());
                if (hitPenKey != null && pm.hasPenetrated(getId(), hitPenKey)) return;
                result = onEntityHit(level, entity,
                    currentPen, currentDamage, hitPointMc, hitNormalMc);
            }
        }

        // ④ 应用结果
        if (result != null) {
            applyHitResultAfterCollision(result, hitPointMc, hitNormalMc, terrainBlockPos, hitPenKey);
        }
    }

    /**
     * 刚体侧命中结果应用。
     * <p>
     * 将 AfterHitResult 转换为刚体状态变更：
     * <ul>
     *   <li>穿透（PassThrough）：回写速度到 Bullet 刚体，登记穿透密钥，广播穿透同步</li>
     *   <li>销毁（DESTROYED）：广播命中效果，标记销毁</li>
     * </ul>
     * 地形穿透去重由调用方 {@link #handleBulletCollision} 直接管理；
     * 零件/实体目标的去重密钥（{@code penKey}）在本方法登记。
     *
     * @param penKey 本次非地形命中的穿透密钥（地形命中与无属主目标为 null）
     */
    private void applyHitResultAfterCollision(AfterHitResult result,
        Vec3 hitPointMc, Vec3 hitNormalMc, @Nullable BlockPos hitBlockPos,
        @Nullable PenetrationKey penKey) {
        ProjectileManager pm = ObjectManager.getOrCreateProjectileManager(level);
        if (!result.destroyed()) {
            // 穿透后减速：回写 Bullet 刚体
            Vector3f newVel = result.newVelocity();
            setLinearVelocity(newVel);
            body.setLinearVelocity(newVel);
            // 登记去重密钥：同一目标在后续物理步不再重复结算
            if (penKey != null) pm.markPenetrated(getId(), penKey);
            pm.enqueueHitSync(getId(), hitPointMc, hitNormalMc, false, newVel, hitBlockPos);
        } else {
            pm.enqueueHitSync(getId(), hitPointMc, hitNormalMc, true, new Vector3f(), hitBlockPos);
            // 有战斗部 → 在命中点入队起爆请求（主线程由 ProjectileManager 冲刷执行）
            pm.enqueueWarheadDetonation(this, hitPointMc);
            markHit();
            destroy();
        }
    }

    // ==================== IProjectile 覆写 ====================

    /**
     * 刚体命中实体：先完成动能伤害结算，再返回 {@link AfterHitResult.DESTROYED}（命中即销毁）。
     * <p>
     * 与质点弹同口径：协议实体先经 {@code BFDamageApi.resolveHitTarget} 决议实际目标，
     * 决议到零件等非实体目标时在本物理步内同步结算；实体目标的伤害提交主线程执行
     * （{@code hurt} 与击退不允许在物理线程进行），上下文由
     * {@link IProjectile#buildHurtContext} 构造，稳定性折减、物理厚度与冲量口径与质点弹一致。
     * <p>
     * 与质点弹的区别是不做"命中暂停 / 恢复"：刚体弹不保留载体，异步结算只用于造成伤害，
     * 弹道无需回填。命中广播与战斗部起爆入队由拿到本结果的
     * {@link #applyHitResultAfterCollision} 统一执行；本方法只给出结果，
     * 自行入队会使同一次命中产生两次爆炸与两个命中包。
     */
    @Override
    public AfterHitResult onEntityHit(Level level, Entity entity,
        float currentPen, float currentDamage, Vec3 hitPoint, Vec3 hitNormal) {
        Vector3f velJme = body.getLinearVelocity(null);
        Vec3 hitVel = new Vec3(velJme.x, velJme.y, velJme.z);

        // 协议实体先决议实际命中目标
        if (BFDamageApi.isProtocolAware(entity)) {
            BFHitResolveResult resolved = BFDamageApi.resolveHitTarget(
                entity, hitPoint, hitVel.scale(1.0 / 20.0));
            if (resolved == null) return null; // 假阳性：继续飞行
            BFHurtTarget actual = resolved.actualTarget();
            if (!(actual instanceof Entity)) {
                // 决议到零件等非实体目标：物理线程内同步结算
                BFDamageExtensions exts = getProjectileType().getBaseExtensions();
                dealDamage(actual, buildHurtContext(level, currentDamage,
                    hitVel, resolved.correctedHitPoint(), resolved.correctedHitNormal(), exts));
                return AfterHitResult.DESTROYED;
            }
        }

        // 实体目标：弹道参数快照提交主线程结算（含击退）
        SparkLevel.submitImmediateTask(level, PPhase.POST, () -> {
            if (entity.isRemoved()) return;
            if (entity instanceof LivingEntity livingEntity) livingEntity.invulnerableTime = 0;
            BFDamageExtensions exts = getProjectileType().getBaseExtensions();
            exts.set(MMDamageExtensions.HIT_PHYSICAL_THICKNESS, entity.getBbWidth() * 1000f);
            dealDamage(entity, buildHurtContext(level, currentDamage, hitVel, hitPoint, hitNormal, exts));
        });
        return AfterHitResult.DESTROYED;
    }

    @Override
    public void prePhysicsTick() {
        super.prePhysicsTick();
        if (isRemoved) return;
        applyDragForce();       // 空气阻力：½·ρ(h)·Cd·A·v²，与质点弹同一模型
        applyGuidanceForce();   // 制导指令（中心力）
        applyVelocityFacing();  // 姿态：弹轴 = 速度方向
    }

    /**
     * 施加空气阻力，模型与质点弹统一：{@code F = ½·ρ(h)·Cd·π·r²·v²}，方向沿 −v̂。
     * <p>
     * 以中心力提交给 Bullet，与制导、重力共用同一条刚体动力学通道。
     * 密度取自 {@link ProjectileManager#getDensityAt}（海平面 y=62 处归一化为 1.0），
     * 因此高空阻力随密度衰减，两端与质点弹口径一致。
     * <p>
     * <b>调用线程：</b>物理线程（Bullet 步进前）。
     */
    private void applyDragForce() {
        float dragFactor = getDragFactor();
        if (dragFactor <= 1e-8f) return;

        Vector3f velocity = body.getLinearVelocity(null);
        float speed = velocity.length();
        if (speed < 1e-6f) return;

        ProjectileManager pm = ObjectManager.levelProjectileManagers.get(level);
        if (pm == null) return;
        Vector3f position = body.getPhysicsLocation(null);
        float rho = pm.getDensityAt(position.x, position.y, position.z);
        float radius = getRadius();
        float dragForce = 0.5f * rho * dragFactor * (float) Math.PI * radius * radius * speed * speed;
        if (dragForce <= 1e-6f) return;

        // 方向沿 −v̂：速度矢量按 dragForce/|v| 缩放后取负
        body.applyCentralForce(velocity.mult(-dragForce / speed));
    }

    /**
     * 按当前制导目标解算指令加速度，并以<b>中心力</b>提交给 Bullet 刚体。
     * <p>
     * 目标点存放在 {@link ProjectileManager} 的 SoA 中（{@code targetX/Y/Z}），由发射方的
     * 武器控制器每物理步推送，{@code NaN} 表示无目标（本步交回纯弹道）。解算走与质点弹
     * 完全相同的 {@code GuidanceLaw} 路径（含过载/转率限幅与诱导阻力），
     * 因此两种运动模型的制导行为一致。
     * <p>
     * 只提交制导项本身：重力由 Bullet 的刚体重力积分负责，在此叠加会重复计入。
     * {@code applyCentralForce} 会累加进总力并唤醒休眠刚体，比直接写线速度更贴合刚体动力学。
     * <p>
     * <b>调用线程：</b>物理线程（Bullet 步进前）。
     */
    private void applyGuidanceForce() {
        if (!projectileType.hasGuidance()) return;

        ProjectileManager pm = ObjectManager.levelProjectileManagers.get(level);
        if (pm == null) return;
        int idx = pm.findIndexByObjId(getId());
        if (idx < 0) return;
        float targetX = pm.targetX[idx];
        if (Float.isNaN(targetX)) return;

        Vector3f position = body.getPhysicsLocation(null);
        Vector3f velocity = body.getLinearVelocity(null);
        GuidanceContext ctx = new GuidanceContext(
            position.x, position.y, position.z,
            velocity.x, velocity.y, velocity.z,
            targetX, pm.targetY[idx], pm.targetZ[idx],
            getMass(), getRadius(),
            pm.getDensityAt(position.x, position.y, position.z));

        if (projectileType.getGuidance().computeAcceleration(ctx, guidanceAccel)) {
            body.applyCentralForce(guidanceAccel.mult(getMass()));
        }
    }

    /**
     * 按当前速度方向刷新刚体姿态：弹轴（本地 {@code -Z}）对齐速度方向。
     * <p>
     * 角速度因子已在构造时置零，Bullet 不产生自转；此处每步再清零角速度，
     * 清除构造后可能残留的角速度，避免其与手动姿态争夺。速度近似为零时保持上一姿态，
     * 避免方向退化。
     * <p>
     * <b>调用线程：</b>物理线程。
     */
    private void applyVelocityFacing() {
        body.setAngularVelocity(Vector3f.ZERO);
        Vector3f velocity = body.getLinearVelocity(null);
        if (velocity.lengthSquared() < 1e-8f) return;
        body.setPhysicsRotation(facingFromVelocity(velocity));
    }

    /**
     * 计算"模型前向轴（本地 {@code -Z}）指向给定方向"的旋转。
     * <p>
     * 项目约定模型前向为本地 {@code -Z}（见
     * {@link io.github.sweetzonzi.machine_max.common.mech.DestroyableObject#getFrontVector()}），
     * 因此以速度反方向作为本地 {@code +Z} 的映射目标构造正交基，再交给
     * {@link Quaternion#fromAxes(Vector3f, Vector3f, Vector3f)}。方向退化为零矢量时返回恒等旋转。
     * <p>
     * 服务端用本函数写入刚体姿态；客户端渲染侧用同一函数从 SoA 速度推导朝向，
     * 两端规则一致，姿态无需随网络传输。
     *
     * @param velocity 速度矢量（JME，m/s）
     * @return 使本地 {@code -Z} 对齐速度方向的旋转
     */
    public static Quaternion facingFromVelocity(Vector3f velocity) {
        float speedSq = velocity.lengthSquared();
        if (speedSq < 1e-8f) return new Quaternion();
        Vector3f back = velocity.mult(-1f / (float) Math.sqrt(speedSq));
        // 参考轴与弹轴近似平行时换轴，避免叉积退化
        Vector3f reference = Math.abs(back.y) < 0.999f ? Vector3f.UNIT_Y : Vector3f.UNIT_Z;
        Vector3f right = reference.cross(back).normalize();
        Vector3f up = back.cross(right).normalize();
        return new Quaternion().fromAxes(right, up, back);
    }

    @Override
    public void postPhysicsTick() {
        // 将 Bullet 刚体状态回写到 SoA（物理线程，供渲染器和其他模块读取）
        if (!level.isClientSide() && body.isInWorld()) {
            ProjectileManager pm = ObjectManager.levelProjectileManagers.get(level);
            if (pm != null) {
                pm.writebackRigidState(getId(),
                    body.getPhysicsLocation(null),
                    body.getLinearVelocity(null));
            }
        }
    }

    /** 覆写：基于 hasHit / SoA 寿命判断摧毁 */
    @Override
    protected boolean checkDestroyed() {
        return !isDestroyed() && (hasHit || getLifetime() <= 0);
    }

    /** 覆写：跳过摧毁倒计时 */
    @Override
    protected void setDestroyed() {
        getSyncedData().set(DATA_DESTROYED_ID, true);
        getSyncedData().set(DESTROY_TIME_ID, 0);
    }

    /** 覆写为空操作：投射物不需要摧毁倒计时推进 */
    @Override
    protected void tickDestroyTimer(int tick) {
    }

    /** 覆写为空操作：投射物不接收伤害累积 */
    @Override
    protected void handleAccumulatedDamage() {
    }

    /** 覆写为空操作：投射物不接收伤害累积 */
    @Override
    public void accumulateDamage(float damage, BFDamageContext ctx) {
    }

    @Override
    public float getMaxDurability() {
        return 1;
    }

    // ========== IAnimatable 实现 ==========

    @Override
    public RigidProjectile getAnimatable() {
        return this;
    }

    @Override
    public Level getAnimLevel() {
        return level;
    }

    private ModelIndex defaultModelIndex;

    @Override
    public ModelIndex getDefaultModelIndex() {
        if (defaultModelIndex == null) {
            ResourceLocation key = projectileType.getRegistryKey();
            defaultModelIndex = new ModelIndex("projectile", key != null ? key
                    : ResourceLocation.fromNamespaceAndPath("machine_max", "rigid_default"));
        }
        return defaultModelIndex;
    }

    @Override
    public AnimController getAnimController() {
        if (animController == null) {
            animController = new AnimController(this);
        }
        return animController;
    }

    @Override
    public ModelController getModelController() {
        if (modelController == null) {
            modelController = new ModelController(this);
        }
        return modelController;
    }

    @Override
    public Map<String, Object> getVariables() {
        return variables;
    }

    // ========== BFHurtTarget 实现 ==========

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public BFDamageContext createContextFromVanilla(DamageSource source, float amount) {
        return null;
    }

    @Override
    public ArmorLevel getArmorLevel(BFDamageContext ctx) {
        return ArmorLevel.UNARMORED_1;
    }

    // ========== 稳定性状态（SoA 数组支持） ==========

    @Override
    public float getRemainingStableDistance() {
        ProjectileManager pm = ObjectManager.levelProjectileManagers.get(level);
        if (pm == null) return 0;
        int idx = pm.findIndexByObjId(getId());
        return (idx >= 0) ? pm.remainingStableDistance[idx] : 0;
    }

    @Override
    public void setRemainingStableDistance(float v) {
        ProjectileManager pm = ObjectManager.levelProjectileManagers.get(level);
        if (pm == null) return;
        int idx = pm.findIndexByObjId(getId());
        if (idx >= 0) pm.remainingStableDistance[idx] = v;
    }
}
