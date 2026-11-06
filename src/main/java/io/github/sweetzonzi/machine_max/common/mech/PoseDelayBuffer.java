package io.github.sweetzonzi.machine_max.common.mech;

import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import org.jetbrains.annotations.NotNull;

/**
 * 网络位姿的抖动缓冲：<b>按包入队、按客户端 tick 播放、固定落后最新样本若干份</b>。
 *
 * <h3>解决的问题</h3>
 * 服务端按 20 Hz 发位姿、客户端按 20 Hz 走 tick，两者相位不同步：某些 tick 里一个包都没有、
 * 另一些 tick 里挤进两个。渲染的插值量只有 {@code partialTick ∈ [0, 1)}，它天然假设
 * 「相邻两个样本恰好跨一个 tick」，于是把样本对按<b>到达事件</b>重建的写法会出现两种帧：
 * <ul>
 *   <li>本 tick 没新包 → 样对压扁（两端相等）→ 画面冻结一帧；</li>
 *   <li>下一 tick 补进两个包 → 样对跨两个样本 → 画面速度翻倍。</li>
 * </ul>
 * 两者数量一比一守恒，看着就是周期性的一顿一窜。
 *
 * <h3>办法</h3>
 * 显示游标（{@link #advance()}）<b>每客户端 tick 前进一个样本</b>，与「包到没到」无关；
 * 包里当前有多少余量只影响它能撑多久。余量就是可吸收的抖动，代价是固定的呈现延迟：
 * 呈现位姿落后最新样本 {@code spare} 到 {@code spare + 1} 个 tick（{@code partialTick} 在区间内滑动）。
 *
 * <ul>
 *   <li>{@code spare = 0}：落后 0～1 tick（与不缓冲相同），一遇空 tick 就停，恢复时位置跳变。</li>
 *   <li>{@code spare = 1}：落后 1～2 tick，吃掉<b>单次</b>空 tick；单 tick 补两个包时不加速。</li>
 *   <li>{@code spare = 2}：落后 2～3 tick，吃掉连续两次空 tick。</li>
 * </ul>
 *
 * <h3>三条不变量</h3>
 * <ol>
 *   <li><b>{@link #left()} 与 {@link #right()} 永远是两个相邻的样本</b>（停住时是同一个样本），
 *       所以调用方的 {@code lerp(left, right, partialTick)} 始终有确切含义；</li>
 *   <li><b>不倒退</b>：余量耗尽时停在最新样本上（位置连续、速度为 0），恢复后从该点继续；</li>
 *   <li><b>不累积延迟</b>：余量超出 {@code spare} 一定量时丢掉多余的样本往前跳，
 *       延迟只由 {@code spare} 决定。</li>
 * </ol>
 *
 * <h3>线程</h3>
 * 不持锁、不做同步：<b>只在客户端主线程调用</b>（{@link #push} 在同步包应用时，
 * {@link #advance} 在每客户端 tick，两个时刻都在主线程）。
 *
 * <h3>样本从哪来</h3>
 * 一个同步包一个样本（服务端每个 tick 至多一次 {@code packDirty()}），因此样本序号就是
 * 服务端 tick 序号的镜像。{@link #push} 必须<b>每个包恰好调用一次</b>，且要在整批字段都已应用
 * 之后（{@code SynchedEntityData#assignValues} 末尾的整批回调就是这个时刻）。
 *
 * @author Sweetzonzi
 */
public final class PoseDelayBuffer {

    /** 判定传送的默认位移阈值 (m)：超过它说明不是连续运动，历史样本全部作废 */
    public static final double DEFAULT_SNAP_DISTANCE = 8.0;

    /** 环形槽数，必须是 2 的幂 */
    private static final int CAPACITY = 16;

    private static final int MASK = CAPACITY - 1;

    /**
     * 余量超出 {@code spare} 多少才丢样本。
     * <p>
     * 不为 0 是为了容忍抖动本身：一个 tick 补进两个包会让余量 +1，这是正常现象，
     * 不该被当成「追不上服务端」而丢样本。只有连续两个 tick 都在补包才动手。
     */
    private static final int SKIP_SLACK = 2;

    /** 环形槽，预分配后不再分配 */
    private final Transform[] samples = new Transform[CAPACITY];

    /** 稳态余量（样本数） */
    private final int spare;

    /** 传送判定阈值 (m) */
    private final double snapDistance;

    /** 下一个样本的绝对序号，同时也是已入队样本数 */
    private long nextIn;

    /** 显示区间左端的绝对序号；{@code -1} 表示还没建立 */
    private long cursor = -1L;

    /** 本次 {@link #advance()} 是否让显示停住（只有一份样本、或余量耗尽） */
    private boolean holding = true;

    /** 累计停住的 tick 数，供遥测判断余量是否够用 */
    private long starvedTicks;

    /** 累计被丢掉的样本数，供遥测判断延迟是否被追上 */
    private long skippedSamples;

    /**
     * @param spare        稳态余量（样本数），{@code >= 0}；见类注释的三档取舍
     * @param snapDistance 传送判定阈值 (m)
     */
    public PoseDelayBuffer(int spare, double snapDistance) {
        if (spare < 0) {
            throw new IllegalArgumentException("spare 不能为负：" + spare);
        }
        this.spare = spare;
        this.snapDistance = snapDistance;
        for (int i = 0; i < CAPACITY; i++) {
            this.samples[i] = new Transform();
        }
    }

