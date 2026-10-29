package io.github.sweetzonzi.machine_max.common.mech.ray;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * 命中某个 {@link SubPart} 有效碰撞形状的解析结果。
 *
 * <p>同一次查询会为同一 SubPart 的每个有效子形状各生成一条结果，不在这里按刚体去重。
 * 由构造入口保证 {@link #subPart()}、{@link #hitBox()}、{@link #point()} 与
 * {@link #normal()} 非空。</p>
 *
 * @param fraction 沿射线的命中比例
 * @param subPart  被命中的子零件
 * @param hitBox   命中的碰撞箱
 * @param point    世界坐标命中点
 * @param normal   世界空间命中法线
 */
public record SubPartRayHit(
        double fraction,
        SubPart subPart,
        HitBox hitBox,
        Vec3 point,
        Vec3 normal) implements RayHit {

    public SubPartRayHit {
        Objects.requireNonNull(subPart, "subPart");
        Objects.requireNonNull(hitBox, "hitBox");
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(normal, "normal");
    }
}
