package io.github.sweetzonzi.machine_max.common.mech.projectile;

import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.ballistics_framework.api.BFHurtTarget;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import io.github.sweetzonzi.machine_max.network.payload.ProjectileHitEffectPayload;
import lombok.Getter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.List;

/**
 * 投射物管理器（每 Level 一个实例）。
 * <p>
 * 由 {@link ObjectManager#levelProjectileManagers} 按维度持有，在
 * {@code PhysicsLevelTickEvent.Pre} 中委托 {@link #updatePointProjectiles(PhysicsLevel)}
 * 批量更新所有活跃质点投射物。
 * <p>
 * 质点投射物数据以 SoA（Structure of Arrays）方式存储：位置/速度使用
 * 基本类型 float 数组，追求 CPU 缓存命中率。
 * <p>
 * 初始容量 256，按需 ×2 动态扩容，移除时使用 swap-with-last 策略（O(1)）。
 */
public class ProjectileManager {

    /** 所属维度 */
    @Getter
    private final Level level;

    // ========== SoA 数组：质点投射物数据 ==========
    public float[] posX, posY, posZ;    // 世界坐标 (JME)
    public float[] velX, velY, velZ;    // 速度 (m/s)
    public int[] lifetime;              // 剩余存活 tick
    public int[] typeIndex;             // 投射物类型索引
    public int[] objId;                 // 对应的 DestroyableObject ID
    public boolean[] alive;             // 活跃标志
    public int count;                   // 当前活跃总数
    private int capacity = 256;         // 当前数组容量

