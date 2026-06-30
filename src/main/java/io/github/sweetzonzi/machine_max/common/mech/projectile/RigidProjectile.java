package io.github.sweetzonzi.machine_max.common.mech.projectile;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.body.ManifoldPoint;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.terrain.PhysicsChunkSection;
import cn.solarmoon.spark_core.physics.terrain.SectionSnapshot;
import cn.solarmoon.spark_core.physics.PhysicsHost;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.entity.MMProjectileEntity;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.ballistics_framework.api.ArmorLevel;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;
import io.github.sweetzonzi.ballistics_framework.api.BFHurtTarget;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableRigidObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;

import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
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
 * 空气阻力在 {@link #prePhysicsTick()} 中手动施加（速度²阻力模型）。
 * <p>
 * 阶段一使用固定半径球体碰撞形状。
 */
public class RigidProjectile extends DestroyableRigidObject implements IProjectile, IAnimatable<RigidProjectile> {

    private final ProjectileType projectileType;
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
    public RigidProjectile(Level level, ProjectileType type, Vector3f position, Vector3f velocity) {
        super(level, createCollisionShape(type.getRadius()), type.getMass());
        this.projectileType = type;

        setPosition(position);
        setLinearVelocity(velocity);
        transform = new Transform(position, Quaternion.IDENTITY);
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
     * <b>调用线程：</b>物理线程（由 {@link io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType#create} → addToLevel 链调用）。
     */
    @Override
    public void addToLevel() {
        super.addToLevel();
        body.setPhysicsLocation(getPosition());
        body.setLinearVelocity(getLinearVelocity());
        PhysicsBodyExtensionKt.setOwner(body, this);
        ObjectManager.addDestroyableObject(this);
        ProjectileManager pm = ObjectManager.getOrCreateProjectileManager(level);
        pm.addRigidProjectile(this);

        // 注册碰撞回调（物理线程），替换原有的 checkBodyCollision 轮询
        PhysicsBodyExtensionKt.onCollideProcessed(body, event -> {
            handleBulletCollision(
                event.getO1(), event.getO2(),
                event.getO1Point(), event.getO2Point()
            );
            return null; // Unit
        });
    }

    /** 创建固定半径球体碰撞形状 */
    private static CompoundCollisionShape createCollisionShape(float radius) {
        CompoundCollisionShape shape = new CompoundCollisionShape();
        shape.addChildShape(new SphereCollisionShape(radius), Vector3f.ZERO);
        return shape;
    }

    @Override
    public ProjectileType getProjectileType() {
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

    // ==================== 碰撞回调（替换 checkBodyCollision 轮询） ====================

    /**
     * 刚体碰撞事件处理入口（由 {@code onCollideProcessed} 回调）。
     * <p>
     * 按碰撞组过滤后分派到对应的 IProjectile 命中解析方法。
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
                ProjectileManager pm = ObjectManager.getOrCreateProjectileManager(level);
                if (!pm.hasPenetrated(getId(), blockPos)) {
                    result = onTerrainHit(level, blockPos, blockState,
                        currentPen, currentSpeed, hitPointMc, hitNormalMc);
                    if (result != null && !result.destroyed()) {
                        pm.markPenetrated(getId(), blockPos, terrain);
                    }
                }
            }
        } else if (group == CollisionGroups.PHYSICS_BODY) {
            // 零件：过滤渲染代理后分派
            if (otherOwner instanceof BFHurtTarget target
                && !(otherOwner instanceof MMPartEntity)
                && !(otherOwner instanceof MMProjectileEntity)) {
                result = onPartHit(level, target,
                    currentPen, currentDamage, hitPointMc, hitNormalMc);
            }
        } else if (group == CollisionGroups.PAWN) {
            // 实体：阶段一覆写直接返回 DESTROYED
            if (otherOwner instanceof Entity entity) {
                result = onEntityHit(level, entity,
                    currentPen, currentDamage, hitPointMc, hitNormalMc);
            }
        }

        // ④ 应用结果
        if (result != null) {
            applyHitResultAfterCollision(result, hitPointMc, hitNormalMc);
        }
    }

    /**
     * 刚体侧命中结果应用。
     * <p>
     * 将 AfterHitResult 转换为刚体状态变更：
     * <ul>
     *   <li>穿透（PassThrough）：回写速度到 Bullet 刚体，广播穿透同步</li>
     *   <li>销毁（DESTROYED）：广播命中效果，标记销毁</li>
     * </ul>
     * 穿透去重由调用方 {@link #handleBulletCollision} 通过
     * {@link ProjectileManager#hasPenetrated}/{@link ProjectileManager#markPenetrated} 管理。
     */
    private void applyHitResultAfterCollision(AfterHitResult result,
        Vec3 hitPointMc, Vec3 hitNormalMc) {
        if (!result.destroyed()) {
            // 穿透后减速：回写 Bullet 刚体
            Vector3f newVel = result.newVelocity();
            setLinearVelocity(newVel);
            body.setLinearVelocity(newVel);
            ProjectileManager pm = ObjectManager.getOrCreateProjectileManager(level);
            pm.enqueueHitSync(getId(), hitPointMc, hitNormalMc, false, newVel, false);
        } else {
            ProjectileManager pm = ObjectManager.getOrCreateProjectileManager(level);
            pm.enqueueHitSync(getId(), hitPointMc, hitNormalMc, true, new Vector3f(), false);
            markHit();
            destroy();
        }
    }

    // ==================== IProjectile 覆写 ====================

    /**
     * 阶段一：刚体命中实体的简化处理。
     * <p>
     * 不走异步管线，命中即销毁并广播。
     * 待整体异步命中框架就绪后移除覆写，统一使用 {@link IProjectile#onEntityHit} 默认实现。
     */
    @Override
    public AfterHitResult onEntityHit(Level level, Entity entity,
        float currentPen, float currentDamage, Vec3 hitPoint, Vec3 hitNormal) {
        ProjectileManager pm = ObjectManager.getOrCreateProjectileManager(level);
        pm.enqueueHitSync(getId(), hitPoint, hitNormal, true, new Vector3f(), false);
        return AfterHitResult.DESTROYED;
    }

    @Override
    public void prePhysicsTick() {
        super.prePhysicsTick();
        if (isRemoved) return;
        // 手动施加空气阻力（速度²阻力模型）
        float speed = body.getLinearVelocity(null).length();
        float dragForce = getDragFactor() * speed * speed;
        if (dragForce > 1e-6f && speed > 1e-6f) {
            Vector3f dragDir = body.getLinearVelocity(null).normalize().multLocal(-1);
            Vector3f dragAcc = dragDir.multLocal(dragForce / getMass());
            body.setLinearVelocity(body.getLinearVelocity(null)
                .add(dragAcc.multLocal(1f / getPhysicsLevel().getTps())));
        }
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

    @Override
    public void onBoneUpdate(cn.solarmoon.spark_core.event.BoneUpdateEvent event) {
        IAnimatable.super.onBoneUpdate(event);
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
}
