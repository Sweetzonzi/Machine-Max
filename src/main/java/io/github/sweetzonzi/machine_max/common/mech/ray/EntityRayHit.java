package io.github.sweetzonzi.machine_max.common.mech.ray;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * 命中普通实体的解析结果。
 *
 * <p>普通实体没有部件子形状，目标身份与命中几何分开读取。是否接受该实体仍由调用方按
 * 原版谓词、乘客规则与交互距离自行判断。</p>
 *
 * @param fraction 沿射线的命中比例
 * @param entity   被命中的实体
 * @param point    世界坐标命中点
 * @param normal   世界空间命中法线
 */
public record EntityRayHit(
        double fraction,
        Entity entity,
        Vec3 point,
        Vec3 normal) implements RayHit {

    public EntityRayHit {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(normal, "normal");
    }
}
