package io.github.sweetzonzi.machine_max.common.mech.ray;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.InteractBox;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * 命中交互判定区子形状的解析结果。
 *
 * <p>解析发生在射线发起方所在线程，只做几何与身份解析：交互区此刻能不能用属于主线程状态
 * （门控表达式、目标可用性），由消费方在主线程用 {@link InteractBox#canInteract()} 判。
 * 交互模式（FAST / ACCURATE）的选择同样由调用方策略处理。</p>
 *
 * @param fraction    沿射线的命中比例
 * @param interactBox 命中的交互判定区
 * @param point       世界坐标命中点
 * @param normal      世界空间命中法线
 */
public record InteractBoxRayHit(
        double fraction,
        InteractBox interactBox,
        Vec3 point,
        Vec3 normal) implements RayHit {

    public InteractBoxRayHit {
        Objects.requireNonNull(interactBox, "interactBox");
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(normal, "normal");
    }
}
