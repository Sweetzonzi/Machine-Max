package io.github.sweetzonzi.machine_max.common.mech.ray;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * 物理射线直接命中 {@link AbstractConnector} 的解析结果。
 *
 * <p>只表示射线与连接器物理体本身的求交；“先命中 SubPart，再从其连接器中挑最近可用者”
 * 属于 Eyesight 的后续选择策略，不在这里伪造成直接连接器命中。</p>
 *
 * @param fraction  沿射线的命中比例
 * @param connector 被命中的连接器
 * @param point     世界坐标命中点
 * @param normal    世界空间命中法线
 */
public record ConnectorRayHit(
        double fraction,
        AbstractConnector connector,
        Vec3 point,
        Vec3 normal) implements RayHit {

    public ConnectorRayHit {
        Objects.requireNonNull(connector, "connector");
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(normal, "normal");
    }
}
