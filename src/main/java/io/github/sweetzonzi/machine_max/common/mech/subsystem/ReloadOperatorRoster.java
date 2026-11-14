package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.attachment.CrewReloadAttachment;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一台装填需求方的"人工段用人"：查询到的座位、当前认领的执行者及其时间倍率。<br>
 * 人工段属于<b>需求方</b>的流程：坐在这台座位上的实际乘员替谁装填、多久装完，由发起装填的那台机器表达。
 * 目前有两台这样的机器——自动装弹机（人工准备 → 机械运行）与声明了人工段的发射器（人力直取：从弹药架
 * 直接取弹、全程由乘员装填）。两者用同一份实现，选人口径因此只有一处。
 * <p>
 * <b>三个量分开：</b>座位表是只读发现结果（结构变化时整体重建）、认领是"此刻替哪条作业干活"（按作业身份
 * 写入与释放，不入存档）、个人倍率记在人物身上（{@link CrewReloadAttachment}）。本类不持有装填进度——
 * 进度归消费者的 {@link ReloadJob}。
 * <p>
 * <b>只在服务端有意义：</b>选人和倍率都由服务端算，客户端只跟随同步下来的本阶段时长。
 */
final class ReloadOperatorRoster {

    /** 发现表的宿主：座位查询从它出发，警告也按它的名字记 */
    private final AbstractSubsystem host;

    /** 装填人员频道 → 目标名称列表 */
    private final Map<String, List<String>> discoveryTargets;

    /** 岗位时间倍率 {@code P}：装填人员频道 → 乘员为该需求方操作时的便利程度；未声明按 1.0 */
    private final Map<String, Double> timeMultipliers;

    /**
     * 查询到的座位，以及该座位到达本方的装填人员频道；结构变化时整体重建。<br>
     * 同一座位可以经多条线路到达，此时保留每条线路——岗位倍率绑定配置条目，候选按各自的代价比较。
     */
    private final Map<SeatSubsystem, List<String>> seats = new LinkedHashMap<>();

    /** 当前认领的座位；null = 没有认领。服务端字段 */
    @Nullable
    private SeatSubsystem seat;

    /** 当前认领的乘员；与 {@link #seat} 一起给出执行者。服务端字段 */
    @Nullable
    private LivingEntity operator;

    /** 已认领执行者的时间倍率（岗位倍率 × 个人倍率），换人时随之更新 */
    private double timeScale = 1.0;

    /** 已经就有没有执行者这件事告过警，避免每次结构变化重复刷屏 */
    private boolean warnedNoOperator;

    /**
     * @param host              发起装填的需求方，座位查询从它出发
     * @param discoveryTargets  装填人员频道 → 目标名称列表
     * @param timeMultipliers   岗位倍率表，未声明的频道按 1.0
     */
    ReloadOperatorRoster(AbstractSubsystem host,
                         Map<String, List<String>> discoveryTargets,
                         Map<String, Double> timeMultipliers) {
        this.host = host;
        this.discoveryTargets = discoveryTargets;
        this.timeMultipliers = timeMultipliers;
    }

