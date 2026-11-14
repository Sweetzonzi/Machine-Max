package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier.SupplierRef;

/**
 * 一份装填作业：某台 {@link LauncherSubsystem} 当前正在取的那一份交付。
 * <p>
 * <b>归属：</b>进度随消费者的当前作业，供给者只回答"放行条件"与"本 tick 可推进多少"
 * （{@link IAmmoSupplier#getAdvanceTicks(IAmmoConsumer)}），不另持一份针对同次操作的计时。
 * 一台管同一时刻只有一个作业，因此进度、总时长与交付目标各是一个值。
 * <p>
 * <b>身份：</b>{@link #key()} 由持有它的 Launcher 在受理时分配，{@code jobId} 自增。机构占用、实际架弹药
 * 预留与独占机构的槽位拥有者都以这个身份为键：释放、交付与结束只对身份完全匹配的作业生效，因此旧作业的
 * 回调不会摘掉同一条输弹线上的新作业。作业不跨存档延续，读档后重新受理会拿到新的编号。
 * <p>
 * <b>分配在受理时冻结：</b>{@link #deliveryTarget()}（交出这一份的机器）、{@link #upstreamOrigin()}
 * （机构为本次作业挑中的上游弹药架，纯人工直取路径为空）、{@link #spec()}（交付规格）与
 * {@link #reservedCount()}（整次作业的弹药预留量）都在 {@link IAmmoSupplier#claimFor} 受理那一刻确定。
 * 此后供给者的交付规格、库存或来源列表变化都不改写这几个值：换架或换规格要作废旧作业并重新申请，
 * 不能把已受理的作业静默换成另一份。
 * <p>
 * <b>两个阶段：</b>{@link Stage#HUMAN}（人工准备，由乘员推进）→ {@link Stage#MACHINE}（机械运行，由机构推进）。
 * 没有人工段的流程从一开始就处于机械段。阶段切换重置进度、保留分配；{@link #totalTicks()} 是<b>当前阶段</b>
 * 的实际时长，由服务端发布，离散变化（阶段切换、换人）时与进度基线一起重发，稳定运行期间不逐 tick 更新。
 * <p>
 * <b>进度是坐标而不是计时器读数：</b>{@link #elapsedTicks()} 与 {@link #totalTicks()} 的关系给出完成比例与
 * 剩余量，两端各自每 tick 推进 1（人工段的倍率已经折进时长里）。因此一次换人只需要把剩余量按新速率换算、
 * 并按原比例缩放进度坐标——见 {@link #retimeHuman(double)}：人变慢时进度条不会倒退，剩余时间如实变长。
 * <p>
 * <b>不存档：</b>未完成的装填读档后由服务端重新发起，作业不跨存档延续。
 */
public final class ReloadJob {

    /**
     * 装填阶段。<br>
     * 固定顺序是"人工准备 → 机械运行"：纯机械流程只有后者，人工段的乘员在段完成时释放，机械段的槽位在
     * 进入该段时才申请（见 {@link IAmmoSupplier#claimFor}）。
     */
    public enum Stage {
        /** 人工准备：由乘员从来源取弹、放上托盘；岗位倍率与个人能力折进本阶段时长 */
        HUMAN,
        /** 机械运行：由装弹机构放行与推进 */
        MACHINE
    }

    /** 作业身份：宿主 SubPart ID + 发射器名 + 编号 */
    private final ReloadJobKey key;

    /** 本次交付的目标：交出这一份的供给者（机构路径是机构，纯人工直取路径是弹药架） */
    private final SupplierRef deliveryTarget;

    /** 本次交付的实际来源弹药架；纯人工直取路径与 {@link #deliveryTarget} 相同，机构路径为上游架 */
    private final SupplierRef upstreamOrigin;

    /** 本次交付的冻结规格：内容、长度与回充配置在受理时定下，交付实例只可能比它少发 */
    private final AmmoBelt spec;

    /** 整次作业的弹药预留量 {@code q}：实际架已按身份扣着这个数量，交付与撤销都以它为准 */
    private final int reservedCount;

    /** 当前阶段 */
    private Stage stage;

    /**
     * 本阶段的实际时长（tick）。<br>
     * 开工时由服务端按流程解析（人工段是 {@code (H + C) × P × A}，机械段是机构的 {@code reload_time}）；
     * 换人使后续速度变化时重发一个新值，进度坐标按 {@link #retimeHuman(double)} 换算，比例不倒退。
     */
    private int totalTicks;

    /** 进度坐标：与 {@link #totalTicks} 一起给出完成比例，最大等于它。换人重定时会整体缩放，不等同于经过的 tick 数 */
    private int elapsedTicks;

