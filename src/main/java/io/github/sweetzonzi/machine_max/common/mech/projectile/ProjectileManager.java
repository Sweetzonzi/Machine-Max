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
 * 统一管理该维度所有质点投射物和刚体投射物的位置/速度/寿命数据，
 * 以 SoA（Structure of Arrays）方式存储，追求 CPU 缓存命中率。
 * <p>
 * <b>生命周期方法由 {@link ObjectManager} 统一调用：</b>
 * <ul>
 *   <li>{@link #preTick()}   — LevelTickEvent.Pre（主线程）</li>
 *   <li>{@link #postTick()}  — LevelTickEvent.Post（主线程）</li>
 *   <li>{@link #prePhysicsTick(PhysicsLevel)} — PhysicsLevelTickEvent.Pre（物理线程）</li>
 *   <li>{@link #postPhysicsTick()} — PhysicsLevelTickEvent.Post（物理线程）</li>
 * </ul>
 * <p>
 * <b>线程分工（单写者 + volatile count 模式）：</b>
 * <ul>
 *   <li>物理线程写入 SoA pos/vel，swapRemove 清理</li>
 *   <li>主线程写入 SoA lifetime，回写到 SynchedEntityData</li>
 * </ul>
 * <p>
 * 初始容量 256，按需 ×2 动态扩容，移除时使用 swap-with-last 策略（O(1)）。
 */
public class ProjectileManager {

    /** 所属维度 */
    @Getter
    private final Level level;

    // ========== SoA 数组：统一存放质点 & 刚体投射物数据 ==========
    public float[] posX, posY, posZ;    // 世界坐标 (JME)
    public float[] velX, velY, velZ;    // 速度 (m/s)
    public int[] lifetime;              // 剩余存活 tick（主线程权威）
    public int[] typeIndex;             // 投射物类型索引
    public int[] objId;                 // 对应的 DestroyableObject ID
    public boolean[] alive;             // 活跃标志
    public volatile int count;          // 当前活跃总数（volatile 保证跨线程可见性）
    private int capacity = 256;         // 当前数组容量

    /** 该维度所有已加载的投射物类型，typeIndex 映射到此数组 */
    private ProjectileType[] typeCache;

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
        addProjectileInternal(p, p.getPosition(), p.getVelocity());
    }

    /**
     * 注册一个刚体投射物到 SoA 数组。
     * 在 {@link RigidProjectile} 构造时由服务端调用。
     *
     * @param r 刚体投射物实例
     */
    public void addRigidProjectile(RigidProjectile r) {
        addProjectileInternal(r, r.getPosition(), r.getLinearVelocity());
    }

    /** 内部：将投射物的位置/速度/类型写入 SoA */
    private void addProjectileInternal(IProjectile proj, Vector3f pos, Vector3f vel) {
        ensureCapacity(count + 1);
        int i = count++;
        posX[i] = pos.x;
        posY[i] = pos.y;
        posZ[i] = pos.z;
        velX[i] = vel.x;
        velY[i] = vel.y;
        velZ[i] = vel.z;
        lifetime[i] = proj.getMaxLifetime();
        typeIndex[i] = getOrAddType(proj.getProjectileType());
        objId[i] = ((DestroyableObject) proj).getId();
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
     * 按 DestroyableObject ID 从 SoA 数组中移除一个投射物（质点/刚体通用）。
     *
     * @param objIdToRemove 目标对象 ID
     */
    public void removeProjectile(int objIdToRemove) {
        for (int i = 0; i < count; i++) {
            if (objId[i] == objIdToRemove) {
                alive[i] = false;
                swapRemove(i);
                return;
            }
        }
    }

    /**
     * 由刚体投射物在其 {@code postTick()} 中调用，将其 Bullet 刚体状态回写到 SoA。
     * <p>
     * 仅更新位置和速度字段；寿命由 {@link #tickAllLifetimes()} 统一管理。
     *
     * @param objId 刚体投射物的 DestroyableObject ID
     * @param pos   刚体当前世界坐标（JME）
     * @param vel   刚体当前速度（JME）
     */
    public void writebackRigidState(int objId, Vector3f pos, Vector3f vel) {
        for (int i = 0; i < count; i++) {
            if (objId[i] == objId && alive[i]) {
                posX[i] = pos.x;
                posY[i] = pos.y;
                posZ[i] = pos.z;
                velX[i] = vel.x;
                velY[i] = vel.y;
                velZ[i] = vel.z;
                return;
            }
        }
    }

    /** 检查指定的 DestroyableObject ID 是否由此管理器管理 */
    public boolean containsProjectile(int objId) {
        for (int i = 0; i < count; i++) {
            if (objId[i] == objId) return true;
        }
        return false;
    }

    /**
     * 遍历 SoA 中所有投射物，调用其 DestroyableObject.preTick()。
     * 早于寿命递减，使 checkDestroyed() 能读到已更新的值。
     */
    private void forEachPreTick() {
        for (int i = 0; i < count; i++) {
            if (!alive[i]) continue;
            DestroyableObject obj = ObjectManager.getDestroyableObject(level, objId[i]);
            if (obj != null) obj.preTick();
        }
    }

    /** 遍历 SoA 中所有投射物，调用其 DestroyableObject.postTick()。 */
    private void forEachPostTick() {
        for (int i = 0; i < count; i++) {
            if (!alive[i]) continue;
            DestroyableObject obj = ObjectManager.getDestroyableObject(level, objId[i]);
            if (obj != null) obj.postTick();
        }
    }

    /** 遍历 SoA 中所有投射物，调用其 DestroyableObject.prePhysicsTick()。 */
    private void forEachPrePhysicsTick() {
        for (int i = 0; i < count; i++) {
            if (!alive[i]) continue;
            DestroyableObject obj = ObjectManager.getDestroyableObject(level, objId[i]);
            if (obj != null) obj.prePhysicsTick();
        }
    }

    /** 遍历 SoA 中所有投射物，调用其 DestroyableObject.postPhysicsTick()。 */
    private void forEachPostPhysicsTick() {
        for (int i = 0; i < count; i++) {
            if (!alive[i]) continue;
            DestroyableObject obj = ObjectManager.getDestroyableObject(level, objId[i]);
            if (obj != null) obj.postPhysicsTick();
        }
    }

    // ================================================================
    //  统一生命周期（由 ObjectManager 调用）
    // ================================================================

    /**
     * 主线程 Pre 阶段。
     * 递减所有投射物寿命 + 调用各投射物的 {@code preTick()}。
     */
    public void preTick() {
        tickAllLifetimes();
        forEachPreTick();
    }

    /**
     * 主线程 Post 阶段。
     * 调用各投射物的 {@code postTick()}，然后将 SoA 位置/速度回写到 SynchedEntityData。
     */
    public void postTick() {
        forEachPostTick();
        syncAllToSyncedData();
    }

    /**
     * 物理线程 Pre 阶段。
     * 调用各投射物的 {@code prePhysicsTick()}，然后执行质点投射物批量积分+碰撞检测。
     */
    public void prePhysicsTick(PhysicsLevel physicsLevel) {
        forEachPrePhysicsTick();
        updatePointProjectiles(physicsLevel);
    }

    /**
     * 物理线程 Post 阶段。
     * 调用各投射物的 {@code postPhysicsTick()}（刚体会在此阶段将 Bullet 位置回写到 SoA）。
     */
    public void postPhysicsTick() {
        forEachPostPhysicsTick();
    }

    // ================================================================
    //  内部方法
    // ================================================================

    /**
     * 递减所有活跃投射物的寿命。
     * <p>
     * 超时的投射物被标记为不活跃（alive[i] = false），
     * 实际的 swapRemove 清理由物理线程在下一 tick 执行。
     */
    private void tickAllLifetimes() {
        for (int i = 0; i < count; i++) {
            if (!alive[i]) continue;
            lifetime[i]--;
            if (lifetime[i] <= 0) {
                alive[i] = false;
                DestroyableObject destroyable = ObjectManager.getDestroyableObject(level, objId[i]);
                if (destroyable != null) {
                    destroyable.isRemoved = true;
                    ObjectManager.removeDestroyableObject(level, objId[i]);
                }
            }
        }
    }

    /**
     * 将 SoA 位置/速度回写到各投射物对象的 SynchedEntityData。
     * <p>
     * 刚体投射物跳过（已在 postTick 中由 RigidProjectile 自行从 Bullet 同步）；
     * 仅回写质点投射物。
     */
    private void syncAllToSyncedData() {
        for (int i = 0; i < count; i++) {
            if (!alive[i]) continue;
            DestroyableObject obj = ObjectManager.getDestroyableObject(level, objId[i]);
            if (obj == null) continue;
            if (obj instanceof RigidProjectile) continue;
            obj.setPosition(new Vector3f(posX[i], posY[i], posZ[i]));
            obj.setLinearVelocity(new Vector3f(velX[i], velY[i], velZ[i]));
        }
    }

    /**
     * 批量更新所有活跃质点投射物（仅质点，刚体由 Bullet 管理）。
     * <p>
     * 每个物理步对每个活跃质点执行：
     * <ol>
     *   <li>清理死条（被主线程 {@link #tickAllLifetimes()} 标记的）</li>
     *   <li>半隐式 Euler 积分（重力 + 空气阻力）</li>
     *   <li>JME {@code rayTest} 碰撞检测</li>
     *   <li>命中处理→发起协议伤害→视觉特效广播</li>
     * </ol>
     * <p>
     * 寿命管理已移至主线程 {@link #tickAllLifetimes()}，此处不再递减或检查寿命。
     *
     * @param physicsLevel 当前维度的物理世界
     */
    public void updatePointProjectiles(PhysicsLevel physicsLevel) {
        // 0) 清理死条（被主线程 tickAllLifetimes 标记为 !alive 的条目）
        for (int i = count - 1; i >= 0; i--) {
            if (!alive[i]) swapRemove(i);
        }
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
                        spawnTerrainHitEffect(hitPointMc);
                        alive[i] = false;
                        hit = true;
                        break label;
                    case SubPart subPart:
                        HitBox hitBox = subPart.getHitBox(result.triangleIndex());
                        if (!hitBox.isActive()) continue;
                        break;
                    case MMPartEntity mmPartEntity:
                        continue;
                    default:
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
