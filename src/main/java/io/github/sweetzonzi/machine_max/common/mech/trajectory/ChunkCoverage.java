package io.github.sweetzonzi.machine_max.common.mech.trajectory;

/**
 * 一条航迹线段在水平面上覆盖到的区块格。
 * <p>
 * 输入的 {@code (ax, az) → (bx, bz)} 是<b>方块坐标</b>（不是区块坐标，也不是米制长度），
 * 输出的格子是区块坐标 {@code blockX >> 4}。坐标系写错时 {@code (15, 14) → (18, 18)}
 * 会落在区块 {@code (15, 14)} 与 {@code (18, 18)}，与期望集合完全不相交，不会静默通过。
 * <p>
 * <b>为什么不是按步长采样端点。</b>覆盖不能由步长负责：XZ 从 {@code (15,14)} 到
 * {@code (18,18)} 只有 5 m，两端落在区块 {@code (0,0)} 与 {@code (1,1)}，
 * 中段有 0.83 m 走在 {@code (1,0)} 里。对任意固定步长 {@code h}，取一条长度小于 {@code h}
 * 的擦角弦即可构造同样的漏判。
 * <p>
 * <b>判据是“线段到格子的距离 ≤ r”</b>，等价于把线段膨胀成半径 {@code r} 的胶囊后与格子求交。
 * 这同时给出两条性质：
 * <ul>
 *   <li><b>跨角点时两侧格都要取</b>——擦角弦 {@code (15.95,15.95) → (16.05,16.05)}
 *       到四个角格的距离都是 0，四格全进；</li>
 *   <li><b>外扩半径按垂直距离</b>——{@code (15,14) → (18,18)} 到角点 {@code (16,16)} 的垂距是
 *       0.4 m，所以 {@code r ≥ 0.4} 才会带上第四个格 {@code (0,1)}，{@code r = 0.39} 不会。</li>
 * </ul>
 * 按轴对齐正方形放宽（而不是按距离）会让第二条提前到 {@code r ≈ 0.286}，把格子数算多，
 * 因此这里按距离判。候选格由线段加外扩后的包围盒枚举——单样本跨度不超过 8 m，
 * 加上 3 m 的外扩也小于一个区块边长，所以每次最多落 2×2 格。
 * <p>
 * <b>格线归属按 {@code floor}。</b>方块坐标正好落在区块分界线（例如 {@code x = 16}）时归属右侧区块，
 * 与 {@code SectionPos#blockToSectionCoord} 同一条约定。真机上物理坐标不会正好落在格线上，
 * 这条只保证退化输入的行为是确定的；跨过格线时两侧区块都在结果里。
 * <p>
 * <b>线程</b>：无状态，任何线程可调用；不产生分配（结果经 {@link CellConsumer} 交出）。
 */
public final class ChunkCoverage {

    /** 区块边长（方块）。 */
    private static final int CHUNK_SIZE = 16;

    /** 水平面内的浮点比较下限，用于把退化线段（零长）当点处理。 */
    private static final double EPSILON = 1e-12;

    /**
     * 接收一个被覆盖的区块。坐标是区块坐标，不是方块坐标。
     */
    @FunctionalInterface
    public interface CellConsumer {
        void accept(int chunkX, int chunkZ);
    }

    private ChunkCoverage() {
    }

    /**
     * 枚举线段 {@code (ax, az) → (bx, bz)} 膨胀 {@code radius} 后覆盖到的全部区块。
     * <p>
     * 同一格只会被交出一次。交出顺序按 {@code chunkX} 外层、{@code chunkZ} 内层的升序，
     * 与覆盖语义无关——调用方不得依赖它表达“由近到远”。
     *
     * @param ax     起点 X（方块坐标）
     * @param az     起点 Z（方块坐标）
     * @param bx     终点 X（方块坐标）
     * @param bz     终点 Z（方块坐标）
     * @param radius 外扩半径（方块），负值按 0 处理
     * @param out    接收每个被覆盖的区块
     */
    public static void cellsCrossedBySegment(double ax, double az, double bx, double bz,
                                             double radius, CellConsumer out) {
        double r = radius > 0.0 ? radius : 0.0;
        double minX = Math.min(ax, bx) - r;
        double maxX = Math.max(ax, bx) + r;
        double minZ = Math.min(az, bz) - r;
        double maxZ = Math.max(az, bz) + r;

        int cx0 = cellOf(minX);
        int cx1 = cellOf(maxX);
        int cz0 = cellOf(minZ);
        int cz1 = cellOf(maxZ);

        for (int cx = cx0; cx <= cx1; cx++) {
            double x0 = (double) cx * CHUNK_SIZE;
            double x1 = x0 + CHUNK_SIZE;
            for (int cz = cz0; cz <= cz1; cz++) {
                double z0 = (double) cz * CHUNK_SIZE;
                double z1 = z0 + CHUNK_SIZE;
                if (segmentToRectDistance(ax, az, bx, bz, x0, z0, x1, z1) <= r) {
                    out.accept(cx, cz);
                }
            }
        }
    }

