package io.github.sweetzonzi.machine_max.common.mech.projectile;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.PenetrationKey;
import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.physics.terrain.PhysicsChunkSection;
import cn.solarmoon.spark_core.physics.terrain.SectionSnapshot;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import io.github.sweetzonzi.ballistics_framework.api.trajectory.BallisticConfig;
import io.github.sweetzonzi.ballistics_framework.api.trajectory.DensityFunction;
import io.github.sweetzonzi.ballistics_framework.api.trajectory.RealisticTrajectory;
import io.github.sweetzonzi.ballistics_framework.api.trajectory.TrajectoryResult;
import io.github.sweetzonzi.ballistics_framework.api.trajectory.TrajectorySample;
import io.github.sweetzonzi.machine_max.client.render.renderer.ClientProjectileRenderer;
import io.github.sweetzonzi.machine_max.common.entity.MMProjectileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
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
import io.github.sweetzonzi.machine_max.common.registry.MMEntities;
import io.github.sweetzonzi.machine_max.network.payload.projectile.ProjectilesHitPayload;
import io.github.sweetzonzi.machine_max.network.payload.projectile.ProjectilesSpawnPayload;
import lombok.Getter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.annotation.Nullable;

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

    /**
     * 所属维度
     */
    @Getter
    private final Level level;

    /**
     * 空气密度函数，用于阻力计算和弹道预测。
     * <p>
     * 使用归一化指数衰减模型：ρ(y) = exp(-(y - 62) / 8500)。
     * 海平面(Y=62)密度 = 1.0，与现有 dragFactor 配平值兼容。
     * 与 {@link RealisticTrajectory#forwardSolve} 共享同一函数，确保预测与模拟一致。
     */
    private final DensityFunction densityFunction;

    // ========== SoA 数组：统一存放质点 & 刚体投射物数据 ==========
    public float[] posX, posY, posZ;    // 世界坐标 (JME)
    public float[] velX, velY, velZ;    // 速度 (m/s)
    public int[] lifetime;              // 剩余存活 tick（主线程权威）
    public int[] typeIndex;             // 投射物类型索引
    public int[] objId;                 // 对应的 DestroyableObject ID
    public boolean[] alive;             // 活跃标志
    public boolean[] skipExtrapolate;    // 命中帧暂停客户端外推（跳弹/穿透后保持位置在命中点）
    public volatile int count;          // 当前活跃总数（volatile 保证跨线程可见性）
    private int capacity = 256;         // 当前数组容量

    // ========== Entity 兼容层数组 ==========
    /**
     * Entity 因区块卸载丢失，待重建标志。
     * 与 SoA 数组索引同步，{@link #swapRemove(int)} 时联动交换。
     */
    public boolean[] needsEntityRecreate;

    /**
     * 关联的 {@link MMProjectileEntity} 引用（下标对应 SoA 索引，可为 null）。
     * 服务端：由 {@link #createProjectileEntity(int)} 设置。
     * 客户端：由 {@link MMProjectileEntity#tryBindProjectile()} 建立关联后保留。
     */
    public MMProjectileEntity[] entities;

    /**
     * Entity 重建检查间隔（tick）。每 N tick 遍历一次 needsEntityRecreate。
     */
    private static final int RECREATE_CHECK_INTERVAL = 10;

    /**
     * 重建检查计数器
     */
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

    /**
     * 暂存的穿透密钥：物理线程记录（命中时立即写入），
     * 后续帧消费命中结果时取出并合并到 {@link #penetratedKeys}。
     * 仅用于命中实体目标的异步路径（命中帧无法确认结果）。
     */
    private final Map<Integer, PenetrationKey> pendingPenKeys = new HashMap<>();

    /**
     * 该维度所有已加载的投射物类型，typeIndex 映射到此数组
     */
    private volatile ProjectileType[] typeCache;

    /**
     * 待主线程清理的代理实体队列。
     * <p>
     * <b>生产者：</b>物理线程（{@link #swapRemove(int)} — 物理线程命中/清理时不可调用
     * {@link MMProjectileEntity#markOrphaned()}，会通过 {@code entity.remove()} 修改
     * {@code ChunkMap.entityMap}，与主线程 {@code ChunkMap.tick()} 遍历冲突）。<br>
     * <b>消费者：</b>主线程（{@link #postTick()} 开头清空，在 {@code ChunkMap.tick()} 之后执行）。<br>
     * 使用 {@link ConcurrentLinkedQueue} 保证无锁安全。
     */
    private final ConcurrentLinkedQueue<MMProjectileEntity> orphanedEntities = new ConcurrentLinkedQueue<>();

    /**
     * 待主线程创建 Entity 并广播的投射物快照队列。
     * <p>
     * <b>生产者：</b>物理线程（{@link #addProjectileInternal(IProjectile, Vector3f, Vector3f)}）
     * — 写入 SoA 后，立即捕获炮口位置和初速的快照入队。<br>
     * <b>消费者：</b>主线程（{@link #flushProjectileEntities()} 清空）。<br>
     * 使用 {@link ConcurrentLinkedQueue} 保证无锁安全。
     * <p>
     * 存快照而非 {@link IProjectile} 引用——物理线程在入队后会继续修改
     * 投射物的 position/velocity，主线程 flush 时若读引用将得到已被积分的值，
     * 导致客户端接收到错误的生成位置（非炮口）。快照在构造瞬间凝固数据。
     */
    private final ConcurrentLinkedQueue<PendingSpawn> pendingProjectiles = new ConcurrentLinkedQueue<>();

    /**
     * 待主线程批量广播的命中同步事件队列。
     * <p>
     * <b>生产者：</b>物理线程（{@link #updatePointProjectiles} 中所有
     * {@code broadcastHitSync/broadcastTerrainHit} 调用，
     * 以及 {@link RigidProjectile#applyHitResultAfterCollision} 通过
     * {@link #enqueueHitSync} 入队）。<br>
     * <b>消费者：</b>主线程（{@link #flushPendingHitSyncs()}，
     * 在 {@link #flushProjectileEntities()} 发包之后调用）。<br>
     * 使用 {@link ConcurrentLinkedQueue} 保证无锁安全。
     * <p>
     * 不加区分地缓冲所有物理线程命中事件，统一到主线程批量发送。
     * 代价是命中特效延迟最多 1 tick（50ms），换取严格保序（创建包→命中包）和带宽节约。
     */
    private final ConcurrentLinkedQueue<PendingHitSync> pendingHitSyncs = new ConcurrentLinkedQueue<>();

    /**
     * 复用 Vector3f 避免热路径中重复分配
     */
    private final Vector3f rayFrom = new Vector3f();
    private final Vector3f rayTo = new Vector3f();
    private final Vector3f hitPointJme = new Vector3f();
    private final Vector3f hitNormalJme = new Vector3f();

    public ProjectileManager(Level level) {
        this.level = level;
        this.densityFunction = createDensityFunction();
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
        skipExtrapolate = new boolean[capacity];
        needsEntityRecreate = new boolean[capacity];
        entities = new MMProjectileEntity[capacity];
        typeCache = new ProjectileType[0];
    }

    /**
     * 创建归一化空气密度函数，用于阻力计算和弹道预测。
     * <p>
     * 使用指数衰减模型：ρ(y) = exp(-(y - 62) / 8500)。
     * 海平面(Y=62)密度归一化为 1.0，高空逐渐衰减。
     * 与 {@link RealisticTrajectory#forwardSolve} 共享同一函数，确保预测与模拟一致。
     * <p>
     * 选用归一化模型而非 {@link DensityFunction#MC_OVERWORLD}（ρ₀=1.225），
     * 是为了保持现有投射物的 dragFactor 配平值不变（dragFactor 在 ρ≈1 的环境下调试）。
     */
    private static DensityFunction createDensityFunction() {
        return pos -> (float) Math.exp(-(pos.y - 62.0) / 8500.0);
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

    /**
     * 内部：将投射物的位置/速度/类型写入 SoA。
     * <p>
     * <b>调用线程：</b>物理线程（{@link PointProjectile}/{@link RigidProjectile} 构造链）。
     * 所有 SoA 数组写入完成后才执行 volatile count 写入，保证 happens-before：
     * 主线程读取 count 后必定能看到完整的 SoA 数据。
     * <p>
     * 不再在此方法内创建 Entity 或发包——改为入队 {@link #pendingProjectiles}，
     * 由主线程 {@link #flushProjectileEntities()} 统一处理。
     */
    private void addProjectileInternal(IProjectile proj, Vector3f pos, Vector3f vel) {
        if (proj instanceof DestroyableObject projectile)
            ObjectManager.addDestroyableObject(projectile);
        ensureCapacity(count + 1);
        // ★ 先读取 count 作为索引，所有数组写入完成后再通过 volatile write 发布
        int i = count;
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
        skipExtrapolate[i] = false;
        projectileObjIds.add(id);
        // volatile write 必须在所有 SoA 数组写入之后，确保主线程读取 count 时数据已完整
        count = i + 1;

        // 服务端：入队快照（炮口位置和初速，cloned 防止后续物理积分覆盖）
        // 由主线程 flushProjectileEntities 统一处理 Entity 创建和发包
        if (!level.isClientSide()) {
            pendingProjectiles.add(new PendingSpawn(id,
                    proj.getProjectileType().getRegistryKey(),
                    pos.clone(),   // ★ 必须 clone：Vector3f 会被后续物理积分修改
                    vel.clone()));
            // 预测弹道路径上的区块，预约地形刚体加载，确保投射物能检测到地形碰撞
            preloadTrajectoryTerrain(pos, vel, proj.getProjectileType());
        }
    }

    /**
     * 检查世界坐标位置的区块是否已加载（仅服务端）
     */
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
     * 为 SoA 中索引为 idx 的投射物创建配套的 {@link MMProjectileEntity}。
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

        MMProjectileEntity entity = new MMProjectileEntity(MMEntities.PROJECTILE_ENTITY.get(), level);
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
     * 若已加载则重建 {@link MMProjectileEntity}。每 {@link #RECREATE_CHECK_INTERVAL} tick 执行一次。
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
     * @param pos         刚体当前世界坐标（JME）
     * @param vel         刚体当前速度（JME）
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

    // ==================== 穿透去重公共 API ====================

    /**
     * 查询指定投射物是否已穿透了给定方块位置。
     * <p>
     * 用于穿透去重：在调用 {@link IProjectile#onTerrainHit} 之前检查，
     * 若已穿透则跳过。仅用于地形穿透。
     *
     * @param projectileId 投射物的 DestroyableObject ID
     * @param pos          目标方块位置
     * @return true 表示该方块已被穿透
     */
    public boolean hasPenetrated(int projectileId, BlockPos pos) {
        Set<PenetrationKey> set = penetratedKeys.get(projectileId);
        if (set == null) return false;
        String zoneId = pos.toShortString();
        for (PenetrationKey key : set) {
            if (zoneId.equals(key.getZoneId())) return true;
        }
        return false;
    }

    /**
     * 记录指定投射物已穿透了给定方块。
     * <p>
     * 在 {@link IProjectile#onTerrainHit} 返回 {@link IProjectile.AfterHitResult.PassThrough}
     * 后由调用方写入。仅用于地形穿透。
     *
     * @param projectileId 投射物的 DestroyableObject ID
     * @param pos          目标方块位置
     * @param terrainOwner 地形的 PhysicsHost（PhysicsChunkSection）
     */
    public void markPenetrated(int projectileId, BlockPos pos, PhysicsHost terrainOwner) {
        PenetrationKey key = new PenetrationKey(terrainOwner, pos.toShortString());
        penetratedKeys.computeIfAbsent(projectileId, k -> new HashSet<>()).add(key);
    }

    /**
     * 按 SoA 索引获取投射物类型。
     * 供客户端渲染器 {@link ClientProjectileRenderer} 使用。
     *
     * @param index SoA 数组索引
     * @return 投射物类型
     */
    public ProjectileType getProjectileTypeByIndex(int index) {
        return typeCache[typeIndex[index]];
    }

    /**
     * 按 DestroyableObject ID 获取投射物对象。
     * <p>
     * 从 {@link ObjectManager#levelDestroyableObjects} 中查找并返回。
     * 该方法是一个方便的封装，调用方无需直接操作 Map。
     * 返回 {@link DestroyableObject} 而非 {@link IProjectile}，
     * 因为部分调用方（如客户端渲染器）需要调用 {@link DestroyableObject#getWorldPositionMatrix}。
     * 调用方若需 {@link IProjectile} 接口，可自行判断 {@code instanceof}。
     *
     * @param objId 目标投射物的 DestroyableObject ID
     * @return 投射物的 DestroyableObject 实例，若不存在则返回 null
     */
    @Nullable
    public DestroyableObject getProjectile(int objId) {
        Map<Integer, DestroyableObject> objMap = ObjectManager.levelDestroyableObjects.get(level);
        if (objMap == null) return null;
        return objMap.get(objId);
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
            obj.oldTransform = obj.getTransform();
            obj.transform = new Transform(obj.getPosition(), Quaternion.IDENTITY);
            obj.setLinearVelocity(new Vector3f(velX[i], velY[i], velZ[i]));
        }
    }

    /**
     * 单趟遍历：prePhysicsTick（缓存本层 Map 引用）
     */
    private void forEachPrePhysicsTick() {
        Map<Integer, DestroyableObject> objMap = ObjectManager.levelDestroyableObjects.get(level);
        if (objMap == null) return;

        for (int i = 0; i < count; i++) {
            if (!alive[i]) continue;
            DestroyableObject obj = objMap.get(objId[i]);
            if (obj != null) obj.prePhysicsTick();
        }
    }

    /**
     * 单趟遍历：postPhysicsTick（缓存本层 Map 引用）
     */
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
     * 冲刷待创建 Entity 的投射物并批量广播生成包（主线程）。
     * <p>
     * 清空 {@link #pendingProjectiles} 队列（每个条目是创建时的快照，而非 IProjectile 引用）。
     * <ol>
     *   <li>通过 {@link #findIndexByObjId(int)} 找到 SoA 索引，区块已加载则创建 {@link MMProjectileEntity}</li>
     *   <li>将快照转换为 {@link ProjectilesSpawnPayload.SpawnEntry} 列表，批量广播</li>
     * </ol>
     * 用快照而非引用——物理线程入队后会继续修改投射物 position/velocity，
     * 主线程 flush 时若读引用将得到已被积分的值（非炮口位置）。
     * <p>
     * <b>调用线程：</b>仅主线程（在 {@link #postTick()} 开头调用）。
     */
    public void flushProjectileEntities() {
        if (pendingProjectiles.isEmpty()) return;

        // ① 清空队列，收集本批所有快照（含已销毁的投射物——客户端需要生成视觉效果）
        List<PendingSpawn> spawns = new ArrayList<>();
        PendingSpawn s;
        while ((s = pendingProjectiles.poll()) != null) {
            spawns.add(s);
        }
        if (spawns.isEmpty()) return;

        // ② 逐个创建 Entity（仅存活 + 区块已加载的投射物）
        for (PendingSpawn spawn : spawns) {
            int idx = findIndexByObjId(spawn.objId());
            if (idx < 0) continue;

            Vector3f pos = spawn.position();
            if (isChunkLoadedAt(pos)) {
                createProjectileEntity(idx);
                needsEntityRecreate[idx] = false;
            }
        }

        // ③ 批量发包（从快照构造 SpawnEntry，初速/炮口位置精确）
        if (level instanceof ServerLevel serverLevel) {
            List<ProjectilesSpawnPayload.SpawnEntry> entries = new ArrayList<>(spawns.size());
            for (PendingSpawn spawn : spawns) {
                Vector3f p = spawn.position();
                Vector3f v = spawn.velocity();
                entries.add(new ProjectilesSpawnPayload.SpawnEntry(
                        spawn.objId(), spawn.typeKey(),
                        p.x, p.y, p.z, v.x, v.y, v.z));
            }
            ProjectilesSpawnPayload.broadcast(serverLevel, entries);
        }
    }

    /**
     * 清空 {@link #pendingHitSyncs} 队列，批量发送命中同步包。
     * <p>
     * 将物理线程缓冲的所有 {@link PendingHitSync} 转换为
     * {@link ProjectilesHitPayload.HitEntry} 列表，通过
     * {@link ProjectilesHitPayload#broadcast} 一次发包。
     * <p>
     * <b>调用线程：</b>仅主线程（在 {@link #postTick()} 中
     * {@link #flushProjectileEntities()} 之后调用），保证创建包先于命中包到达客户端。
     * <p>
     * 客户端收到后会逐条更新 SoA 状态并播放粒子特效。
     */
    private void flushPendingHitSyncs() {
        if (pendingHitSyncs.isEmpty()) return;

        List<ProjectilesHitPayload.HitEntry> entries = new ArrayList<>();
        PendingHitSync h;
        while ((h = pendingHitSyncs.poll()) != null) {
            Long hitBlockPosLong = h.hitBlockPos() != null ? h.hitBlockPos().asLong() : null;
            entries.add(new ProjectilesHitPayload.HitEntry(
                    h.objId(),
                    h.hitPoint().x, h.hitPoint().y, h.hitPoint().z,
                    h.hitNormal().x, h.hitNormal().y, h.hitNormal().z,
                    h.destroyed(),
                    h.newVelocity() != null ? h.newVelocity().x : 0,
                    h.newVelocity() != null ? h.newVelocity().y : 0,
                    h.newVelocity() != null ? h.newVelocity().z : 0,
                    hitBlockPosLong));
        }
        if (entries.isEmpty()) return;

        if (level instanceof ServerLevel serverLevel) {
            ProjectilesHitPayload.broadcast(serverLevel, entries);
        }
    }

    /**
     * 按 SoA 索引获取投射物类型的注册键。
     * <p>
     * 工具方法，供批量发包等外部调用方回退读取使用。
     *
     * @param idx SoA 数组索引
     * @return 投射物类型的 {@link ResourceLocation} 注册键，索引无效时返回 null
     */
    @Nullable
    public ResourceLocation getTypeKeyByIndex(int idx) {
        if (idx < 0 || idx >= count || typeIndex[idx] < 0 || typeIndex[idx] >= typeCache.length) return null;
        ProjectileType type = typeCache[typeIndex[idx]];
        return type != null ? type.getRegistryKey() : null;
    }

    /**
     * 主线程 Pre 阶段。
     * 递减所有投射物寿命 + 调用各投射物的 {@code preTick()} +
     * 尝试重建因区块卸载丢失的 {@link MMProjectileEntity}。
     * 客户端额外执行质点投射物外推（5 子步 semi-implicit Euler）。
     * <p>
     * 优化：合并寿命递减和 preTick 为一趟遍历，减少 SoA 数组重复访问。
     */
    public void preTick() {
        tickAndPreTick();
        tryRecreateEntities();
        // 客户端：主线程自主外推质点投射物，消除物理线程与渲染线程的 SoA 并发读写竞争
        if (level.isClientSide()) {
            clientExtrapolate();
        }
    }

    /**
     * 主线程 Post 阶段。
     * <ol>
     *   <li>清理物理线程延迟的代理实体（{@link #orphanedEntities}），
     *       确保在 {@code ChunkMap.tick()} 之后执行，不会并发修改 {@code entityMap}</li>
     *   <li>冲刷本帧物理线程新增的投射物 Entity 创建与发包</li>
     *   <li>调用各投射物的 {@code postTick()}，回写 SoA 到 SynchedEntityData</li>
     * </ol>
     * <p>
     * 优化：合并 postTick 和 syncToSyncedData 为一趟遍历。
     */
    public void postTick() {
        // ★ 物理线程延迟的代理实体清理（必须在 ChunkMap.tick() 之后执行）
        cleanOrphanedEntities();
        flushProjectileEntities();   // ① 先发创建包，确保客户端 SoA 中有该投射物
        flushPendingHitSyncs();      // ② 再发命中包，保证创建包严格先于命中包到达
        postTickAndSync();
    }

    /**
     * 清理物理线程 {@link #swapRemove(int)} 延迟的代理实体。
     * <p>
     * <b>调用线程：</b>仅主线程（由 {@link #postTick()} 调用，在 {@code LevelTickEvent.Post} 阶段，
     * 即 {@code ChunkMap.tick()} 之后）。
     * <p>
     * 物理线程不可直接调用 {@link MMProjectileEntity#markOrphaned()}，
     * 否则会通过 {@code entity.remove()} 修改 {@code ChunkMap.entityMap}，
     * 与主线程 {@code ChunkMap.tick()} 中正在遍历的 {@code entityMap.values()} 迭代器冲突。
     */
    private void cleanOrphanedEntities() {
        MMProjectileEntity entity;
        while ((entity = orphanedEntities.poll()) != null) {
            if (!entity.isRemoved()) {
                entity.markOrphaned();
            }
        }
    }

    /**
     * 物理线程 Pre 阶段。
     * 调用各投射物的 {@code prePhysicsTick()}，服务端执行质点投射物批量积分+碰撞检测。
     * <p>
     * 客户端不再在此阶段外推——已迁移至主线程 {@link #preTick()}。
     */
    public void prePhysicsTick(PhysicsLevel physicsLevel) {
        forEachPrePhysicsTick();
        if (!level.isClientSide()) {
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
     * 客户端自主外推所有质点投射物（Semi-implicit Euler 5 子步积分，无碰撞检测）。
     * <p>
     * 服务端仅广播关键事件（创建/命中/超时），客户端依赖自主外推来维持帧间
     * 位置连续性，供 {@code ClientProjectileRenderer} 读取。
     * <p>
     * 5 子步推进，每子步 dt = 0.01s（匹配服务端 100Hz 物理步进），
     * 消除大步长 Euler 积分在非线性阻力下的精度损失。
     * 刚体投射物跳过——状态由服务端 {@link #writebackRigidState} 同步，客户端不双重积分。
     * <p>
     * <b>调用线程：</b>主线程（由 {@link #preTick()} 调用）。
     */
    private void clientExtrapolate() {
        ProjectileType[] types = this.typeCache;
        for (int i = count - 1; i >= 0; i--) {
            if (!alive[i] && lifetime[i] < types[typeIndex[i]].getMaxLifetimeTicks()) { // 至少保证存在1tick
                projectileObjIds.remove(objId[i]);
                swapRemove(i);
            }
        }
        if (count == 0) return;

        // 5 子步 = 服务端 100Hz / 主线程 20tps，每子步 0.01s
        final int SUBSTEPS = 5;
        final float dt = 0.05f / SUBSTEPS;

        for (int sub = 0; sub < SUBSTEPS; sub++) {
            for (int i = 0; i < count; i++) {
                lifetime[i]--;
                if (!alive[i]) continue;

                ProjectileType type = types[typeIndex[i]];
                // 刚体投射物跳过客户端外推（状态由服务端 writebackRigidState 同步）
                if (type.getType().isRigid()) continue;

                // 命中帧暂停外推：保持 SoA 位置在命中点，使渲染器绘制出跳弹/穿透折角
                if (skipExtrapolate[i]) {
                    skipExtrapolate[i] = false;  // 仅暂停一帧，下帧恢复正常外推
                    continue;
                }

                float mass = type.getMass();
                float gravityFactor = type.getGravityFactor();
                float dragFactor = type.getDragFactor();
                float radius = type.getRadius();
                float speed = (float) Math.sqrt(velX[i] * velX[i] + velY[i] * velY[i] + velZ[i] * velZ[i]);

                float gravityAccY = -gravityFactor * 9.81f;
                float dragAccX = 0, dragAccY = 0, dragAccZ = 0;
                if (dragFactor > 1e-8f && speed > 1e-8f) {
                    float rho = densityFunction.getDensity(new Vec3(posX[i], posY[i], posZ[i]));
                    // F_drag = ½ · ρ · Cd · A · v²，其中 Cd=dragFactor, A=π·r²
                    float dragForce = 0.5f * rho * dragFactor * (float) Math.PI * radius * radius * speed * speed;
                    float dragAcc = dragForce / mass;
                    float invSpeed = 1f / speed;
                    dragAccX = dragAcc * (-velX[i] * invSpeed);
                    dragAccY = dragAcc * (-velY[i] * invSpeed);
                    dragAccZ = dragAcc * (-velZ[i] * invSpeed);
                }

                // Semi-implicit Euler：先更新速度，再用新速度更新位置
                velX[i] += dragAccX * dt;
                velY[i] += (gravityAccY + dragAccY) * dt;
                velZ[i] += dragAccZ * dt;

                posX[i] += velX[i] * dt;
                posY[i] += velY[i] * dt;
                posZ[i] += velZ[i] * dt;
            }
        }
    }

    /**
     * 批量更新所有活跃质点投射物（仅质点，刚体由 Bullet 管理）。
     * <p>
     * 每个物理步对每个活跃质点执行：
     * <ol>
     *   <li>清理死条（被主线程 {@link #tickAndPreTick()} 标记的）</li>
     *   <li>暂停恢复：若投射物在等待主线程命中结果，消费结果并决定飞/停</li>
     *   <li>半隐式 Euler 积分（重力 + 空气阻力）</li>
     *   <li>JME {@code rayTest} 碰撞检测</li>
     *   <li>穿透去重检查（{@link PenetrationKey}）</li>
     *   <li>命中处理 — 分层策略：
     *     <ul>
     *       <li>地形：永远停止</li>
     *       <li>SubPart / 非实体 BFHurtTarget：同步调用 dealDamage，
     *           由 BallisticsFramework 管线完成穿透判定并回调写入 {@link IProjectile.AfterHitResult}，
     *           Manager 立即消费</li>
     *       <li>Entity BFHurtTarget：通过 {@code BFDamageApi.resolveHitTarget} 决议目标，
     *           暂停投射物，提交主线程执行 hurt，下一物理帧消费结果</li>
     *       <li>非协议感知 Entity → 汇入统一异步管线，onNormalEntityHit 回调决定去留</li>
     *     </ul>
     *   </li>
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

            DestroyableObject destroyable = (objMap != null) ? objMap.get(objId[i]) : null;

            // ============================================================
            //  暂停恢复：消费主线程写入的命中结果
            // ============================================================
            if (destroyable instanceof IProjectile proj && proj.isHitPending()) {
                IProjectile.AfterHitResult result = proj.consumePendingHitResult();
                if (result == null) continue;

                proj.setHitPending(false);
                if (result.destroyed()) {
                    proj.markHit();
                    alive[i] = false;
                    projectileObjIds.remove(objId[i]);
                    broadcastHitSync(i,
                            new Vec3(posX[i], posY[i], posZ[i]),
                            new Vec3(0, 1, 0), null, result);
                    destroyable.destroy();
                    swapRemove(i);
                } else {
                    velX[i] = result.newVelocity().x;
                    velY[i] = result.newVelocity().y;
                    velZ[i] = result.newVelocity().z;
                    PenetrationKey penKey = pendingPenKeys.remove(objId[i]);
                    if (penKey != null) {
                        penetratedKeys.computeIfAbsent(objId[i], k -> new HashSet<>()).add(penKey);
                    }
                    broadcastHitSync(i,
                            new Vec3(posX[i], posY[i], posZ[i]),
                            new Vec3(0, 1, 0), null, result);
                }
                continue;
            }

            // 从类型缓存读取弹道参数
            ProjectileType type = types[typeIndex[i]];
            float mass = type.getMass();
            float gravityFactor = type.getGravityFactor();
            float dragFactor = type.getDragFactor();
            float radius = type.getRadius();
            float speed = (float) Math.sqrt(velX[i] * velX[i] + velY[i] * velY[i] + velZ[i] * velZ[i]);

            // 半隐式 Euler 积分
            float gravityAccY = -gravityFactor * 9.81f;
            float dragAccX = 0, dragAccY = 0, dragAccZ = 0;
            if (dragFactor > 1e-8f && speed > 1e-8f) {
                float rho = densityFunction.getDensity(new Vec3(posX[i], posY[i], posZ[i]));
                // F_drag = ½ · ρ · Cd · A · v²，其中 Cd=dragFactor, A=π·r²
                float dragForce = 0.5f * rho * dragFactor * (float) Math.PI * radius * radius * speed * speed;
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

            rayFrom.set(prevX, prevY, prevZ);
            rayTo.set(posX[i], posY[i], posZ[i]);

            // 射线方向分量，用于精确计算命中点（而非取方块中心）
            float dx = rayTo.x - rayFrom.x;
            float dy = rayTo.y - rayFrom.y;
            float dz = rayTo.z - rayFrom.z;

            List<PhysicsRayTestResult> results = world.rayTest(rayFrom, rayTo);

            // ===== 阶段1：收集所有命中条目到统一列表 =====
            List<HitEntry> allHits = new ArrayList<>();

            for (PhysicsRayTestResult result : results) {
                PhysicsCollisionObject collObj = result.getCollisionObject();
                if (collObj.getCollisionGroup() != CollisionGroups.PHYSICS_BODY
                        && collObj.getCollisionGroup() != CollisionGroups.TERRAIN
                        && collObj.getCollisionGroup() != CollisionGroups.PAWN)
                    continue;
                if (!(collObj instanceof PhysicsRigidBody body)) continue;

                Object owner = PhysicsBodyExtensionKt.getOwner(body);

                float hitFrac = result.getHitFraction();
                hitPointJme.set(
                        rayFrom.x + (rayTo.x - rayFrom.x) * hitFrac,
                        rayFrom.y + (rayTo.y - rayFrom.y) * hitFrac,
                        rayFrom.z + (rayTo.z - rayFrom.z) * hitFrac);
                Vec3 hitPointMc = new Vec3(hitPointJme.x, hitPointJme.y, hitPointJme.z);
                result.getHitNormalLocal(hitNormalJme);
                Vec3 hitNormalMc = new Vec3(hitNormalJme.x, hitNormalJme.y, hitNormalJme.z);

                if (owner instanceof PhysicsChunkSection terrain) {
                    // 地形：DDA遍历展开为逐方块条目，每个方块携带独立的hitFraction和面法线
                    List<BlockHitEntry> blocks = walkBlocksAlongRay(rayFrom, rayTo, terrain);
                    for (BlockHitEntry be : blocks) {
                        PenetrationKey pk = new PenetrationKey(terrain, be.blockPos().toShortString());
                        // 通过 hitFraction 线性插值计算射线进入该方块的精确命中点
                        float t = be.hitFraction();
                        Vec3 bp = new Vec3(rayFrom.x + dx * t, rayFrom.y + dy * t, rayFrom.z + dz * t);
                        // 使用DDA中射线-包围盒求交得出的精确法线
                        Vector3f bnJme = be.hitNormal();
                        Vec3 bn = (bnJme.x == 0 && bnJme.y == 0 && bnJme.z == 0)
                                ? hitNormalMc  // 安全回退（理论上不会发生）
                                : new Vec3(bnJme.x, bnJme.y, bnJme.z);
                        allHits.add(new HitEntry(be.hitFraction(), result, body, terrain,
                                be.blockPos(), terrain, bp, bn, pk));
                    }
                } else if (owner == null) {
                    // null owner：无属主命中，作为停止条目参与排序
                    allHits.add(new HitEntry(hitFrac, result, body, null,
                            null, null, hitPointMc, hitNormalMc, null));
                } else if (owner instanceof MMPartEntity || owner instanceof MMProjectileEntity) {
                    // 渲染代理，跳过
                } else {
                    // 非地形命中（实体/零件）：收集阶段做穿透去重
                    PenetrationKey pk = PenetrationKey.fromCollision(collObj, result.triangleIndex());
                    if (pk != null) {
                        Set<PenetrationKey> penetrated = penetratedKeys.get(objId[i]);
                        if (penetrated != null && penetrated.contains(pk)) {
                            continue; // 已穿透，跳过此非地形命中
                        }
                    }
                    allHits.add(new HitEntry(hitFrac, result, body, owner,
                            null, null, hitPointMc, hitNormalMc, pk));
                }
            }

            // ===== 阶段1补充：若 rayFrom 在地形方块内部，rayTest 无法检测到地形命中 =====
            // Bullet 的 GJK/SubSimplex 算法声明"起点不应在凸体内部，否则结果未定义"。
            // 当投射物穿透方块后继续飞行，下一物理帧的 rayFrom 埋在地形内部，
            // 尤其是水平射线 + 同 Y 层贪心合并大 BoxShape 场景下，rayTest 会完全 miss。
            // 此时通过 DDA 直接遍历射线路径上的方块，手动构造 HitEntry 补充地形命中条目。
            // 实体命中仍由上方 rayTest 正常检测，不受影响。
            BlockPos fromBP = BlockPos.containing(prevX, prevY, prevZ);
            var terrainMgr = physicsLevel.getTerrainManager();
            if (terrainMgr.getBlockSnapshotAt(fromBP) != null) {
                // 射线起点在地形方块内部，获取起点所在 section 并做 DDA 遍历
                PhysicsChunkSection startSection = terrainMgr.getSectionForBlockPos(fromBP);
                if (startSection != null && !startSection.isEmpty()) {
                    List<BlockHitEntry> ddaBlocks = walkBlocksAlongRay(rayFrom, rayTo, startSection);
                    // 法线取射线反方向作为回退（DDA 步进面法线的近似）
                    Vec3 ddaNormal = SparkMathKt.toVec3(rayTo.subtract(rayFrom).normalize());
                    for (BlockHitEntry be : ddaBlocks) {
                        PenetrationKey pk = new PenetrationKey(startSection, be.blockPos().toShortString());
                        // 通过 hitFraction 线性插值计算射线进入该方块的精确命中点
                        float t = be.hitFraction();
                        Vec3 bp = new Vec3(rayFrom.x + dx * t, rayFrom.y + dy * t, rayFrom.z + dz * t);
                        // 使用DDA中射线-包围盒求交得出的精确法线
                        Vector3f bnJme = be.hitNormal();
                        Vec3 bn = (bnJme.x == 0 && bnJme.y == 0 && bnJme.z == 0)
                                ? ddaNormal  // 安全回退（理论上不会发生）
                                : new Vec3(bnJme.x, bnJme.y, bnJme.z);
                        allHits.add(new HitEntry(be.hitFraction(), null,
                                startSection.getPhysicsBody(), startSection,
                                be.blockPos(), startSection, bp, bn, pk));
                    }
                }
            }

            // ===== 阶段2：按hitFraction升序排序，确保命中严格按射线方向处理 =====
            allHits.sort(Comparator.comparingDouble(HitEntry::hitFraction));

            // ===== 阶段3：按序遍历处理所有命中 =====
            boolean shouldRemove = false;
            Vector3f refVel = new Vector3f(velX[i], velY[i], velZ[i]); // 参考速度，用于统一检测跳弹方向变化
            for (HitEntry entry : allHits) {
                if (shouldRemove) break;
                // 上轮命中若导致方向改变（跳弹），跳出循环，下个物理帧用新方向重做 rayTest
                if (velocityDirectionChanged(refVel, velX[i], velY[i], velZ[i])) break;

                if (entry.terrain() != null) {
                    // ---- 地形方块命中 ----
                    PhysicsChunkSection terrain = entry.terrain();
                    BlockPos blockPos = entry.blockPos();

                    if (!(destroyable instanceof IProjectile proj)) {
                        broadcastTerrainHit(i, entry.hitPoint(), entry.blockPos());
                        alive[i] = false;
                        projectileObjIds.remove(objId[i]);
                        shouldRemove = true;
                        break;
                    }

                    // 逐方块穿透去重
                    PenetrationKey pk = entry.penKey();
                    if (pk != null) {
                        Set<PenetrationKey> penetrated = penetratedKeys.get(objId[i]);
                        if (penetrated != null && penetrated.contains(pk)) {
                            continue; // 已穿透此方块，跳过
                        }
                    }

                    SectionSnapshot.BlockSnapshot snap = terrain.getBlockSnapshot(blockPos);
                    if (snap == null || terrain.isRemoved(blockPos)) continue;

                    BlockState state = snap.getState();
                    float currentPen = proj.calculateCurrentPenetration();
                    float currentSpeed = (float) Math.sqrt(velX[i] * velX[i] + velY[i] * velY[i] + velZ[i] * velZ[i]);

                    IProjectile.AfterHitResult hitResult = proj.onTerrainHit(level, blockPos, state,
                            currentPen, currentSpeed, entry.hitPoint(), entry.hitNormal());

                    if (hitResult != null && !hitResult.destroyed()) {
                        // 穿透：更新速度，记录穿透密钥，广播
                        velX[i] = hitResult.newVelocity().x;
                        velY[i] = hitResult.newVelocity().y;
                        velZ[i] = hitResult.newVelocity().z;
                        penetratedKeys.computeIfAbsent(objId[i], k -> new HashSet<>()).add(pk);
                        broadcastHitSync(i, entry.hitPoint(), entry.hitNormal(), false, hitResult.newVelocity(), entry.blockPos());
                    } else {
                        // 无法击穿，停止
                        broadcastTerrainHit(i, entry.hitPoint(), entry.blockPos());
                        alive[i] = false;
                        projectileObjIds.remove(objId[i]);
                        shouldRemove = true;
                    }
                } else if (entry.owner() == null) {
                    // null owner：直接停止（无属主信息）
                    broadcastTerrainHit(i, entry.hitPoint(), null);
                    alive[i] = false;
                    projectileObjIds.remove(objId[i]);
                    shouldRemove = true;
                } else {
                    // ---- 非地形命中（实体/零件） ----
                    if (!(destroyable instanceof IProjectile projectile)) {
                        alive[i] = false;
                        projectileObjIds.remove(objId[i]);
                        shouldRemove = true;
                        break;
                    }

                    float currentPen = projectile.calculateCurrentPenetration();
                    float currentDmg = projectile.calculateCurrentDamage();
                    PhysicsRayTestResult result = entry.rayResult();
                    Vec3 hp = entry.hitPoint();
                    Vec3 hn = entry.hitNormal();
                    PenetrationKey pk = entry.penKey();

                    switch (entry.owner()) {
                        case SubPart subPart -> {
                            HitBox hitBox = subPart.getHitBox(result.triangleIndex());
                            if (hitBox.isActive()) {
                                shouldRemove = applyAfterHitResult(i, projectile,
                                        projectile.onPartHit(level, subPart,
                                                currentPen, currentDmg, hp, hn),
                                        hp, hn, pk);
                            }
                        }
                        case BFHurtTarget bfTarget when !(entry.owner() instanceof Entity) -> {
                            shouldRemove = applyAfterHitResult(i, projectile,
                                    projectile.onPartHit(level, bfTarget,
                                            currentPen, currentDmg, hp, hn),
                                    hp, hn, pk);
                        }
                        case Entity entity -> {
                            if (entity.isRemoved() || (entity instanceof LivingEntity living && living.isDeadOrDying()))
                                continue; // 实体已死亡，跳过
                            shouldRemove = handleEntityHit(i, projectile, entity, hp, hn, dt, pk);
                        }
                        default -> {}
                    }
                }
            }

            if (shouldRemove) {
                // 异步管线中等待主线程回调的投射物不要提前清理——下一 tick 的暂停恢复会处理
                if (destroyable instanceof IProjectile proj && proj.isHitPending()) {
                    continue;
                }
                DestroyableObject finalDestroyable = (objMap != null) ? objMap.get(objId[i]) : null;
                if (finalDestroyable != null) {
                    finalDestroyable.setPosition(new Vector3f(posX[i], posY[i], posZ[i]));
                    finalDestroyable.oldTransform = finalDestroyable.getTransform();
                    finalDestroyable.transform = new Transform(finalDestroyable.getPosition(), Quaternion.IDENTITY);
                    finalDestroyable.destroy();
                }
                swapRemove(i);
            }
        }
    }

    /**
     * 检测速度方向是否发生显著变化（统一跳弹判定）。
     * <p>
     * 比较参考速度与当前 SoA 速度的方向余弦，阈值 0.99（约 8°）。
     * 穿透保持同方向 → cos ≈ 1.0 → 不触发；跳弹反射 → cos 显著下降 → 触发。
     * <p>
     * 循环每次迭代开始时调用，替代分散在各命中路径中的单独检测。
     *
     * @param refVel 参考速度（上轮迭代结束时的 SoA 速度快照）
     * @param vx     当前 SoA 速度 X 分量
     * @param vy     当前 SoA 速度 Y 分量
     * @param vz     当前 SoA 速度 Z 分量
     * @return true 表示方向变化显著，应停止遍历本帧后续命中条目
     */
    private static boolean velocityDirectionChanged(Vector3f refVel, float vx, float vy, float vz) {
        float refLen = refVel.length();
        float newLen = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (refLen < 1e-6f || newLen < 1e-6f) return false;
        float cosAngle = (refVel.x * vx + refVel.y * vy + refVel.z * vz) / (refLen * newLen);
        return cosAngle < 0.99f;
    }

    /**
     * 应用已消费的 AfterHitResult（通过 onPartHit 返回值获得），
     * 执行 SoA 操作并广播命中同步包。
     * <p>
     * 与 {@link #applyHitResult} 的区别在于不调用 {@code consumePendingHitResult()}，
     * 因为 onPartHit 已在内部消费。
     *
     * @param i      SoA 索引
     * @param proj   投射物实例
     * @param result 已消费的命中结果（来自 onPartHit 返回值）
     * @param penKey 穿透密钥（可为 null）
     * @return true 表示投射物应销毁
     */
    private boolean applyAfterHitResult(int i, IProjectile proj, @Nullable IProjectile.AfterHitResult result,
                                         Vec3 hitPoint, Vec3 hitNormal, PenetrationKey penKey) {
        if (result == null) return false;
        broadcastHitSync(i, hitPoint, hitNormal, null, result);
        if (result.destroyed()) {
            proj.markHit();
            alive[i] = false;
            projectileObjIds.remove(objId[i]);
            return true;
        }
        velX[i] = result.newVelocity().x;
        velY[i] = result.newVelocity().y;
        velZ[i] = result.newVelocity().z;
        if (penKey != null) {
            penetratedKeys.computeIfAbsent(objId[i], k -> new HashSet<>()).add(penKey);
        }
        return false;
    }

    /**
     * 通过 consumePendingHitResult() 消费后应用命中结果。
     *
     * @see #applyAfterHitResult
     */
    private boolean applyHitResult(int i, IProjectile proj, Vec3 hitPoint, Vec3 hitNormal, boolean isArmorHit, PenetrationKey penKey) {
        return applyAfterHitResult(i, proj, proj.consumePendingHitResult(), hitPoint, hitNormal, penKey);
    }

    /**
     * 处理实体命中。委托 {@link IProjectile#onEntityHit} 统一管线。
     * <p>
     * 同步路径（决议到非实体 BFHurtTarget）：onEntityHit 返回即时结果，直接 apply。
     * 异步路径（Entity / 非协议实体）：onEntityHit 返回 null，Manager 保存穿透密钥以待后续帧消费。
     *
     * @return true 表示投射物已停止或被标记为暂停（hitPending）
     */
    private boolean handleEntityHit(int i, IProjectile projectile, Entity entity,
                                    Vec3 hitPoint, Vec3 hitNormal, float dt,
                                    PenetrationKey penKey) {
        IProjectile.AfterHitResult result = projectile.onEntityHit(level, entity,
                projectile.calculateCurrentPenetration(),
                projectile.calculateCurrentDamage(),
                hitPoint, hitNormal);

        if (result == null) {
            // 异步路径：保存穿透密钥，投射物已由 onEntityHit 标记 hitPending
            if (penKey != null) pendingPenKeys.put(objId[i], penKey);
            return true;
        }

        // 同步路径（决议到非实体 BFHurtTarget）：直接应用结果
        return applyAfterHitResult(i, projectile, result, hitPoint, hitNormal, penKey);
    }

    /**
     * 入队命中同步数据（物理线程），等待主线程批量广播。
     * <p>
     * 替代原先的直接 {@code ProjectileHitSyncPayload.broadcast()} 调用。
     * 所有命中事件统一走此方法 → {@link #pendingHitSyncs} 队列 →
     * 主线程 {@link #flushPendingHitSyncs()} 批量发包。
     * <p>
     * <b>调用线程：</b>物理线程（{@link #updatePointProjectiles} 内部广播方法，
     * 以及 {@link RigidProjectile} 碰撞回调）。
     * <p>
     * <b>参数语义：</b>
     * <ul>
     *   <li>地形命中：{@code hitBlockPos} 非 null，客户端收到后查方块播原生粒子/音效</li>
     *   <li>SubPart / Entity 命中：{@code hitBlockPos} 为 null，特效由各自自理包负责，命中包仅做弹道状态同步</li>
     * </ul>
     *
     * @param objId       投射物 DestroyableObject ID
     * @param hitPoint    命中点世界坐标
     * @param hitNormal   命中面法线
     * @param destroyed   投射物是否已销毁
     * @param newVelocity 穿透后剩余速度（destroyed=true 时可为 null）
     * @param hitBlockPos 地形命中时为被命中方块位置，客户端据此查方块播特效；非地形命中为 null
     */
    public void enqueueHitSync(int objId, Vec3 hitPoint, Vec3 hitNormal,
                               boolean destroyed, @Nullable Vector3f newVelocity, @Nullable BlockPos hitBlockPos) {
        pendingHitSyncs.add(new PendingHitSync(objId, hitPoint, hitNormal, destroyed, newVelocity, hitBlockPos));
    }

    /**
     * 广播命中同步（携带 AfterHitResult），改为入队缓冲。
     * <p>
     * SubPart/Entity 命中路径专用——参数 {@code hitBlockPos} 始终为 null，
     * 因为此类命中的特效由 SubPart/Entity 自理包负责，命中包仅做弹道状态同步。
     */
    private void broadcastHitSync(int i, Vec3 hitPoint, Vec3 hitNormal, @Nullable BlockPos hitBlockPos, @Nullable IProjectile.AfterHitResult result) {
        boolean destroyed = result == null || result.destroyed();
        Vector3f newVel = destroyed ? new Vector3f() : result.newVelocity();
        pendingHitSyncs.add(new PendingHitSync(objId[i], hitPoint, hitNormal, destroyed, newVel, hitBlockPos));
    }

    /**
     * 广播命中同步（显式指定销毁状态和速度），改为入队缓冲。
     * 用于 terrain 穿透/停止、suspend/resume 等已确定结果但无 HitResult 的场景。
     */
    private void broadcastHitSync(int i, Vec3 hitPoint, Vec3 hitNormal, boolean destroyed, @Nullable Vector3f newVel, @Nullable BlockPos hitBlockPos) {
        pendingHitSyncs.add(new PendingHitSync(objId[i], hitPoint, hitNormal, destroyed, newVel, hitBlockPos));
    }

    /**
     * 命中地形时的命中同步，改为入队缓冲。
     *
     * @param hitBlockPos 被命中的方块位置，客户端据此查方块播特效；null 用于无方块信息场景（如 null owner）
     */
    private void broadcastTerrainHit(int i, Vec3 hitPoint, @Nullable BlockPos hitBlockPos) {
        pendingHitSyncs.add(new PendingHitSync(objId[i], hitPoint, new Vec3(0, 1, 0), true, null, hitBlockPos));
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

    /**
     * 在发射时预测投射物弹道经过的区块，并预约地形刚体加载。
     * <p>
     * 使用 {@link RealisticTrajectory#forwardSolve} 进行弹道正解，
     * 物理模型与 {@link #updatePointProjectiles} 统一（相同的阻力公式+密度函数），
     * 确保预测轨迹与实际飞行轨迹一致。
     * <p>
     * 预测长度 = 投射物最大寿命（{@link ProjectileType#getMaxLifetimeTicks}），
     * 遍历所有采样点，按 {@link ChunkPos} 分组收集 Y 范围，
     * 对每个唯一区块调用 {@link SparkLevel#scheduleChunkLoad} 预约地形加载。
     * <p>
     * 性能：单次发射约 200~600 次浮点运算（取决于寿命），
     * 最多预加载 200 个区块，去重后通常远小于此值。
     *
     * @param startPos 发射位置（JME 世界坐标，米）
     * @param startVel 初速度（m/s，JME）
     * @param type     投射物类型（提供质量、阻力系数、重力等物理参数）
     */
    private void preloadTrajectoryTerrain(Vector3f startPos, Vector3f startVel, ProjectileType type) {
        // ProjectileType 与 BallisticConfig 1:1 映射（阻力公式统一为 ½·ρ·Cd·A·v²）
        // dragFactor = Cd, π·r² = A, gravityFactor·9.81 = g
        BallisticConfig config = new BallisticConfig(
                type.getDragFactor(),                                    // Cd（阻力系数）
                type.getMass(),                                          // 质量 (kg)
                (float) (Math.PI * type.getRadius() * type.getRadius()), // A = π·r² (m²)
                type.getGravityFactor() * 9.81f,                         // 重力加速度 (m/s²)
                1f / SparkLevel.getPhysicsLevel(getLevel()).getTps(),    // 时间步长 = 1 物理 tick
                type.getMaxLifetimeTicks()                               // 预测长度 = 投射物寿命
        );

        // JME Vector3f → MC Vec3（坐标轴一致，都是米制右手系 Y-up）
        Vec3 startMc = new Vec3(startPos.x, startPos.y, startPos.z);
        Vec3 velMc = new Vec3(startVel.x, startVel.y, startVel.z);

        // 弹道正解（使用与 updatePointProjectiles 相同的密度函数）
        TrajectoryResult result = RealisticTrajectory.forwardSolve(startMc, velMc, config, densityFunction);

        // 按 ChunkPos 分组，收集每个区块的到达 tick 和 Y 范围
        LinkedHashMap<ChunkPos, int[]> chunkInfo = new LinkedHashMap<>(); // int[3]: [arrivalTick, minY, maxY]
        int maxChunks = 200; // 最多预加载 200 个区块，防止极端情况

        for (int tick = 0; tick < result.samples().size(); tick++) {
            TrajectorySample sample = result.samples().get(tick);
            Vec3 p = sample.position();

            // 出界检查：超出 MC 世界 Y 范围则停止预测
            if (p.y < -64 || p.y > 320) break;

            ChunkPos cp = new ChunkPos((int) Math.floor(p.x) >> 4, (int) Math.floor(p.z) >> 4);
            int y = (int) p.y;

            chunkInfo.merge(cp, new int[]{tick, y, y}, (old, cur) -> {
                old[0] = Math.min(old[0], cur[0]); // 最早到达 tick
                old[1] = Math.min(old[1], cur[1]); // 最低 Y
                old[2] = Math.max(old[2], cur[2]); // 最高 Y
                return old;
            });

            if (chunkInfo.size() >= maxChunks) break;
        }

        // 为每个预测区块预约地形加载
        int yPadding = 8;   // Y 方向扩展，覆盖预测偏差和弹道弧度
        int leadTicks = 20;  // 提前 1 秒开始加载，留足异步碰撞形状构建时间
        int holdTicks = 60;  // 加载后保持 3 秒，覆盖穿透/跳弹后的残余飞行

        for (var entry : chunkInfo.entrySet()) {
            ChunkPos cp = entry.getKey();
            int[] info = entry.getValue();
            int arrivalTick = info[0];
            int minY = info[1] - yPadding;
            int maxY = info[2] + yPadding;

            int delayTicks = Math.max(0, arrivalTick - leadTicks);
            SparkLevel.scheduleChunkLoad(level, cp, minY, maxY, delayTicks, holdTicks);
        }
    }

    /**
     * 射线与轴对齐包围盒求交（slab法），返回命中参数t和面法线。
     * <p>
     * 对每个轴分别计算进入/离开参数t0/t1，取最大进入t和最小离开t。
     * 若进入t ≤ 离开t则命中，命中面为进入t最大的轴对应的面。
     *
     * @param origin 射线起点（世界坐标）
     * @param rdx    射线方向X分量（非归一化）
     * @param rdy    射线方向Y分量
     * @param rdz    射线方向Z分量
     * @param minX   包围盒最小X
     * @param minY   包围盒最小Y
     * @param minZ   包围盒最小Z
     * @param maxX   包围盒最大X
     * @param maxY   包围盒最大Y
     * @param maxZ   包围盒最大Z
     * @return float[]{t, nx, ny, nz}，t为命中参数，法线指向射线来源侧；未命中返回null
     */
    @Nullable
    private static float[] rayAabbIntersect(Vector3f origin, float rdx, float rdy, float rdz,
                                            float minX, float minY, float minZ,
                                            float maxX, float maxY, float maxZ) {
        float tEnter = 0f;
        float tExit = 1f;
        int normalAxis = -1;
        float normalSign = 0;

        // X轴
        if (rdx != 0) {
            float invD = 1.0f / rdx;
            float t0 = (minX - origin.x) * invD;
            float t1 = (maxX - origin.x) * invD;
            if (t0 > t1) { float tmp = t0; t0 = t1; t1 = tmp; }
            if (t0 > tEnter) { tEnter = t0; normalAxis = 0; normalSign = (rdx > 0) ? -1 : 1; }
            if (t1 < tExit) tExit = t1;
        } else if (origin.x < minX || origin.x > maxX) {
            return null; // 射线平行于X轴且原点在slab外
        }

        // Y轴
        if (rdy != 0) {
            float invD = 1.0f / rdy;
            float t0 = (minY - origin.y) * invD;
            float t1 = (maxY - origin.y) * invD;
            if (t0 > t1) { float tmp = t0; t0 = t1; t1 = tmp; }
            if (t0 > tEnter) { tEnter = t0; normalAxis = 1; normalSign = (rdy > 0) ? -1 : 1; }
            if (t1 < tExit) tExit = t1;
        } else if (origin.y < minY || origin.y > maxY) {
            return null;
        }

        // Z轴
        if (rdz != 0) {
            float invD = 1.0f / rdz;
            float t0 = (minZ - origin.z) * invD;
            float t1 = (maxZ - origin.z) * invD;
            if (t0 > t1) { float tmp = t0; t0 = t1; t1 = tmp; }
            if (t0 > tEnter) { tEnter = t0; normalAxis = 2; normalSign = (rdz > 0) ? -1 : 1; }
            if (t1 < tExit) tExit = t1;
        } else if (origin.z < minZ || origin.z > maxZ) {
            return null;
        }

        if (tEnter > tExit) return null; // 未命中

        float nx = 0, ny = 0, nz = 0;
        switch (normalAxis) {
            case 0: nx = normalSign; break;
            case 1: ny = normalSign; break;
            case 2: nz = normalSign; break;
        }
        return new float[]{tEnter, nx, ny, nz};
    }

    /**
     * DDA体素遍历产生的单一方块命中条目，携带沿射线的hitFraction和面法线。
     *
     * @param hitFraction 沿全射线(rayFrom→rayTo)的参数t值 [0, 1]，由射线-方块包围盒精确求交得出
     * @param blockPos    命中的方块世界坐标
     * @param hitNormal   该方块命中面的法线（JME），方向指向射线来源侧，
     *                    由射线-包围盒slab求交直接得出
     */
    private record BlockHitEntry(float hitFraction, BlockPos blockPos, Vector3f hitNormal) {}

    /**
     * 物理线程缓冲的命中同步数据，等待主线程批量广播。
     * <p>
     * 所有命中同步不再从物理线程直接发包，而是打包为此记录入队到
     * {@link #pendingHitSyncs}，由主线程 {@link #flushPendingHitSyncs()}
     * 统一转换为 {@link ProjectilesHitPayload.HitEntry} 并批量发送。
     *
     * @param objId       投射物 DestroyableObject ID
     * @param hitPoint    命中点世界坐标（MC Vec3，不可变值类型）
     * @param hitNormal   命中面法线（MC Vec3，不可变值类型）
     * @param destroyed   投射物是否已销毁
     * @param newVelocity 穿透后剩余速度（destroyed=true 时为 null）
     * @param hitBlockPos 地形命中时为对应方块位置（客户端据此查方块播特效），非地形命中为 null
     */
    private record PendingHitSync(
            int objId,
            Vec3 hitPoint,
            Vec3 hitNormal,
            boolean destroyed,
            @Nullable Vector3f newVelocity,
            @Nullable BlockPos hitBlockPos
    ) {}

    /**
     * 物理线程捕获的投射物创建快照，等待主线程创建 Entity 并广播。
     * <p>
     * 物理线程创建投射物后立即捕获炮口位置、初速和类型键，
     * 入队到 {@link #pendingProjectiles}。主线程 flush 时读取快照中的凝固数据，
     * 而非从 {@link IProjectile} 引用读取已被物理积分覆盖的当前值。
     *
     * @param objId    DestroyableObject ID
     * @param typeKey  投射物类型注册键
     * @param position 炮口世界坐标（JME Vector3f，已 clone 独立副本）
     * @param velocity 初速矢量（JME Vector3f，已 clone 独立副本）
     */
    private record PendingSpawn(
            int objId,
            ResourceLocation typeKey,
            Vector3f position,
            Vector3f velocity
    ) {}

    /**
     * 使用3D DDA（Amanatides-Woo）体素遍历 + 方块包围盒精确求交，获取射线在指定
     * PhysicsChunkSection内经过的所有有效方块的命中信息。
     * <p>
     * 解决JME Bullet rayTest对同一刚体仅返回最近命中点的问题：
     * PhysicsChunkSection内部是CompoundCollisionShape（多子形状，且经过贪心合并优化），
     * 但rayTest按碰撞体粒度报告，高速投射物在一帧内穿过section内多个方块时，
     * 只会检测到最近的那个。本方法通过纯数学的体素遍历解决此问题，
     * 并利用方块真实的VoxelShape包围盒做精确射线-AABB求交（而非用1×1×1立方体近似），
     * 支持半砖、楼梯、栅栏等非完整碰撞体积方块。
     * <p>
     * 算法步骤：
     * <ol>
     *   <li>用slab法裁剪射线到section的AABB范围（16×16×16），得到进入/离开参数tMin/tMax</li>
     *   <li>在裁剪后的区间内执行Amanatides-Woo 3D DDA遍历</li>
     *   <li>对每个有效方块，获取其VoxelShape的包围盒列表，逐一做射线-AABB slab求交</li>
     *   <li>仅当射线实际命中包围盒时才记录该方块，携带精确的命中t值和面法线</li>
     * </ol>
     * <p>
     * 性能：section最大尺寸16×16×16，单条射线最多遍历~48个网格步；
     * VoxelShape→AABB列表有静态缓存，且大部方块仅1个AABB，开销可忽略。
     *
     * @param rayFrom 射线起点（世界坐标，JME）
     * @param rayTo   射线终点（世界坐标，JME）
     * @param terrain 目标地形section
     * @return 射线在section内实际命中的有效方块列表（按命中顺序，含精确hitFraction和面法线）
     */
    private static List<BlockHitEntry> walkBlocksAlongRay(Vector3f rayFrom, Vector3f rayTo, PhysicsChunkSection terrain) {
        List<BlockHitEntry> blocks = new ArrayList<>();

        SectionPos sectionPos = terrain.getSectionPos();
        // section的世界坐标范围（方块坐标：min ~ min+15，浮点边界用于裁剪：min ~ min+16）
        int secMinX = sectionPos.x() * 16;
        int secMinY = sectionPos.y() * 16;
        int secMinZ = sectionPos.z() * 16;
        float secMaxX = secMinX + 16.0f;
        float secMaxY = secMinY + 16.0f;
        float secMaxZ = secMinZ + 16.0f;

        float dx = rayTo.x - rayFrom.x;
        float dy = rayTo.y - rayFrom.y;
        float dz = rayTo.z - rayFrom.z;

        // slab法裁剪射线到section AABB，计算进入/离开的t参数
        float tMin = 0f;
        float tMax = 1f;

        // X轴裁剪
        if (dx != 0) {
            float t1 = (secMinX - rayFrom.x) / dx;
            float t2 = (secMaxX - rayFrom.x) / dx;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
        } else if (rayFrom.x < secMinX || rayFrom.x >= secMaxX) {
            return blocks; // 射线平行于X轴且不在section范围内
        }

        // Y轴裁剪
        if (dy != 0) {
            float t1 = (secMinY - rayFrom.y) / dy;
            float t2 = (secMaxY - rayFrom.y) / dy;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
        } else if (rayFrom.y < secMinY || rayFrom.y >= secMaxY) {
            return blocks;
        }

        // Z轴裁剪
        if (dz != 0) {
            float t1 = (secMinZ - rayFrom.z) / dz;
            float t2 = (secMaxZ - rayFrom.z) / dz;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
        } else if (rayFrom.z < secMinZ || rayFrom.z >= secMaxZ) {
            return blocks;
        }

        if (tMin > tMax) return blocks; // 射线与section不相交

        // 裁剪后的射线起点和终点
        float startX = rayFrom.x + dx * tMin;
        float startY = rayFrom.y + dy * tMin;
        float startZ = rayFrom.z + dz * tMin;
        float endX = rayFrom.x + dx * tMax;
        float endY = rayFrom.y + dy * tMax;
        float endZ = rayFrom.z + dz * tMax;

        // DDA起始体素
        int curX = (int) Math.floor(startX);
        int curY = (int) Math.floor(startY);
        int curZ = (int) Math.floor(startZ);

        // DDA终点体素
        int endXi = (int) Math.floor(endX);
        int endYi = (int) Math.floor(endY);
        int endZi = (int) Math.floor(endZ);

        // 步进方向
        int stepX = (dx > 0) ? 1 : (dx < 0 ? -1 : 0);
        int stepY = (dy > 0) ? 1 : (dy < 0 ? -1 : 0);
        int stepZ = (dz > 0) ? 1 : (dz < 0 ? -1 : 0);

        // tDelta：跨一个体素所需的参数步长
        float tDeltaX = (dx != 0) ? Math.abs(1.0f / dx) : Float.MAX_VALUE;
        float tDeltaY = (dy != 0) ? Math.abs(1.0f / dy) : Float.MAX_VALUE;
        float tDeltaZ = (dz != 0) ? Math.abs(1.0f / dz) : Float.MAX_VALUE;

        // tMax：到达下一个体素边界的参数值（相对于裁剪起点startX）
        float tMaxX = (dx != 0) ? ((stepX > 0 ? (curX + 1) : curX) - startX) / dx : Float.MAX_VALUE;
        float tMaxY = (dy != 0) ? ((stepY > 0 ? (curY + 1) : curY) - startY) / dy : Float.MAX_VALUE;
        float tMaxZ = (dz != 0) ? ((stepZ > 0 ? (curZ + 1) : curZ) - startZ) / dz : Float.MAX_VALUE;

        // Amanatides-Woo 3D DDA主循环，利用方块包围盒做精确射线求交
        int maxSteps = 48; // 16+16+16 最坏情况
        for (int step = 0; step < maxSteps; step++) {
            BlockPos bp = new BlockPos(curX, curY, curZ);
            boolean isLast = (curX == endXi && curY == endYi && curZ == endZi);

            // 获取方块快照，检查有效性
            SectionSnapshot.BlockSnapshot snap = terrain.getBlockSnapshot(bp);
            if (snap != null && !terrain.isRemoved(bp)) {
                BlockState state = snap.getState();
                // 从Spark-Core缓存获取该方块的碰撞包围盒列表（构建时已预缓存）
                List<AABB> aabbs = terrain.getPhysicsLevel().getBlockShapeManager().getBlockAabbs(state);

                float closestT = Float.MAX_VALUE;
                Vector3f closestNormal = null;

                // 遍历方块的所有包围盒子形状，做精确射线求交
                for (AABB aabb : aabbs) {
                    float[] result = rayAabbIntersect(rayFrom, dx, dy, dz,
                            bp.getX() + (float) aabb.minX,
                            bp.getY() + (float) aabb.minY,
                            bp.getZ() + (float) aabb.minZ,
                            bp.getX() + (float) aabb.maxX,
                            bp.getY() + (float) aabb.maxY,
                            bp.getZ() + (float) aabb.maxZ);
                    if (result != null && result[0] < closestT) {
                        closestT = result[0];
                        closestNormal = new Vector3f(result[1], result[2], result[3]);
                    }
                }

                if (closestNormal != null) {
                    // 射线实际命中该方块的碰撞体积，使用精确的命中参数和法线
                    blocks.add(new BlockHitEntry(closestT, bp, closestNormal));
                }
                // 若射线未命中实际碰撞体积（如穿过楼梯的空隙），则跳过该方块
            }

            // 到达终点体素则停止
            if (isLast) break;

            // 选择tMax最小的轴步进，并记录步进前的minT作为下一体素的进入t
            float minT;
            if (tMaxX < tMaxY) {
                if (tMaxX < tMaxZ) {
                    minT = tMaxX;
                    curX += stepX;
                    tMaxX += tDeltaX;
                } else {
                    minT = tMaxZ;
                    curZ += stepZ;
                    tMaxZ += tDeltaZ;
                }
            } else {
                if (tMaxY < tMaxZ) {
                    minT = tMaxY;
                    curY += stepY;
                    tMaxY += tDeltaY;
                } else {
                    minT = tMaxZ;
                    curZ += stepZ;
                    tMaxZ += tDeltaZ;
                }
            }
        }

        return blocks;
    }

    /**
     * 统一命中条目。收集阶段由rayTest非地形结果或DDA地形遍历展开产生，
     * 按 hitFraction 排序后统一逐条处理。
     * <p>
     * rayResult 和 body 可为 null——当射线起点在地形内部时，rayTest 无法检测到命中，
     * 此时由 DDA 遍历直接构造 HitEntry，不经过 Bullet 射线检测结果。
     *
     * @param hitFraction 沿全射线(rayFrom→rayTo)的参数t值 [0, 1]
     * @param rayResult   原始射线检测结果（DDA补充条目为null）
     * @param body        碰撞刚体（DDA补充条目为null）
     * @param owner       碰撞体所有者（PhysicsChunkSection / SubPart / Entity / BFHurtTarget）
     * @param blockPos    地形方块位置（仅地形命中非null）
     * @param terrain     地形section引用（仅地形命中非null）
     * @param hitPoint    命中点世界坐标（MC Vec3）
     * @param hitNormal   命中法线（MC Vec3）
     * @param penKey      穿透去重密钥（可为null）
     */
    private record HitEntry(
            float hitFraction,
            @Nullable PhysicsRayTestResult rayResult,
            @Nullable PhysicsRigidBody body,
            Object owner,
            @Nullable BlockPos blockPos,
            @Nullable PhysicsChunkSection terrain,
            Vec3 hitPoint,
            Vec3 hitNormal,
            @Nullable PenetrationKey penKey
    ) {}

    /**
     * O(1) swap-with-last 移除（联动交换 Entity 数组，清理穿透记录）。
     * <p>
     * <b>代理实体清理延迟到主线程：</b>物理线程不可调用 {@link MMProjectileEntity#markOrphaned()}，
     * 否则 {@code entity.remove()} 会修改 {@code ChunkMap.entityMap}，
     * 与主线程 {@code ChunkMap.tick()} 中的 {@code entityMap.values()} 遍历产生并发修改。
     * 改为将实体引用入队 {@link #orphanedEntities}，由 {@link #postTick()} 在主线程统一清理。
     */
    private void swapRemove(int index) {
        // ★ 物理线程安全：延迟 Entity 清理到主线程，避免修改 ChunkMap.entityMap
        if (entities[index] != null) {
            orphanedEntities.add(entities[index]);
            entities[index] = null;
        }
        // 清理穿透记录
        penetratedKeys.remove(objId[index]);
        pendingPenKeys.remove(objId[index]);
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
            skipExtrapolate[index] = skipExtrapolate[last];
            needsEntityRecreate[index] = needsEntityRecreate[last];
            // ★ entities 数组交换：将 last 位置的引用搬到 index 位置
            //    注意：last 位置的 entity 可能已在上一次 swapRemove 中被标记为待清理
            //    但尚未被主线程处理，此时将其转移到 index 位置继续等待即可
            entities[index] = entities[last];
        }
        count--;
    }

    /**
     * ×2 动态扩容（联动扩容 Entity 数组）
     */
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
        skipExtrapolate = Arrays.copyOf(skipExtrapolate, newCap);
        needsEntityRecreate = Arrays.copyOf(needsEntityRecreate, newCap);
        entities = Arrays.copyOf(entities, newCap);
        capacity = newCap;
    }
}
