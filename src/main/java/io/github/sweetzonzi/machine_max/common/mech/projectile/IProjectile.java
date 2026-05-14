package io.github.sweetzonzi.machine_max.common.mech.projectile;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageApi;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;
import io.github.sweetzonzi.ballistics_framework.api.BFHurtTarget;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.phys.Vec3;

public interface IProjectile {

    Vector3f getPosition();
    Vector3f getVelocity();
    Vector3f getFrontVector();

    default float getSpeed() {
        return getVelocity().length();
    }

    default Vector3f getDirection() {
        Vector3f vel = getVelocity();
        if (vel.lengthSquared() < 1e-12f) return getFrontVector();
        return vel.normalize();
    }

    ProjectileType getProjectileType();

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

    boolean isAlive();
    int getLifetime();
    int getMaxLifetime();
    void markHit();

    default float calculateCurrentPenetration() {
        float coeff = getPenetrationVelocityCoefficient();
        if (Math.abs(coeff) < 1e-6f) return getBasePenetration();
        float baseV = Math.max(getBaseVelocity(), 1e-6f);
        return getBasePenetration() * (float) Math.pow(getSpeed() / baseV, coeff);
    }

    default float calculateCurrentDamage() {
        float coeff = getDamageVelocityCoefficient();
        if (Math.abs(coeff) < 1e-6f) return getBaseDamage();
        float baseV = Math.max(getBaseVelocity(), 1e-6f);
        return getBaseDamage() * (float) Math.pow(getSpeed() / baseV, coeff);
    }

    default float dealDamage(BFHurtTarget target, Vec3 hitPoint, Vec3 hitNormal) {
        DamageSource source = target.getBFEntity().damageSources().generic();
        BFDamageContext ctx = BFDamageContext.builder()
            .source(source)
            .baseDamage(calculateCurrentDamage())
            .penetration(calculateCurrentPenetration())
            .hitVelocity(new Vec3(getVelocity().x, getVelocity().y, getVelocity().z))
            .hitPoint(hitPoint)
            .hitNormal(hitNormal)
            .build();
        return BFDamageApi.hurt(target, ctx);
    }
}