    /** 该维度所有已加载的投射物类型，typeIndex 映射到此数组 */
    private ProjectileType[] typeCache; // 按 typeIndex 索引

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
        typeCache = new ProjectileType[0];
    }

    /**
     * 注册一个质点投射物到 SoA 数组。
     * 在 {@link PointProjectile} 构造时由服务端调用。
     *
     * @param p 质点投射物实例
     */
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
        typeIndex[i] = getOrAddType(p.getProjectileType());
        objId[i] = p.getId();
        alive[i] = true;
    }

    /**
     * 获取或注册一个投射物类型到类型缓存，返回索引。
     * 相同的 ProjectileType 实例复用同一索引。
     */
    private int getOrAddType(ProjectileType type) {
        for (int i = 0; i < typeCache.length; i++) {
            if (typeCache[i] == type) return i;
        }
        ProjectileType[] newCache = Arrays.copyOf(typeCache, typeCache.length + 1);
        newCache[typeCache.length] = type;
        typeCache = newCache;
        return typeCache.length - 1;
    }

    /**
     * 按 DestroyableObject ID 从 SoA 数组中移除一个质点。
     *
     * @param objIdToRemove 目标对象 ID
     */
    public void removePointProjectile(int objIdToRemove) {
        for (int i = 0; i < count; i++) {
            if (objId[i] == objIdToRemove) {
                alive[i] = false;
                swapRemove(i);
                return;
            }
        }
    }

    /**
     * 批量更新所有活跃质点投射物。
     * <p>
     * 每个物理步对每个活跃质点执行：
     * <ol>
     *   <li>半隐式 Euler 积分（重力 + 空气阻力）</li>
     *   <li>JME {@code rayTest} 碰撞检测（参照 LivingEntityEyesightAttachment 模式）</li>
     *   <li>命中处理→发起协议伤害→视觉特效广播</li>
     *   <li>寿命检查→超时清理</li>
     * </ol>
     * <p>
     * 物理时间步 dt 从 {@link PhysicsLevel#getTps()} 换算：dt = 1/tps。
     *
     * @param physicsLevel 当前维度的物理世界
     */
    public void updatePointProjectiles(PhysicsLevel physicsLevel) {
        if (count == 0) return;
        var world = physicsLevel.getWorld();
        float dt = 1.0f / physicsLevel.getTps();

        for (int i = 0; i < count; i++) {
            if (!alive[i]) continue;

            // 从类型缓存读取弹道参数
            int tIdx = typeIndex[i];
            float mass = (tIdx >= 0 && tIdx < typeCache.length) ? typeCache[tIdx].getMass() : 1.0f;
            float gravityFactor = (tIdx >= 0 && tIdx < typeCache.length) ? typeCache[tIdx].getGravityFactor() : 1.0f;
            float dragFactor = (tIdx >= 0 && tIdx < typeCache.length) ? typeCache[tIdx].getDragFactor() : 0f;

            float speed = (float) Math.sqrt(velX[i] * velX[i] + velY[i] * velY[i] + velZ[i] * velZ[i]);

            // 半隐式 Euler 积分
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

            // JME 物理射线碰撞检测
            Vector3f prevPos = new Vector3f(prevX, prevY, prevZ);
            Vector3f currPos = new Vector3f(posX[i], posY[i], posZ[i]);

            List<PhysicsRayTestResult> results = world.rayTest(prevPos, currPos);
            boolean hit = false;
            label:
            for (PhysicsRayTestResult result : results) {
                PhysicsCollisionObject obj = result.getCollisionObject();
                if (obj.getCollisionGroup() != CollisionGroups.PHYSICS_BODY
                        && obj.getCollisionGroup() != CollisionGroups.TERRAIN)
                    continue;
                if (!(obj instanceof PhysicsRigidBody body)) continue;

                Object owner = PhysicsBodyExtensionKt.getOwner(body);
                Vector3f hitPointJme = prevPos.add(currPos.subtract(prevPos).mult(result.getHitFraction()));
                Vec3 hitPointMc = new Vec3(hitPointJme.x, hitPointJme.y, hitPointJme.z);
                Vector3f hitNormalJme = new Vector3f();
                result.getHitNormalLocal(hitNormalJme);
                Vec3 hitNormalMc = new Vec3(hitNormalJme.x, hitNormalJme.y, hitNormalJme.z);

                switch (owner) {
                    case null:
                        // 命中地形碰撞体 — 仅有视觉特效
                        spawnTerrainHitEffect(hitPointMc);
                        alive[i] = false;
                        hit = true;
                        break label;
                    case SubPart subPart:
                        HitBox hitBox = subPart.getHitBox(result.triangleIndex());
                        if (!hitBox.isActive()) continue; // 忽略未激活碰撞体积
                        break;
                    case MMPartEntity mmPartEntity:
                        continue; // 忽略部件实体
                    default:
                        break;
                }

                DestroyableObject destroyable = ObjectManager.getDestroyableObject(level, objId[i]);
                if (!(destroyable instanceof IProjectile projectile)) {
                    alive[i] = false;
                    hit = true;
                    break;
                }

                // 命中 BFHurtTarget（SubPart 等），直接发起协议伤害
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

            // 命中后销毁对应的 DestroyableObject
            if (hit) {
                DestroyableObject destroyable = ObjectManager.getDestroyableObject(level, objId[i]);
                if (destroyable != null) {
                    destroyable.destroy();
                }
                swapRemove(i);
                continue;
            }

            // 寿命检查
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

    /** 命中地形时的视觉特效（服务端广播） */
    private void spawnTerrainHitEffect(Vec3 hitPoint) {
        if (level instanceof ServerLevel serverLevel) {
            PacketDistributor.sendToPlayersInDimension(serverLevel,
                new ProjectileHitEffectPayload(
                    hitPoint.x, hitPoint.y, hitPoint.z,
                    0, 1, 0, false));
        }
    }

    /**
     * 命中视觉特效（服务端广播至维度内所有玩家）。
     * <p>
     * 由 {@link PointProjectile} 的碰撞处理和 {@link RigidProjectile} 的碰撞处理调用。
     * 客户端收到 {@link ProjectileHitEffectPayload} 后播放粒子音效。
     *
     * @param level      维度
     * @param hitPoint   命中点坐标
     * @param hitNormal  命中面法线
     * @param isArmorHit 是否命中装甲目标（影响粒子类型）
     */
    public static void spawnHitVisualEffect(Level level, Vec3 hitPoint, Vec3 hitNormal, boolean isArmorHit) {
        if (level instanceof ServerLevel serverLevel) {
            PacketDistributor.sendToPlayersInDimension(serverLevel,
                new ProjectileHitEffectPayload(
                    hitPoint.x, hitPoint.y, hitPoint.z,
                    hitNormal.x, hitNormal.y, hitNormal.z, isArmorHit));
        }
    }

    /**
     * 同步某个质点投射物的状态（客户端从网络包接收后调用）。
     *
     * @param targetObjId 目标对象 ID
     * @param pos         位置
     * @param vel         速度
     * @param life        剩余寿命
     */
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

    /** O(1) swap-with-last 移除 */
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

    /** ×2 动态扩容 */
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
