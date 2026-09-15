package io.github.sweetzonzi.machine_max.util.mechanic;

/**
 * 3D DDA 体素遍历的游标状态。
 *
 * <p>由调用方独占持有并交给 {@link VoxelRayWalker#walk} 原地推进：爆炸侧跨 tick 复用
 * （存在射线上），光束侧每次扫描传一个即可（它不需要跨 tick 续接）。</p>
 *
 * <p>游标只承载两件跨调用有意义的信息：是否已经初始化过（决定"起始体素"是否参与判定），
 * 以及最近一次跨越的体素与跨越面轴（供诊断与法线推断）。每次 {@code walk} 内部会按
 * 射线当前位置重新推导步进参数，因此即使起点恰好落在体素边界上也不会累计浮点误差。</p>
 */
public final class VoxelCursor {

    /** 是否已初始化。首次 walk 会显式判定起点所在体素，之后再遍历不再重复判定。 */
    boolean started;

    /** 当前体素坐标（最近一次跨越后的体素） */
    int voxelX;
    int voxelY;
    int voxelZ;

    /** 最近一次跨越面的轴：0=X、1=Y、2=Z；-1 表示尚无跨越 */
    int lastNormalAxis = -1;

    public VoxelCursor() {
    }

    /** 重置游标，使下一次 walk 重新按"首次遍历"处理（会判定起始体素）。 */
    public void reset() {
        started = false;
        lastNormalAxis = -1;
    }

    /** 是否已经初始化过 */
    public boolean isStarted() {
        return started;
    }

    /** 最近一次跨越面的轴：0=X、1=Y、2=Z；-1 表示尚无跨越 */
    public int getLastNormalAxis() {
        return lastNormalAxis;
    }
}
