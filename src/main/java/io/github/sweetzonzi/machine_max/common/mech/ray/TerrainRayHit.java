package io.github.sweetzonzi.machine_max.common.mech.ray;

import cn.solarmoon.spark_core.physics.terrain.PhysicsChunkSection;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * 命中地形物理区块的解析结果。
 *
 * <p>地形区块是世界几何的遮挡来源之一；消费方可以用它判断部件命中前方是否有地形，
 * 而不必重新发射一次射线。</p>
 *
 * @param fraction 沿射线的命中比例
 * @param section  被命中的地形区块
 * @param point    世界坐标命中点
 * @param normal   世界空间命中法线
 */
public record TerrainRayHit(
        double fraction,
        PhysicsChunkSection section,
        Vec3 point,
        Vec3 normal) implements RayHit {

    public TerrainRayHit {
        Objects.requireNonNull(section, "section");
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(normal, "normal");
    }
}
