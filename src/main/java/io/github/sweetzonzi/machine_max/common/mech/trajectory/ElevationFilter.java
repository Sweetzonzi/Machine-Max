package io.github.sweetzonzi.machine_max.common.mech.trajectory;

import javax.annotation.Nullable;

/**
 * 高程过滤：把“弹道在这一区块的 Y 窗口”与“该区块实体方块区间”求交。
 * <p>
 * 判据只有三态，由调用方按 {@link #intersect} 的返回区分：
 * <table border="1">
 *   <tr><th>输入</th><th>返回</th><th>含义</th></tr>
 *   <tr><td>{@code intervals == null}</td><td>—</td><td>无索引（区块从未加载过）。查询在调用方完成，
 *       {@link #intersect} 收到 null 时同样按无交集处理，但调用方应另行计数</td></tr>
 *   <tr><td>数组为空</td><td>{@code null}</td><td>整列无实体方块（全空区块）</td></tr>
 *   <tr><td>区间与本窗口无交集</td><td>{@code null}</td><td>确定空中，不预约</td></tr>
 *   <tr><td>有交集</td><td>{@code [lo, hi]}</td><td>预约该区间</td></tr>
 * </table>
 * <p>
 * <b>返回的是并集区间而不是逐段区间。</b>同一区块可能被穿过多次（高抛弹道的上升段与下降段、
 * 一条线段跨过两层实体地形），而预约接口吃的是一个连续的 Y 范围；返回首段下界到末段上界
 * 会把中间的空隙一起带上——那是多激活几个 section，不会漏，也不会影响“预约与就绪查询用同一窗口”
 * 这条不变式（两边拿到的是同一个 {@code [lo, hi]}）。
 * <p>
 * <b>入参是方块 Y，不是 section Y。</b>索引给出的是方块 Y 区间，弹道窗口也是方块 Y；
 * 到 section 的换算只在 Spark 的包装层发生。在这里做一次 {@code >> 4} 就会与包装层的
 * {@link net.minecraft.core.SectionPos#blockToSectionCoord} 相差一次截断方向，且不会报错。
 * <p>
 * <b>线程</b>：纯函数，只读入参、不分配（除返回的 {@code int[2]}），任何线程可调用。
 */
public final class ElevationFilter {

    private ElevationFilter() {
    }

    /**
     * 求实体方块区间与弹道 Y 窗口的交集。
     *
     * @param intervals 该区块的实体方块区间，格式为 {@code [min1, max1, min2, max2, ...]}，
     *                  按 {@code min} 升序且互不重叠（{@code ChunkHeightIndex} 的编码）
     * @param yLo       弹道 Y 窗口下界（方块 Y，含）
     * @param yHi       弹道 Y 窗口上界（方块 Y，含）
     * @return {@code [lo, hi]}（含端点）或 {@code null}（无交集 / 无数据）
     */
    public static @Nullable int[] intersect(@Nullable short[] intervals, int yLo, int yHi) {
        if (intervals == null || intervals.length == 0) return null;
        if (yHi < yLo) {
            int swap = yLo;
            yLo = yHi;
            yHi = swap;
        }

        boolean found = false;
        int lo = 0;
        int hi = 0;
        for (int i = 0; i + 1 < intervals.length; i += 2) {
            int intervalLo = intervals[i];
            int intervalHi = intervals[i + 1];
            // 区间升序：彻底落在窗口下方的跳过，落在上方的后面只会更靠上
            if (intervalHi < yLo) continue;
            if (intervalLo > yHi) break;

            int hitLo = Math.max(yLo, intervalLo);
            int hitHi = Math.min(yHi, intervalHi);
            if (!found) {
                lo = hitLo;
                hi = hitHi;
                found = true;
            } else {
                lo = Math.min(lo, hitLo);
                hi = Math.max(hi, hitHi);
            }
        }
        return found ? new int[]{lo, hi} : null;
    }
}
