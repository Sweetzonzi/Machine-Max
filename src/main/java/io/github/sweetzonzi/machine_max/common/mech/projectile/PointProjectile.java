package io.github.sweetzonzi.machine_max.common.mech.projectile;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.ballistics_framework.api.ArmorLevel;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.network.payload.projectile.ProjectileSpawnPayload;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * 质点投射物。
 * <p>
 * 适用于小口径穿甲弹、APFSDS 长杆弹等高速投射物。
 * 没有 JME 物理刚体，不受 Bullet 管理，运动由 {@link ProjectileManager} 的 SoA 批量积分驱动。
 * <p>
 * 继承 {@link DestroyableObject} 以复用其生命周期管理（自动注册/注销于
 * {@link ObjectManager#levelDestroyableObjects}），但覆写了所有摧毁倒计时相关方法
 * （投射物命中即消失，无需倒计时）。
 * <p>
 * 碰撞检测在 {@link ProjectileManager#updatePointProjectiles} 中通过 JME rayTest 完成。
 */
public class PointProjectile extends DestroyableObject implements IProjectile, IAnimatable<PointProjectile> {

    private final ProjectileType projectileType;
    private boolean hasHit = false;

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
     * 创建一个质点投射物。
     * <p>
     * 服务端：注册到 {@link ObjectManager} 和 {@link ProjectileManager} 的 SoA 数组。
     * 客户端：仅创建实例等待服务端同步。
     *
     * @param level    维度
     * @param type     投射物类型定义
     * @param position 初始世界坐标（JME）
     * @param velocity 初始速度矢量（JME，单位 m/s）
     */
    public PointProjectile(Level level, ProjectileType type, Vector3f position, Vector3f velocity) {
        super(level);
        this.projectileType = type;
        setPosition(position);
        setLinearVelocity(velocity);
        transform = new Transform(position, Quaternion.IDENTITY);
        oldTransform = transform.clone();
    }

    /**
     * 将投射物注册到世界（两端的统一入口）。
     * <p>
     * {@link DestroyableObject#addToLevel()} → 注册到 {@link ObjectManager#levelDestroyableObjects}
     * → 注册到 {@link ProjectileManager} SoA 数组
     * → 服务端广播 {@link ProjectileSpawnPayload} 到客户端。
     */
    @Override
    public void addToLevel() {
        super.addToLevel();
        ProjectileManager pm = ObjectManager.getOrCreateProjectileManager(level);
        pm.addPointProjectile(this);
        if (!level.isClientSide()) {
            ProjectileSpawnPayload.broadcast(level, getId(), projectileType.getRegistryKey(),
                getPosition(), getLinearVelocity(), projectileType.getMaxLifetimeTicks(), false);
        }
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

    /**
     * 返回剩余存活 tick 数。
     * <p>
     * 寿命的权威来源是 {@link ProjectileManager} 的 SoA 数组，
     * {@link ProjectileManager#tickAndPreTick()} 在每 tick 将 SoA 中的寿命
     * 写入 {@link #cachedLifetime}，避免在此处进行 O(n) 线性扫描。
     */
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

    // ========== 覆写 DestroyableObject 生命周期 ==========

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
     * 覆写：基于 hasHit / SoA 寿命判断摧毁，而非耐久度。
     * 寿命权威来源为 {@link ProjectileManager} SoA 数组。
     */
    @Override
    protected boolean checkDestroyed() {
        return !isDestroyed() && (hasHit || getLifetime() <= 0);
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

    /**
     * 质点投射物无物理刚体，调用此方法将抛出异常。
     */
    @Override
    public @NotNull PhysicsLevel getPhysicsLevel() {
        throw new UnsupportedOperationException("PointProjectile has no physics body");
    }

    @Override
    protected void defineSyncedData(SynchedEntityData.Builder builder) {
    }

    // ========== IAnimatable 实现 ==========

    @Override
    public PointProjectile getAnimatable() {
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
                    : ResourceLocation.fromNamespaceAndPath("machine_max", "point_default"));
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