    // ==========================================
    // 入队（每个同步包一次）
    // ==========================================

    /**
     * 收下一个样本。
     * <p>
     * 与上一个样本的位移超过 {@link #snapDistance} 时按传送处理：历史全部作废、缓冲重建，
     * 显示随即落到新位置并静止，直到攒够余量才继续插值。传送 / 换维度因此不会拖出一条
     * 跨越地图的尾巴。
     *
     * @param position 位姿平移（会复制，调用方可以复用自己的缓冲）
     * @param rotation 位姿旋转（会复制）
     */
    public void push(@NotNull Vector3f position, @NotNull Quaternion rotation) {
        long newest = this.nextIn - 1L;
        if (newest >= 0L && position.distance(this.samples[index(newest)].getTranslation()) > this.snapDistance) {
            this.nextIn = 0L;
            this.cursor = -1L;
            this.holding = true;
        }
        // Transform 的 setTranslation / setRotation 内部是 set 语义，不会与调用方的对象共享
        this.samples[index(this.nextIn)]
                .setTranslation(position)
                .setRotation(rotation);
        this.nextIn++;
    }

    // ==========================================
    // 播放（每个客户端 tick 一次）
    // ==========================================

    /**
     * 把一个客户端 tick 的时间推进到显示上。
     * <p>
     * 与到达事件完全无关：游标每次前进一个样本，余量只决定它能不能一直走。
     *
     * @return 游标是否前进（{@code false} 表示本次停在最新样本上）
     */
    public boolean advance() {
        long newest = this.nextIn - 1L;
        if (newest < 0L) {
            // 一份样本都没有：保持在出生位姿上，什么都不动
            this.holding = true;
            return false;
        }
        if (this.cursor < 0L) {
            // 样本还不够建立余量：停在<b>最新</b>样本上（左右两端同一个样本，显示静止但零延迟）。
            // 取最新而不是首样本，是为了在入世头两个 tick 里不做一次「先落后再追上」的肉眼可见停顿
            if (newest < this.spare + 1L) {
                this.holding = true;
                return false;
            }
            this.cursor = newest - 1L - this.spare;
            this.holding = false;
            return true;
        }
        // 余量 = 除了「当前区间右端」之外还留着几个样本
        long reserve = newest - 1L - this.cursor;
        if (reserve <= 0L) {
            // 区间右端已经是最新样本：这一 tick 没有可用的新样本，停住而不是倒退
            this.cursor = newest - 1L;
            this.holding = true;
            this.starvedTicks++;
            return false;
        }
        long reserveAfterAdvance = reserve - 1L;
        if (reserveAfterAdvance > this.spare + SKIP_SLACK) {
            // 积压过多：丢掉多余样本，把延迟限回 spare
            this.skippedSamples += reserveAfterAdvance - this.spare;
            this.cursor = newest - 1L - this.spare;
            this.holding = false;
            return true;
        }
        this.cursor++;
        this.holding = false;
        return true;
    }

    // ==========================================
    // 查询
    // ==========================================

    /**
     * 呈现区间的左端样本（只读）。
     * <p>
     * 返回的是缓冲内部的槽，<b>下一次 {@link #push} 或 {@link #advance} 会改写它</b>；
     * 需要跨 tick 保留必须自行复制。
     */
    @NotNull
    public Transform left() {
        return this.holding ? this.samples[index(this.nextIn - 1L)] : this.samples[index(this.cursor)];
    }

    /** 呈现区间的右端样本（只读，约束同 {@link #left()}）。 */
    @NotNull
    public Transform right() {
        return this.holding ? this.samples[index(this.nextIn - 1L)] : this.samples[index(this.cursor + 1L)];
    }

    /** 是否已经有至少一个样本。 */
    public boolean hasSample() {
        return this.nextIn > 0L;
    }

    /** 是否已经在插值（{@code false} 表示停在某个样本上）。 */
    public boolean isInterpolating() {
        return !this.holding && this.cursor >= 0L;
    }

    /** 最新样本的绝对序号；没有样本时为 {@code -1}。 */
    public long newestIndex() {
        return this.nextIn - 1L;
    }

    /** 显示区间左端的绝对序号；还没建立时为 {@code -1}。 */
    public long cursorIndex() {
        return this.cursor;
    }

    /** 当前余量（样本数）：{@code 0} 表示下一 tick 就会停住。 */
    public long reserve() {
        long newest = this.nextIn - 1L;
        if (newest < 0L || this.cursor < 0L) return 0L;
        return Math.max(0L, newest - 1L - this.cursor);
    }

    /** 稳态余量配置。 */
    public int spare() {
        return this.spare;
    }

    /** 累计停住的 tick 数。 */
    public long starvedTicks() {
        return this.starvedTicks;
    }

    /** 累计丢掉的样本数（延迟被追回的次数与幅度）。 */
    public long skippedSamples() {
        return this.skippedSamples;
    }

    /** 清空历史（换维度 / 注销 / 手工重同步），累计计数保留。 */
    public void reset() {
        this.nextIn = 0L;
        this.cursor = -1L;
        this.holding = true;
    }

    private static int index(long absolute) {
        return (int) (absolute & MASK);
    }
}
