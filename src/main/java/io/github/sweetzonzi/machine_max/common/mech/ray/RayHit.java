package io.github.sweetzonzi.machine_max.common.mech.ray;

import net.minecraft.world.phys.Vec3;

/**
 * 一次物理射线查询中的单条解析结果。
 *
 * <p>所有命中都提供沿射线的比例、世界坐标命中点与世界空间法线；消费方再按具体结果
 * 类型读取目标身份（SubPart / HitBox、连接器、交互判定区、地形区块或实体）。</p>
 *
 * <p>线程：由 {@link RayHitResolver#resolve} 在查询发起方所在线程构造，构造后不可变。</p>
 */
public interface RayHit {

    /**
     * 命中点在本条射线上的比例，范围 {@code [0, 1]}。
     *
     * @return 沿射线的命中比例
     */
    double fraction();

    /**
     * 世界坐标命中点。
     *
     * @return 命中点
     */
    Vec3 point();

    /**
     * 世界空间命中法线。
     *
     * @return 命中法线
     */
    Vec3 normal();
}
