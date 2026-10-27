package io.github.sweetzonzi.machine_max.common.mech.vehicle.collision;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.physics.terrain.PhysicsChunkManager;
import cn.solarmoon.spark_core.physics.terrain.PhysicsChunkSection;
import cn.solarmoon.spark_core.physics.terrain.SectionSnapshot;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.ManifoldPoints;
import com.jme3.bullet.collision.PersistentManifolds;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageApi;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageExtensions;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.MMServerConfig;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.MMDamageExtensions;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import io.github.sweetzonzi.machine_max.util.MMMath;
import io.github.sweetzonzi.machine_max.util.mechanic.ArmorUtil;
import io.github.sweetzonzi.machine_max.util.mechanic.DamageUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 碰撞 Post 阶段结算器：每个 Level 一个，物理线程独占。
 *
 * <p>一次物理子步内的职责按固定顺序完成，全部集中在本类：</p>
 * <ol>
 *   <li>{@link #beginStep} 在 Level 物理 Pre 的最前面开始新子步，清空上一步的候选与累计；</li>
 *   <li>Processed 阶段通过 {@link #registerTerrainImpact} 登记已通过速度门的撞击组，
 *       单点即时破坏也由 {@link #resolveVanillaBlockPos} 定位同一个目标；</li>
 *   <li>{@link #settle} 在本子步 Post 的最前面只枚举一次全空间 manifold，逐点过滤并按方块、部件聚合；</li>
 *   <li>枚举结束后先按方块判定破坏，再按部件投递一次碰撞伤害（§8.1）；</li>
 *   <li>本步候选在下一子步开始时清空，资格不跨子步保留。</li>
 * </ol>
 *
 * <p>线程：所有方法在物理线程调用；字段无锁，只在该 Level 的物理线程内读写。主线程只承接
 * 方块删除任务与地形快照补偿。</p>
 */
public final class CollisionPostSettlement {

    /** 禁用接触的距离哨兵：{@code getDistance1} 达到该值的点不参与本步结算。 */
    public static final float DISABLED_DISTANCE = 500f;

    /** 攀爬辅助跳过接触使用的更大距离哨兵。 */
    public static final float SKIPPED_DISTANCE = 5000f;

    /** 诊断日志的最小间隔（纳秒）。 */
    private static final long LOG_INTERVAL_NS = 5_000_000_000L;

    private final Level level;

    // 逐点复用的临时向量：每个结算器独占一份，避免跨 Level 共享静态缓冲
    private final Vector3f tmpPoint = new Vector3f();
    private final Vector3f tmpNormal = new Vector3f();

    // ==================== 本步数据 ====================

    private float dt;
    private int substep;

    /** 本步通过速度门的地形撞击组，仅当前子步有效。 */
    private final Set<TerrainImpactKey> terrainCandidates = new HashSet<>();
    /** 候选零件刚体 native ID，用于把 manifold 先筛到相关集合。 */
    private final Set<Long> candidateBodyIds = new HashSet<>();

    /** 按方块聚合：方块侧冲量与背撑加权耐久。 */
    private final Map<TargetBlockKey, BlockTotal> blocks = new HashMap<>();
    /** 按部件聚合：本步自承冲量与代表性命中几何。 */
    private final Map<PartDamageKey, PartTotal> parts = new HashMap<>();

    // ==================== 诊断计数 ====================

    private int manifoldsEnumerated;
    private int relevantManifolds;
    private int pointsVisited;
    private int pointsSkippedDistance;
    private int pointsSkippedImpulse;
    private int pointsResolved;
    private int pointsHit;
    private int pointsNotCandidate;
    private int unresolvedTargets;
    private int blocksBroken;
    private int partsDamaged;
    private int jniCalls;
    private long settleNanos;
    private long lastLogNanos;

    public CollisionPostSettlement(Level level) {
        this.level = level;
    }

    // ==================== 生命周期 ====================

    /**
     * 开始一个物理子步：清空上一步的候选与累计。
     *
     * @param dt 本子步实际时长（{@code 1 / PhysicsLevel#tps}），不是游戏 tick 时长
     */
    public void beginStep(float dt) {
        this.dt = Float.isFinite(dt) && dt > 0f ? dt : 0f;
        this.substep++;
        clearStep();
    }

    /**
     * 登记一个已通过速度门的地形撞击组。
     *
     * <p>资格只在当前子步内有效：同组任一点通过后，本步该组的其余有效点不再重复判门。
     * 重复登记同一组是幂等的。</p>
     *
     * @param partBodyId  零件刚体 native ID
     * @param hitBoxIndex 接触点对应的子形状索引
     * @param target      被撞方块身份
     */
    public void registerTerrainImpact(long partBodyId, int hitBoxIndex, TargetBlockKey target) {
        if (level.isClientSide()) return;
        if (partBodyId == 0L || target == null) return;
        if (terrainCandidates.add(new TerrainImpactKey(partBodyId, hitBoxIndex, target))) {
            candidateBodyIds.add(partBodyId);
        }
    }

    /**
     * 结算本子步：只枚举一次全空间 manifold，逐点按方块与部件聚合，然后判定破坏并投递部件伤害。
     *
     * <p>候选为空时枚举次数为零。整个枚举阶段只读物理世界；破坏与伤害都在固定贡献之后发生。</p>
     *
     * @param space 本 Level 的物理空间
     */
    public void settle(PhysicsSpace space) {
        if (level.isClientSide()) return;
        if (terrainCandidates.isEmpty()) return;
        long startNanos = System.nanoTime();
        for (long manifoldId : space.listManifoldIds()) {
            jniCalls++;
            manifoldsEnumerated++;
            long bodyAId = PersistentManifolds.getBodyAId(manifoldId);
            long bodyBId = PersistentManifolds.getBodyBId(manifoldId);
            jniCalls += 2;
            boolean partIsA = candidateBodyIds.contains(bodyAId);
            boolean partIsB = candidateBodyIds.contains(bodyBId);
            if (!partIsA && !partIsB) continue;
            relevantManifolds++;
            collectManifold(manifoldId, bodyAId, bodyBId, partIsA);
        }
        settleNanos = System.nanoTime() - startNanos;
        decideBlockBreaks();
        deliverPartDamage();
        publishDiagnostics();
    }

    /** 本步是否整理出了方块聚合。 */
    public boolean hasBlockResults() {
        return !blocks.isEmpty();
    }

    /** 本步是否整理出了部件聚合。 */
    public boolean hasPartResults() {
        return !parts.isEmpty();
    }

    /** 本步整理出的方块聚合（只读副本）。 */
    public Map<TargetBlockKey, BlockTotal> blockResults() {
        return Map.copyOf(blocks);
    }

    /** 本步整理出的部件聚合（只读副本）。 */
    public Map<PartDamageKey, PartTotal> partResults() {
        return Map.copyOf(parts);
    }

    /** 最近一次 {@link #beginStep} 收到的本步时长（秒）。 */
    public float stepSeconds() {
        return dt;
    }

    /** 本步序号，仅用于诊断。 */
    public int substep() {
        return substep;
    }

    // ==================== manifold 逐点采集 ====================

    /**
     * 采集一个相关 manifold：实例与 owner 分类在每个 manifold 只做一次。
     *
     * @param partIsA true 表示零件是 A 侧，false 表示零件是 B 侧
     */
    private void collectManifold(long manifoldId, long bodyAId, long bodyBId, boolean partIsA) {
        int count = PersistentManifolds.countPoints(manifoldId);
        jniCalls++;
        if (count <= 0) return;
        long partBodyId = partIsA ? bodyAId : bodyBId;
        long terrainBodyId = partIsA ? bodyBId : bodyAId;
        int partIndex = partIsA ? 0 : 1;

        PhysicsCollisionObject partObject = PhysicsCollisionObject.findInstance(partBodyId);
        PhysicsCollisionObject terrainObject = PhysicsCollisionObject.findInstance(terrainBodyId);
        jniCalls += 2;
        if (!(partObject instanceof PhysicsRigidBody partBody) || terrainObject == null) return;
        PhysicsHost partOwner = PhysicsBodyExtensionKt.getOwner(partBody);
        // 本期只结算地形接触；零件间与生物路径维持既有实现（§8.3）
        if (!(partOwner instanceof SubPart part)) return;
        if (!(PhysicsBodyExtensionKt.getOwner(terrainObject) instanceof PhysicsChunkSection section)) return;

        for (int index = 0; index < count; index++) {
            long pointId = PersistentManifolds.getPointId(manifoldId, index);
            jniCalls++;
            pointsVisited++;
            float distance = ManifoldPoints.getDistance1(pointId);
            jniCalls++;
            if (!(distance < DISABLED_DISTANCE)) {
                pointsSkippedDistance++;
                continue;
            }
            float impulse = ManifoldPoints.getAppliedImpulse(pointId);
            jniCalls++;
            if (!CollisionImpactModel.isSolverImpulse(impulse)) {
                pointsSkippedImpulse++;
                continue;
            }
            collectTerrainPoint(pointId, part, partBody, partBodyId, terrainBodyId, partIndex, section, impulse);
        }
    }

    /**
     * 采集一个地形接触点：按材料分配算出方块与部件各自承担的冲量，再按目标键聚合。
     *
     * @param pointId       原生点 ID，只在本调用栈内解引用
     * @param part          零件
     * @param partBody      零件刚体
     * @param partBodyId    零件侧刚体 native ID
     * @param terrainBodyId 地形侧刚体 native ID
     * @param partIndex     零件所在侧：0 为 A，1 为 B
     * @param section       地形区块段
     * @param impulse       本步法向冲量
     */
    private void collectTerrainPoint(long pointId, SubPart part, PhysicsRigidBody partBody,
                                     long partBodyId, long terrainBodyId, int partIndex,
                                     PhysicsChunkSection section, float impulse) {
        int hitBoxIndex = partIndex == 0
                ? ManifoldPoints.getIndex0(pointId)
                : ManifoldPoints.getIndex1(pointId);
        jniCalls++;
        // normalWorldOnB 从 B 指向 A；统一成“地形指向零件”
        ManifoldPoints.getNormalWorldOnB(pointId, tmpNormal);
        if (partIndex == 1) tmpNormal.negateLocal();
        jniCalls++;
        // 取零件侧接触点：与 Processed 阶段登记的输入完全一致，避免两阶段解析出不同的方块
        if (partIndex == 0) {
            ManifoldPoints.getPositionWorldOnA(pointId, tmpPoint);
        } else {
            ManifoldPoints.getPositionWorldOnB(pointId, tmpPoint);
        }
        jniCalls++;

        TargetBlockKey target = resolveVanillaTarget(terrainBodyId, section, tmpPoint, tmpNormal);
        pointsResolved++;
        if (target == null) {
            unresolvedTargets++;
            return;
        }
        HitBox hitBox = part.getHitBox(hitBoxIndex);
        TerrainImpactKey key = new TerrainImpactKey(partBodyId, hitBoxIndex, target);
        if (!terrainCandidates.contains(key)) {
            pointsNotCandidate++;
            return;
        }
        SectionSnapshot.BlockSnapshot snapshot = section.getBlockSnapshot(
                ((TargetBlockKey.Vanilla) target).worldPos());
        if (snapshot == null) {
            unresolvedTargets++;
            return;
        }
        BlockState state = snapshot.getState();
        pointsHit++;

        // 双方防护分配：零件 RHA 与方块护甲
        float share = CollisionImpactModel.blockShare(part.getRHA(hitBox), blockArmorOf(state));
        float blockFactor = part.contactMaterial(hitBoxIndex, hitBox).physics().blockDamageFactor();
        float blockImpulse = CollisionImpactModel.blockContribution(blockFactor, share, impulse);
        float partImpulse = CollisionImpactModel.partContribution(share, impulse);
        float durability = supportedDurabilityAt(level, ((TargetBlockKey.Vanilla) target).worldPos(),
                tmpNormal, blockDurabilityOf(state));

        BlockTotal blockTotal = blocks.computeIfAbsent(target, ignored -> new BlockTotal());
        blockTotal.absorb(section, state, blockImpulse, durability);

        Vector3f velocity = contactVelocity(partBody, tmpPoint);
        PartTotal partTotal = parts.computeIfAbsent(
                new PartDamageKey(partBodyId, hitBoxIndex, ImpactSource.TERRAIN),
                ignored -> new PartTotal(part, hitBox,
                        SparkMathKt.toVec3(tmpPoint), SparkMathKt.toVec3(tmpNormal), SparkMathKt.toVec3(velocity)));
        partTotal.absorb(partImpulse);
    }

    /** 接触点的世界速度：零件平动 + 角速度在接触点的切向贡献（地形侧视为静止）。 */
    private static Vector3f contactVelocity(PhysicsRigidBody body, Vector3f point) {
        Vector3f lever = point.subtract(body.getPhysicsLocation(null));
        return body.getLinearVelocity(null).add(body.getAngularVelocity(null).cross(lever));
    }

    // ==================== 判定与投递 ====================

    /** 按方块聚合判定破坏：先固定贡献，再每块最多一次破坏决策。 */
    private void decideBlockBreaks() {
        if (blocks.isEmpty()) return;
        // 方块破坏受总开关约束；部件伤害不受它影响（§7）
        if (!MMServerConfig.shouldDestroyBlocks()) return;
        float coefficient = MMServerConfig.collisionImpulsePerDurability();
        for (Map.Entry<TargetBlockKey, BlockTotal> entry : blocks.entrySet()) {
            if (!(entry.getKey() instanceof TargetBlockKey.Vanilla vanilla)) continue;
            BlockTotal total = entry.getValue();
            if (total.section == null || total.state == null) continue;
            float blockImpulse = total.impulse;
            if (!(blockImpulse > 0f)) continue;
            float weightedDurability = total.weightedDurability / blockImpulse;
            float threshold = CollisionImpactModel.breakImpulse(coefficient, weightedDurability);
            if (!CollisionImpactModel.meetsBreakThreshold(blockImpulse, threshold)) continue;
            if (!total.section.isRemoved(vanilla.worldPos())) {
                total.section.markRemoved(vanilla.worldPos());
            }
            scheduleBlockRemoval(level, vanilla.worldPos(), total.state, dropChance(blockImpulse, threshold));
            blocksBroken++;
        }
    }

    /**
     * 按部件聚合投递一次地形碰撞伤害。
     *
     * <p>{@code baseDamage = C_D × ΣI_part} 已在聚合阶段完成分配，这里只调用一次协议，
     * 由材料修正完成剩余部分；上下文标记为普通碰撞并显式零额外冲量。</p>
     */
    private void deliverPartDamage() {
        if (parts.isEmpty()) return;
        float coefficient = MMServerConfig.collisionDamagePerImpulse();
        if (!(coefficient > 0f)) return;
        DamageSource source = level.damageSources().flyIntoWall();
        for (PartTotal total : parts.values()) {
            float baseDamage = CollisionImpactModel.collisionDamage(coefficient, total.impulse);
            if (!(baseDamage > 0f) || !Float.isFinite(baseDamage)) continue;
            BFDamageContext ctx = BFDamageContext.builder()
                    .source(source)
                    .baseDamage(baseDamage)
                    .hitVelocity(total.velocity)
                    .hitPoint(total.point)
                    .hitNormal(total.normal)
                    .penetration(0f)
                    .build();
            ctx.extensions().set(MMDamageExtensions.HIT_BOX, total.hitBox);
            ctx.extensions().set(MMDamageExtensions.COLLISION_IMPACT, true);
            ctx.extensions().set(BFDamageExtensions.IMPULSE, 0f);
            BFDamageApi.hurt(total.part, ctx);
            partsDamaged++;
        }
    }

    /**
     * 主线程按目标去重删除方块，并在目标变化或删除失败时补偿地形快照。
     *
     * <p>物理线程已经 {@link PhysicsChunkSection#markRemoved} 过的目标不会重复进入伤害统计；
     * 这里的任务只负责落地。目标已被替换或删除失败时，用 Spark 的方块更新通道把该位置的
     * 物理快照拉回现实状态，避免留下幽灵碰撞体。</p>
     */
    public static void scheduleBlockRemoval(Level level, BlockPos pos, BlockState expected, float dropChance) {
        if (level.isClientSide() || pos == null || expected == null) return;
        SparkLevel.submitDeduplicatedTask(level, "mm_collision_break_" + pos.toShortString(), PPhase.PRE, () -> {
            if (!level.hasChunkAt(pos) || level.getBlockState(pos).getBlock() != expected.getBlock()) {
                resyncTerrain(level, pos);
                return;
            }
            boolean drop = dropChance > 0f && Math.random() < dropChance;
            if (!level.destroyBlock(pos, drop)) resyncTerrain(level, pos);
        });
    }

    /** 用 Spark 的方块更新通道重建该位置的物理快照。 */
    private static void resyncTerrain(Level level, BlockPos pos) {
        PhysicsLevel physicsLevel = SparkLevel.getPhysicsLevelOrNull(level);
        if (physicsLevel == null) return;
        PhysicsChunkManager terrain = physicsLevel.getTerrainManager();
        if (terrain != null) terrain.onBlockUpdated(Set.of(pos));
    }

    /** 破坏后的完整掉落概率：超额越大越少，每块最多一次决策。 */
    public static float dropChance(float contribution, float threshold) {
        if (!Float.isFinite(threshold) || threshold <= 0f) return 1f;
        float ratio = Math.max(1f, contribution / threshold);
        return Math.clamp((float) Math.exp(1d - ratio), 0f, 1f);
    }

    // ==================== 目标与方块数据解析 ====================

    /**
     * 把地形接触点解析成方块坐标。Processed 与 Post 共用本方法，保证两阶段得到同一个目标。
     *
     * @param section           地形区块段
     * @param contactWorld      零件侧接触点（世界坐标）
     * @param normalTowardsPart 由地形指向零件的单位法线
     * @return 方块坐标；无法定位时返回 {@code null}
     */
    @Nullable
    public static BlockPos resolveVanillaBlockPos(PhysicsChunkSection section,
                                                 Vector3f contactWorld, Vector3f normalTowardsPart) {
        if (section == null || contactWorld == null || normalTowardsPart == null) return null;
        // 接触点通常落在方块表面上，沿法线反方向压入几何小量后再取整；
        // 薄形状与整数边界可能落空，逐级加深偏移，仍落空就放弃（不破坏、不伤害、只计诊断）。
        for (float backoff : new float[]{0.001f, 0.25f, 0.5f}) {
            BlockPos pos = section.getBlockPosFromContactPoint(contactWorld, normalTowardsPart, -backoff);
            if (isSolidSnapshot(section, pos)) {
                return pos;
            }
        }
        BlockPos pos = section.getBlockPosFromContactPoint(contactWorld, normalTowardsPart, 0f);
        return isSolidSnapshot(section, pos) ? pos : null;
    }

    /**
     * 把地形接触点解析成方块身份。
     *
     * @param terrainBodyId     地形侧刚体 native ID
     * @param section           地形区块段
     * @param contactWorld      零件侧接触点（世界坐标）
     * @param normalTowardsPart 由地形指向零件的单位法线
     * @return 方块身份；无法定位时返回 {@code null}
     */
    @Nullable
    public static TargetBlockKey resolveVanillaTarget(long terrainBodyId, PhysicsChunkSection section,
                                                      Vector3f contactWorld, Vector3f normalTowardsPart) {
        BlockPos pos = resolveVanillaBlockPos(section, contactWorld, normalTowardsPart);
        return pos == null ? null : new TargetBlockKey.Vanilla(terrainBodyId, pos);
    }

    /** 坐标在本段范围内且存在碰撞快照。 */
    private static boolean isSolidSnapshot(PhysicsChunkSection section, BlockPos pos) {
        BlockPos origin = section.getSectionPos().origin();
        if (pos.getX() < origin.getX() || pos.getX() > origin.getX() + 15
                || pos.getY() < origin.getY() || pos.getY() > origin.getY() + 15
                || pos.getZ() < origin.getZ() || pos.getZ() > origin.getZ() + 15) {
            return false;
        }
        return section.getBlockSnapshot(pos) != null;
    }

    /** 方块侧的防护水平（{@code ArmorUtil#getBlockArmor}）。 */
    public static float blockArmorOf(BlockState state) {
        return ArmorUtil.getBlockArmor(EmptyBlockGetter.INSTANCE, state, BlockPos.ZERO);
    }

    /**
     * 方块自身耐久（{@code DamageUtil#getMaxBlockDurability}）。
     *
     * <p>不可破坏与空形状返回 0；调用方据此让背撑与门槛都失效，不会把负硬度代进公式。</p>
     */
    public static float blockDurabilityOf(BlockState state) {
        return DamageUtil.getMaxBlockDurability(EmptyBlockGetter.INSTANCE, state, BlockPos.ZERO);
    }

    /**
     * 带背撑的有效耐久：{@code D* = D_target + 0.5 × D_back}。
     *
     * <p>液体、空形状与已逻辑移除的方块不提供加成；不可破坏的背撑按同耐久支撑处理，
     * 保证前方普通方块不会因背后一块基岩变成无限耐久。</p>
     *
     * @param targetDurability 目标方块自身耐久
     */
    public static float supportedDurabilityAt(Level level, BlockPos pos, Vector3f normalTowardsPart,
                                              float targetDurability) {
        float backDurability = 0f;
        PhysicsLevel physicsLevel = SparkLevel.getPhysicsLevelOrNull(level);
        if (physicsLevel != null) {
            PhysicsChunkManager terrain = physicsLevel.getTerrainManager();
            if (terrain != null) {
                Vec3i into = MMMath.getClosestAxisAlignedVector(
                        SparkMathKt.toVec3(normalTowardsPart).scale(-1d));
                SectionSnapshot.BlockSnapshot back = terrain.getBlockSnapshotAt(pos.offset(into));
                if (back != null) {
                    BlockState backState = back.getState();
                    float hardness = backState.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                    backDurability = hardness < 0f || !Float.isFinite(hardness)
                            ? targetDurability
                            : blockDurabilityOf(backState);
                }
            }
        }
        return CollisionImpactModel.supportedDurability(targetDurability, backDurability);
    }

    // ==================== 诊断 ====================

    private void publishDiagnostics() {
        if (manifoldsEnumerated == 0 && pointsVisited == 0) return;
        long now = System.nanoTime();
        if (lastLogNanos != 0L && now - lastLogNanos < LOG_INTERVAL_NS) return;
        lastLogNanos = now;
        MachineMax.LOGGER.info(
                "[collision] substep={} dt={}ms manifolds={} relevant={} points={} resolved={} hits={} "
                        + "skipDist={} skipImpulse={} notCandidate={} unresolved={} blocks={} broken={} "
                        + "parts={} hurt={} jni={} collect={}us",
                substep, String.format("%.2f", dt * 1000f), manifoldsEnumerated, relevantManifolds,
                pointsVisited, pointsResolved, pointsHit, pointsSkippedDistance, pointsSkippedImpulse,
                pointsNotCandidate, unresolvedTargets, blocks.size(), blocksBroken,
                parts.size(), partsDamaged, jniCalls, settleNanos / 1000L);
    }

    private void clearStep() {
        terrainCandidates.clear();
        candidateBodyIds.clear();
        blocks.clear();
        parts.clear();
        manifoldsEnumerated = 0;
        relevantManifolds = 0;
        pointsVisited = 0;
        pointsSkippedDistance = 0;
        pointsSkippedImpulse = 0;
        pointsResolved = 0;
        pointsHit = 0;
        pointsNotCandidate = 0;
        unresolvedTargets = 0;
        blocksBroken = 0;
        partsDamaged = 0;
        jniCalls = 0;
        settleNanos = 0L;
    }

    // ==================== 键与聚合类型 ====================

    /** 方块所属坐标域。 */
    public enum TerrainDomain {
        /** 原版世界地形，坐标是 {@link BlockPos} 的世界坐标。 */
        VANILLA,
        /** Create 装置，坐标是装置本地坐标。 */
        CREATE
    }

    /**
     * 被撞击方块的身份：地形侧刚体身份 + 该坐标域内的方块坐标。
     *
     * <p>两个实现都是本文件的嵌套记录，因此 {@code sealed} 不需要显式 {@code permits} 列表。</p>
     */
    public sealed interface TargetBlockKey {

        /** 该目标的坐标域。 */
        TerrainDomain domain();

        /**
         * 原版地形方块。
         *
         * @param sectionBodyId 承载该方块的地形刚体 native ID
         * @param worldPos      世界方块坐标
         */
        record Vanilla(long sectionBodyId, BlockPos worldPos) implements TargetBlockKey {
            @Override
            public TerrainDomain domain() {
                return TerrainDomain.VANILLA;
            }
        }

        /**
         * Create 装置方块。
         *
         * @param contraptionBodyId 装置的物理对象 native ID
         * @param localPos          装置本地方块坐标，不是世界坐标
         */
        record Create(long contraptionBodyId, BlockPos localPos) implements TargetBlockKey {
            @Override
            public TerrainDomain domain() {
                return TerrainDomain.CREATE;
            }
        }
    }

    /**
     * 地形撞击组：同一零件刚体、同一 HitBox、同一目标方块。
     *
     * <p>资格按组判定，但只对解析到同一目标的点生效——同一 manifold 中的其他方块
     * 不能借这个资格参与本步结算。</p>
     */
    public record TerrainImpactKey(long partBodyId, int hitBoxIndex, TargetBlockKey target) {
    }

    /**
     * 部件伤害投递组：同一零件刚体、同一 HitBox、同一来源类别。
     */
    public record PartDamageKey(long partBodyId, int hitBoxIndex, ImpactSource source) {
    }

    /** 部件伤害来源类别。 */
    public enum ImpactSource {
        /** 与方块的撞击。 */
        TERRAIN
    }

    /**
     * 单个方块本步的聚合结果。
     *
     * <p>{@code impulse} 是方块侧贡献之和；{@code weightedDurability} 是同一贡献加权的背撑耐久之和，
     * 判定时两者相除得到加权平均 {@code D*}，避免用最后一个点的门槛代表整块。</p>
     */
    public static final class BlockTotal {
        private PhysicsChunkSection section;
        private BlockState state;
        private float impulse;
        private float weightedDurability;
        private int points;

        private void absorb(PhysicsChunkSection section, BlockState state, float contribution, float durability) {
            this.section = section;
            this.state = state;
            this.impulse += contribution;
            this.weightedDurability += contribution * durability;
            this.points++;
        }

        public float impulse() {
            return impulse;
        }

        public int points() {
            return points;
        }

        /** 加权平均有效耐久；没有贡献时为 0。 */
        public float weightedDurability() {
            return impulse > 0f ? weightedDurability / impulse : 0f;
        }
    }

    /**
     * 单个部件本步的聚合结果：自承冲量、代表性命中几何与命中箱。
     */
    public static final class PartTotal {
        private final SubPart part;
        private final HitBox hitBox;
        private float impulse;
        private final Vec3 point;
        private final Vec3 normal;
        private final Vec3 velocity;
        private int points;

        private PartTotal(SubPart part, HitBox hitBox, Vec3 point, Vec3 normal, Vec3 velocity) {
            this.part = part;
            this.hitBox = hitBox;
            this.point = point;
            this.normal = normal;
            this.velocity = velocity;
        }

        private void absorb(float contribution) {
            this.impulse += contribution;
            this.points++;
        }

        public float impulse() {
            return impulse;
        }

        public int points() {
            return points;
        }
    }
}
