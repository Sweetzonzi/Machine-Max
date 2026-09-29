package io.github.sweetzonzi.machine_max.common.entity;

import cn.solarmoon.spark_core.EntityPatch;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.physics.PhysicsHost;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.ballistics_framework.api.BFHitResolveResult;
import io.github.sweetzonzi.ballistics_framework.api.BFHitResolver;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.BallisticProjectile;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileManager;
import lombok.Getter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import org.jetbrains.annotations.Nullable;

/**
 * 轻量 Entity 兼容层 —— 代表 SOA 中一个投射物在 Minecraft 世界的"投影"。
 * <p>
 * 自身不承担物理计算，位置/速度从关联的 {@link BallisticProjectile} 对象拉取，
 * 仅作为模组兼容性外壳，使其他模组可通过 {@code instanceof Projectile}
 * 和 {@code level.getProjectiles()} 识别 Machine-Max 投射物。
 * <p>
 * 服务端由 {@link ProjectileManager#createProjectileEntity(int)} 创建并
 * 通过 {@link #bindToProjectile(BallisticProjectile)} 直接持有对象引用；
 * 客户端通过 {@link IEntityWithComplexSpawn} 携带的 objId 反查
 * {@link ObjectManager#levelDestroyableObjects} 获取引用。
 * <p>
 * 本类实现 {@link BFHitResolver}：原版与其他模组的投射物命中这个实体投影时，
 * 伤害经框架的协议外转发落到 {@link BallisticProjectile} 本体（见
 * {@link #resolveHit(Vec3, Vec3)}）。这条通路要求实体先能被投射物选中，
 * 因此 {@link #isPickable()} 返回 true。
 * <p>
 * 网络同步：见设计文档 §8.4.3。EntityTracker 周期性位置同步被
 * {@code updateInterval(Int.MAX_VALUE)} 禁用，仅登场/退场/DataTracker。
 */
public class MMProjectileEntity extends Projectile implements IEntityWithComplexSpawn, EntityPatch, BFHitResolver {
    @Getter
    private BallisticProjectile projectile;
    private volatile boolean orphaned;
    private int projectileObjId = -1;

    public MMProjectileEntity(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    public void bindToProjectile(BallisticProjectile projectile) {
        this.projectile = projectile;
        this.projectileObjId = projectile.getId();
        this.orphaned = false;
    }

    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        buffer.writeInt(projectileObjId);
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
        this.projectileObjId = additionalData.readInt();
    }

    private void tryBindProjectile() {
        if (projectileObjId < 0 || projectile != null) return;
        DestroyableObject obj = ObjectManager.getDestroyableObject(level(), projectileObjId);
        if (obj instanceof BallisticProjectile proj) {
            this.projectile = proj;
        }
    }

    @Override
    public boolean shouldCreateDefaultPhysicsBody() {
        return false;
    }

    @Override
    public void baseTick() {
        super.baseTick();
        if (orphaned) return;

        if (projectile == null) {
            tryBindProjectile();
            if (projectile == null) {
                if (tickCount > 100) {
                    MachineMax.LOGGER.warn("MMProjectileEntity 未匹配到 BallisticProjectile (objId={})，已移除", projectileObjId);
                    markOrphaned();
                }
                return;
            }
        }

        if (!projectile.isAlive()) {
            markOrphaned();
            return;
        }

        Vector3f pos = projectile.getPosition();
        Vector3f vel = projectile.getVelocity();
        this.setPos(pos.x, pos.y, pos.z);

        double dx = vel.x, dy = vel.y, dz = vel.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        this.setRot(yaw, pitch);
        this.setDeltaMovement(dx * 0.05, dy * 0.05, dz * 0.05);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        if (!level().isClientSide && !orphaned && projectile != null
                && reason == RemovalReason.UNLOADED_TO_CHUNK) {
            ProjectileManager pm = ObjectManager.levelProjectileManagers.get(level());
            if (pm != null) {
                int idx = pm.findIndexByObjId(projectileObjId);
                if (idx >= 0) {
                    pm.needsEntityRecreate[idx] = true;
                    MachineMax.LOGGER.debug("投射物飞入未加载区块，标记 needsEntityRecreate (objId={})", projectileObjId);
                }
            }
        }
    }

    @Override
    public void move(MoverType type, Vec3 delta) {
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    /**
     * 返回 true，使本实体进入原版投射物的命中判定链。
     * <p>
     * 原版 {@code Projectile.canHitEntity} 的判据链是
     * {@code Entity.canBeHitByProjectile()} → {@code isPickable()}，
     * 返回 false 时原版箭矢永远不会为本实体生成 {@code EntityHitResult}，
     * {@link #resolveHit(Vec3, Vec3)} 也就没有触发机会。
     * <p>
     * 副作用是本实体同时进入玩家准星与近战的实体选取——这正是
     * "外部攻击者可以命中投射物"需要的行为。
     */
    @Override
    public boolean isPickable() {
        return true;
    }

    /**
     * 把命中原版实体投影的伤害解析到投射物本体。
     * <p>
     * 契约要求本方法是幂等且无副作用的纯查询：同一组 {@code (hitPoint, delta)}
     * 必须始终返回同一结果，否则投射物会在"继续飞行"与"销毁"之间反复。
     * 返回 null 表示实际未命中（投射物已不存在），交回原版流程。
     *
     * @param hitPoint 原版报告的命中点（世界坐标）
     * @param delta    搜索矢量，其模为搜索距离上限（m）
     * @return 解析到投射物本体的结果；投射物已不存在时返回 null
     */
    @Override
    @Nullable
    public BFHitResolveResult resolveHit(Vec3 hitPoint, Vec3 delta) {
        BallisticProjectile p = projectile;
        if (p == null || !p.isAlive()) return null;
        return new BFHitResolveResult(p, hitPoint, Vec3.ZERO);
    }

    public void markOrphaned() {
        orphaned = true;
        this.remove(RemovalReason.DISCARDED);
    }

    /**
     * @return 投射物模型控制器，委托至关联的 {@link BallisticProjectile#getModelController()}。
     * 投射物本身（{@link BallisticProjectile}）实现
     * {@link cn.solarmoon.spark_core.animation.IAnimatable}，拥有真实的
     * {@link ModelController}；未实现时返回 null，渲染器将跳过渲染。
     */
    public ModelController getModelController() {
        return projectile != null ? projectile.getModelController() : null;
    }
}
