package io.github.sweetzonzi.machine_max.common.entity;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.IProjectile;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

/**
 * 轻量 Entity 兼容层 —— 代表 SOA 中一个投射物在 Minecraft 世界的"投影"。
 * <p>
 * 自身不承担物理计算，位置/速度从关联的 {@link IProjectile} 对象拉取，
 * 仅作为模组兼容性外壳，使其他模组可通过 {@code instanceof Projectile}
 * 和 {@code level.getProjectiles()} 识别 Machine-Max 投射物。
 * <p>
 * 服务端由 {@link ProjectileManager#createProjectileEntity(int)} 创建并
 * 通过 {@link #bindToProjectile(IProjectile)} 直接持有对象引用；
 * 客户端通过 {@link IEntityWithComplexSpawn} 携带的 objId 反查
 * {@link ObjectManager#levelDestroyableObjects} 获取引用。
 * <p>
 * 网络同步：见设计文档 §8.4.3。EntityTracker 周期性位置同步被
 * {@code updateInterval(Int.MAX_VALUE)} 禁用，仅登场/退场/DataTracker。
 */
public class ProjectileEntity extends Projectile implements IEntityWithComplexSpawn {

    private IProjectile projectile;
    private volatile boolean orphaned;
    private int projectileObjId = -1;

    public ProjectileEntity(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    public void bindToProjectile(IProjectile projectile) {
        this.projectile = projectile;
        this.projectileObjId = ((DestroyableObject) projectile).getId();
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
        if (obj instanceof IProjectile proj) {
            this.projectile = proj;
            MachineMax.LOGGER.debug("ProjectileEntity 成功绑定到 IProjectile(objId={})", projectileObjId);
        }
    }

    @Override
    public void baseTick() {
        super.baseTick();
        if (orphaned) return;

        if (projectile == null) {
            tryBindProjectile();
            if (projectile == null) {
                if (tickCount > 100) {
                    MachineMax.LOGGER.warn("ProjectileEntity 未匹配到 IProjectile (objId={})，已移除", projectileObjId);
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

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isAlwaysTicking() {
        return projectile != null && projectile.isAlive() && !isRemoved();
    }

    public void markOrphaned() {
        orphaned = true;
        this.remove(RemovalReason.DISCARDED);
    }
}