    /**
     * 连续推不动的 tick 数：本 tick 推进量为 0 时自增，推进量大于 0 时清零。<br>
     * 暂停是"承诺还在但推不动"（机构受阻、供能不足、没有可用执行者），与"等重试"（还没有作业）不同——
     * 后者没有作业对象，也就没有这个计数。超时判定在持有作业的一方（见
     * {@code LauncherSubsystem#tickReload()}），本类只如实记录。
     */
    private int pausedTicks;

    /**
     * 人工段当前的时间倍率（岗位倍率 {@code P} × 个人倍率 {@code A}）。<br>
     * 只用来把"剩余标准人力时间"换算成实际剩余 tick：1 表示标准人力（也就没有人工段剩余量要换算）。
     * 机械段恒为 1——机构的时间不乘人员倍率。服务端在换人时更新它；客户端只跟随同步过来的时长，不读这个值。
     */
    private double humanRate;

    /**
     * @param key             作业身份
     * @param deliveryTarget  交付目标
     * @param upstreamOrigin  实际来源（机构路径为上游架）
     * @param spec            冻结规格
     * @param reservedCount   预留份额
     * @param stage           起始阶段
     * @param totalTicks      起始阶段的实际时长（tick，至少 1）
     * @param humanRate       人工段的时间倍率（机械段传 1）
     */
    public ReloadJob(ReloadJobKey key, SupplierRef deliveryTarget, SupplierRef upstreamOrigin,
                     AmmoBelt spec, int reservedCount, Stage stage, int totalTicks, double humanRate) {
        this.key = key;
        this.deliveryTarget = deliveryTarget;
        this.upstreamOrigin = upstreamOrigin;
        this.spec = spec;
        this.reservedCount = Math.max(0, reservedCount);
        this.stage = stage;
        this.totalTicks = Math.max(1, totalTicks);
        this.humanRate = humanRate > 0 && Double.isFinite(humanRate) ? humanRate : 1.0;
    }

    /** 只有机械段的作业：从一开始就处于机械段。 */
    public ReloadJob(ReloadJobKey key, SupplierRef deliveryTarget, SupplierRef upstreamOrigin,
                     AmmoBelt spec, int reservedCount, int totalTicks) {
        this(key, deliveryTarget, upstreamOrigin, spec, reservedCount, Stage.MACHINE, totalTicks, 1.0);
    }

    /** 作业身份；机构占用与架预留都以它为准 */
    public ReloadJobKey key() {
        return key;
    }

    /** 作业编号，是 {@link #key()} 的第三项 */
    public int jobId() {
        return key.jobId();
    }

    /** 本次交付的目标供给者身份 */
    public SupplierRef deliveryTarget() {
        return deliveryTarget;
    }

    /**
     * 本次交付的实际来源弹药架身份。<br>
     * 机构路径下由机构在受理时挑定，纯人工直取路径下与 {@link #deliveryTarget()} 相同。
     */
    public SupplierRef upstreamOrigin() {
        return upstreamOrigin;
    }

    /** 本次交付的冻结规格 */
    public AmmoBelt spec() {
        return spec;
    }

    /** 整次作业的弹药预留量；实际架的可分配库存已按它扣减 */
    public int reservedCount() {
        return reservedCount;
    }

    /** 当前阶段 */
    public Stage stage() {
        return stage;
    }

    /** 人工段当前的时间倍率；机械段为 1 */
    public double humanRate() {
        return humanRate;
    }

    /** 本阶段的实际时长（tick） */
    public int totalTicks() {
        return totalTicks;
    }

    /** 进度坐标（见类文档；与 {@link #totalTicks()} 一起给出完成比例） */
    public int elapsedTicks() {
        return elapsedTicks;
    }

    /** 连续推不动的 tick 数；任何一次有效推进都把它清零 */
    public int pausedTicks() {
        return pausedTicks;
    }

    /** 距离本阶段完成还剩多少 tick */
    public int remainingTicks() {
        return Math.max(0, totalTicks - elapsedTicks);
    }

    /** 已完成比例（0~1） */
    public float progress() {
        return Math.clamp((float) elapsedTicks / totalTicks, 0f, 1f);
    }

    /** 本阶段是否已走完 */
    public boolean isComplete() {
        return elapsedTicks >= totalTicks;
    }

    /**
     * 推进本阶段的时间。推进量由执行者给出，作业只负责累计。
     *
     * @param ticks 本 tick 可推进的 tick 数；{@code <= 0} 表示本 tick 推不动（缺弹、机构受阻、没有执行者），
     *              记一次暂停
     */
    public void advance(int ticks) {
        if (ticks <= 0) {
            pausedTicks++;
            return;
        }
        pausedTicks = 0;
        elapsedTicks = Math.min(totalTicks, elapsedTicks + ticks);
    }

