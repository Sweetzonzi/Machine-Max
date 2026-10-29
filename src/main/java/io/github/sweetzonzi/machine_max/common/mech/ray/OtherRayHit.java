package io.github.sweetzonzi.machine_max.common.mech.ray;

import cn.solarmoon.spark_core.physics.PhysicsHost;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * 命中已知 owner、但不属于其他具体类型的解析结果。
 *
 * <p>保留该类型是为了避免静默丢掉未知目标；命中点与法线照常提供，消费方仍可据此
 * 判断遮挡或距离。</p>
 *
 * @param fraction 沿射线的命中比例
 * @param owner    被命中物理体的 owner
 * @param point    世界坐标命中点
 * @param normal   世界空间命中法线
 */
public record OtherRayHit(
        double fraction,
        PhysicsHost owner,
        Vec3 point,
        Vec3 normal) implements RayHit {

    public OtherRayHit {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(normal, "normal");
    }
}
