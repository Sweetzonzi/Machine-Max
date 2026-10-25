package io.github.sweetzonzi.machine_max.common.mech.trajectory;

import io.github.sweetzonzi.ballistics_framework.api.trajectory.BallisticConfig;
import io.github.sweetzonzi.ballistics_framework.api.trajectory.DensityFunction;
import io.github.sweetzonzi.ballistics_framework.api.trajectory.ThrustProfile;
import cn.solarmoon.spark_core.api.SparkLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 飞行期间的地形预载：走一遍弹道视界，把“这段航迹可能撞到的地形”预约上来。
 * <p>
 * <b>只约视界。</b>任何时刻被持有的区块只有“未来 {@code W} 米航迹”（外加 {@code hold} 的余量），
 * 剩余全程从不预约。早死、降速、跳弹因此都不会拖出一条整条弹道的白加载；代价是
 * 已登记的区块会持到“到达 + 余量”才放，至多 {@code W / v + 0.1 s}。
 * <p>
 * <b>视界长度必须含重算间隔。</b>区块在“首次进入视界”时被预约，而视界每 {@code Δ} 才推一次，
 * 所以它被提交的那一刻离自己的到达时刻只剩 {@code W − v·Δ} 的提前量。要求这个量不小于
 * {@code v·L}，即 {@code W = v·(L + Δ) + 16}。少了 {@code v·Δ} 这一项，匀速直飞也会在
 * APFSDS 上漏掉 82.5 m 宽的带。{@code 16 m} 覆盖区块栅格的量化误差——航迹真正进入某区块的位置
 * 可以比采样点早至多一个区块边长——它不是时间余量，对 APFSDS 只折合 9.7 ms。
 * <p>
 * <b>提前量从登记时刻算起，不引任何墙钟。</b>{@code hold} 是“到达时刻减登记时刻”再折算成物理步，
 * 而登记就发生在本次调用的同一个游戏 tick 里（经即时队列由 {@code requestStep} 消费），
 * 因此差值就是航迹行走的时间 {@code lead}，不需要读 {@code gameTime}（20 tps）也不需要用
 * 物理步计数反推绝对秒数——两者混用会把余量静默吃掉一半。
 * <p>
 * <b>提交与查询同窗口。</b>Y 窗口一律是<b>方块 Y</b>（整数），section 换算留给
 * {@link SparkLevel#scheduleChunkLoad} 的包装层。自己换算过一次 {@code >> 4} 不会报错，
 * 只会让一个 section 都不激活。
 * <p>
 * <b>线程</b>：{@link #reserve} 只在主线程调用（{@code ProjectileManager#preTick}，
 * {@code EventPriority.HIGHEST}）；它读的是物理线程发布的可变快照，不碰 SoA 数组。
 */
public final class TerrainPreload {

    /**
     * 就绪提前量 {@code L}：从“预约登记”到“射线看得见”的标称时长。
     * <p>
     * 取冷区块的 3 个游戏 tick：热区块（已加载、不必重试）是 1 个 tick，冷区块多 1~2 个 tick
     * 的重试；正文与预算里单独出现的 {@code L} 一律指这个冷值。最终取值由实测的
     * 构建延迟 p95 反过来定，不靠估算。
     */
    public static final float LEAD_SECONDS = 3f / 20f;

    /** 重算间隔 {@code Δ}：视界每个游戏 tick 推一次。 */
    public static final float REPLAN_SECONDS = 1f / 20f;

    /** 视界余量（m）：只覆盖区块栅格的量化误差，不是时间余量。 */
    public static final float HORIZON_MARGIN_METERS = 16f;

    /** 单个样本的时间跨度上限（s）：与物理步同阶，保证 Y 窗口不会跨住一段弯折。 */
    public static final float SAMPLE_SECONDS = 0.01f;

    /** 单个样本的空间跨度上限（m）：小于半个区块，落格最多 2×2。 */
    public static final float SAMPLE_METERS = 8f;

    /**
     * 单轮最多推进的样本数。
     * <p>
     * 最长的视界是 APFSDS 的 346 m，按 8 m 一步是 44 步。速度很低时（低于约 13 m/s）
     * 样本的时间跨度被 {@link #SAMPLE_SECONDS} 卡住、空间跨度只剩几厘米，64 步覆盖不到视界；
     * 这类弹丸的到达时刻以秒计，远超 {@code L}，不必走满。
     */
    public static final int MAX_SAMPLES = 64;

    /** {@code hold} 的余量（s）：吸收两次重算之间的过期抖动与登记延迟。 */
    public static final float HOLD_MARGIN_SECONDS = 0.1f;

    /** 一段航迹的接收者，由 {@link #walk} 逐段交出 */
    @FunctionalInterface
    public interface SegmentSink {
        /**
         * @param ax          线段起点 X（方块坐标）
         * @param ay          线段起点 Y（方块坐标，未取整）
         * @param az          线段起点 Z（方块坐标）
         * @param bx          线段终点 X
         * @param by          线段终点 Y
         * @param bz          线段终点 Z
         * @param yLo         该线段的 Y 窗口下界（方块 Y，含）
         * @param yHi         该线段的 Y 窗口上界（方块 Y，含）
         * @param leadSeconds 到达线段终点的提前量（s），自本轮行走起点算起
         */
        void accept(float ax, float ay, float az, float bx, float by, float bz,
                    int yLo, int yHi, float leadSeconds);
    }

    /** 一个待提交的区块：Y 窗口取各穿越段的并集，到达时刻取最早 */
    private static final class Window {
        final int chunkX;
        final int chunkZ;
        int yLo = Integer.MAX_VALUE;
        int yHi = Integer.MIN_VALUE;
        float leadSeconds = Float.MAX_VALUE;

        Window(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        void merge(int lo, int hi, float lead) {
            if (lo < yLo) yLo = lo;
            if (hi > yHi) yHi = hi;
            if (lead < leadSeconds) leadSeconds = lead;
        }
    }

    private final Level level;
    private final DensityFunction density;
    /** 单轮内的临时累加器，走完就丢；跨 tick 不保留任何计划 */
    private final Map<Long, Window> windows = new HashMap<>();
    private final List<Window> ordered = new ArrayList<>();

    public TerrainPreload(Level level, DensityFunction density) {
        this.level = level;
        this.density = density;
    }

    /**
     * 视界长度（m）。
     *
     * @param speed 本轮起点速度（m/s）
     */
    public static float horizonMeters(float speed) {
        return speed * (LEAD_SECONDS + REPLAN_SECONDS) + HORIZON_MARGIN_METERS;
    }

    /**
     * 采样步长（s）：取“0.01 s”与“8 m”的较小者。
     *
     * @param speed 本轮起点速度（m/s）
     */
    public static float sampleSeconds(float speed) {
        if (!(speed > 0f)) return SAMPLE_SECONDS;
        return Math.min(SAMPLE_SECONDS, SAMPLE_METERS / speed);
    }

    /**
     * 提前量折算成物理步数，并加余量。
     * <p>
     * 分母是物理步频率 {@code tps}，<b>不是</b>视界行走的采样步长：后者在 {@code v > 800 m/s}
     * 时小于 0.01 s，用错分母会把 {@code hold} 放大 {@code 0.01/dt} 倍（APFSDS 上两倍以上）。
     *
     * @param leadSeconds 到达提前量（s）
     * @param tps         物理步频率（服务端 100，客户端 60）
     * @return 物理步数，恒为正
     */
    public static int holdSteps(float leadSeconds, int tps) {
        int margin = Math.round(HOLD_MARGIN_SECONDS * tps);
        float lead = Math.max(0f, leadSeconds);
        return (int) Math.ceil(lead * tps) + margin;
    }

    /**
     * 从给定状态出发走一遍视界，逐段交给 {@code sink}。
     * <p>
     * 开火瞬间的首段预约与每 tick 的重算走的是同一份实现（{@code Δ = 1/20 s} 时两者的深度
     * {@code W₀} 与 {@code W} 相等），只差调用时刻，因此不存在“首段与视界分家”的可能。
     * <p>
     * 制导的指令加速度不在行走内：预约覆盖的是整个提前期内的可达集，那一份余量并进外扩半径
     * （见 {@code BallisticProjectileType#getPreloadSweepRadius}）。
     *
     * @param cfg       弹道参数
     * @param thrust    推力剖面
     * @param density   空气密度
     * @param px        起点 X（世界坐标，方块）
     * @param py        起点 Y
     * @param pz        起点 Z
     * @param vx        起点速度 X（m/s）
     * @param vy        起点速度 Y
     * @param vz        起点速度 Z
     * @param burnTime  起点已燃烧时长（s）
     * @param radius    外扩半径（方块）
     * @param sink      逐段接收者
     */
    public static void walk(BallisticConfig cfg, ThrustProfile thrust, DensityFunction density,
                            float px, float py, float pz,
                            float vx, float vy, float vz,
                            float burnTime, float radius, SegmentSink sink) {
        float speed = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        float horizon = horizonMeters(speed);
        float dt = sampleSeconds(speed);
        float lead = 0f;
        float travelled = 0f;
        float[] out = new float[TrajectoryStep.OUT_LENGTH];

        for (int k = 0; k < MAX_SAMPLES && travelled < horizon; k++) {
            TrajectoryStep.step(px, py, pz, vx, vy, vz, burnTime + lead,
                    cfg, thrust, density, 0f, 0f, 0f, dt, out);
            float nx = out[TrajectoryStep.OUT_PX];
            float ny = out[TrajectoryStep.OUT_PY];
            float nz = out[TrajectoryStep.OUT_PZ];
            float nvx = out[TrajectoryStep.OUT_VX];
            float nvy = out[TrajectoryStep.OUT_VY];
            float nvz = out[TrajectoryStep.OUT_VZ];

            int yLo = (int) Math.floor(Math.min(py, ny) - radius);
            int yHi = (int) Math.ceil(Math.max(py, ny) + radius);

            lead += dt;
            sink.accept(px, py, pz, nx, ny, nz, yLo, yHi, lead);

            float dx = nx - px;
            float dy = ny - py;
            float dz = nz - pz;
            travelled += (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

            px = nx;
            py = ny;
            pz = nz;
            vx = nvx;
            vy = nvy;
            vz = nvz;
        }
    }

    /**
     * 走一遍视界并提交预约。开火瞬间的首段与每 tick 的重算都调这一个入口。
     *
     * @param cfg       弹道参数
     * @param thrust    推力剖面
     * @param radius    外扩半径（方块），含制导可达量
     * @param tps       物理步频率，用于折算 {@code hold}
     * @param px        起点 X
     * @param py        起点 Y
     * @param pz        起点 Z
     * @param vx        起点速度 X（m/s）
     * @param vy        起点速度 Y
     * @param vz        起点速度 Z
     * @param burnTime  起点已燃烧时长（s）
     */
    public void reserve(BallisticConfig cfg, ThrustProfile thrust, float radius, int tps,
                        float px, float py, float pz,
                        float vx, float vy, float vz, float burnTime) {
        windows.clear();
        ordered.clear();
        try {
            walk(cfg, thrust, density, px, py, pz, vx, vy, vz, burnTime, radius,
                    (ax, ay, az, bx, by, bz, yLo, yHi, lead) ->
                            submitSegment(radius, ax, az, bx, bz, yLo, yHi, lead));
            flush(tps);
        } finally {
            windows.clear();
            ordered.clear();
        }
    }

    /**
     * 一个线段的落格与高程过滤。三态在这里分流：无索引跳过、确定空中跳过、有交集并入窗口。
     */
    private void submitSegment(float radius, float ax, float az, float bx, float bz,
                               int yLo, int yHi, float leadSeconds) {
        int minBuildHeight = level.getMinBuildHeight();
        int maxBuildHeightExclusive = level.getMaxBuildHeight();
        // 越界样本取“跳过”而不是终止：世界上下界之外没有地形，整条视界继续
        if (yHi < minBuildHeight || yLo >= maxBuildHeightExclusive) return;

        ChunkCoverage.cellsCrossedBySegment(ax, az, bx, bz, radius, (chunkX, chunkZ) -> {
            ChunkPos cp = new ChunkPos(chunkX, chunkZ);
            short[] intervals = SparkLevel.getSolidIntervals(level, cp);
            if (intervals == null) return;                       // 无索引：不预约，不为它触发生成
            int[] hit = ElevationFilter.intersect(intervals, yLo, yHi);
            if (hit == null) return;                             // 有索引但确定空中：不预约
            windows.computeIfAbsent(cp.toLong(), k -> new Window(chunkX, chunkZ))
                    .merge(hit[0], hit[1], leadSeconds);
        });
    }

    /**
     * 按到达时刻由近到远提交。
     * <p>
     * 这只决定同一个游戏 tick 内的登记次序，与构建顺序无关——{@code pendingBuildRequests}
     * 是并发映射、消费时还要再复制一遍，沿航迹的调用顺序不会变成构建顺序。
     */
    private void flush(int tps) {
        if (windows.isEmpty()) return;

        ordered.addAll(windows.values());
        ordered.sort(Comparator.comparingDouble(w -> w.leadSeconds));

        for (Window w : ordered) {
            SparkLevel.scheduleChunkLoad(level, new ChunkPos(w.chunkX, w.chunkZ),
                    w.yLo, w.yHi, 0, holdSteps(w.leadSeconds, tps));
        }
    }
}
