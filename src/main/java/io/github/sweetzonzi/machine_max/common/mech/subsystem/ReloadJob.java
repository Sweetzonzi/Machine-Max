package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;


/**
 * 一份装填作业：某台 {@link LauncherSubsystem} 当前正在取的那一份交付。
 * <p>
 * <b>归属：</b>进度随接收者的当前作业，运输者只回答"执行资源"与"本 tick 可推进多少"
 * （{@link IAmmoTransporter#getAdvanceTicks(IAmmoConsumer)}），不另持一份针对同次操作的计时。
 * 一台管同一时刻只有一个作业，因此进度、总时长与这条路上的一对身份各是一个值。
 * <p>
 * <b>身份：</b>{@link #key()} 由持有它的 Launcher 在受理时分配，{@code jobId} 自增。运输者的占用、提供者的
 * 弹药预留与独占运输者的槽位拥有者都以这个身份为键：释放、交付与结束只对身份完全匹配的作业生效，因此旧作业的
 * 回调不会摘掉同一条输弹线上的新作业。作业不跨存档延续，读档后重新受理会拿到新的编号。
 * <p>
 * <b>分配在受理时冻结：</b>{@link #transporter()}（本段的执行者）、{@link #provider()}（这批弹出自哪台架）、
 * {@link #spec()}（交付规格）与 {@link #reservedCount()}（整次作业的弹药预留量）都在受理那一刻确定。
 * 此后提供者的交付规格、库存或来源列表变化都不改写这几个值：换架或换规格要作废旧作业并重新申请，
 * 不能把已受理的作业静默换成另一份。
 * <p>
 * <b>两个阶段：</b>{@link Stage#HUMAN}（人工准备，由乘员推进）→ {@link Stage#MACHINE}（机械运行，由机构推进）。
 * 没有人工段的流程从一开始就处于机械段。阶段切换重置进度、保留分配；{@link #totalTicks()} 是<b>当前阶段</b>
 * 的实际时长，由服务端发布，离散变化（阶段切换、换人）时与进度基线一起重发，稳定运行期间不逐 tick 更新。
 * <p>
 * <b>时长为 0 是一个正常值：</b>没有机械段的机构不申报时长，作业在受理的那一 tick 里推进、走完并兑现，
 * 完成比例恒为 100%（见 {@link #progress()}）。这描述的是"没有可等的机构动作"，而不是"字段还没到"。
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
     * 进入该段时才申请（见 {@link IAmmoTransporter#claimForMachineStage}）。
     */
    public enum Stage {
        /** 人工准备：由乘员从来源取弹、放上托盘；岗位倍率与个人能力折进本阶段时长 */
        HUMAN,
        /** 机械运行：由装弹机构放行与推进 */
        MACHINE
    }

    /** 作业身份：宿主 SubPart ID + 发射器序号 + 编号 */
    private final ReloadJobKey key;

    /** 本次运输的执行者；{@link AmmoRef#NONE} = 人力运输者（本管自己的装填手） */
    private final AmmoRef transporter;

    /** 本次交付的弹药来源。机构路径是装弹机的上游架，人力直取路径是本管自己发现到的架 */
    private final AmmoRef provider;

    /** 本次交付的冻结规格：内容、长度与回充配置在受理时定下，交付实例只可能比它少发 */
    private final AmmoBelt spec;

    /** 整次作业的弹药预留量 {@code q}：实际架已按身份扣着这个数量，交付与撤销都以它为准 */
    private final int reservedCount;

    /** 当前阶段 */
    private Stage stage;

    /**
     * 本阶段的实际时长（tick），0 表示本阶段没有可等的动作。<br>
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
     * @param transporter     本次运输的运输者身份
     * @param provider        本次交付的提供者身份
     * @param spec            冻结规格
     * @param reservedCount   预留份额
     * @param stage           起始阶段
     * @param totalTicks      起始阶段的实际时长（tick，0 表示没有可等的动作，作业立即完成）
     * @param humanRate       人工段的时间倍率（机械段传 1）
     */
    public ReloadJob(ReloadJobKey key, AmmoRef transporter, AmmoRef provider,
                     AmmoBelt spec, int reservedCount, Stage stage, int totalTicks, double humanRate) {
        this.key = key;
        this.transporter = transporter;
        this.provider = provider;
        this.spec = spec;
        this.reservedCount = Math.max(0, reservedCount);
        this.stage = stage;
        this.totalTicks = Math.max(0, totalTicks);
        this.humanRate = humanRate > 0 && Double.isFinite(humanRate) ? humanRate : 1.0;
    }

    /** 只有机械段的作业：从一开始就处于机械段。 */
    public ReloadJob(ReloadJobKey key, AmmoRef transporter, AmmoRef provider,
                     AmmoBelt spec, int reservedCount, int totalTicks) {
        this(key, transporter, provider, spec, reservedCount, Stage.MACHINE, totalTicks, 1.0);
    }

    /** 作业身份；机构占用与架预留都以它为准 */
    public ReloadJobKey key() {
        return key;
    }

    /** 作业编号，是 {@link #key()} 的第三项 */
    public int jobId() {
        return key.jobId();
    }

    /** 本次运输的运输者身份；{@link AmmoRef#NONE} = 人力运输者 */
    public AmmoRef transporter() {
        return transporter;
    }

    /** 本次交付的提供者身份；{@link AmmoRef#NONE} 只应出现在没有作业的情况下，作业里恒有提供者 */
    public AmmoRef provider() {
        return provider;
    }

    /** 本次交付的冻结规格 */
    public AmmoBelt spec() {
        return spec;
    }

    /** 整次作业的弹药预留量；实际架的可分配库存已按它扣减 */
    public int reservedCount() {
        return reservedCount;
    }

    /**
     * 本作业的冻结分配是否与一份权威样本一致：编号、运输者、提供者、冻结规格与预留份额。
     *
     * <p>用途是区分"同一条作业又收到一次更新"与"两条不同的作业碰巧用了同一个编号"。后者必须按分叉处理：
     * 沿用本地进度会让本端按另一份规格继续推进，交付内容也与权威不同。规格比较走
     * {@link AmmoBelt#sameDeliverySpec(AmmoBelt)}，比到回充配置本身；进度、本阶段时长与阶段不参与——
     * 它们在一条作业的生命周期里本来就会变。</p>
     *
     * @param snapshot 权威样本
     * @return true 表示这是同一条作业的离散更新
     */
    public boolean allocationMatches(ReloadJobSpec snapshot) {
        return jobId() == snapshot.jobId()
                && transporter.equals(snapshot.transporter())
                && provider.equals(snapshot.provider())
                && reservedCount == snapshot.reserved()
                && spec.sameDeliverySpec(snapshot.spec());
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

    /**
     * 已完成比例（0~1）。<br>
     * 本阶段时长为 0 时恒为 1：没有要等的人或机构动作，作业在受理的那一 tick 里走完，读进度的一方
     * 因此不必为零分母另设分支。
     */
    public float progress() {
        if (totalTicks <= 0) return 1f;
        return Math.clamp((float) elapsedTicks / totalTicks, 0f, 1f);
    }

    /** 本阶段是否已走完；时长为 0 的作业从一开始就是走完的 */
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
     * @param machineTicks 机械段实际时长，即机构的 {@link IAmmoSupplier#getReloadTimeTicks()}；0 表示没有机械段
     */
    public void enterMachineStage(int machineTicks) {
        stage = Stage.MACHINE;
        totalTicks = Math.max(0, machineTicks);
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
     * <p>总时长不小于 0，基线超出时长时钳在时长上，因此一次坏值不会让完成比例超过 100%。
     *
     * @param stage    权威阶段
     * @param total    权威的本阶段实际时长，0 表示这一阶段没有可等的动作
     * @param baseline 权威进度基线
     */
    public void alignRemote(Stage stage, int total, int baseline) {
        int totalTicks = Math.max(0, total);
        if (stage != this.stage || totalTicks != this.totalTicks) {
            this.stage = stage;
            this.totalTicks = totalTicks;
            this.elapsedTicks = Math.clamp(baseline, 0, this.totalTicks);
            this.pausedTicks = 0;
            if (stage == Stage.MACHINE) this.humanRate = 1.0;
            return;
        }
        advanceTo(Math.max(0, baseline));
    }
}
