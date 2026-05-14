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

public class RigidProjectile extends DestroyableRigidObject implements IProjectile {

    private final ProjectileType projectileType;
    private boolean hasHit = false;
    private int lifetime;

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
        float speed = body.getLinearVelocity(null).length();
        float dragForce = getDragFactor() * speed * speed;
        if (dragForce > 1e-6f && speed > 1e-6f) {
            Vector3f dragDir = body.getLinearVelocity(null).normalize().multLocal(-1);
            Vector3f dragAcc = dragDir.multLocal(dragForce / getMass());
            body.setLinearVelocity(body.getLinearVelocity(null).add(dragAcc.multLocal(1f / getPhysicsLevel().getTps())));
        }
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
    public void destroy() {
        super.destroy();
    }

    @Override
    protected void defineSyncedData(SynchedEntityData.Builder builder) {
        builder.define(IS_ACTIVE_ID, true);
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
