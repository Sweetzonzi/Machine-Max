package io.github.sweetzonzi.machine_max.common.mech.ray;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.physics.level.WorldSnapshot;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.simsilica.mathd.Vec3d;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * 一次原版实体射线查询的输入上下文：起终点、实体谓词与共享射线解析结果。
 *
 * <p>原版 {@code ProjectileUtil} 的候选循环在首次遇到部件候选时，用本上下文的起终点向
 * 只读物理快照发射一次射线并解析；其余候选只查本上下文的 SubPart 索引，不重复发射射线。</p>
 *
 * <p>解析结果包含 {@link RayHitResolver} 产出的全部命中类型；准星与投射物适配层只消费其中
 * 的 {@link SubPartRayHit}，其余类型留给调用方按需读取。</p>
 *
 * <p>返回给投射物的 {@link EntityHitResult} 通过 {@link #rememberResult} 与本上下文按实例身份
 * 关联，{@link io.github.sweetzonzi.machine_max.common.entity.PartHitHandler} 在实际命中事件中
 * {@link #consume} 它，用同一输入做一次独立复核。该关联使用后立即移除；未被消费的关联在
 * 后续查询进入时按游戏时间清理。</p>
 *
 * <p>线程：创建、解析与消费都在发起查询的主线程完成。同一上下文不在线程间共享可变状态
 * （结果字段为 volatile，仅防御性发布）。</p>
 */
public final class RayQueryContext {

    /** 已返回、尚未被实际命中事件消费的上下文，按结果实例身份关联。 */
    private static final Map<EntityHitResult, RayQueryContext> PENDING = new ConcurrentHashMap<>();

    private final long gameTime;
    private final Level level;
    @Nullable
    private final Entity source;
    private final Vec3 from;
    private final Vec3 to;
    @Nullable
    private final Predicate<Entity> filter;

    private volatile List<RayHit> hits;
    private volatile Map<SubPart, SubPartRayHit> nearestBySubPart;

    private RayQueryContext(Level level, @Nullable Entity source, Vec3 from, Vec3 to, @Nullable Predicate<Entity> filter) {
        this.gameTime = level.getGameTime();
        this.level = level;
        this.source = source;
        this.from = from;
        this.to = to;
        this.filter = filter;
    }

    /**
     * 开始一次查询上下文。
     *
     * @param level   查询所在维度
     * @param source  发起查询的实体（原版循环的 shooter / projectile），可为 null
     * @param from    射线起点（世界坐标）
     * @param to      射线终点（世界坐标）
     * @param filter  原版候选实体谓词，可为 null
     * @return 新建上下文
     */
    public static RayQueryContext begin(Level level, @Nullable Entity source, Vec3 from, Vec3 to,
                                        @Nullable Predicate<Entity> filter) {
        RayQueryContext context = new RayQueryContext(level, source, from, to, filter);
        if (PENDING.size() > 32) {
            long now = level.getGameTime();
            PENDING.entrySet().removeIf(entry -> entry.getValue().gameTime < now);
        }
        return context;
    }

    @Nullable
    public Entity source() {
        return source;
    }

    @Nullable
    public Predicate<Entity> filter() {
        return filter;
    }

    /**
     * 取指定部件在本次查询中的最近有效命中。
     *
     * @param subPart 部件
     * @return 最近命中；未命中或数据缺失时返回 null
     */
    @Nullable
    public SubPartRayHit nearestSubPartHit(SubPart subPart) {
        hits();
        Map<SubPart, SubPartRayHit> index = nearestBySubPart;
        return index == null ? null : index.get(subPart);
    }

    /**
     * 按实体谓词筛选后取最近的部件命中。
     *
     * <p>谓词在解析结果上按最近到最远依次应用，已失效、已移除或不在本维度的部件跳过。</p>
     *
     * @param entityFilter 实体谓词（对部件实体应用）；为 null 时不过滤
     * @return 最近的有效部件命中；没有时返回 null
     */
    @Nullable
    public SubPartRayHit nearestSubPartHitMatching(@Nullable Predicate<Entity> entityFilter) {
        for (RayHit hit : hits()) {
            if (!(hit instanceof SubPartRayHit subPartHit)) continue;
            MMPartEntity entity = subPartHit.subPart().getEntity();
            if (entity == null || entity.isRemoved() || entity.level() != level) continue;
            if (entityFilter != null && !entityFilter.test(entity)) continue;
            return subPartHit;
        }
        return null;
    }

    /**
     * 把本次原版查询返回的结果与上下文关联，供实际命中事件按结果实例取回输入。
     *
     * <p>只有投射物发起的查询才需要关联；准星等普通实体查询不产生投射物命中事件。</p>
     *
     * @param result 原版方法返回的实体命中结果
     */
    public void rememberResult(EntityHitResult result) {
        if (result == null || !(source instanceof Projectile)) return;
        PENDING.put(result, this);
    }

    /**
     * 取回并移除与某个原版结果关联的上下文。
     *
     * @param result 命中事件携带的结果实例
     * @return 关联的上下文；没有时返回 null
     */
    @Nullable
    public static RayQueryContext consume(EntityHitResult result) {
        return result == null ? null : PENDING.remove(result);
    }

    /**
     * 本次查询解析出的全部命中，按 fraction 升序。
     *
     * <p>首次调用时向只读物理快照发射一次射线并解析，之后返回同一结果。</p>
     *
     * @return 不可变结果列表
     */
    private List<RayHit> hits() {
        List<RayHit> current = hits;
        if (current != null) return current;
        List<RayHit> resolved = resolveFromSnapshot();
        nearestBySubPart = buildSubPartIndex(resolved);
        hits = resolved;
        return resolved;
    }

    /**
     * 向只读物理快照发射一次双精度端点射线并解析。
     */
    private List<RayHit> resolveFromSnapshot() {
        try {
            PhysicsLevel physicsLevel = SparkLevel.getPhysicsLevelOrNull(level);
            if (physicsLevel == null) return List.of();
            WorldSnapshot snapshot = physicsLevel.getWorld().getWorldSnapshot();
            List<PhysicsRayTestResult> raw = snapshot.rayTestDp(
                    new Vec3d(from.x, from.y, from.z),
                    new Vec3d(to.x, to.y, to.z),
                    new ArrayList<>(16));
            return RayHitResolver.resolve(raw, from, to);
        } catch (RuntimeException e) {
            // 物理世界尚未初始化（首 tick、维度切换、卸载中）时本次不生成部件命中
            MachineMax.LOGGER.debug("物理快照射线查询不可用，本次不生成部件命中: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 按 SubPart 建立最近命中索引；输入已按 fraction 升序，首次出现的即为最近。
     */
    private static Map<SubPart, SubPartRayHit> buildSubPartIndex(List<RayHit> resolved) {
        if (resolved.isEmpty()) return Map.of();
        Map<SubPart, SubPartRayHit> index = new IdentityHashMap<>();
        for (RayHit hit : resolved) {
            if (hit instanceof SubPartRayHit subPartHit) {
                index.putIfAbsent(subPartHit.subPart(), subPartHit);
            }
        }
        return index;
    }
}
