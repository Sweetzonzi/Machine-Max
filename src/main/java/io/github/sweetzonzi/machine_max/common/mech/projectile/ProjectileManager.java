package io.github.sweetzonzi.machine_max.common.mech.projectile;

import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.ballistics_framework.api.BFHurtTarget;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.network.payload.ProjectileHitEffectPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;

public class ProjectileManager {

    private final Level level;

    public float[] posX, posY, posZ;
    public float[] velX, velY, velZ;
    public int[] lifetime;
    public int[] typeIndex;
    public int[] objId;
    public boolean[] alive;
    public int count;
    private int capacity = 256;

    public ProjectileManager(Level level) {
        this.level = level;
        posX = new float[capacity];
        posY = new float[capacity];
        posZ = new float[capacity];
        velX = new float[capacity];
        velY = new float[capacity];
        velZ = new float[capacity];
        lifetime = new int[capacity];
        typeIndex = new int[capacity];
        objId = new int[capacity];
        alive = new boolean[capacity];
    }

    public Level getLevel() {
        return level;
    }

    public void addPointProjectile(PointProjectile p) {
        ensureCapacity(count + 1);
        int i = count++;
        Vector3f pos = p.getPosition();
        Vector3f vel = p.getVelocity();
        posX[i] = pos.x;
        posY[i] = pos.y;
        posZ[i] = pos.z;
        velX[i] = vel.x;
        velY[i] = vel.y;
        velZ[i] = vel.z;
        lifetime[i] = p.getMaxLifetime();
        typeIndex[i] = -1;
        objId[i] = p.getId();
        alive[i] = true;
    }

    public void removePointProjectile(int objIdToRemove) {
        for (int i = 0; i < count; i++) {
            if (objId[i] == objIdToRemove) {
                alive[i] = false;
                swapRemove(i);
                return;
            }
        }
    }

    public void updatePointProjectiles(PhysicsLevel physicsLevel) {
        if (count == 0) return;
        var world = physicsLevel.getWorld();
        float dt = 1.0f / physicsLevel.getTps();

        for (int i = 0; i < count; i++) {
            if (!alive[i]) continue;

            float mass = 1.0f;
            float gravityFactor = 1.0f;
            float dragFactor = 0f;
            if (typeIndex[i] >= 0) {
                ProjectileType type = null;
                if (!level.isClientSide()) {
                }
            }

            float speed = (float) Math.sqrt(velX[i] * velX[i] + velY[i] * velY[i] + velZ[i] * velZ[i]);

            float gravityAccY = -gravityFactor * 9.81f;
            float dragAccX = 0, dragAccY = 0, dragAccZ = 0;
            if (dragFactor > 1e-8f && speed > 1e-8f) {
                float dragForce = dragFactor * speed * speed;
                float dragAcc = dragForce / mass;
                float invSpeed = 1f / speed;
                dragAccX = dragAcc * (-velX[i] * invSpeed);
                dragAccY = dragAcc * (-velY[i] * invSpeed);
                dragAccZ = dragAcc * (-velZ[i] * invSpeed);
            }

            velX[i] += dragAccX * dt;
            velY[i] += (gravityAccY + dragAccY) * dt;
            velZ[i] += dragAccZ * dt;

            float prevX = posX[i], prevY = posY[i], prevZ = posZ[i];
            posX[i] += velX[i] * dt;
            posY[i] += velY[i] * dt;
            posZ[i] += velZ[i] * dt;

            Vector3f prevPos = new Vector3f(prevX, prevY, prevZ);
            Vector3f currPos = new Vector3f(posX[i], posY[i], posZ[i]);

            List<PhysicsRayTestResult> results = world.rayTest(prevPos, currPos);
            boolean hit = false;
            for (PhysicsRayTestResult result : results) {
                PhysicsCollisionObject obj = result.getCollisionObject();
                if (!(obj instanceof PhysicsRigidBody body)) continue;

                Object owner = PhysicsBodyExtensionKt.getOwner(body);
                Vector3f hitPointJme = prevPos.add(currPos.subtract(prevPos).mult(result.getHitFraction()));
                Vec3 hitPointMc = new Vec3(hitPointJme.x, hitPointJme.y, hitPointJme.z);
                Vector3f hitNormalJme = new Vector3f();
                result.getHitNormalLocal(hitNormalJme);
                Vec3 hitNormalMc = new Vec3(hitNormalJme.x, hitNormalJme.y, hitNormalJme.z);

                if (owner == null) {
                    spawnTerrainHitEffect(hitPointMc);
                    alive[i] = false;
                    hit = true;
                    break;
                }

                DestroyableObject destroyable = ObjectManager.getDestroyableObject(level, objId[i]);
                if (!(destroyable instanceof IProjectile projectile)) {
                    alive[i] = false;
                    hit = true;
                    break;
                }

                if (owner instanceof BFHurtTarget target) {
                    projectile.dealDamage(target, hitPointMc, hitNormalMc);
                } else if (owner instanceof Entity entity) {
                    entity.hurt(entity.damageSources().generic(), projectile.calculateCurrentDamage());
                }

                spawnHitVisualEffect(level, hitPointMc, hitNormalMc, owner instanceof BFHurtTarget);
                projectile.markHit();
                alive[i] = false;
                hit = true;
                break;
            }

            if (hit) {
                DestroyableObject destroyable = ObjectManager.getDestroyableObject(level, objId[i]);
                if (destroyable != null) {
                    destroyable.destroy();
                }
                swapRemove(i);
                continue;
            }

            lifetime[i]--;
            if (lifetime[i] <= 0) {
                alive[i] = false;
                DestroyableObject destroyable = ObjectManager.getDestroyableObject(level, objId[i]);
                if (destroyable != null) {
                    destroyable.isRemoved = true;
                    ObjectManager.removeDestroyableObject(level, objId[i]);
                }
                swapRemove(i);
            }
        }
    }

