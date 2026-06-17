package io.github.sweetzonzi.machine_max.common.mech.projectile;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.PenetrationKey;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.physics.terrain.PhysicsChunkSection;
import cn.solarmoon.spark_core.physics.terrain.SectionSnapshot;
import cn.solarmoon.spark_core.util.PPhase;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.client.render.renderer.ClientProjectileRenderer;
import io.github.sweetzonzi.machine_max.common.entity.MMProjectileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageApi;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageExtensions;
import io.github.sweetzonzi.ballistics_framework.api.BFHurtTarget;
import io.github.sweetzonzi.ballistics_framework.api.BFHitResolveResult;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.MMServerConfig;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.util.mechanic.ArmorUtil;
import io.github.sweetzonzi.machine_max.util.mechanic.DamageUtil;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import io.github.sweetzonzi.machine_max.common.registry.MMEntities;
import io.github.sweetzonzi.machine_max.network.payload.projectile.ProjectileBatchSpawnPayload;
import io.github.sweetzonzi.machine_max.network.payload.projectile.ProjectileHitSyncPayload;
import lombok.Getter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
     * 关联的 {@link MMProjectileEntity} 引用（下标对应 SoA 索引，可为 null）。
     * 服务端：由 {@link #createProjectileEntity(int)} 设置。
     * 客户端：由 {@link MMProjectileEntity#tryBindProjectile()} 建立关联后保留。
     */
    public MMProjectileEntity[] entities;

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

    /**
     * 暂存的穿透密钥：物理线程记录（命中时立即写入），
     * 后续帧消费命中结果时取出并合并到 {@link #penetratedKeys}。
     * 仅用于命中实体目标的异步路径（命中帧无法确认结果）。
     */
    private final Map<Integer, PenetrationKey> pendingPenKeys = new HashMap<>();

    /** 该维度所有已加载的投射物类型，typeIndex 映射到此数组 */
    private volatile ProjectileType[] typeCache;

    /**
     * 待主线程创建 Entity 的投射物队列。
     * <p>
     * <b>生产者：</b>物理线程（{@link #addProjectileInternal(IProjectile, Vector3f, Vector3f)}）
     * — 写入 SoA 后在 volatile count++ 之前入队。<br>
     * <b>消费者：</b>主线程（{@link #flushProjectileEntities()} 清空）。<br>
     * 使用 {@link ConcurrentLinkedQueue} 保证无锁安全。
     * <p>
     * 缓存 {@link IProjectile} 引用而非 objId——防止同一物理 tick 内投射物出膛即命中、
     * 已从 {@link ObjectManager} 和 SoA 移除后主线程无法找到对象。
     */
    private final ConcurrentLinkedQueue<IProjectile> pendingProjectiles = new ConcurrentLinkedQueue<>();

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
        entities = new MMProjectileEntity[capacity];
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

    /**
     * 内部：将投射物的位置/速度/类型写入 SoA。
     * <p>
     * <b>调用线程：</b>物理线程（{@link PointProjectile}/{@link RigidProjectile} 构造链）。
     * 写入后通过 volatile count++ 保证 happens-before，主线程可在 {@link #preTick()} 中安全读取。
     * <p>
     * 不再在此方法内创建 Entity 或发包——改为入队 {@link #pendingProjectiles}，
     * 由主线程 {@link #flushProjectileEntities()} 统一处理。
     */
    private void addProjectileInternal(IProjectile proj, Vector3f pos, Vector3f vel) {
        if (proj instanceof DestroyableObject projectile)
            ObjectManager.addDestroyableObject(projectile);
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

        // 服务端：入队 pendingProjectiles，由主线程 flushProjectileEntities 统一处理
        if (!level.isClientSide()) {
            pendingProjectiles.add(proj);
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

        MMProjectileEntity entity = new MMProjectileEntity(MMEntities.getPROJECTILE_ENTITY().get(), level);
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
     * 冲刷待创建 Entity 的投射物（主线程）。
     * <p>
     * 清空 {@link #pendingProjectiles} 队列，对每个待创建投射物（无论是否已销毁）：
     * <ol>
     *   <li>通过 {@link #findIndexByObjId(int)} 找到 SoA 索引，区块已加载则创建 {@link MMProjectileEntity}</li>
     * </ol>
     * 最后将整批 {@link IProjectile} 引用传给 {@link ProjectileBatchSpawnPayload#broadcast}
     * 统一发包。即使投射物已销毁，引用的字段（pos/vel/typeKey）仍可读。
     * <p>
     * <b>调用线程：</b>仅主线程（在 {@link #postTick()} 开头调用）。
     */
    public void flushProjectileEntities() {
        if (pendingProjectiles.isEmpty()) return;

        // ① 清空队列，收集本批所有投射物（含已销毁的——客户端需要生成视觉效果）
        List<IProjectile> projs = new ArrayList<>();
        IProjectile proj;
        while ((proj = pendingProjectiles.poll()) != null) {
            projs.add(proj);
        }
        if (projs.isEmpty()) return;

        // ② 逐个创建 Entity（仅存活 + 区块已加载的投射物）
        for (IProjectile p : projs) {
            int objId = ((DestroyableObject) p).getId();
            int idx = findIndexByObjId(objId);
            if (idx < 0) continue;

            if (isChunkLoadedAt(p.getPosition())) {
                createProjectileEntity(idx);
                needsEntityRecreate[idx] = false;
            }
        }

        // ③ 一次批量发包（broadcast 内部从 IProjectile 引用构造 SpawnEntry）
        if (level instanceof ServerLevel serverLevel) {
            ProjectileBatchSpawnPayload.broadcast(serverLevel, projs);
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
     * <p>
     * 优化：合并寿命递减和 preTick 为一趟遍历，减少 SoA 数组重复访问。
     */
    public void preTick() {
        tickAndPreTick();
        tryRecreateEntities();
    }

    /**
     * 主线程 Post 阶段。
     * 先冲刷本帧物理线程新增的投射物 Entity 创建与发包，保证新生投射物发送其创建时的位姿，
     * 再调用各投射物的 {@code postTick()}，然后将 SoA 位置/速度回写到 SynchedEntityData。
     * <p>
     * 优化：合并 postTick 和 syncToSyncedData 为一趟遍历。
     */
    public void postTick() {
        flushProjectileEntities();
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
            float radius = type.getRadius();
            float speed = (float) Math.sqrt(velX[i] * velX[i] + velY[i] * velY[i] + velZ[i] * velZ[i]);

            float gravityAccY = -gravityFactor * 9.81f;
            float dragAccX = 0, dragAccY = 0, dragAccZ = 0;
            if (dragFactor > 1e-8f && speed > 1e-8f) {
                float dragForce = dragFactor * speed * speed * radius * radius * 3.14159f;
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
                        new Vec3(0, 1, 0), true, result);
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
                        new Vec3(0, 1, 0), true, result);
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
                float dragForce = dragFactor * speed * speed * radius * radius * 3.14159f;
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

            List<PhysicsRayTestResult> results = world.rayTest(rayFrom, rayTo);
            boolean stopped = false;
            label:
            for (PhysicsRayTestResult result : results) {
                PhysicsCollisionObject collObj = result.getCollisionObject();
                if (collObj.getCollisionGroup() != CollisionGroups.PHYSICS_BODY
                        && collObj.getCollisionGroup() != CollisionGroups.TERRAIN
                            && collObj.getCollisionGroup() != CollisionGroups.PAWN)
                    continue;
                if (!(collObj instanceof PhysicsRigidBody body)) continue;

                // 穿透去重检查
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

                // 地形碰撞：尝试穿透或停止
                if (owner instanceof PhysicsChunkSection terrain) {
                    BlockPos blockPos = terrain.getBlockPosFromContactPoint(hitPointJme, hitNormalJme, 0);
                    SectionSnapshot.BlockSnapshot blockSnap = terrain.getBlockSnapshot(blockPos);
                    if (blockSnap == null || terrain.isRemoved(blockPos)) continue;

                    BlockState blockState = blockSnap.getState();
                    float blockArmor = ArmorUtil.getBlockArmor(level, blockState, BlockPos.ZERO);
                    float pen = destroyable instanceof IProjectile proj ? proj.calculateCurrentPenetration() : 0;

                    if (pen > blockArmor && destroyable instanceof IProjectile) {
                        // 击穿方块，继续飞行
                        ProjectileType pType = types[typeIndex[i]];
                        float impactSpeed = (float) Math.sqrt(velX[i]*velX[i] + velY[i]*velY[i] + velZ[i]*velZ[i]);
                        float newSpeed = IProjectile.speedAfterPenetration(
                                impactSpeed, pen, blockArmor, pType.getPenetrationVelocityCoefficient());
                        float scale = newSpeed / Math.max(impactSpeed, 0.001f);
                        velX[i] *= scale;
                        velY[i] *= scale;
                        velZ[i] *= scale;

                        // 记录穿透密钥，防同帧重复判定
                        if (penKey != null) {
                            penetratedKeys.computeIfAbsent(objId[i], k -> new HashSet<>()).add(penKey);
                        }

                        // 是否实际破坏方块（受 ServerConfig + blockDamageFactor 双重控制）
                        if (MMServerConfig.projectileDestroyBlocks()
                                && pType.getBlockDamageFactor() > 0
                                && !level.isClientSide()) {
                            float kineticEnergy = 0.5f * pType.getMass() * impactSpeed * impactSpeed;
                            float blockDurability = DamageUtil.getMaxBlockDurability(
                                    EmptyBlockGetter.INSTANCE, blockState, BlockPos.ZERO);
                            if (pType.getBlockDamageFactor() * kineticEnergy > 250f * blockDurability) {
                                terrain.markRemoved(blockPos);
                                SparkLevel.submitDeduplicatedTask(level, blockPos.toShortString(), PPhase.PRE,
                                        () -> level.destroyBlock(blockPos, false));
                            }
                        }

                        // 广播命中同步（携带新速度，非销毁）
                        broadcastHitSync(i, hitPointMc, hitNormalMc, false,
                                new Vector3f(velX[i], velY[i], velZ[i]), false);
                    } else {
                        // 无法击穿，停止
                        broadcastTerrainHit(i, hitPointMc);
                        alive[i] = false;
                        projectileObjIds.remove(objId[i]);
                        stopped = true;
                        break;
                    }
                } else if (owner == null) {
                    // null owner 直接停止（无地形信息）
                    broadcastTerrainHit(i, hitPointMc);
                    alive[i] = false;
                    projectileObjIds.remove(objId[i]);
                    stopped = true;
                    break;
                }

                // MM*Entity：渲染代理，跳过
                if (owner instanceof MMPartEntity || owner instanceof MMProjectileEntity) continue;

                // 获取投射物实例
                if (!(destroyable instanceof IProjectile projectile)) {
                    alive[i] = false;
                    projectileObjIds.remove(objId[i]);
                    stopped = true;
                    break;
                }

                // ========================================================
                //  分辨目标
                // ========================================================
                switch (owner) {
                    case SubPart subPart -> {
                        stopped = handleSubPartHit(i, projectile, subPart, result, hitPointMc, hitNormalMc, penKey);
                        if (stopped) break label;
                    }
                    case BFHurtTarget bfTarget when !(owner instanceof Entity) -> {
                        stopped = handleSyncBfHit(i, projectile, bfTarget, hitPointMc, hitNormalMc, penKey);
                        if (stopped) break label;
                    }
                    case Entity entity ->
                            stopped = handleEntityHit(i, projectile, entity, hitPointMc, hitNormalMc, dt, penKey);
                    default -> {
                    }
                }

                if (stopped) break;
            }

            if (stopped) {
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
     * 处理命中 SubPart（物理原生，同步管线）。
     * <p>
     * {@code dealDamage} 在物理线程同步走完 BallisticsFramework 全管线，
     * 回调立即写入 {@link IProjectile.AfterHitResult}，Manager 消费后即时调整弹道。
     *
     * @return true 表示投射物已停止（需要销毁）
     */
    private boolean handleSubPartHit(int i, IProjectile projectile, SubPart subPart,
                                     PhysicsRayTestResult result, Vec3 hitPoint, Vec3 hitNormal,
                                     PenetrationKey penKey) {
        HitBox hitBox = subPart.getHitBox(result.triangleIndex());
        if (!hitBox.isActive()) return false;

        projectile.dealDamage(subPart, hitPoint, hitNormal);
        return applyHitResult(i, projectile, hitPoint, hitNormal, true, penKey);
    }

    /**
     * 处理命中非实体的 BFHurtTarget（同步管线）。
     */
    private boolean handleSyncBfHit(int i, IProjectile projectile, BFHurtTarget target,
                                    Vec3 hitPoint, Vec3 hitNormal, PenetrationKey penKey) {
        projectile.dealDamage(target, hitPoint, hitNormal);
        return applyHitResult(i, projectile, hitPoint, hitNormal, true, penKey);
    }

    /**
     * 处理命中实体目标。
     * <p>
     * 先通过 {@code BFDamageApi.resolveHitTarget} 决议实际目标。
     * 若决议到 SubPart 或非实体 BFHurtTarget → 回退同步处理。
     * 若决议到 Entity / BFHurtTarget → 暂停投射物，提交主线程延迟伤害。
     * 若决议失败（null）→ 假阳性，继续飞行。
     * 若无协议感知（普通 Entity）→ 保守停止。
     *
     * @param dt 本帧时间步长（用于构造 search delta）
     * @return true 表示已停止（或已标记暂停）
     */
    private boolean handleEntityHit(int i, IProjectile projectile, Entity entity,
                                    Vec3 hitPoint, Vec3 hitNormal, float dt,
                                    PenetrationKey penKey) {
        Vec3 delta = new Vec3(velX[i] * dt, velY[i] * dt, velZ[i] * dt);

        // 命中点、法线、extensions 先赋原始值，协议感知时再修正
        Vec3 finalPoint = hitPoint;
        Vec3 finalNormal = hitNormal;
        BFDamageExtensions extensions = new BFDamageExtensions();

        if (BFDamageApi.isProtocolAware(entity)) {
            BFHitResolveResult resolved = BFDamageApi.resolveHitTarget(entity, hitPoint, delta);
            if (resolved == null) return false; // 假阳性，继续飞行

            BFHurtTarget rt = resolved.actualTarget();
            finalPoint = resolved.correctedHitPoint();
            finalNormal = resolved.correctedHitNormal();
            extensions = resolved.extensions().copy();

            // 决议到非实体 BFHurtTarget（如 SubPart）→ 同步管线
            if (!(rt instanceof Entity)) {
                projectile.dealDamage(rt, finalPoint, finalNormal);
                return applyHitResult(i, projectile, finalPoint, finalNormal, true, penKey);
            }
        }

        // ===== 统一异步管线：非协议实体 + 决议到 Entity 的 BFHurtTarget =====
        BFDamageContext ctx = BFDamageContext.builder()
            .source(level.damageSources().generic())
            .baseDamage(projectile.calculateCurrentDamage())
            .penetration(projectile.calculateCurrentPenetration())
            .hitVelocity(new Vec3(velX[i], velY[i], velZ[i]))
            .hitPoint(finalPoint)
            .hitNormal(finalNormal)
            .extensions(extensions)
            .build()
            .withHandler(projectile);

        projectile.setHitPending(true);
        if (penKey != null) pendingPenKeys.put(objId[i], penKey);

        SparkLevel.submitImmediateTask(level, PPhase.POST,
            () -> {
                if (entity instanceof LivingEntity livingEntity)
                    livingEntity.invulnerableTime = 0; // 重置无敌时间
                BFDamageApi.hurt(entity, ctx);
            });
        return true;
    }

    /**
     * 消费投射物的命中结果，执行对应的 SoA 操作并广播命中同步包。
     *
     * @param i      SoA 索引
     * @param proj   投射物实例
     * @param penKey 穿透密钥（可为 null）
     * @return true 表示投射物应销毁
     */
    private boolean applyHitResult(int i, IProjectile proj, Vec3 hitPoint, Vec3 hitNormal, boolean isArmorHit, PenetrationKey penKey) {
        IProjectile.AfterHitResult result = proj.consumePendingHitResult();
        broadcastHitSync(i, hitPoint, hitNormal, isArmorHit, result);
        if (result == null || result.destroyed()) {
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

    /** 广播命中同步包（服务端→客户端），携带 SoA 状态更新 */
    private void broadcastHitSync(int i, Vec3 hitPoint, Vec3 hitNormal, boolean isArmorHit, @Nullable IProjectile.AfterHitResult result) {
        if (level instanceof ServerLevel serverLevel) {
            boolean destroyed = result == null || result.destroyed();
            Vector3f newVel = destroyed ? new Vector3f() : result.newVelocity();
            ProjectileHitSyncPayload.broadcast(serverLevel, objId[i], hitPoint, hitNormal, destroyed, newVel, isArmorHit);
        }
    }

    /**
     * 广播命中同步包（显式指定销毁状态和速度）。
     * 用于 suspend/resume 路径等已确定结果但无 HitResult 的场景。
     */
    private void broadcastHitSync(int i, Vec3 hitPoint, Vec3 hitNormal, boolean destroyed, @Nullable Vector3f newVel, boolean isArmorHit) {
        if (level instanceof ServerLevel serverLevel) {
            ProjectileHitSyncPayload.broadcast(serverLevel, objId[i], hitPoint, hitNormal, destroyed,
                destroyed ? new Vector3f() : newVel, isArmorHit);
        }
    }

    /** 命中地形时的命中同步（服务端广播） */
    private void broadcastTerrainHit(int i, Vec3 hitPoint) {
        broadcastHitSync(i, hitPoint, new Vec3(0, 1, 0), true, null, false);
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
