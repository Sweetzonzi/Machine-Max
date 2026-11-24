package io.github.sweetzonzi.machine_max.common.mech.ray;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * 命中方块地形的解析结果。
 *
 * <p>地形不走物理快照：Spark-Core 的快照世界只追踪物理刚体与投射物，地形区块既不进快照，
 * 也只按需在物理刚体附近构建。因此方块命中由主线程的 {@code Level.clip} 得出，覆盖当前已加载的
 * 区块，不等待地形网格构建。方块判定用 {@link ClipContext.Block#COLLIDER}，与物理地形网格同源
 * （两者都读 {@code BlockState.getCollisionShape}），所以同一次查询里方块与刚体的遮挡不会互相矛盾。</p>
 *
 * <p>{@link #clip} 以方块命中的身份建立本类型：命中点落在射线上、终点之前才算命中。</p>
 *
 * <p>线程：主线程。{@code Level.clip} 读区块数据，不能在物理线程或渲染线程调用。</p>
 *
 * @param fraction  沿射线的命中比例，范围 {@code (0, 1)}
 * @param pos       被命中的方块坐标
 * @param direction 被命中的面朝向，也是世界空间法线
 * @param point     世界坐标命中点
 */
public record BlockRayHit(
        double fraction,
        BlockPos pos,
        Direction direction,
        Vec3 point) implements RayHit {

    /** 命中点与终点的最小距离差（平方值）；小于它的命中视为落在终点上。 */
    private static final double ENDPOINT_TOLERANCE_SQR = 1.0e-6;

    public BlockRayHit {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(point, "point");
    }

    /**
     * 沿 {@code from → to} 打一条方块射线并解析。
     *
     * <p>终点是查询目标本身（碰撞箱中心、瞄准点），因此落在终点上的命中不计入结果——
     * 否则所有贴地的目标都会被自己脚下的方块判成遮挡。</p>
     *
     * @param level  查询所在维度
     * @param source 发起查询的实体，用于提供碰撞上下文；为 null 时用空上下文
     * @param from   射线起点（世界坐标）
     * @param to     射线终点（世界坐标）
     * @return 方块命中；没有命中、命中落在终点上或射线长度为零时返回 null
     */
    @Nullable
    public static BlockRayHit clip(Level level, @Nullable Entity source, Vec3 from, Vec3 to) {
        if (from == null || to == null) return null;
        double lengthSqr = from.distanceToSqr(to);
        if (!(lengthSqr > 0.0) || !Double.isFinite(lengthSqr)) return null;
        CollisionContext collisionContext = source == null ? CollisionContext.empty() : CollisionContext.of(source);
        BlockHitResult result = level.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, collisionContext));
        if (result.getType() != HitResult.Type.BLOCK) return null;
        Vec3 point = result.getLocation();
        double hitSqr = point.distanceToSqr(from);
        if (!(hitSqr < lengthSqr - ENDPOINT_TOLERANCE_SQR)) return null;
        return new BlockRayHit(Math.sqrt(hitSqr / lengthSqr), result.getBlockPos(), result.getDirection(), point);
    }

    /**
     * 世界空间命中法线，由命中的面朝向给出。
     *
     * @return 单位轴向法线
     */
    @Override
    public Vec3 normal() {
        return new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }
}
