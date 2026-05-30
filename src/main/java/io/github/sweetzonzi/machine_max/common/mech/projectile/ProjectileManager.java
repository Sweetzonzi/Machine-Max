package io.github.sweetzonzi.machine_max.common.mech.projectile;

import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.PenetrationKey;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import net.minecraft.core.BlockPos;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.ballistics_framework.api.BFHurtTarget;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.entity.ProjectileEntity;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.registry.MMEntities;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import io.github.sweetzonzi.machine_max.network.payload.projectile.ProjectileHitEffectPayload;
import lombok.Getter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    // ========== Entity 兼容层数组 ==========
    /**
     * Entity 因区块卸载丢失，待重建标志。
     * 与 SoA 数组索引同步，{@link #swapRemove(int)} 时联动交换。
     */
    public boolean[] needsEntityRecreate;

    /**
     * 关联的 {@link ProjectileEntity} 引用（下标对应 SoA 索引，可为 null）。
     * 服务端：由 {@link #createProjectileEntity(int)} 设置。
     * 客户端：由 {@link ProjectileEntity#tryBindProjectile()} 建立关联后保留。
     */
    public ProjectileEntity[] entities;

    /** Entity 重建检查间隔（tick）。每 N tick 遍历一次 needsEntityRecreate。 */
    private static final int RECREATE_CHECK_INTERVAL = 10;

    /** 重建检查计数器 */
    private int recreateCheckCounter = 0;

    /**
     * 投射物 Object ID 集合，用于 O(1) containsProjectile 查询。
     * 与 SoA 数组同步更新，替代线性扫描。
     */
    private final Set<Integer> projectileObjIds = ConcurrentHashMap.newKeySet();

    /**
     * 穿透记录：投射物 objId → 已穿透的穿透密钥集合。
     * 仅在物理线程（{@link #updatePointProjectiles}）读写，无并发问题。
     * 同一次飞行中，同一 (owner, zoneId) 不会被重复判定。
     */
    private final Map<Integer, Set<PenetrationKey>> penetratedKeys = new HashMap<>();

    /** 该维度所有已加载的投射物类型，typeIndex 映射到此数组 */
    private ProjectileType[] typeCache;

    /** 复用 Vector3f 避免热路径中重复分配 */
    private final Vector3f rayFrom = new Vector3f();
    private final Vector3f rayTo = new Vector3f();
    private final Vector3f hitPointJme = new Vector3f();
    private final Vector3f hitNormalJme = new Vector3f();

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
        needsEntityRecreate = new boolean[capacity];
        entities = new ProjectileEntity[capacity];
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

    /** 内部：将投射物的位置/速度/类型写入 SoA，并在服务端创建配套 Entity */
    private void addProjectileInternal(IProjectile proj, Vector3f pos, Vector3f vel) {
        ensureCapacity(count + 1);
        int i = count++;
        int id = ((DestroyableObject) proj).getId();
        posX[i] = pos.x;
        posY[i] = pos.y;
        posZ[i] = pos.z;
        velX[i] = vel.x;
        velY[i] = vel.y;
        velZ[i] = vel.z;
        lifetime[i] = proj.getMaxLifetime();
        typeIndex[i] = getOrAddType(proj.getProjectileType());
        objId[i] = id;
        alive[i] = true;
        projectileObjIds.add(id);

        // 服务端：发射位置区块已加载则立即创建 Entity，否则标记延迟创建
        if (!level.isClientSide()) {
            if (isChunkLoadedAt(pos)) {
                createProjectileEntity(i);
            } else {
                needsEntityRecreate[i] = true;
            }
        }
    }

    /** 检查世界坐标位置的区块是否已加载（仅服务端） */
    private boolean isChunkLoadedAt(Vector3f pos) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        return serverLevel.isPositionEntityTicking(BlockPos.containing(pos.x, pos.y, pos.z));
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
                projectileObjIds.remove(objIdToRemove);
                swapRemove(i);
                return;
            }
        }
    }

    // ========== Entity 兼容层方法 ==========

    /**
     * 为 SoA 中索引为 idx 的投射物创建配套的 {@link ProjectileEntity}。
     * 通过 {@link ObjectManager#levelDestroyableObjects} 实时获取 IProjectile 对象引用。
     * 仅在服务端主线程调用（内部调用了 {@code level.addFreshEntity}，需要主线程上下文）。
     *
     * @param idx SoA 数组索引
     */
    public void createProjectileEntity(int idx) {
        var objMap = ObjectManager.levelDestroyableObjects.get(level);
        if (objMap == null) return;
        DestroyableObject obj = objMap.get(objId[idx]);
        if (!(obj instanceof IProjectile projectile)) return;

        // 清理旧 Entity（若存在）
        if (entities[idx] != null) {
            entities[idx].markOrphaned();
            entities[idx] = null;
        }

        ProjectileEntity entity = new ProjectileEntity(MMEntities.getPROJECTILE_ENTITY().get(), level);
        entity.bindToProjectile(projectile);
        entity.setPos(posX[idx], posY[idx], posZ[idx]);
        entities[idx] = entity;
        level.addFreshEntity(entity);
    }

    /**
     * 按 objId 查找 SoA 数组中的索引（O(n) 线性扫描）。
     * 仅在 Entity 端调用（非热路径）。
     *
     * @param targetObjId 目标 DestroyableObject ID
     * @return SoA 索引，-1 表示未找到
     */
    public int findIndexByObjId(int targetObjId) {
        for (int i = 0; i < count; i++) {
            if (objId[i] == targetObjId) return i;
        }
        return -1;
    }

    /**
     * 遍历所有 {@code needsEntityRecreate=true} 的条目，检查区块是否已加载。
     * 若已加载则重建 {@link ProjectileEntity}。每 {@link #RECREATE_CHECK_INTERVAL} tick 执行一次。
     * <p>
     * 在主线程 Pre 阶段由 {@link #preTick()} 调用。
     */
    public void tryRecreateEntities() {
        if (++recreateCheckCounter % RECREATE_CHECK_INTERVAL != 0) return;
        if (!(level instanceof ServerLevel serverLevel)) return;
        for (int i = 0; i < count; i++) {
            if (!needsEntityRecreate[i] || !alive[i]) continue;
            if (!serverLevel.isPositionEntityTicking(
                    BlockPos.containing(posX[i], posY[i], posZ[i]))) continue;
            createProjectileEntity(i);
            needsEntityRecreate[i] = false;
        }
    }

    /**
     * 由刚体投射物在其 {@code postTick()} 中调用，将其 Bullet 刚体状态回写到 SoA。
     * <p>
     * 仅更新位置和速度字段；寿命由 {@link #tickAndPreTick()} 统一管理。
     *
     * @param targetObjId 刚体投射物的 DestroyableObject ID
     * @param pos   刚体当前世界坐标（JME）
     * @param vel   刚体当前速度（JME）
     */
    public void writebackRigidState(int targetObjId, Vector3f pos, Vector3f vel) {
        for (int i = 0; i < count; i++) {
            if (this.objId[i] == targetObjId && alive[i]) {
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

    /**
     * O(1) 检查指定的 DestroyableObject ID 是否由此管理器管理。
     * 使用 ConcurrentHashSet 替代线性扫描 SoA 数组。
     */
    public boolean containsProjectile(int targetObjId) {
        return projectileObjIds.contains(targetObjId);
    }

    /**
     * 单趟遍历：寿命递减 + preTick。
     * <p>
     * 将原本两趟独立的遍历（tickAllLifetimes + forEachPreTick）合并为一趟，
     * 减少对 SoA 数组的重复访问，改善缓存局部性。
     */
    private void tickAndPreTick() {
        Map<Integer, DestroyableObject> objMap = ObjectManager.levelDestroyableObjects.get(level);
        for (int i = 0; i < count; i++) {
            if (!alive[i]) continue;

            // 寿命递减
            lifetime[i]--;
            if (lifetime[i] <= 0) {
                alive[i] = false;
                projectileObjIds.remove(objId[i]);
                // 清理关联的 Entity
                if (entities[i] != null) {
                    entities[i].markOrphaned();
                    entities[i] = null;
                }
                needsEntityRecreate[i] = false;
                DestroyableObject obj = (objMap != null) ? objMap.get(objId[i]) : null;
                if (obj != null) {
                    obj.isRemoved = true;
                    objMap.remove(objId[i]);
                }
                continue;
            }

            // preTick：先写入缓存寿命，避免 preTick → checkDestroyed → getLifetime 的 O(n) 线性扫描
            DestroyableObject obj = (objMap != null) ? objMap.get(objId[i]) : null;
            if (obj != null) {
                if (obj instanceof PointProjectile pp) {
                    pp.cachedLifetime = lifetime[i];
                } else if (obj instanceof RigidProjectile rp) {
                    rp.cachedLifetime = lifetime[i];
                }
                obj.preTick();
            }
        }
    }

    /**
     * 单趟遍历：postTick + SoA 回写 SynchedEntityData。
     * <p>
     * 将原本两趟独立的遍历（forEachPostTick + syncAllToSyncedData）合并为一趟，
     * 减少对 SoA 数组和 HashMap 的重复访问。
     */
    private void postTickAndSync() {
        Map<Integer, DestroyableObject> objMap = ObjectManager.levelDestroyableObjects.get(level);
        if (objMap == null) return;

        for (int i = 0; i < count; i++) {
            if (!alive[i]) continue;
            DestroyableObject obj = objMap.get(objId[i]);
            if (obj == null) continue;

            obj.postTick();

            // 刚体投射物跳过回写（RigidProjectile.postTick 中已自行从 Bullet 同步）
            if (obj instanceof RigidProjectile) continue;

            obj.setPosition(new Vector3f(posX[i], posY[i], posZ[i]));
            obj.setLinearVelocity(new Vector3f(velX[i], velY[i], velZ[i]));
        }
    }

    /** 单趟遍历：prePhysicsTick（缓存本层 Map 引用） */
    private void forEachPrePhysicsTick() {
        Map<Integer, DestroyableObject> objMap = ObjectManager.levelDestroyableObjects.get(level);
        if (objMap == null) return;

        for (int i = 0; i < count; i++) {
            if (!alive[i]) continue;
            DestroyableObject obj = objMap.get(objId[i]);
            if (obj != null) obj.prePhysicsTick();
        }
    }

    /** 单趟遍历：postPhysicsTick（缓存本层 Map 引用） */
    private void forEachPostPhysicsTick() {
        Map<Integer, DestroyableObject> objMap = ObjectManager.levelDestroyableObjects.get(level);
        if (objMap == null) return;

        for (int i = 0; i < count; i++) {
            if (!alive[i]) continue;
            DestroyableObject obj = objMap.get(objId[i]);
            if (obj != null) obj.postPhysicsTick();
        }
    }

    // ================================================================
    //  统一生命周期（由 ObjectManager 调用）
    // ================================================================

    /**
     * 主线程 Pre 阶段。
     * 递减所有投射物寿命 + 调用各投射物的 {@code preTick()} +
     * 尝试重建因区块卸载丢失的 {@link ProjectileEntity}。
     * <p>
     * 优化：合并寿命递减和 preTick 为一趟遍历，减少 SoA 数组重复访问。
     */
    public void preTick() {
        tickAndPreTick();
        tryRecreateEntities();
    }

    /**
     * 主线程 Post 阶段。
     * 调用各投射物的 {@code postTick()}，然后将 SoA 位置/速度回写到 SynchedEntityData。
     * <p>
     * 优化：合并 postTick 和 syncToSyncedData 为一趟遍历。
     */
    public void postTick() {
        postTickAndSync();
    }

    /**
     * 物理线程 Pre 阶段。
     * 调用各投射物的 {@code prePhysicsTick()}，然后：
     * <ul>
     *   <li>服务端：执行质点投射物批量积分+碰撞检测</li>
     *   <li>客户端：执行简化积分外推（无碰撞检测）</li>
     * </ul>
     */
    public void prePhysicsTick(PhysicsLevel physicsLevel) {
        forEachPrePhysicsTick();
        if (level.isClientSide()) {
            clientExtrapolate(physicsLevel);
        } else {
            updatePointProjectiles(physicsLevel);
        }
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
     * 客户端自主外推所有投射物（质点+刚体的简化积分，无碰撞检测）。
     * <p>
     * 服务端仅广播关键事件（创建/命中/超时），客户端依赖自主外推来维持帧间
     * 位置连续性，供 {@code ClientProjectileRenderer} 读取。
     * <p>
     * 优化：在热路径中缓存 typeCache 引用，一次提取 ProjectileType 代替三次数组访问。
     *
     * @param physicsLevel 客户端物理世界
     */
    private void clientExtrapolate(PhysicsLevel physicsLevel) {
        for (int i = count - 1; i >= 0; i--) {
            if (!alive[i]) {
                projectileObjIds.remove(objId[i]);
                swapRemove(i);
            }
        }
        if (count == 0) return;

        float dt = 1.0f / physicsLevel.getTps();
        ProjectileType[] types = this.typeCache;

        for (int i = 0; i < count; i++) {
            if (!alive[i]) continue;

            ProjectileType type = types[typeIndex[i]];
            float mass = type.getMass();
            float gravityFactor = type.getGravityFactor();
            float dragFactor = type.getDragFactor();

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

            posX[i] += velX[i] * dt;
            posY[i] += velY[i] * dt;
            posZ[i] += velZ[i] * dt;
        }
    }

    /**
     * 批量更新所有活跃质点投射物（仅质点，刚体由 Bullet 管理）。
     * <p>
     * 每个物理步对每个活跃质点执行：
     * <ol>
     *   <li>清理死条（被主线程 {@link #tickAndPreTick()} 标记的）</li>
     *   <li>半隐式 Euler 积分（重力 + 空气阻力）</li>
     *   <li>JME {@code rayTest} 碰撞检测</li>
     *   <li>穿透去重检查（{@link PenetrationKey}）——同一次飞行中已穿透的 (owner, zoneId) 不再判定</li>
     *   <li>命中处理 → 发起协议伤害 → 视觉特效广播</li>
     *   <li>击穿判定：穿透力 > 装甲等效厚度则击穿——记录密钥、扣减速度、继续飞行；否则投射物停止</li>
     * </ol>
     * <p>
     * 优化项：
     * <ul>
     *   <li>缓存 typeCache 引用，一次提取 ProjectileType 避免三次数组访问</li>
     *   <li>复用 Vector3f 实例避免热路径中重复分配</li>
     *   <li>缓存 ObjectManager.levelDestroyableObjects 本层 Map 引用</li>
     * </ul>
     *
     * @param physicsLevel 当前维度的物理世界
     */
    public void updatePointProjectiles(PhysicsLevel physicsLevel) {
        // 0) 清理死条（被主线程 tickAndPreTick 标记为 !alive 的条目）
        for (int i = count - 1; i >= 0; i--) {
            if (!alive[i]) {
                projectileObjIds.remove(objId[i]);
                swapRemove(i);
            }
        }
        if (count == 0) return;

        var world = physicsLevel.getWorld();
        float dt = 1.0f / physicsLevel.getTps();
        ProjectileType[] types = this.typeCache;
        Map<Integer, DestroyableObject> objMap = ObjectManager.levelDestroyableObjects.get(level);
        Vector3f rayFrom = this.rayFrom;
        Vector3f rayTo = this.rayTo;
        Vector3f hitPointJme = this.hitPointJme;
        Vector3f hitNormalJme = this.hitNormalJme;

        for (int i = 0; i < count; i++) {
            if (!alive[i]) continue;

            // 从类型缓存读取弹道参数
            ProjectileType type = types[typeIndex[i]];
            float mass = type.getMass();
            float gravityFactor = type.getGravityFactor();
            float dragFactor = type.getDragFactor();

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

            // JME 物理射线碰撞检测（复用 Vector3f 实例）
            rayFrom.set(prevX, prevY, prevZ);
            rayTo.set(posX[i], posY[i], posZ[i]);

            List<PhysicsRayTestResult> results = world.rayTest(rayFrom, rayTo);
            boolean stopped = false;
            for (PhysicsRayTestResult result : results) {
                PhysicsCollisionObject collObj = result.getCollisionObject();
                if (collObj.getCollisionGroup() != CollisionGroups.PHYSICS_BODY
                        && collObj.getCollisionGroup() != CollisionGroups.TERRAIN
                            && collObj.getCollisionGroup() != CollisionGroups.PAWN)
                    continue;
                if (!(collObj instanceof PhysicsRigidBody body)) continue;

                // 穿透去重检查：同一飞行中，已穿透的 (owner, zoneId) 不再重复判定
                PenetrationKey penKey = PenetrationKey.fromCollision(collObj, result.triangleIndex());
                if (penKey != null) {
                    Set<PenetrationKey> penetrated = penetratedKeys.get(objId[i]);
                    if (penetrated != null && penetrated.contains(penKey)) {
                        continue;
                    }
                }

                Object owner = PhysicsBodyExtensionKt.getOwner(body);
                float hitFrac = result.getHitFraction();
                hitPointJme.set(
                    rayFrom.x + (rayTo.x - rayFrom.x) * hitFrac,
                    rayFrom.y + (rayTo.y - rayFrom.y) * hitFrac,
                    rayFrom.z + (rayTo.z - rayFrom.z) * hitFrac);
                Vec3 hitPointMc = new Vec3(hitPointJme.x, hitPointJme.y, hitPointJme.z);
                result.getHitNormalLocal(hitNormalJme);
                Vec3 hitNormalMc = new Vec3(hitNormalJme.x, hitNormalJme.y, hitNormalJme.z);

                // 地形碰撞：永远停止
                if (owner == null) {
                    spawnTerrainHitEffect(hitPointMc);
                    alive[i] = false;
                    projectileObjIds.remove(objId[i]);
                    stopped = true;
                    break;
                }

                // MMPartEntity：渲染代理，跳过
                if (owner instanceof MMPartEntity) continue;

                // 获取投射物实例
                DestroyableObject destroyable = (objMap != null) ? objMap.get(objId[i]) : null;
                if (!(destroyable instanceof IProjectile projectile)) {
                    alive[i] = false;
                    projectileObjIds.remove(objId[i]);
                    stopped = true;
                    break;
                }

                switch (owner) {
                    case SubPart subPart -> {
                        HitBox hitBox = subPart.getHitBox(result.triangleIndex());
                        if (!hitBox.isActive()) continue;

                        projectile.dealDamage(subPart, hitPointMc, hitNormalMc);

                        // 判定是否击穿：穿透力 > 装甲等效厚度则击穿
                        float pen = projectile.calculateCurrentPenetration();
                        float rha = hitBox.getRHA(subPart);
                        if (pen > rha) {
                            // 击穿：记录穿透密钥，扣减残余速度，继续飞行
                            if (penKey != null) {
                                penetratedKeys.computeIfAbsent(objId[i], k -> new HashSet<>()).add(penKey);
                            }
                            float residualRatio = Math.max(0.1f, (pen - rha) / Math.max(pen, 0.001f));
                            float speedRatio = (float) Math.sqrt(residualRatio);
                            velX[i] *= speedRatio;
                            velY[i] *= speedRatio;
                            velZ[i] *= speedRatio;
                            spawnHitVisualEffect(level, hitPointMc, hitNormalMc, true);
                            continue;
                        }
                        // 未击穿
                        spawnHitVisualEffect(level, hitPointMc, hitNormalMc, true);
                    }
                    case BFHurtTarget target -> {
                        projectile.dealDamage(target, hitPointMc, hitNormalMc);
                        spawnHitVisualEffect(level, hitPointMc, hitNormalMc, true);
                        if (penKey != null) {
                            penetratedKeys.computeIfAbsent(objId[i], k -> new HashSet<>()).add(penKey);
                        }
                    }
                    case Entity entity -> {
                        entity.hurt(entity.damageSources().generic(), projectile.calculateCurrentDamage());
                        spawnHitVisualEffect(level, hitPointMc, hitNormalMc, false);
                        if (penKey != null) {
                            penetratedKeys.computeIfAbsent(objId[i], k -> new HashSet<>()).add(penKey);
                        }
                    }
                    default -> {
                    }
                }

                // 非 SubPart 目标：命中后投射物停止
                projectile.markHit();
                alive[i] = false;
                projectileObjIds.remove(objId[i]);
                stopped = true;
                break;
            }

            if (stopped) {
                DestroyableObject destroyable = (objMap != null) ? objMap.get(objId[i]) : null;
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

    /** O(1) swap-with-last 移除（联动交换 Entity 数组，清理穿透记录） */
    private void swapRemove(int index) {
        // 先清理被移除条目的 Entity
        if (entities[index] != null) {
            entities[index].markOrphaned();
            entities[index] = null;
        }
        // 清理穿透记录
        penetratedKeys.remove(objId[index]);
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
            needsEntityRecreate[index] = needsEntityRecreate[last];
            entities[index] = entities[last];
        }
        count--;
    }

    /** ×2 动态扩容（联动扩容 Entity 数组） */
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
        needsEntityRecreate = Arrays.copyOf(needsEntityRecreate, newCap);
        entities = Arrays.copyOf(entities, newCap);
        capacity = newCap;
    }
}