    private void spawnTerrainHitEffect(Vec3 hitPoint) {
        if (level instanceof ServerLevel serverLevel) {
            PacketDistributor.sendToPlayersInDimension(serverLevel,
                new ProjectileHitEffectPayload(
                    hitPoint.x, hitPoint.y, hitPoint.z,
                    0, 1, 0, false));
        }
    }

    public static void spawnHitVisualEffect(Level level, Vec3 hitPoint, Vec3 hitNormal, boolean isArmorHit) {
        if (level instanceof ServerLevel serverLevel) {
            PacketDistributor.sendToPlayersInDimension(serverLevel,
                new ProjectileHitEffectPayload(
                    hitPoint.x, hitPoint.y, hitPoint.z,
                    hitNormal.x, hitNormal.y, hitNormal.z, isArmorHit));
        }
    }

    public void syncPointProjectileState(int targetObjId, Vector3f pos, Vector3f vel, int life) {
        for (int i = 0; i < count; i++) {
            if (targetObjId == objId[i]) {
                posX[i] = pos.x;
                posY[i] = pos.y;
                posZ[i] = pos.z;
                velX[i] = vel.x;
                velY[i] = vel.y;
                velZ[i] = vel.z;
                lifetime[i] = life;
                return;
            }
        }
    }

    private void swapRemove(int index) {
        int last = count - 1;
        if (index != last) {
            posX[index] = posX[last];
            posY[index] = posY[last];
            posZ[index] = posZ[last];
            velX[index] = velX[last];
            velY[index] = velY[last];
            velZ[index] = velZ[last];
            lifetime[index] = lifetime[last];
            typeIndex[index] = typeIndex[last];
            objId[index] = objId[last];
            alive[index] = alive[last];
        }
        count--;
    }

    private void ensureCapacity(int required) {
        if (required <= capacity) return;
        int newCap = Math.max(required, capacity * 2);
        posX = Arrays.copyOf(posX, newCap);
        posY = Arrays.copyOf(posY, newCap);
        posZ = Arrays.copyOf(posZ, newCap);
        velX = Arrays.copyOf(velX, newCap);
        velY = Arrays.copyOf(velY, newCap);
        velZ = Arrays.copyOf(velZ, newCap);
        lifetime = Arrays.copyOf(lifetime, newCap);
        typeIndex = Arrays.copyOf(typeIndex, newCap);
        objId = Arrays.copyOf(objId, newCap);
        alive = Arrays.copyOf(alive, newCap);
        capacity = newCap;
    }
}