    /**
     * 把进度对齐到权威基线：只向前，不倒退，不计暂停。<br>
     * 与 {@link #advance(int)} 分开是因为两者含义不同——那条路表达"这一 tick 走了多久"，
     * 这条路表达"权威说进度到这里了"（作业开始、离散重建、全量补推）。基线可能比本地进度小
     * （客户端已经跑在前面），此时保持本地进度；它不是一次零推进，因此不增加暂停计数。
     *
     * @param elapsed 权威基线（进度坐标）
     */
    public void advanceTo(int elapsed) {
        elapsedTicks = Math.clamp(elapsed, elapsedTicks, totalTicks);
    }

    /**
     * 进入机械段：进度从 0 开始，人工段的倍率不再参与。<br>
     * 由 Launcher 在人工段完成、并且已经取得机构槽位之后调用，随后重发阶段与时长（见
     * {@code LauncherSubsystem#pushReloadJob()}）。
     *
     * @param machineTicks 机械段实际时长，即机构的 {@link IAmmoSupplier#getReloadTimeTicks()}
     */
    public void enterMachineStage(int machineTicks) {
        stage = Stage.MACHINE;
        totalTicks = Math.max(1, machineTicks);
        elapsedTicks = 0;
        pausedTicks = 0;
        humanRate = 1.0;
    }

    /**
     * 人工段换人后的重定时：剩余量按新倍率换算，进度比例保持不变。<br>
     * 人变快时剩余 tick 变少，剩下的进度比例仍然接着走；人变慢时剩余 tick 变多，进度条<b>不会</b>倒退
     * ——为此进度坐标与总时长一起缩放（比例不变，两者同乘一个系数），因此 {@link #elapsedTicks()} 在换人
     * 前后不是同一把尺子，只有它与 {@link #totalTicks()} 的比值可以跨换人比较。
     * <p>新倍率不是有限正数时不处理：作业保持原速率，由调用方决定是否换人。
     *
     * @param newRate 新的人工段时间倍率（岗位倍率 × 个人倍率）
     * @return true 表示时长被改写，调用方需要重发一次时长与进度基线
     */
    public boolean retimeHuman(double newRate) {
        if (stage != Stage.HUMAN || !Double.isFinite(newRate) || newRate <= 0) return false;
        if (humanRate == newRate) return false;
        // 剩余标准人力（不乘倍率）折算成新倍率下的实际 tick：倍率是时间乘数，越大越慢
        int remaining = Math.max(1, (int) Math.ceil((totalTicks - elapsedTicks) * newRate / humanRate));
        humanRate = newRate;
        if (elapsedTicks <= 0) {
            // 还没动：比例是 0，只剩一个剩余量要换
            totalTicks = remaining;
            return true;
        }
        if (elapsedTicks >= totalTicks) {
            // 已经走完：没有剩余量可换算，进度保持在 100%
            return true;
        }
        double ratio = (double) elapsedTicks / totalTicks;
        int scaledElapsed = (int) Math.round(ratio * remaining / (1.0 - ratio));
        scaledElapsed = Math.max(0, Math.min(scaledElapsed, Integer.MAX_VALUE - remaining));
        elapsedTicks = scaledElapsed;
        totalTicks = scaledElapsed + remaining;
        return true;
    }

    /**
     * 客户端按同步字段对齐本端作业。<br>
     * 阶段变了（人工段 → 机械段）或者时长变了（换人重定时）都换了坐标系：按基线重设进度，那不是倒退——
     * 基线是服务端按同一比例缩放过的量，重设之后完成比例保持不变。其余情况（同一阶段、同一时长，
     * 例如完整补推带来的一条基线）只向前对齐，不让进度倒退。
     * <p>总时长不小于 1；基线超出时长时钳在时长上，因此一次坏值不会让完成比例超过 100%。
     *
     * @param stage    权威阶段
     * @param total    权威的本阶段实际时长；{@code <= 0} 表示这一批字段还没到齐，保持现状
     * @param baseline 权威进度基线
     */
    public void alignRemote(Stage stage, int total, int baseline) {
        if (stage != this.stage || (total > 0 && total != totalTicks)) {
            this.stage = stage;
            this.totalTicks = Math.max(1, total > 0 ? total : totalTicks);
            this.elapsedTicks = Math.clamp(baseline, 0, this.totalTicks);
            this.pausedTicks = 0;
            if (stage == Stage.MACHINE) this.humanRate = 1.0;
            return;
        }
        advanceTo(Math.max(0, baseline));
    }
}
