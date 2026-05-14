package io.github.sweetzonzi.machine_max.common.mech.projectile;

import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.ballistics_framework.api.ArmorLevel;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;
import io.github.sweetzonzi.ballistics_framework.api.BFHurtTarget;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableRigidObject;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

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
public class RigidProjectile extends DestroyableRigidObject implements IProjectile {

    private final ProjectileType projectileType;
    private boolean hasHit = false;
    private int lifetime;

    /**
     * 创建一个刚体投射物。
     * <p>
     * 服务端：创建球体 CompoundCollisionShape → 初始化刚体 → 设置 CCD（连续碰撞检测）
     * → 注册到物理世界。
     *
     * @param level    维度
     * @param type     投射物类型定义
     * @param position 初始世界坐标（JME）
     * @param velocity 初始速度矢量（JME，单位 m/s）
     */
    public RigidProjectile(Level level, ProjectileType type, Vector3f position, Vector3f velocity) {
        super(level, createCollisionShape(type.getRadius()), type.getMass());
        this.projectileType = type;
        this.lifetime = type.getMaxLifetimeTicks();

        setPosition(position);
        setLinearVelocity(velocity);
        body.setFriction(0f);
        body.setRestitution(0f);
        body.setCcdMotionThreshold(0.01f);
        body.setCcdSweptSphereRadius(type.getRadius());

        if (!level.isClientSide()) {
            body.setPhysicsLocation(position);
            body.setLinearVelocity(velocity);
            PhysicsBodyExtensionKt.setOwner(body, this);
            addToLevel();
        }
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
        return lifetime;
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
        lifetime--;
        if (!level.isClientSide() && checkDestroyed()) {
            setDestroyed();
        }
    }

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
                checkBodyCollision();
            }
        }
        if (!level.isClientSide()) {
            if (isDestroyed()) {
                super.tickDestroyTimer(1);
            }
            syncToClient();
        }
        if (isDestroyed() && getDestroyTime() <= 0) {
            this.destroy();
        }
    }

    /**
     * 检测刚体是否发生了碰撞。
     * <p>
     * 通过 {@link com.jme3.bullet.objects.PhysicsRigidBody#isColliding} 标志检测，
     * 当刚体与其他物体接触时该标志为 true。
     * 阶段一简化实现：命中后直接销毁，不区分命中目标类型。
     */
    private void checkBodyCollision() {
        if (hasHit || isDestroyed()) return;
        if (!body.isColliding) return;

        Vector3f hitPointJme = body.getPhysicsLocation(null);
        Vec3 hitPointMc = new Vec3(hitPointJme.x, hitPointJme.y, hitPointJme.z);
        Vec3 hitNormalMc = new Vec3(0, 1, 0);

        ProjectileManager.spawnHitVisualEffect(level, hitPointMc, hitNormalMc, false);
        markHit();
        setDestroyed();
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
    }

    /** 覆写：基于 hasHit / lifetime 判断摧毁 */
    @Override
    protected boolean checkDestroyed() {
        return !isDestroyed() && (hasHit || lifetime <= 0);
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

    @Override
    public void destroy() {
        super.destroy();
    }

    @Override
    protected void defineSyncedData(SynchedEntityData.Builder builder) {
        builder.define(IS_ACTIVE_ID, true);
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