    /**
     * 按发现表重新查询可达的座位；结果整体替换，不累积历史。<br>
     * 座位只作为发现结果保存，实际是否有人由每个 tick 读 {@code passenger}——乘员上下座不触发结构变化，
     * 不能只在发现时判定一次。配置了人工段却一个座位都没查到是一处内容错误，只告警一次。
     * <p><b>调用线程：</b>主线程（挂载与结构变化）。
     *
     * @param standardTicks 人工段的基准时长；{@code <= 0} 表示本方没有人工段，不做告警判定
     */
    void discover(int standardTicks) {
        Map<SeatSubsystem, List<String>> found = new LinkedHashMap<>();
        for (String channel : discoveryTargets.keySet()) {
            for (Map.Entry<String, List<ISignalReceiver>> arrival : host.resolveTargets(channel).entrySet()) {
                for (ISignalReceiver terminal : arrival.getValue()) {
                    if (!(terminal instanceof SeatSubsystem foundSeat)) continue;
                    if (!sameAssembly(foundSeat)) continue;
                    List<String> channels = found.computeIfAbsent(foundSeat, k -> new ArrayList<>(2));
                    if (!channels.contains(channel)) channels.add(channel);
                }
            }
        }
        seats.clear();
        seats.putAll(found);

        if (standardTicks > 0 && seats.isEmpty()) {
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

    /**
     * 为人工段认领一名执行者，或在已有更好的人时换人。<br>
     * 候选是"有效座位的存活乘员 × 它到达本方的线路"，代价是剩余人工段的实际用时
     * （剩余标准人力时间 × 岗位倍率 × 个人倍率）。当前执行者仍然可用时，只有严格更便宜的候选才换人；
     * 相等时保持——同一个人经多条线路出现时也只取最有利的那条（{@link ReloadCrewSelection}）。
     * <p>选不到人时返回 false：消费者据此暂停（作业与预留都还在），暂停超过上限才终止。
     * <p><b>调用线程：</b>主线程（服务端，消费者的每个装填 tick）。
     *
     * @param consumer      申请人工段的消费者
     * @param key           它即将写进作业的身份
     * @param standardTicks 人工段的基准时长（标准人力，未乘倍率）
     * @return true 表示此刻这条作业有人可干
     */
    boolean claim(IAmmoConsumer consumer, ReloadJobKey key, int standardTicks) {
        if (host.getLevel().isClientSide()) return true;
        if (standardTicks <= 0) return true;

        double remaining = remainingStandardTicks(consumer, standardTicks);
        Candidate best = bestOperator(consumer, remaining);
        if (holds(consumer)) {
            if (best == null || best.estimateTicks() >= timeScale * remaining) return true;
            release(consumer, key);
        }
        if (best == null) return false;
        CrewReloadAttachment crew = CrewReloadAttachment.of(best.person());
        if (!crew.claim(consumer, key)) return false;
        operator = best.person();
        seat = best.seat();
        timeScale = multiplierFor(best.channel()) * crew.multiplier();
        return true;
    }

    /**
     * 这条作业的人工段是否仍有可用执行者。<br>
     * 乘员离座、死亡、实体移除、座位被摧毁，或者认领已经不在原作业上，都算不可用；此时人工段不能空跑。
     * 客户端不判断人员（人员状态由服务端判定，实际时长也已经随同步值发布）。
     *
     * @param consumer 询问的消费者
     */
    boolean holds(IAmmoConsumer consumer) {
        if (host.getLevel().isClientSide()) return true;
        LivingEntity person = operator;
        SeatSubsystem claimedSeat = seat;
        if (person == null || claimedSeat == null) return false;
        if (person.isRemoved() || person.isDeadOrDying()) return false;
        if (claimedSeat.getPassenger() != person) return false;
        if (claimedSeat.isDestroyed() || !claimedSeat.isActive()) return false;
        return CrewReloadAttachment.of(person).holdsClaim(consumer);
    }

    /**
     * 按身份释放执行者认领。<br>
     * 人工段完成、作业终止、换人都是这个入口；身份不匹配时无操作，旧作业的回调因此摘不掉新作业的认领。
     * 释放不改变已完成的人工进度。
     *
     * @param consumer 结束人工段或结束作业的消费者
     * @param key      这条作业的身份
     */
    void release(IAmmoConsumer consumer, ReloadJobKey key) {
        if (host.getLevel().isClientSide() || operator == null) return;
        if (!CrewReloadAttachment.of(operator).release(key)) return;
        operator = null;
        seat = null;
        timeScale = 1.0;
    }

    /**
     * 已认领执行者的时间倍率（岗位倍率 {@code P} × 个人倍率 {@code A}）。<br>
     * 消费者拿它在换人后重算剩余时长；没有认领时按标准人力 1。
     */
    double timeScale() {
        if (host.getLevel().isClientSide() || operator == null) return 1.0;
        return timeScale > 0 && Double.isFinite(timeScale) ? timeScale : 1.0;
    }

    /** 某条发现关系上的岗位倍率；未声明时按标准 1.0。 */
    private double multiplierFor(String channel) {
        Double value = timeMultipliers.get(channel);
        return value == null ? 1.0 : value;
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
     * @param remainingStandard 剩余标准人力 tick
     * @return 最优候选；没有可用乘员时返回 null
     */
    @Nullable
    private Candidate bestOperator(IAmmoConsumer consumer, double remainingStandard) {
        List<ReloadCrewSelection.Candidate> keys = new ArrayList<>(seats.size());
        List<Candidate> candidates = new ArrayList<>(seats.size());
        for (Map.Entry<SeatSubsystem, List<String>> entry : seats.entrySet()) {
            SeatSubsystem candidateSeat = entry.getKey();
            LivingEntity person = usableOperator(candidateSeat, consumer);
            if (person == null) continue;
            CrewReloadAttachment crew = CrewReloadAttachment.of(person);
            for (String channel : entry.getValue()) {
                double estimate = remainingStandard * multiplierFor(channel) * crew.multiplier();
                keys.add(new ReloadCrewSelection.Candidate(person.getUUID(), candidateSeat.getSubPart().getId(),
                        candidateSeat.name, estimate));
                candidates.add(new Candidate(candidateSeat, channel, person, estimate));
            }
        }
        int index = ReloadCrewSelection.bestIndex(keys);
        return index < 0 ? null : candidates.get(index);
    }

    /**
     * 座位上此刻能不能派人：有人、人还在、座位在、这个人没在替别的作业干活。
     *
     * @return 可用的乘员；任一项不成立时返回 null
     */
    @Nullable
    private static LivingEntity usableOperator(SeatSubsystem candidateSeat, IAmmoConsumer consumer) {
        LivingEntity person = candidateSeat.getPassenger();
        if (person == null || person.isRemoved() || person.isDeadOrDying()) return null;
        if (candidateSeat.isDestroyed() || !candidateSeat.isActive()) return null;
        if (CrewReloadAttachment.of(person).isClaimedByOther(consumer)) return null;
        return person;
    }

    /**
     * 一个候选执行者：谁、坐在哪、经哪条线路被发现、干完剩余人工段还要多久。
     *
     * @param seat          候选座位
     * @param channel       该座位到达本方的装填人员频道（岗位倍率按它查）
     * @param person        实际乘员
     * @param estimateTicks 预计完成剩余人工段所需的 tick
     */
    private record Candidate(SeatSubsystem seat, String channel, LivingEntity person, double estimateTicks) {
    }
}