    /** 方块坐标 → 所在区块坐标（向下取整，负坐标同样成立）。 */
    private static int cellOf(double blockCoord) {
        return (int) Math.floor(blockCoord / CHUNK_SIZE);
    }

    /**
     * 线段到轴对齐矩形的距离。相交为 0。
     * <p>
     * 两个集合都凸，最近点对只有三种形态：线段端点到矩形、矩形顶点到线段、或相交为 0。
     * 前两种由下面的候选值覆盖：若最近点对落在矩形边的内部与线段内部，则线段与那条边平行，
     * 此时矩形的一个顶点投影在线段上、距离正是这条垂直距离（边比线段短），
     * 或线段的一个端点投影在边上、距离也正是它（边比线段长）。
     */
    private static double segmentToRectDistance(double ax, double az, double bx, double bz,
                                               double x0, double z0, double x1, double z1) {
        if (segmentIntersectsRect(ax, az, bx, bz, x0, z0, x1, z1)) {
            return 0.0;
        }
        double best = Math.min(
                pointToRectDistance(ax, az, x0, z0, x1, z1),
                pointToRectDistance(bx, bz, x0, z0, x1, z1));
        best = Math.min(best, pointToSegmentDistance(x0, z0, ax, az, bx, bz));
        best = Math.min(best, pointToSegmentDistance(x1, z0, ax, az, bx, bz));
        best = Math.min(best, pointToSegmentDistance(x0, z1, ax, az, bx, bz));
        best = Math.min(best, pointToSegmentDistance(x1, z1, ax, az, bx, bz));
        return best;
    }

    /** 线段与闭矩形是否相交（Liang–Barsky 的 slab 形式）。 */
    private static boolean segmentIntersectsRect(double ax, double az, double bx, double bz,
                                                 double x0, double z0, double x1, double z1) {
        double tMin = 0.0;
        double tMax = 1.0;
        double dx = bx - ax;
        double dz = bz - az;

        if (Math.abs(dx) < EPSILON) {
            if (ax < x0 || ax > x1) return false;
        } else {
            double t1 = (x0 - ax) / dx;
            double t2 = (x1 - ax) / dx;
            if (t1 > t2) {
                double swap = t1;
                t1 = t2;
                t2 = swap;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return false;
        }

        if (Math.abs(dz) < EPSILON) {
            if (az < z0 || az > z1) return false;
        } else {
            double t1 = (z0 - az) / dz;
            double t2 = (z1 - az) / dz;
            if (t1 > t2) {
                double swap = t1;
                t1 = t2;
                t2 = swap;
            }
            tMin = Math.max(tMin, t1);
            tMax = Math.min(tMax, t2);
            if (tMin > tMax) return false;
        }
        return true;
    }

    /** 点到闭矩形的距离，点在矩形内为 0。 */
    private static double pointToRectDistance(double px, double pz,
                                              double x0, double z0, double x1, double z1) {
        double dx = Math.max(Math.max(x0 - px, 0.0), px - x1);
        double dz = Math.max(Math.max(z0 - pz, 0.0), pz - z1);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 点到线段的最短距离（端点夹紧）。 */
    private static double pointToSegmentDistance(double px, double pz,
                                                 double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double lengthSq = dx * dx + dz * dz;
        double t = lengthSq < EPSILON
                ? 0.0
                : ((px - ax) * dx + (pz - az) * dz) / lengthSq;
        t = Math.max(0.0, Math.min(1.0, t));
        double ex = px - (ax + dx * t);
        double ez = pz - (az + dz * t);
        return Math.sqrt(ex * ex + ez * ez);
    }
}
