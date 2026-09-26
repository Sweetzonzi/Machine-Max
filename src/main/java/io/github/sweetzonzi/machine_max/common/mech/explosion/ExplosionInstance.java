package io.github.sweetzonzi.machine_max.common.mech.explosion;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.PenetrationKey;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.level.WorldSnapshot;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageApi;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageExtensions;
import io.github.sweetzonzi.ballistics_framework.api.PenetrationResult;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.MMServerConfig;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.MMDamageExtensions;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import io.github.sweetzonzi.machine_max.util.mechanic.ArmorUtil;
import io.github.sweetzonzi.machine_max.util.mechanic.DamageUtil;
import io.github.sweetzonzi.machine_max.util.mechanic.VoxelHit;
import io.github.sweetzonzi.machine_max.util.mechanic.VoxelRayWalker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 一次爆炸的聚合根。
 *
 * <p>跨 tick 存活，寿命 = {@code max_radius / front_speed} 秒；结束时连同射线组一并被回收。
 * 一次推进 = 一次 {@code PhysicsSnapshotReadyEvent}，内部按固定顺序执行
 * <b>推进 → 结算 → 执行</b> 三阶段：</p>
 *
 * <ol>
 *   <li>{@link #advance()} —— DDA + 粗筛 + rayTest + 实体 AABB，只登记命中，不施加；</li>
 *   <li>{@link #settle()} —— 逐去重键汇总广延量并对外施加一次；</li>
 *   <li>{@link #execute()} —— 统一摧毁本 tick 标记的方块。</li>
 * </ol>
 *
 * <p><b>纯主线程</b>；不持有 {@link ExplosionManager}。</p>
 */
public final class ExplosionInstance {

    /**
     * 方块破坏倍率。爆炸本身没有弹种，投影物接入（阶段 K）后会由投射物类型提供；
     * 在此之前取 1.0，即基准伤害直接与方块耐久阈值比较。
     */
    private static final float BLOCK_DAMAGE_FACTOR = 1.0f;

    /**
     * 一般实体击退：把"单位迎流面积冲量"换算为原版 delta-movement 的经验系数。
     * 刚体侧走 {@link BFDamageExtensions#IMPULSE}（真实 N·s），实体侧没有质量信息，故用系数折算。
     */
    private static final float ENTITY_IMPULSE_TO_VELOCITY = 0.02f;

    // ==================== 标识与年龄 ====================

    /** 实例标识 */
    public final UUID id;

    /** 起爆点（副本，不随外部改动） */
    private final Vector3f origin;

    private final ExplosionParams params;

    /** 造成本次爆炸的伤害源（{@code machine_max:blast}） */
    private final DamageSource source;

    /** 本实例的年龄：起爆为 0，每次 {@link #advance()} 自增 */
    public int tickCount;

    /** 全部射线均终止时为 true */
    private boolean finished;

    // ==================== 射线组与推导常量 ====================

    private final BlastRay[] rays;
    private final int rayCount;
    /** 立体角份额 λ_r = 1/rayCount */
    private final float lambda;

    // ==================== 累计与待摧毁 ====================

    /** 本 tick 的累积表，settle 后清空。用插入序容器保证确定性。 */
    private final Map<Object, BlastAccum> accum = new LinkedHashMap<>();

    /** 本 tick 标记待摧毁的方块，execute 后清空。 */
    private final Set<BlockPos> pendingDestroy = new LinkedHashSet<>();

    // ==================== 只读依赖与复用缓冲 ====================

    private final WorldSnapshot worldSnapshot;
    private final Level level;

    private final List<PhysicsRigidBody> candidates = new ArrayList<>();
    private float[] candidateBox = new float[0];
    private int candidateCount;
    private final Vector3f scratchMin = new Vector3f();
    private final Vector3f scratchMax = new Vector3f();
    private final Vector3f dirTmp = new Vector3f();

    private final List<VoxelHit> blockHits = new ArrayList<>();
    private final List<PhysicsRayTestResult> rayResults = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();

    /**
     * @param level  所在世界
     * @param origin 起爆点
     * @param params 参数集
     * @param source 伤害源
     * @param seed   起爆种子
     */
    ExplosionInstance(Level level, Vector3f origin, ExplosionParams params, DamageSource source, long seed) {
        this.level = level;
        this.origin = origin.clone();
        this.params = params;
        this.source = source;
        this.id = UUID.randomUUID();
        this.rayCount = BlastRayGenerator.adaptiveRayCount(params.nearRadius());
        this.lambda = 1.0f / rayCount;
        this.rays = BlastRayGenerator.generate(this.origin, seed, rayCount);
        this.worldSnapshot = resolveSnapshot(level);
    }

    private static WorldSnapshot resolveSnapshot(Level level) {
        try {
            return SparkLevel.getPhysicsLevel(level).getWorld().getWorldSnapshot();
        } catch (RuntimeException e) {
            // 物理世界尚未初始化（例如极早期调用）：退化为无快照通道，仅地形与实体生效
            MachineMax.LOGGER.warn("爆炸实例无法取得物理快照，本发爆炸将跳过刚体通道: {}", e.toString());
            return null;
        }
    }

    // ==================== 派生读取 ====================

    /** 已推进距离 = tickCount × advancePerStep。 */
    public float frontRadius() {
        return tickCount * params.advancePerStep();
    }

    public boolean isFinished() {
        return finished;
    }

    public ExplosionParams params() {
        return params;
    }

    public Vector3f origin() {
        return origin;
    }

    public int rayCount() {
        return rayCount;
    }

    /** 活跃射线数（诊断用）。 */
    public int aliveRayCount() {
        int n = 0;
        for (BlastRay ray : rays) if (ray.isAlive()) n++;
        return n;
    }

    // ==================== 推进期 ====================

    /**
     * 推进一次（一次快照就绪事件 = 一步），并登记命中。
     * 步长固定为 {@code frontSpeed / 20}。
     */
    void advance() {
        if (finished) return;
        tickCount++;
        float step = params.advancePerStep();

        // 每爆炸每 tick 只付一次粗筛
        coarseCull(step);

        for (BlastRay ray : rays) {
            if (ray.isAlive()) {
                stepRay(ray, step);
            }
        }

        boolean allDead = true;
        for (BlastRay ray : rays) {
            if (ray.isAlive()) {
                allDead = false;
                break;
            }
        }
        if (allDead) finished = true;
    }

    /** 单条射线推进一步：登记本 tick 推进段内的全部命中，顺次消费并决定终止/折减。 */
    private void stepRay(BlastRay ray, float step) {
        float ox = ray.x(), oy = ray.y(), oz = ray.z();
        float ex = ox + ray.dirX() * step;
        float ey = oy + ray.dirY() * step;
        float ez = oz + ray.dirZ() * step;

        ray.moveTo(ex, ey, ez, step);
        if (ray.distance() > params.maxRadius()) {
            ray.kill();
            return;
        }
        if (!ray.isInteracting()) {
            return; // 纯推进，无 DDA、无 rayTest
        }

        hits.clear();
        // ① 地形：3D DDA 逐体素
        walkVoxels(ray, ox, oy, oz, step);
        // ② 快照刚体：裁剪到本 tick 推进段，每 tick 每射线一次
        rayTestRigid(ray, ox, oy, oz, ex, ey, ez);
        // ③ 一般实体：AABB 求交，取进入 t
        entitiesAlong(ray, ox, oy, oz, ex, ey, ez);

        if (hits.isEmpty()) {
            return;
        }

        // 三条通道合并成同一条按 hitFraction 升序的序列（保序是这一段的全部意义）
        hits.sort(Comparator.comparingDouble(h -> h.fraction));

        float baseDistance = ray.distance() - step;
        for (Hit hit : hits) {
            if (!ray.isAlive()) break;
            consume(ray, hit, baseDistance, step);
        }

        if (ray.isAlive() && ray.energy() < MMServerConfig.explosionInteractionCutoff()) {
            ray.stopInteracting();
        }
    }

    /** 每爆炸每 tick 一次的球查询，把候选刚体及其 AABB 读进 Java 侧缓存。 */
    private void coarseCull(float step) {
        if (worldSnapshot == null) {
            candidateCount = 0;
            return;
        }
        float r = frontRadius() + step;
        scratchMin.set(origin.x - r, origin.y - r, origin.z - r);
        scratchMax.set(origin.x + r, origin.y + r, origin.z + r);
        worldSnapshot.collectBodiesInAabb(scratchMin, scratchMax, candidates);

        candidateCount = candidates.size();
        if (candidateBox.length < candidateCount * 6) {
            candidateBox = new float[candidateCount * 6];
        }
        for (int i = 0; i < candidateCount; i++) {
            BoundingBox bb = candidates.get(i).boundingBox(null);
            bb.getMin(scratchMin);
            bb.getMax(scratchMax);
            int b = i * 6;
            candidateBox[b] = scratchMin.x;
            candidateBox[b + 1] = scratchMin.y;
            candidateBox[b + 2] = scratchMin.z;
            candidateBox[b + 3] = scratchMax.x;
            candidateBox[b + 4] = scratchMax.y;
            candidateBox[b + 5] = scratchMax.z;
        }
    }

    /** 共享体素遍历器，读取本 tick 推进段内的方块命中。 */
    private void walkVoxels(BlastRay ray, float ox, float oy, float oz, float step) {
        blockHits.clear();
        dirTmp.set(ray.dirX(), ray.dirY(), ray.dirZ());
        scratchMin.set(ox, oy, oz);
        VoxelRayWalker.walk(level, scratchMin, dirTmp, step, ray.cursor(), blockHits);
        for (VoxelHit vh : blockHits) {
            float frac = vh.hitFraction();
            float px = ox + ray.dirX() * step * frac;
            float py = oy + ray.dirY() * step * frac;
            float pz = oz + ray.dirZ() * step * frac;
            // 方块法线取朝爆心方向（-射线方向）；方块不吃冲量，此处仅用于统一累积
            hits.add(Hit.block(frac, vh.pos(), vh.state(),
                    -ray.dirX(), -ray.dirY(), -ray.dirZ(), px, py, pz));
        }
    }

    /**
     * 对 [起点, 终点] 段做一次快照 rayTest，命中刚体登记。
     * 候选集按 owner 分派：仅 {@link SubPart} 走完整通路；其余（实体刚体、交互区、地形、未知）跳过。
     */
    private void rayTestRigid(BlastRay ray, float ox, float oy, float oz, float ex, float ey, float ez) {
        if (worldSnapshot == null || candidateCount == 0) return;
        if (!segmentNearAnyCandidate(ox, oy, oz, ex, ey, ez)) return;

        scratchMin.set(ox, oy, oz);
        scratchMax.set(ex, ey, ez);
        rayResults.clear();
        worldSnapshot.rayTest(scratchMin, scratchMax, rayResults);

        float dirX = ray.dirX(), dirY = ray.dirY(), dirZ = ray.dirZ();
        for (PhysicsRayTestResult r : rayResults) {
            PhysicsCollisionObject pco = r.getCollisionObject();
            if (!(pco instanceof PhysicsRigidBody body)) continue;
            Object owner = PhysicsBodyExtensionKt.getOwner(body);
            if (!(owner instanceof SubPart subPart)) continue;

            int tri = r.triangleIndex();
            PenetrationKey key = PenetrationKey.fromCollision(pco, tri);
            if (key == null) continue;
            HitBox hitBox = subPart.getHitBox(tri);

            float frac = r.getHitFraction();
            float px = ox + (ex - ox) * frac;
            float py = oy + (ey - oy) * frac;
            float pz = oz + (ez - oz) * frac;

            // 真实几何法线，统一朝向爆心（与射线方向同向时翻转）
            Vector3f n = r.getHitNormalLocal(null);
            if (n.dot(dirTmp.set(dirX, dirY, dirZ)) > 0f) {
                n.negateLocal();
            }
            hits.add(Hit.rigid(frac, subPart, hitBox, key, n.x, n.y, n.z, px, py, pz));
        }
    }

    /** 判断射线段 AABB 是否与任一候选刚体 AABB 相交（纯 Java 算术，无 native 调用）。 */
    private boolean segmentNearAnyCandidate(float ox, float oy, float oz, float ex, float ey, float ez) {
        float minX = Math.min(ox, ex) - 1e-3f, maxX = Math.max(ox, ex) + 1e-3f;
        float minY = Math.min(oy, ey) - 1e-3f, maxY = Math.max(oy, ey) + 1e-3f;
        float minZ = Math.min(oz, ez) - 1e-3f, maxZ = Math.max(oz, ez) + 1e-3f;
        for (int i = 0; i < candidateCount; i++) {
            int b = i * 6;
            if (candidateBox[b + 3] >= minX && candidateBox[b] <= maxX
                    && candidateBox[b + 4] >= minY && candidateBox[b + 1] <= maxY
                    && candidateBox[b + 5] >= minZ && candidateBox[b + 2] <= maxZ) {
                return true;
            }
        }
        return false;
    }

    /** 一般实体用主线程 AABB 相交发现命中，取进入 t 作 hitFraction。 */
    private void entitiesAlong(BlastRay ray, float ox, float oy, float oz, float ex, float ey, float ez) {
        double minX = Math.min(ox, ex) - 0.3, maxX = Math.max(ox, ex) + 0.3;
        double minY = Math.min(oy, ey) - 0.3, maxY = Math.max(oy, ey) + 0.3;
        double minZ = Math.min(oz, ez) - 0.3, maxZ = Math.max(oz, ez) + 0.3;
        AABB search = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        List<Entity> ents = level.getEntities((Entity) null, search,
                e -> e.isAlive() && !(e instanceof MMPartEntity));
        if (ents.isEmpty()) return;

        Vec3 from = new Vec3(ox, oy, oz);
        Vec3 to = new Vec3(ex, ey, ez);
        float len = (float) from.distanceTo(to);
        if (len <= 1e-6f) return;

        for (Entity e : ents) {
            Optional<Vec3> hit = e.getBoundingBox().inflate(1e-7).clip(from, to);
            if (hit.isEmpty()) continue;
            Vec3 p = hit.get();
            float frac = (float) (from.distanceTo(p) / len);
            // 实体法线取朝爆心方向（-射线方向）
            hits.add(Hit.entity(frac, e, -ray.dirX(), -ray.dirY(), -ray.dirZ(),
                    (float) p.x, (float) p.y, (float) p.z));
        }
    }

    /**
     * 顺次消费一个命中：判定是否拦下本射线，击穿则折减能量，并把原始数据登记进累积表。
     * <b>只登记，不施加</b>。
     */
    private void consume(BlastRay ray, Hit hit, float baseDistance, float step) {
        float d = baseDistance + hit.fraction * step;
        float e = ray.energy();
        float intensity = BlastField.rayIntensity(e, d, params);
        float aPen = params.basePenetration() * intensity;
        float w = e * lambda;
        float footprintSqrtI = BlastField.footprint(d, params) * (float) Math.sqrt(intensity);

        switch (hit.kind) {
            case Hit.KIND_BLOCK -> {
                // 生命周期级去重：同一方块在本射线生命周期内只判定一次
                if (!ray.markStruck(hit.pos)) return;
                float aBlock = MMServerConfig.explosionArmorK()
                        * ArmorUtil.getBlockArmor(level, hit.state, hit.pos);
                BlastAccum acc = accumFor(hit.pos);
                acc.hitCount++;
                acc.sumFootprintSqrtI += footprintSqrtI;
                accumulateWeights(acc, w, hit);
                if (aPen > aBlock) {
                    acc.deltaE += w;
                    ray.absorb(eta(aPen, aBlock));
                } else {
                    ray.kill();
                }
            }
            case Hit.KIND_RIGID -> {
                if (!ray.markStruck(hit.key)) return;
                SubPart subPart = hit.subPart;
                // 逐射线判定上下文：入射角恒 0（法线取射线反向、速度取射线方向），跳弹分支不可达
                BFDamageContext ctx = BFDamageContext.builder()
                        .source(source)
                        .baseDamage(0f)
                        .penetration(aPen)
                        .hitVelocity(new Vec3(ray.dirX(), ray.dirY(), ray.dirZ()))
                        .hitNormal(new Vec3(-ray.dirX(), -ray.dirY(), -ray.dirZ()))
                        .hitPoint(new Vec3(hit.px, hit.py, hit.pz))
                        .build();
                ctx.extensions().set(MMDamageExtensions.HIT_BOX, hit.hitBox);

                PenetrationResult result = subPart.resolvePenetration(ctx);
                float effPen = subPart.modifyPenetration(ctx);
                float rha = subPart.getRHA(ctx);

                BlastAccum acc = accumFor(hit.key);
                acc.subPart = subPart;
                acc.hitBox = hit.hitBox;
                acc.hitCount++;
                acc.maxAPen = Math.max(acc.maxAPen, aPen);
                acc.sumFootprintSqrtI += footprintSqrtI;
                accumulateWeights(acc, w, hit);
                if (result == PenetrationResult.PENETRATED) {
                    acc.deltaE += w;
                    ray.absorb(eta(effPen, rha));
                } else {
                    ray.kill();
                }
            }
            case Hit.KIND_ENTITY -> {
                if (!ray.markStruck(hit.entity)) return;
                BlastAccum acc = accumFor(hit.entity);
                acc.hitCount++;
                // 实体不延续传播：ΔE 按"到达"计
                acc.deltaE += w;
                acc.sumFootprintSqrtI += footprintSqrtI;
                accumulateWeights(acc, w, hit);
                // 实体不阻碍传播，故不 kill
            }
            default -> {
            }
        }
    }

    /** 能量加权的法线与命中点累加（权重 = E_r·λ_r）。 */
    private static void accumulateWeights(BlastAccum acc, float w, Hit hit) {
        acc.weightSum += w;
        acc.wNx += w * hit.nx;
        acc.wNy += w * hit.ny;
        acc.wNz += w * hit.nz;
        acc.wPx += w * hit.px;
        acc.wPy += w * hit.py;
        acc.wPz += w * hit.pz;
    }

    private BlastAccum accumFor(Object key) {
        return accum.computeIfAbsent(key, k -> new BlastAccum());
    }

    /** 传输效率 η = (A_pen / (A_pen + A_armor))²。 */
    private static float eta(float aPen, float aArmor) {
        if (aArmor <= 0f) return 1f;
        float denom = aPen + aArmor;
        if (denom <= 0f) return 1f;
        float r = aPen / denom;
        return r * r;
    }

    // ==================== 结算期与施加 ====================

    /** 逐去重键结算并对外施加一次。 */
    void settle() {
        if (accum.isEmpty()) return;
        Iterator<Map.Entry<Object, BlastAccum>> it = accum.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Object, BlastAccum> entry = it.next();
            Object key = entry.getKey();
            BlastAccum acc = entry.getValue();
            if (key instanceof PenetrationKey) {
                settleSubPart(acc);
            } else if (key instanceof BlockPos pos) {
                settleBlock(pos, acc);
            } else if (key instanceof Entity entity) {
                settleEntity(entity, acc);
            }
        }
        accum.clear();
    }

    /** 聚合后每个 HitBox 每 tick 只 hurt 一次；代表穿深取本键最大的 A_pen。 */
    private void settleSubPart(BlastAccum acc) {
        if (acc.subPart == null) return;
        float damage = BlastField.damage(acc.deltaE, params);
        float impulse = BlastField.impulse(acc.sumFootprintSqrtI, params);
        Vec3 normal = averagedNormal(acc);
        Vec3 point = averagedPoint(acc);

        BFDamageContext ctx = BFDamageContext.builder()
                .source(source)
                .baseDamage(damage)                 // 可为 0：D=0 时仍要投递冲量
                .penetration(acc.maxAPen)
                .hitNormal(normal)
                .hitVelocity(normal.scale(-1))      // 入射角 0；冲量方向 = -n̄（推离爆心）
                .hitPoint(point)
                .build();
        ctx.extensions().set(MMDamageExtensions.HIT_BOX, acc.hitBox);
        ctx.extensions().set(BFDamageExtensions.IMPULSE, impulse); // 必须显式写入，即使为 0
        BFDamageApi.hurt(acc.subPart, ctx);
    }

    /** 方块看伤害不看能量；只标记，由 execute 统一摧毁。 */
    private void settleBlock(BlockPos pos, BlastAccum acc) {
        if (acc.deltaE <= 0f) return;
        if (!params.destroyBlocks()) return;
        if (!MMServerConfig.explosionBlockDamage()) return;

        float damage = BlastField.damage(acc.deltaE, params)
                * BLOCK_DAMAGE_FACTOR * MMServerConfig.explosionTerrainDamageMultiplier();
        if (damage <= 0f) return;

        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;
        float threshold = DamageUtil.getMaxBlockDurability(level, state, pos);
        if (damage > threshold) {
            pendingDestroy.add(pos);
        }
    }

    /** 实体伤害交 BF/原版链条，击退按冲量方向直接施加。 */
    private void settleEntity(Entity entity, BlastAccum acc) {
        if (entity.isRemoved()) return;
        float damage = BlastField.damage(acc.deltaE, params);
        float impulse = BlastField.impulse(acc.sumFootprintSqrtI, params);
        Vec3 normal = averagedNormal(acc);
        Vec3 point = averagedPoint(acc);

        BFDamageContext ctx = BFDamageContext.builder()
                .source(source)
                .baseDamage(damage)
                .penetration(0f)
                .hitNormal(normal)
                .hitVelocity(normal.scale(-1))
                .hitPoint(point)
                .build();
        ctx.extensions().set(BFDamageExtensions.IMPULSE, impulse);
        BFDamageApi.hurt(entity, ctx);

        if (impulse > 0f) {
            Vec3 dir = normal.scale(-1);
            float k = impulse * ENTITY_IMPULSE_TO_VELOCITY;
            entity.push(dir.x * k, dir.y * k, dir.z * k);
        }
    }

    /** 能量加权法线均值（朝爆心）；无命中时为 (0,1,0) 兜底。 */
    private static Vec3 averagedNormal(BlastAccum acc) {
        if (acc.weightSum <= 0f) return new Vec3(0, 1, 0);
        Vec3 v = new Vec3(acc.wNx, acc.wNy, acc.wNz);
        return v.lengthSqr() < 1e-9 ? new Vec3(0, 1, 0) : v.normalize();
    }

    /** 能量加权命中点均值。 */
    private static Vec3 averagedPoint(BlastAccum acc) {
        if (acc.weightSum <= 0f) return Vec3.ZERO;
        return new Vec3(acc.wPx / acc.weightSum, acc.wPy / acc.weightSum, acc.wPz / acc.weightSum);
    }

    // ==================== 执行期 ====================

    /** 统一摧毁本 tick 标记的方块，杜绝遍历顺序依赖。 */
    void execute() {
        if (pendingDestroy.isEmpty()) return;
        boolean drop = params.dropItems();
        for (BlockPos pos : pendingDestroy) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            if (drop) {
                BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
                Block.dropResources(state, level, pos, blockEntity, null, ItemStack.EMPTY);
            }
            // 与原版爆炸同路：直接置为空气，不走 Level#destroyBlock。
            // 后者会逐方块发 levelEvent(2001)（N 次网络广播 + N 个客户端音效实例与方块粒子），
            // 大批量破坏时开销显著，且起爆音效与粒子已覆盖听感与观感。
            level.removeBlock(pos, false);
        }
        pendingDestroy.clear();
    }

    // ==================== 内部结构 ====================

    /**
     * 本 tick 对<b>一个去重键</b>的累积。
     *
     * <p>两个求和范围不同，这是设计口径的直接体现，不可混用：
     * {@link #deltaE} 只累加<b>被击穿</b>的射线（实体键按"到达"计）；
     * {@link #sumFootprintSqrtI} 与权重累加<b>全部命中</b>的射线。</p>
     */
    private static final class BlastAccum {
        /** 击穿射线的能量之和 —— 伤害的分子（实体键按"到达"计） */
        float deltaE;
        /** 全部命中射线的冲量项 A_r·√I_r 之和 */
        float sumFootprintSqrtI;
        /** 能量加权法线累加 */
        float wNx, wNy, wNz;
        /** 能量加权命中点累加 */
        float wPx, wPy, wPz;
        /** 权重分母 */
        float weightSum;
        /** 本键上最大的 A_pen —— 聚合 hurt 的代表穿深 */
        float maxAPen;
        /** 零件侧：本键对应的命中框 */
        HitBox hitBox;
        /** 零件侧：本键对应的零件 */
        SubPart subPart;
        /** 本键命中的射线数，用于诊断 */
        int hitCount;
    }

    /** 合并序列中的一条原始命中（推进期只登记）。 */
    private static final class Hit {
        static final int KIND_BLOCK = 0;
        static final int KIND_RIGID = 1;
        static final int KIND_ENTITY = 2;

        int kind;
        float fraction;

        // 方块
        BlockPos pos;
        BlockState state;

        // 刚体
        SubPart subPart;
        HitBox hitBox;
        PenetrationKey key;

        // 一般实体
        Entity entity;

        // 命中点与朝爆心法线
        float nx, ny, nz;
        float px, py, pz;

        static Hit block(float fraction, BlockPos pos, BlockState state,
                         float nx, float ny, float nz, float px, float py, float pz) {
            Hit h = new Hit();
            h.kind = KIND_BLOCK;
            h.fraction = fraction;
            h.pos = pos;
            h.state = state;
            h.nx = nx;
            h.ny = ny;
            h.nz = nz;
            h.px = px;
            h.py = py;
            h.pz = pz;
            return h;
        }

        static Hit rigid(float fraction, SubPart subPart, HitBox hitBox, PenetrationKey key,
                         float nx, float ny, float nz, float px, float py, float pz) {
            Hit h = new Hit();
            h.kind = KIND_RIGID;
            h.fraction = fraction;
            h.subPart = subPart;
            h.hitBox = hitBox;
            h.key = key;
            h.nx = nx;
            h.ny = ny;
            h.nz = nz;
            h.px = px;
            h.py = py;
            h.pz = pz;
            return h;
        }

        static Hit entity(float fraction, Entity entity,
                          float nx, float ny, float nz, float px, float py, float pz) {
            Hit h = new Hit();
            h.kind = KIND_ENTITY;
            h.fraction = fraction;
            h.entity = entity;
            h.nx = nx;
            h.ny = ny;
            h.nz = nz;
            h.px = px;
            h.py = py;
            h.pz = pz;
            return h;
        }
    }
}
