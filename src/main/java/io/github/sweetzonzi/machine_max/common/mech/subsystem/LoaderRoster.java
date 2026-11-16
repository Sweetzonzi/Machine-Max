package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.attachment.CrewReloadAttachment;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 一台装填需求方的"人工段用人"：查询到的座位，以及此刻在为哪条作业派谁。<br>
 * 它同时是<b>人力运输者</b>（{@link IAmmoTransporter}）：整条运输由一名乘员完成，没有机械段、没有喂弹线、
 * 不占机械槽，{@code isHuman()} 恒为真。目前有两台这样的机器——自动装弹机（人工准备 → 机械运行）与声明了
 * 完整人力流程的发射器（人力直取：从弹药架直接取弹、全程由乘员装填）。两者用同一份实现，选人口径因此只有
 * 一处。
 * <p>
 * <b>三个量分开：</b>座位表是只读发现结果（结构变化时整体重建）、认领是"此刻替哪条作业干活"（按作业身份
 * 写入与释放，不入存档）、个人倍率记在人物身上（{@link CrewReloadAttachment}）。本类不持有装填进度——
 * 进度归接收者的 {@link ReloadJob}。
 * <p>
 * <b>倍率不是状态：</b>岗位倍率在发现时定到座位上（同一座位经多条线路可达时取其中最低的那条），个人倍率
 * 读人物，两者相乘即得，不在认领里再存一份副本——两份同义值迟早会走岔。认领因此只记"坐哪个座位、派的是
 * 谁"这一件事。
 * <p>
 * <b>两端同源：</b>候选、代价与比较在两端用同一套代码；只有"认领记在哪里"这一处分端——服务端记在人物实体的
 * {@link CrewReloadAttachment} 上，客户端没有实体附件，按人物 UUID 记在
 * {@link io.github.sweetzonzi.machine_max.common.mech.ObjectManager} 的客户端 Level 表里。候选身份因此来自
 * {@link SeatSubsystem#loaderCandidate()}，客户端不读 {@code passenger} 引用，也不会从"座位有人"推出一个倍率
 * 1.0 的装填手。
 */
final class LoaderRoster implements IAmmoTransporter {

    /** 发现表的宿主：座位查询从它出发，警告也按它的名字记 */
    private final AbstractSubsystem host;

    /** 装填人员频道 → 目标名称列表 */
    private final Map<String, List<String>> discoveryTargets;

    /** 岗位时间倍率 {@code P}：装填人员频道 → 乘员为该需求方操作时的便利程度；未声明按 1.0 */
    private final Map<String, Double> timeMultipliers;

    /**
     * 查询到的座位与该座位的岗位倍率 {@code P}；结构变化时整体重建。<br>
     * 同一座位可以经多条线路到达：这些线路表达的是"选哪条"而不是"叠加"，因此发现时就把它们收成一个值——
     * 取其中最低的倍率，也就是乘员在该座位上能用上的最有利的那条。座位表因此是"座位 → 一个倍率"，
     * 选人时每个座位只出一个候选。
     */
    private final Map<SeatSubsystem, Double> seats = new LinkedHashMap<>();

    /** 当前认领；null = 没有认领。服务端字段 */
    @Nullable
    private Claim claim;

    /**
     * 本需求方人工段的基准时长（标准人力 tick）；0 表示它没有人工段，因此不作为人力运输者进入路由。<br>
     * 由 {@link #discover(int)} 每次结构变化时写入一次，认领与估价都读它，签名里因此不必再传。
     */
    private int standardTicks;

    /** 已经就有没有执行者这件事告过警，避免每次结构变化重复刷屏 */
    private boolean warnedNoOperator;

    /**
     * @param host              发起装填的需求方，座位查询从它出发
     * @param discoveryTargets  装填人员频道 → 目标名称列表
     * @param timeMultipliers   岗位倍率表，未声明的频道按 1.0
     */
    LoaderRoster(AbstractSubsystem host,
                 Map<String, List<String>> discoveryTargets,
                 Map<String, Double> timeMultipliers) {
        this.host = host;
        this.discoveryTargets = discoveryTargets;
        this.timeMultipliers = timeMultipliers;
    }

    // ==================== 发现 ====================

    /**
     * 按发现表重新查询可达的座位，并记下本方的基准时长；结果整体替换，不累积历史。<br>
     * 座位只作为发现结果保存，实际是否有人由每个 tick 读 {@code passenger}——乘员上下座不触发结构变化，
     * 不能只在发现时判定一次。同一座位经多条线路到达时只留其中最低的岗位倍率（见 {@link #seats}）。
     * 配置了人工段却一个座位都没查到是一处内容错误，只告警一次。
     * <p><b>调用线程：</b>主线程（挂载与结构变化）。
     *
     * @param standardTicks 人工段的基准时长；{@code <= 0} 表示本方没有人工段，此时本类不作为运输者进入路由
     */
    void discover(int standardTicks) {
        this.standardTicks = Math.max(0, standardTicks);
        Map<SeatSubsystem, Double> found = new LinkedHashMap<>();
        for (String channel : discoveryTargets.keySet()) {
            double multiplier = multiplierFor(channel);
            for (Map.Entry<String, List<ISignalReceiver>> arrival : host.resolveTargets(channel).entrySet()) {
                for (ISignalReceiver terminal : arrival.getValue()) {
                    if (!(terminal instanceof SeatSubsystem foundSeat)) continue;
                    if (!sameAssembly(foundSeat)) continue;
                    found.merge(foundSeat, multiplier, Math::min);
                }
            }
        }
        seats.clear();
        seats.putAll(found);

        if (this.standardTicks > 0 && seats.isEmpty()) {
            if (!warnedNoOperator) {
                warnedNoOperator = true;
                MachineMax.LOGGER.warn("{} 配置了人工段，但没有任何座位声明装填人员频道 {}；"
                                + "这条流程等不到执行者，装填会一直停在等待人工",
                        host.name, discoveryTargets.keySet());
            }
        } else {
            warnedNoOperator = false;
        }
    }

    /** 该子系统是否与宿主挂在同一个装配体上。 */
    private boolean sameAssembly(AbstractSubsystem other) {
        return other.getOwner().getSubPart().getPart().assembly
                == host.getOwner().getSubPart().getPart().assembly;
    }

    // ==================== IAmmoTransporter ====================

    /** 人力运输者：整条运输由一名乘员完成。 */
    @Override
    public boolean isHuman() {
        return true;
    }

    /** 人力运输者没有机构条件，恒为可工作；它是否进入路由由基准时长与座位的发现结果决定。 */
    @Override
    public boolean isOperational() {
        return true;
    }

    /** 一名乘员一次只推进一段人工装填。 */
    @Override
    public boolean canServeMultipleConsumers() {
        return false;
    }

    @Override
    public int getHumanReloadTimeTicks() {
        return standardTicks;
    }

    /** 人力运输者没有机械段。 */
    @Override
    public int getReloadTimeTicks() {
        return 0;
    }

    /** 人力运输者没有喂弹线：提供者取接收者自己发现到的架。 */
    @Override
    public List<IAmmoSupplier> getFeedSources() {
        return List.of();
    }

    /**
     * 为本阶段认领一名执行者，或在已有更好的人时换人。<br>
     * 候选是"有效座位的存活乘员"，代价是剩余人工段的实际用时（剩余标准人力时间 × 该座位的岗位倍率 ×
     * 个人倍率）。当前执行者仍然可用时，只有严格更便宜的候选才换人，相等时保持。
     * <p>换人前先按身份释放旧认领，因此本方法自己就能处理"原来的人已经离座"——调用方不必先问一次
     * {@link #holds(IAmmoConsumer)} 再释放。
     * <p>选不到人时返回 false：接收者据此暂停（作业与预留都还在），暂停超过上限才终止。
     * <p><b>调用线程：</b>主线程（服务端，接收者的每个装填 tick）。
     *
     * @param consumer 申请人工段的接收者
     * @param key      它即将写进作业的身份
     * @return true 表示此刻这条作业有人可干
     */
    @Override
    public boolean claim(IAmmoConsumer consumer, ReloadJobKey key) {
        if (standardTicks <= 0) return false;

        double remaining = remainingStandardTicks(consumer, standardTicks);
        Candidate best = bestOperator(consumer, remaining);
        if (claim != null) {
            // 原来的人还在干活，并且没有明显更早收工的候选：不动
            if (holds(consumer) && (best == null || best.estimateTicks() >= timeScale() * remaining)) return true;
            release(key);
        }
        if (best == null) return false;
        if (!takeCrew(best, consumer, key)) return false;
        claim = new Claim(best.seat(), best.crew().personId(), best.person());
        return true;
    }

    /**
     * 把这条作业的认领记到这个人身上。
     *
     * <p>这是唯一分端的一步：服务端写实体附件，客户端写客户端 Level 的 UUID 表。两边的受理判据相同，
     * 因此"这个人已经被别人的作业占着"在两端给出同样的结论。</p>
     */
    private boolean takeCrew(Candidate best, IAmmoConsumer consumer, ReloadJobKey key) {
        if (host.getLevel().isClientSide()) {
            return ObjectManager.claimCrew(host.getLevel(), best.crew().personId(), consumer, key);
        }
        LivingEntity person = best.person();
        return person != null && CrewReloadAttachment.of(person).claim(consumer, key);
    }

    /**
     * 这条作业的人工段是否仍有可用执行者。<br>
     * 乘员离座、死亡、实体移除、座位被摧毁、这条作业的座位已经不在本轮发现结果里，或者认领已经不在原作业上，
     * 都算不可用；此时人工段不能空跑。两端都判，判据相同。
     *
     * @param consumer 询问的接收者
     */
    boolean holds(IAmmoConsumer consumer) {
        Claim current = claim;
        if (current == null || !seats.containsKey(current.seat())) return false;
        SeatSubsystem.LoaderCandidate crew = current.seat().loaderCandidate();
        if (crew == null || !crew.usable() || !crew.personId().equals(current.personId())) return false;
        if (current.seat().isDestroyed() || !current.seat().isActive()) return false;
        if (host.getLevel().isClientSide()) {
            return ObjectManager.crewHolds(host.getLevel(), current.personId(), consumer);
        }
        LivingEntity person = current.person();
        if (person == null || person.isRemoved() || person.isDeadOrDying()) return false;
        if (current.seat().getPassenger() != person) return false;
        return CrewReloadAttachment.of(person).holdsClaim(consumer);
    }

    /**
     * 人工段能不能往前走：有人就 1，没有就 0（接收者据此暂停并等待换人）。<br>
     * 人离场不构成承诺消失——下一 tick 可能换一个人；承诺里属于"预留"的那一半由接收者向提供者判。
     */
    @Override
    public int getAdvanceTicks(IAmmoConsumer consumer) {
        return holds(consumer) ? 1 : 0;
    }

    /**
     * 人力运输者没有占用可摘：一份认领就是一名乘员，人走了只暂停、不构成承诺消失。<br>
     * 因此本方法恒为 {@code true}，承诺的另外两半（提供者的预留、接收者自身）由接收者判。
     */
    @Override
    public boolean holdsCommitment(IAmmoConsumer consumer) {
        return true;
    }

    /** 一台 roster 只服务自己的接收者，不存在"被别人占着"这回事。 */
    @Override
    public boolean isClaimedByOther(IAmmoConsumer consumer) {
        return false;
    }

    /**
     * 按身份释放执行者认领。<br>
     * 人工段完成、作业终止、换人都是这个入口；身份不匹配时无操作，旧作业的回调因此摘不掉新作业的认领。
     * 释放不改变已完成的人工进度，也不碰提供者的预留表。
     * <p>释放的对象是认领记下的那个人：人已经离座时座位不再答得出他是谁，所以认领本身要记着人。
     *
     * @param key 这条作业的身份
     */
    @Override
    public void releaseClaim(IAmmoConsumer consumer, ReloadJobKey key) {
        release(key);
    }

    /**
     * 已认领执行者的时间倍率（岗位倍率 {@code P} × 个人倍率 {@code A}）。<br>
     * 接收者每个装填 tick 拿它比对作业上记的倍率，换人与人物能力变化都经
     * {@link ReloadJob#retimeHuman(double)} 折算成新的剩余量。没有认领时按标准人力 1。
     * <p>这里读而不是存：存一份副本的话，换人或人物的能力被改过，副本不会跟着变。{@code P} 是发现时定到
     * 座位上的值，所以换座位会换倍率、换人只换 {@code A}。
     */
    @Override
    public double getOperatorTimeScale(IAmmoConsumer consumer) {
        return timeScale();
    }

    /**
     * 人力路的代价：作业已在这条路上就报本阶段剩余，否则按"选出乘员的 {@code P × A}"折算整段基准时长。<br>
     * 估价只读——不认领、不改预留，与 {@link #claim} 共用同一批候选与同一个比较纯函数。
     */
    @Override
    public int estimateTicks(IAmmoConsumer consumer, IAmmoSupplier provider) {
        if (provider == null || !provider.isSupplyOperational()) return Integer.MAX_VALUE;
        ReloadJob job = consumer.getReloadJob();
        boolean onThisRoute = job != null
                && job.transporter().isNone()
                && job.provider().equals(AmmoRef.ofProvider(provider));
        if (onThisRoute) return Math.max(0, job.remainingTicks());
        if (standardTicks <= 0) return Integer.MAX_VALUE;
        if (provider.getRemainingCount() == 0) return Integer.MAX_VALUE;
        double rate = estimateRate(consumer, standardTicks);
        if (!Double.isFinite(rate) || rate <= 0) return Integer.MAX_VALUE;
        return humanStageTicks(standardTicks, rate);
    }

    // ==================== 内部 ====================

    /**
     * 按身份释放认领。两端都释放，且只清掉身份匹配的那一条。
     *
     * @param key 这条作业的身份
     */
    void release(ReloadJobKey key) {
        Claim current = claim;
        if (current == null) return;
        if (host.getLevel().isClientSide()) {
            if (ObjectManager.releaseCrewClaim(host.getLevel(), current.personId(), key)) claim = null;
            return;
        }
        LivingEntity person = current.person();
        if (person == null) {
            claim = null;
            return;
        }
        if (!CrewReloadAttachment.of(person).release(key)) return;
        claim = null;
    }

    /** 已认领执行者的时间倍率（见 {@link #getOperatorTimeScale(IAmmoConsumer)}）。 */
    double timeScale() {
        Claim current = claim;
        if (current == null) return 1.0;
        Double standard = seats.get(current.seat());
        if (standard == null) return 1.0;
        double personal;
        if (host.getLevel().isClientSide()) {
            // 客户端没有实体附件：个人倍率取自己座位下发的候选快照，换人与能力变化随同步值更新
            SeatSubsystem.LoaderCandidate crew = current.seat().loaderCandidate();
            personal = crew != null && crew.personId().equals(current.personId())
                    ? crew.multiplier()
                    : CrewReloadAttachment.DEFAULT_MULTIPLIER;
        } else {
            LivingEntity person = current.person();
            if (person == null) return 1.0;
            personal = CrewReloadAttachment.of(person).multiplier();
        }
        double value = standard * personal;
        return value > 0 && Double.isFinite(value) ? value : 1.0;
    }

    /**
     * 只读估一次本方人工段的倍率（岗位倍率 × 个人倍率），供机械运输者把它折进整条路的代价。<br>
     * 不认领、不改预留；没有人可用且没有在位执行者时返回 {@code NaN}（调用方按不可达处理）。
     */
    double estimateTimeScale(IAmmoConsumer consumer) {
        return estimateRate(consumer, standardTicks);
    }

    /** 某条发现关系上的岗位倍率；未声明时按标准 1.0。 */
    private double multiplierFor(String channel) {
        Double value = timeMultipliers.get(channel);
        return value == null ? 1.0 : value;
    }

    /**
     * 只读地估一次"这段人工段由最合适的乘员来做要多久"的倍率。<br>
     * 与 {@link #claim} 共用 {@link #bestOperator}，区别是这里不写认领；没人可换但当前执行者仍在位时按他的
     * 倍率报，两条都没有时返回 {@code NaN}（调用方按不可达处理）。
     */
    private double estimateRate(IAmmoConsumer consumer, int standardTicks) {
        double remaining = remainingStandardTicks(consumer, standardTicks);
        if (remaining <= 0) return 1.0;
        Candidate best = bestOperator(consumer, remaining);
        if (best != null) {
            double rate = best.estimateTicks() / remaining;
            return rate > 0 && Double.isFinite(rate) ? rate : 1.0;
        }
        return claim != null && holds(consumer) ? timeScale() : Double.NaN;
    }

    /**
     * 剩余人工段的<b>标准</b>人力 tick 数：作业上剩余的进度折算回未乘倍率的量。<br>
     * 还没有作业（开工前的选人）时就是整段基准时长。
     */
    private static double remainingStandardTicks(IAmmoConsumer consumer, int standardTicks) {
        ReloadJob job = consumer.getReloadJob();
        if (job == null || job.stage() != ReloadJob.Stage.HUMAN) return standardTicks;
        return Math.max(0.0, (job.totalTicks() - job.elapsedTicks()) * job.humanRate());
    }

    /**
     * 挑出剩余人工段用时最短的候选。
     *
     * <p>候选身份取座位的装填候选快照（两端同源），代价与比较用同一套公式；只有"这个人是否正替别人的作业
     * 干活"这一处分端——服务端问实体附件，客户端问本端 UUID 表。</p>
     *
     * @param remainingStandard 剩余标准人力 tick
     * @return 最优候选；没有可用乘员时返回 null
     */
    @Nullable
    private Candidate bestOperator(IAmmoConsumer consumer, double remainingStandard) {
        List<ReloadCrewSelection.Candidate> keys = new ArrayList<>(seats.size());
        List<Candidate> candidates = new ArrayList<>(seats.size());
        for (Map.Entry<SeatSubsystem, Double> entry : seats.entrySet()) {
            SeatSubsystem candidateSeat = entry.getKey();
            if (candidateSeat.isDestroyed() || !candidateSeat.isActive()) continue;
            SeatSubsystem.LoaderCandidate crew = candidateSeat.loaderCandidate();
            if (crew == null || !crew.usable()) continue;
            if (claimedByOther(candidateSeat, crew, consumer)) continue;
            LivingEntity person = host.getLevel().isClientSide() ? null : candidateSeat.getPassenger();
            double estimate = remainingStandard * entry.getValue() * crew.multiplier();
            keys.add(new ReloadCrewSelection.Candidate(crew.personId(), candidateSeat.getSubPart().getId(),
                    candidateSeat.name, estimate));
            candidates.add(new Candidate(candidateSeat, crew, person, estimate));
        }
        int index = ReloadCrewSelection.bestIndex(keys);
        return index < 0 ? null : candidates.get(index);
    }

    /** 这个人是否正替别的作业干活：服务端问实体附件，客户端问本端预测表。 */
    private boolean claimedByOther(SeatSubsystem seat, SeatSubsystem.LoaderCandidate crew, IAmmoConsumer consumer) {
        if (host.getLevel().isClientSide()) {
            return ObjectManager.crewClaimedByOther(host.getLevel(), crew.personId(), consumer);
        }
        LivingEntity person = seat.getPassenger();
        return person != null && CrewReloadAttachment.of(person).isClaimedByOther(consumer);
    }

    /** 人工段实际时长：标准人力 tick 乘上岗位倍率与个人倍率。 */
    private static int humanStageTicks(int standardTicks, double rate) {
        double safe = rate > 0 && Double.isFinite(rate) ? rate : 1.0;
        return Math.max(1, (int) Math.round(standardTicks * safe));
    }

    /**
     * 一个候选执行者：坐在哪、候选快照、实际乘员与预计用时。
     *
     * @param seat          候选座位
     * @param crew          候选快照（人物 UUID、可用性、个人倍率）
     * @param person        服务端解析到的乘员实体；客户端为 {@code null}
     * @param estimateTicks 预计完成剩余人工段所需的 tick
     */
    private record Candidate(SeatSubsystem seat, SeatSubsystem.LoaderCandidate crew,
                             @Nullable LivingEntity person, double estimateTicks) {
    }

    /**
     * 一次认领：坐哪个座位、派的是谁。<br>
     * 座位给出"人还在不在位"（候选快照每 tick 刷新，上下座不触发结构变化）与岗位倍率；人物 UUID 是两端
     * 通用的身份，服务端的 {@code person} 额外用来读写实体附件上的认领（离座之后座位答不出他是谁，
     * 释放只能靠这个引用），客户端没有实体因此只记 UUID。
     */
    private record Claim(SeatSubsystem seat, UUID personId, @Nullable LivingEntity person) {
    }
}
