package io.github.sweetzonzi.machine_max.client.render.renderer;

import io.github.sweetzonzi.machine_max.common.registry.MMEntities;

/** 实体渲染与远距事件渲染共用的归属边界。 */
final class VehicleRenderDistance {
    private static final double ENTITY_RENDER_DISTANCE_BLOCKS = MMEntities.PART_ENTITY_TRACKING_RANGE_CHUNKS * 16.0;

    private VehicleRenderDistance() {
    }

    /**
     * 返回实体渲染管线的最大距离平方，单位为方块距离的平方。
     *
     * @return 实体渲染与远距事件渲染的共同边界平方
     */
    static double entityRenderDistanceSqr() {
        return ENTITY_RENDER_DISTANCE_BLOCKS * ENTITY_RENDER_DISTANCE_BLOCKS;
    }

    /**
     * 判断指定三维位置是否仍由实体渲染管线负责；最大范围之外归远距事件渲染。
     *
     * @param dx 目标与相机在 X 轴上的距离
     * @param dy 目标与相机在 Y 轴上的距离
     * @param dz 目标与相机在 Z 轴上的距离
     * @return 位于交接边界内时返回 {@code true}
     */
    static boolean isEntityRenderOwner(double dx, double dy, double dz) {
        double distanceSqr = dx * dx + dy * dy + dz * dz;
        return isEntityRenderOwnerSqr(distanceSqr);
    }

    /**
     * 判断给定距离平方是否仍由实体渲染管线负责；边界本身归远距事件渲染。
     *
     * @param distanceSqr 目标与相机的三维距离平方
     * @return 严格位于交接边界内时返回 {@code true}
     */
    static boolean isEntityRenderOwnerSqr(double distanceSqr) {
        return distanceSqr < entityRenderDistanceSqr();
    }
}
