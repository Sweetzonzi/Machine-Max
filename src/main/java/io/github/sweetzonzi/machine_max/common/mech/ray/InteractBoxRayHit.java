package io.github.sweetzonzi.machine_max.common.mech.ray;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.InteractBox;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * 命中交互判定区子形状的解析结果。
 *
 * <p>{@link #enabledAtQuery()} 是解析时读取的启用状态值；交互模式
 * （FAST / ACCURATE）的选择仍由调用方策略处理。</p>
 *
 * @param fraction       沿射线的命中比例
 * @param interactBox    命中的交互判定区
 * @param enabledAtQuery 解析时是否启用
 * @param point          世界坐标命中点
 * @param normal         世界空间命中法线
 */
public record InteractBoxRayHit(
        double fraction,
        InteractBox interactBox,
        boolean enabledAtQuery,
        Vec3 point,
        Vec3 normal) implements RayHit {

    public InteractBoxRayHit {
        Objects.requireNonNull(interactBox, "interactBox");
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(normal, "normal");
    }
}
