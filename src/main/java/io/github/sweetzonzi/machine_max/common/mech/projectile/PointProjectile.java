package io.github.sweetzonzi.machine_max.common.mech.projectile;

import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.ballistics_framework.api.ArmorLevel;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;
import io.github.sweetzonzi.ballistics_framework.api.BFHurtTarget;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class PointProjectile extends DestroyableObject implements IProjectile {

    private final ProjectileType projectileType;
    private boolean hasHit = false;
    private int lifetime;

    public PointProjectile(Level level, ProjectileType type, Vector3f position, Vector3f velocity) {
        super(level);
        this.projectileType = type;
        this.lifetime = type.getMaxLifetimeTicks();
        setPosition(position);
        setLinearVelocity(velocity);

        if (!level.isClientSide()) {
            addToLevel();
            ObjectManager.getOrCreateProjectileManager(level).addPointProjectile(this);
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
        if (!level.isClientSide()) {
            if (isDestroyed()) {
                tickDestroyTimer(1);
            }
            syncToClient();
        }
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

    @Override
    protected boolean checkDestroyed() {
        return !isDestroyed() && (hasHit || lifetime <= 0);
    }

    @Override
    protected void setDestroyed() {
        getSyncedData().set(DATA_DESTROYED_ID, true);
        getSyncedData().set(DESTROY_TIME_ID, 0);
    }

    @Override
    protected void tickDestroyTimer(int tick) {
    }

    @Override
    protected void handleAccumulatedDamage() {
    }

    @Override
    public void accumulateDamage(float damage, BFDamageContext ctx) {
    }

    @Override
    public float getMaxDurability() {
        return 1;
    }

    @Override
    public @NotNull PhysicsLevel getPhysicsLevel() {
        throw new UnsupportedOperationException("PointProjectile has no physics body");
    }

    @Override
    protected void defineSyncedData(SynchedEntityData.Builder builder) {
    }

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
