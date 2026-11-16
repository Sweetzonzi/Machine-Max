package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.sound.ISoundSpreader;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AmmoLoaderSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.registry.MMEntityDataSerializers;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自动装弹机子系统：一条机械装填路的<b>运输者</b>。<br>
 * 它拥有本段的时间基准（{@link #getReloadTimeTicks()}）、执行资源（机械槽，或人工准备段的一名乘员）与
 * 本 tick 的推进量（{@link #getAdvanceTicks(IAmmoConsumer)}）；库存、预留与交付实例归上游弹药架
 * （{@link RegenAmmoRackSubsystem}），进度与阶段归接收者的 {@link ReloadJob}。本机不缓存 AmmoBelt 实例。
 * <p>
 * <b>两段流程：</b>型号可以配置一段人工准备（{@link #getHumanReloadTimeTicks()}）。有它时作业先走
 * {@link ReloadJob.Stage#HUMAN}，由本机发现的座位上的实际乘员推进；人工段完成后释放人员，再申请机械槽位进入
 * {@link ReloadJob.Stage#MACHINE}。所以人工段不占机械槽：{@link #claim} 在人工段里只认领乘员，
 * {@link #claimForMachineStage} 到机械段才登记槽位。整次作业的弹药预留由接收者向提供者取，本机不代办。
 * <p>
 * 上游来源由本机自己按 {@code ammo_discovery_targets} 查询，只接受库存持有者（弹药架）；机构之间不互认上游，
 * 机械链因此不会成环。下游按 {@code ammo_discovery_inputs} 在供弹频道上发现本机；人工段的乘员按
 * {@code loader_discovery_targets} 在装填人员频道上发现座位。
 * <p>
 * <b>受理是唯一写入点：</b>一次受理在本机写入机械槽（仅独占机构）或在人工段里认领乘员。没被受理的请求不写
 * 占用，也不开始计时；机构据此既不挡别人也不承诺自己没有的弹。占用表只记消费者引用与作业身份，来源绑定
 * 只有一份——它在来源架的预留表里，本机不另存"某作业用哪个上游"。
 * <p>
 * 并发产能沿用布尔语义：{@code can_supply_multiple=false} 的机构一次只为一条作业推进（独占且满速），并把
 * 槽位拥有者下发；{@code true} 时不限槽位、各作业分别满速，没有独占槽位。客户端据下发的拥有者建立本地镜像，
 * 不运行第二套抢占判定。
 * <p>
 * 进度音效属于本机配置，但<b>触发源是接收者的作业进度</b>：接收者的每个客户端 tick 把当前进度交给
 * {@link #tickStageSounds(IAmmoConsumer, float)}。人工段的声音定位在发射器上（人把弹交到炮闩跟前），
 * 机械段定位在本机；已播游标按阶段分开记，阶段切换先把上一段剩下的标记补完再起新段。
 */
public class AmmoLoaderSubsystem extends BasicSubsystem implements IAmmoTransporter, ISoundSpreader {

    public final AmmoLoaderSubsystemAttr attr;

    /**
     * 独占机构的槽位拥有者（网络同步）。<br>
     * 值是唯一实际占用记录里那一条作业的身份；{@link ReloadJobKey#NONE} 表示没有作业占用本机。
     * {@code can_supply_multiple=true} 没有独占槽位，本字段不下发（保持空值），各接收者跟随自己作业的
     * 受理状态。客户端用它建立本地占用镜像，不自行分配。
     */
    private static final EntityDataAccessor<ReloadJobKey> SLOT_OWNER_ID =
            SynchedEntityData.defineId(AmmoLoaderSubsystem.class, MMEntityDataSerializers.getRELOAD_JOB_KEY());

    /**
     * 已受理作业的占用表：消费者 → 该作业的身份。<br>
     * 只在受理点（{@link #claim}／{@link #claimForMachineStage}）写入，由接收者按身份释放：旧作业的回调因此
     * 摘不掉新作业的占用。只记消费者引用与身份，不复制作业内容；不入存档。人工段不在这张表里——那时还没有
     * 机械动作。
     */
    private final Map<IAmmoConsumer, ReloadJobKey> claimedJobs = new LinkedHashMap<>();

    /**
     * 客户端镜像的槽位拥有者；服务端读 {@link #claimedJobs}。<br>
     * 在 {@link #onPostTick()} 落地，因此一次收到的拥有者变化不会在模拟中途改变本 tick 的放行判定。
     */
    private ReloadJobKey slotOwner = ReloadJobKey.NONE;

    /** 客户端是否有待落地的槽位拥有者 */
    private boolean pendingSlotOwner;

    /**
     * 每个消费者在本阶段的音效游标：已播到的进度比例与它是哪一段。<br>
     * 游标是各客户端自己的量：进度回退（作业重开、上行校准把进度往回拉）只把游标拉回去，
     * 不重播已经越过的阈值；阶段切换时先把上一段剩下的标记补完，再为新段重建游标。
     */
    private final Map<IAmmoConsumer, SoundCursor> soundCursors = new LinkedHashMap<>();

    /**
     * 本机查询得到的上游弹药架，按到达顺序保存；结构变化时整体重建。<br>
     * 只含库存持有者，且不含本机自身。
     */
    private final List<RegenAmmoRackSubsystem> upstreams = new ArrayList<>();

    /** {@link #upstreams} 的只读视图：喂弹线每 tick 被路由枚举，避免每次复制 */
    private final List<IAmmoSupplier> feedSources = Collections.unmodifiableList(upstreams);

    /**
     * 人工段的用人：座位发现、执行者认领与岗位倍率。<br>
     * 发射器在人力直取路径上需要同一份东西，因此选人口径只有 {@link LoaderRoster} 一处。
     */
    private final LoaderRoster loaders;

    public AmmoLoaderSubsystem(ISubsystemHost owner, String name, AmmoLoaderSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        this.loaders = new LoaderRoster(this, attr.loaderDiscoveryTargets,
                attr.loaderTimeMultipliers);
    }

    // ==================== 同步数据 ====================

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SLOT_OWNER_ID, ReloadJobKey.NONE);
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> dataAccessor) {
        super.onSyncedDataUpdated(dataAccessor);
        if (!getLevel().isClientSide()) return;
        if (dataAccessor.equals(SLOT_OWNER_ID)) pendingSlotOwner = true;
    }

    /**
     * 客户端在 tick 收尾落地槽位拥有者镜像。<br>
     * 本 tick 的放行判定读的是上一轮落地的值，因此一次拥有者变化不会让同一个 tick 内出现
     * "前半 tick 允许、后半 tick 拒绝"的中间态。
     * <p><b>调用线程：</b>主线程（客户端 tick 收尾）。
     */
    @Override
    public void onPostTick() {
        super.onPostTick();
        if (!getLevel().isClientSide() || !pendingSlotOwner) return;
        pendingSlotOwner = false;
        slotOwner = getSynchedData().get(SLOT_OWNER_ID);
    }

    // ==================== 上游与人员发现 ====================

    @Override
    public void onAttach() {
        super.onAttach();
        discoverUpstreams();
        loaders.discover(getHumanReloadTimeTicks());
    }

    @Override
    public void onVehicleStructureChanged() {
        super.onVehicleStructureChanged();
        discoverUpstreams();
        loaders.discover(getHumanReloadTimeTicks());
    }

    /**
     * 按 {@code ammo_discovery_targets} 的每个频道做只读查询，收集可达的库存持有者。<br>
     * 查询结果按对象身份去重，且只保留与宿主同一装配体内的架；机构本身不是库存持有者，因此不会被
     * 收进来，机械链不会成环。
     * <p><b>调用线程：</b>主线程（挂载与结构变化）。
     */
    private void discoverUpstreams() {
        List<RegenAmmoRackSubsystem> found = new ArrayList<>();
        for (String channel : attr.ammoDiscoveryTargets.keySet()) {
            for (Map.Entry<String, List<ISignalReceiver>> arrival : resolveTargets(channel).entrySet()) {
                for (ISignalReceiver terminal : arrival.getValue()) {
                    if (!(terminal instanceof RegenAmmoRackSubsystem rack)) continue;
                    if (!sameAssembly(rack)) continue;
                    if (!found.contains(rack)) found.add(rack);
                }
            }
        }
        upstreams.clear();
        upstreams.addAll(found);
    }

    /** 该子系统是否与本机挂在同一个装配体上。 */
    private boolean sameAssembly(AbstractSubsystem other) {
        return other.getOwner().getSubPart().getPart().assembly == getOwner().getSubPart().getPart().assembly;
    }

    // ==================== IAmmoTransporter 实现 ====================

    /** 机械运输者。 */
    @Override
    public boolean isHuman() {
        return false;
    }

    /**
     * 本机此刻能否工作：启用、未摧毁。<br>
     * 上游是否存在、是否可工作属于提供者的可用性，由接收者按作业上的提供者判定，不并入本方法——否则一台
     * 暂时没接弹药架的机构会被报成"机械故障"。
     */
    @Override
    public boolean isOperational() {
        return isActive() && !isDestroyed();
    }

    @Override
    public boolean canServeMultipleConsumers() {
        return attr.staticAttribute.isCanSupplyMultiple();
    }

    /**
     * 机械段的基准时长：型号的 {@code reload_time} 秒换算成 tick，0 表示没有机械段。<br>
     * 接收者的作业在进入机械段时读一次并定死，段内不再改读本方法。零时长不在这里抬成 1 tick：
     * 交付的节奏由上游弹药架的整批回充决定，机构本身没有可等的动作（弹链直接接在枪上的机枪）。
     */
    @Override
    public int getReloadTimeTicks() {
        return Math.max(0, (int) (attr.staticAttribute.getReloadTime() * 20f)); // 秒 → tick
    }

    /**
     * 人工段的基准时长：型号的 {@code human_reload_time} 秒换算成 tick；没有配置人工段时是 0。<br>
     * 这是标准人力下的秒数，岗位倍率与个人倍率折算进本阶段的实际时长。
     */
    @Override
    public int getHumanReloadTimeTicks() {
        float seconds = attr.staticAttribute.getHumanReloadTime();
        if (seconds <= 0f) return 0;
        return Math.max(1, (int) (seconds * 20f));
    }

    /** 喂弹线：本机自己发现到的上游弹药架。 */
    @Override
    public List<IAmmoSupplier> getFeedSources() {
        return feedSources;
    }

    /**
     * 为本阶段取得（或维持）执行资源。<br>
     * 有人工段时认领一名乘员（每 tick 调用，因此换人与"还有没有人"是同一个入口）；没有人工段时取机械槽。
     * 可重入且幂等：同一身份重复调用只补本阶段资源，不追加预留、不重置进度。
     * <p><b>调用线程：</b>主线程（服务端受理点）。
     */
    @Override
    public boolean claim(IAmmoConsumer consumer, ReloadJobKey key) {
        if (getLevel().isClientSide()) return true;
        if (key.isNone()) return false;
        sweepStaleClaims();
        if (!isOperational()) return false;
        if (getHumanReloadTimeTicks() > 0) {
            // 人工段：只占人、不占槽
            return loaders.claim(consumer, key);
        }
        return takeSlot(consumer, key);
    }

    /**
     * 人工段结束：把乘员认领交还，再登记机械槽。<br>
     * 槽位一时拿不到时返回 false，接收者让作业停在人工段末端等（原库存预留保持），由暂停上限兜底。
     */
    @Override
    public boolean claimForMachineStage(IAmmoConsumer consumer, ReloadJobKey key) {
        if (getLevel().isClientSide()) return true;
        if (key.isNone()) return false;
        sweepStaleClaims();
        if (!isOperational()) return false;
        loaders.release(key);
        return takeSlot(consumer, key);
    }

    /** 登记本机的机械槽（独占机构一次只接一条作业）。 */
    private boolean takeSlot(IAmmoConsumer consumer, ReloadJobKey key) {
        if (!canServeMultipleConsumers() && hasOtherClaim(consumer)) return false;
        claimedJobs.put(consumer, key);
        publishSlotOwner();
        return true;
    }

    /**
     * 本 tick 能为该接收者的作业推进多少。<br>
     * 人工段只看人还在不在：时长已经把倍率折进去，本机不再按秒数算推进量。机械段认两条：本机受理过这条作业
     * 且槽位归它、该作业的提供者仍承认那份预留。客户端用同一套判据的镜像——人工段恒为 1（人员状态由服务端
     * 判定），机械段看下发的槽位拥有者与作业上的那一台提供者。
     */
    @Override
    public int getAdvanceTicks(IAmmoConsumer consumer) {
        if (!isOperational()) return 0;
        ReloadJob job = consumer.getReloadJob();
        if (job == null) return 0;
        boolean human = job.stage() == ReloadJob.Stage.HUMAN;
        if (getLevel().isClientSide()) {
            if (human) return 1;
            if (!canServeMultipleConsumers() && !slotOwner.equals(job.key())) return 0;
            return reservationStillThere(job) ? 1 : 0;
        }
        if (human) return loaders.holds(consumer) ? 1 : 0;
        sweepStaleClaims();
        if (!ownsClaim(consumer, job.key())) return 0;
        return reservationStillThere(job) ? 1 : 0;
    }

    /**
     * 作业上那一台提供者是否仍承认这份预留。<br>
     * 只问作业指名的这一台，不退回"第一台可工作的架"：兜底会拿另一台架的库存回答本作业的份额。
     */
    private boolean reservationStillThere(ReloadJob job) {
        IAmmoSupplier provider = findProvider(job.provider());
        return provider != null && provider.holdsReservation(job.key(), job.reservedCount());
    }

    /**
     * 本机是否仍为这条作业保留执行资源。<br>
     * 人工段恒为 true：乘员离场只让推进量变成 0，人可以在下一 tick 换一个，不构成承诺消失。机械段看占用表
     * （客户端看槽位拥有者镜像）。承诺里属于"提供者预留"的那一半由接收者直接向提供者判。
     */
    @Override
    public boolean holdsCommitment(IAmmoConsumer consumer) {
        ReloadJob job = consumer.getReloadJob();
        if (job == null) return false;
        if (job.stage() == ReloadJob.Stage.HUMAN) return true;
        if (getLevel().isClientSide()) {
            return canServeMultipleConsumers() || slotOwner.equals(job.key());
        }
        sweepStaleClaims();
        return ownsClaim(consumer, job.key());
    }

    /** 该消费者是否正持有本机这一条占用。 */
    private boolean ownsClaim(IAmmoConsumer consumer, ReloadJobKey key) {
        ReloadJobKey held = claimedJobs.get(consumer);
        return held != null && held.equals(key);
    }

    /** 本机是否已被属于别人的作业占着。 */
    private boolean hasOtherClaim(IAmmoConsumer consumer) {
        for (IAmmoConsumer holder : claimedJobs.keySet()) {
            if (holder != consumer) return true;
        }
        return false;
    }

    @Override
    public boolean isClaimedByOther(IAmmoConsumer consumer) {
        if (canServeMultipleConsumers()) return false;
        sweepStaleClaims();
        return hasOtherClaim(consumer);
    }

    /**
     * 按身份释放本机的执行资源：乘员认领与机械槽。<br>
     * 不碰上游架的预留表——正常撤销归接收者；只有失效清理（{@link #sweepStaleClaims()}）会在消费者已无机会
     * 收尾时代撤销。身份不匹配时无操作，旧作业的回调因此摘不掉新作业的占用。
     */
    @Override
    public void releaseClaim(IAmmoConsumer consumer, ReloadJobKey key) {
        if (key == null || key.isNone()) return;
        loaders.release(key);
        if (getLevel().isClientSide()) return;
        if (ownsClaim(consumer, key)) {
            claimedJobs.remove(consumer);
            publishSlotOwner();
        }
    }

    /** 已认领乘员的人工段时间倍率（岗位倍率 × 个人倍率）；没有人工段时按标准人力 1。 */
    @Override
    public double getOperatorTimeScale(IAmmoConsumer consumer) {
        return loaders.timeScale();
    }

    /**
     * 沿这条路，接收者还要多久拿到下一份交付。<br>
     * 作业已在本路上就报本阶段剩余（人工段再加整段机械时长）；否则按人工段标准剩余乘岗位倍率与个人倍率、
     * 加整段机械时长。缺弹等待只按本 tick 的可分配库存判定——0 即等价于不可达，再生时间不折算。
     */
    @Override
    public int estimateTicks(IAmmoConsumer consumer, IAmmoSupplier provider) {
        if (!isOperational()) return Integer.MAX_VALUE;
        if (provider == null || !provider.isSupplyOperational()) return Integer.MAX_VALUE;
        ReloadJob job = consumer.getReloadJob();
        AmmoRef self = AmmoRef.ofTransporter(this);
        boolean onThisRoute = job != null
                && job.transporter().equals(self)
                && job.provider().equals(AmmoRef.ofProvider(provider));
        if (onThisRoute) {
            int remaining = job.remainingTicks();
            if (job.stage() == ReloadJob.Stage.HUMAN) remaining += getReloadTimeTicks();
            return remaining;
        }
        if (provider.getRemainingCount() == 0) return Integer.MAX_VALUE;
        int human = getHumanReloadTimeTicks();
        if (human > 0) {
            double rate = loaders.estimateTimeScale(consumer);
            if (!Double.isFinite(rate) || rate <= 0) return Integer.MAX_VALUE;
            human = Math.max(1, (int) Math.round(human * rate));
        }
        return human + getReloadTimeTicks();
    }

    // ==================== 占用清理 ====================

    /**
     * 剔除失效占用，按不存在处理。<br>
     * 失效包括消费者已摧毁／失活、它的指认已不指向本机、它的当前作业缺失或身份对不上。合法的暂停
     * （机构受阻、供能不足）不影响占用：作业还在，身份也还在。清理只删占用与预留，不改库存、不补弹。
     * <p>同一轮按身份撤销该作业的预留，是本类唯一一处代撤销：消费者已经不会再走收尾，留着那份预留会永久
     * 占住库存。
     * <p><b>调用线程：</b>主线程（每次读取或受理前）。
     */
    private void sweepStaleClaims() {
        Iterator<Map.Entry<IAmmoConsumer, ReloadJobKey>> it = claimedJobs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<IAmmoConsumer, ReloadJobKey> entry = it.next();
            if (isStaleClaim(entry.getKey(), entry.getValue())) {
                releaseReservationEverywhere(entry.getValue());
                it.remove();
                publishSlotOwner();
            }
        }
    }

    /**
     * 按身份在全部上游架上撤销预留。<br>
     * 只服务失效清理（见 {@link #sweepStaleClaims()}）；正常收尾由接收者调用它指名的那一台提供者。
     */
    private void releaseReservationEverywhere(ReloadJobKey key) {
        for (RegenAmmoRackSubsystem rack : upstreams) rack.releaseReservation(key);
    }

    /** 这条占用是否已经不能代表一条真正在推进的作业。 */
    private boolean isStaleClaim(IAmmoConsumer consumer, ReloadJobKey key) {
        if (!(consumer instanceof AbstractSubsystem sub)) return true;
        if (sub.isDestroyed() || !sub.isActive()) return true;
        AmmoPath route = consumer.getCurrentRoute();
        if (route == null || route.transporter() != this) return true;
        ReloadJob job = consumer.getReloadJob();
        return job == null || !job.key().equals(key);
    }

    /** 把独占机构的槽位拥有者推给客户端；并发机构没有独占槽位，不下发。 */
    private void publishSlotOwner() {
        if (getLevel().isClientSide() || canServeMultipleConsumers()) return;
        ReloadJobKey owner = claimedJobs.values().stream().findFirst().orElse(ReloadJobKey.NONE);
        getSynchedData().set(SLOT_OWNER_ID, owner);
    }

    // ==================== 音效 ====================

    /** 某消费者在本阶段的音效游标：阶段与已播到的进度比例 */
    private record SoundCursor(ReloadJob.Stage stage, float progress) {
    }

    /** 该消费者是否已经不能再来取弹（被摧毁或失活）。 */
    private static boolean isConsumerGone(IAmmoConsumer consumer) {
        return consumer instanceof AbstractSubsystem sub && (sub.isDestroyed() || !sub.isActive());
    }

    /**
     * 降低伤害入口：供给能力因本击失效时立刻清掉音效游标。<br>
     * 比较投递前后的 {@link #isOperational()} 而不是只看耐久，是因为"失效"还包含上游架被摧毁的
     * 情形，零耐久后的追加命中也不该重复触发清理。
     * <p>
     * <b>调用线程：</b>主线程（伤害在主线程结算）。
     */
    @Override
    public void onHurt(io.github.sweetzonzi.machine_max.common.mech.vehicle.event.subpart.SubPartDamageEvent.Pre event) {
        boolean wasOperational = isOperational();
        super.onHurt(event);
        if (wasOperational && !isOperational()) {
            soundCursors.clear();
        }
    }

    /**
     * 按接收者作业的当前进度处理本段的进度音效。<br>
     * 阈值 0 是段开始音、1 是本段走完的音，两者都不进越过循环；一次推进跨过多个阈值时按升序逐个播放。
     * 阶段切换先把上一段剩下的标记补完，再为新段重建游标；进度回退只把游标拉回当前进度，不重播。
     * <p><b>调用线程：</b>主线程（客户端 tick，由 {@link LauncherSubsystem#onTick()} 驱动）。
     *
     * @param consumer 正在装填的接收者
     * @param progress 该接收者当前作业的进度（0~1）
     */
    @Override
    public void tickStageSounds(IAmmoConsumer consumer, float progress) {
        if (!getLevel().isClientSide()) return;
        ReloadJob job = consumer.getReloadJob();
        if (job == null) return;
        ReloadJob.Stage stage = job.stage();
        float clamped = Math.clamp(progress, 0f, 1f);
        SoundCursor cursor = soundCursors.get(consumer);

        if (cursor != null && cursor.stage() != stage) {
            // 阶段切换：上一段没播完的标记先补完，再起新段
            playCrossed(consumer, cursor.stage(), cursor.progress(), 1f);
            cursor = null;
        }
        if (cursor == null) {
            // 本段第一次被观察到：只有正好落在段首时才播开始音，晚到的观察者只建立基线
            if (job.elapsedTicks() <= 1) playMarker(consumer, stage, 0f);
            cursor = new SoundCursor(stage, 0f);
        }
        if (clamped < cursor.progress()) {
            // 作业重开或校准把进度往回拉：游标跟回去，已经播过的不重播
            soundCursors.put(consumer, new SoundCursor(stage, clamped));
            if (clamped <= 0f && job.elapsedTicks() <= 1) playMarker(consumer, stage, 0f);
            return;
        }
        playCrossed(consumer, stage, cursor.progress(), clamped);
        soundCursors.put(consumer, new SoundCursor(stage, clamped));
        // 已退场的消费者不再需要游标
        soundCursors.keySet().removeIf(AmmoLoaderSubsystem::isConsumerGone);
    }

    /** 把 {@code (from, to]} 区间里跨过的阈值按升序播一遍；阈值 0 属于段开始音，不在这个区间里。 */
    private void playCrossed(IAmmoConsumer consumer, ReloadJob.Stage stage, float from, float to) {
        for (SoundEvent sound : ReloadSoundMarkers.crossed(soundsOf(stage), from, to)) {
            playAt(consumer, stage, sound);
        }
    }

    /** 播放某个固定阈值的音效（0 = 段开始）；该段没有配置这个阈值时什么都不做。 */
    private void playMarker(IAmmoConsumer consumer, ReloadJob.Stage stage, float threshold) {
        playAt(consumer, stage, ReloadSoundMarkers.at(soundsOf(stage), threshold));
    }

    /** 该阶段的进度音效配置：人工段在发射器上，机械段在本机。 */
    private Map<String, SoundEvent> soundsOf(ReloadJob.Stage stage) {
        return stage == ReloadJob.Stage.HUMAN
                ? attr.staticAttribute.getHumanProgressSounds()
                : attr.staticAttribute.getProgressSounds();
    }

    /**
     * 播放一处进度音效：人工段定位在发射器（人把弹交到炮闩跟前），机械段定位在本机。
     */
    private void playAt(IAmmoConsumer consumer, ReloadJob.Stage stage, @Nullable SoundEvent sound) {
        if (sound == null) return;
        if (stage == ReloadJob.Stage.HUMAN) {
            if (consumer instanceof ISoundSpreader spreader) spreader.playSpreadingSound(sound, SoundSource.NEUTRAL);
            return;
        }
        playSpreadingSound(sound, SoundSource.NEUTRAL);
    }

    // ==================== 信号 ====================

    /** 本机接受的供弹服务频道：下游 Launcher 的发现查询按它到达本机。 */
    @Override
    public List<String> getAcceptedChannels() {
        return attr.ammoDiscoveryInputs;
    }

    /**
     * 本机的查询表：上游弹药架与人工段座位。<br>
     * 两者都是只读发现的入口，因此都进首跳表；装填人员频道只用于握手，本机不往座位发业务信号。
     */
    @Override
    public Map<String, List<String>> getTargetNames() {
        if (attr.loaderDiscoveryTargets.isEmpty()) return attr.ammoDiscoveryTargets;
        Map<String, List<String>> merged = new LinkedHashMap<>(attr.ammoDiscoveryTargets);
        merged.putAll(attr.loaderDiscoveryTargets);
        return merged;
    }
}
