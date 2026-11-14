package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.sound.ISoundSpreader;
import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自动装弹机子系统。<br>
 * 提供一次装填的<b>可工作条件</b>、<b>占用与库存承诺</b>、<b>人工段的用人</b>与<b>交付本身</b>：为每条作业
 * 回答本 tick 能推进多少（{@link #getAdvanceTicks(IAmmoConsumer)}），并在作业走完后把上游弹药架的那份预留
 * 兑现成交付。装填进度归消费者的 {@link ReloadJob}，本机不持有一份并行计时；库存、容量、预留与再生归
 * {@link RegenAmmoRackSubsystem}；本机不缓存 AmmoBelt 实例，扣减与写入在同一个主线程提交点完成。
 * <p>
 * <b>两段流程：</b>型号可以配置一段人工准备（{@link #getHumanReloadTimeTicks()}）。有它时作业先走
 * {@link ReloadJob.Stage#HUMAN}，由本机发现的座位上的实际乘员推进；人工段完成后释放人员，再申请机械槽位进入
 * {@link ReloadJob.Stage#MACHINE}。所以人工段不占机械槽（{@link #claimFor} 只在机械段调用，
 * {@link #reserveFor} 在人工段前只取弹药预留），而整次作业的弹药在首个阶段之前就已按身份预留。
 * <p>
 * 上游来源由本机自己按 {@code ammo_discovery_targets} 查询，只接受库存持有者（弹药架）；
 * 机构之间不互认上游，机械链因此不会成环。下游按 {@code ammo_discovery_inputs} 在供弹频道上
 * 发现本机；人工段的乘员按 {@code reload_operator_discovery_targets} 在装填人员频道上发现座位。
 * <p>
 * <b>受理是唯一写入点：</b>一次受理同时取得机构槽位（仅独占机构）与该次作业在来源架上的弹药预留
 * （{@link #claimFor}）。没被受理的请求不写占用、不占预留，也不开始计时；机构据此既不挡别人也不承诺
 * 自己没有的弹。占用表只记消费者引用与作业身份，来源绑定只有一份——它在来源架的预留表里，
 * 本机不另存"某作业用哪个上游"。
 * <p>
 * 并发产能沿用布尔语义：{@code can_supply_multiple=false} 的机构一次只为一条作业推进
 * （独占且满速），并把槽位拥有者下发；{@code true} 时不限槽位、各作业分别满速，没有独占槽位。
 * 客户端据下发的拥有者建立本地镜像，不运行第二套抢占判定。
 * <p>
 * 进度音效属于本机配置，但<b>触发源是消费者的作业进度</b>：消费者的每个客户端 tick 把当前进度交给
 * {@link #tickStageSounds(IAmmoConsumer, float)}。人工段的声音定位在发射器上（人把弹交到炮闩跟前），
 * 机械段定位在本机；已播游标按阶段分开记，阶段切换先把上一段剩下的标记补完再起新段。
 */
public class AmmoLoaderSubsystem extends BasicSubsystem implements IAmmoSupplier, ISoundSpreader {

    public final AmmoLoaderSubsystemAttr attr;

    /**
     * 独占机构的槽位拥有者（网络同步）。<br>
     * 值是唯一实际占用记录里那一条作业的身份；{@link ReloadJobKey#NONE} 表示没有作业占用本机。
     * {@code can_supply_multiple=true} 没有独占槽位，本字段不下发（保持空值），各消费者跟随自己
     * 作业的受理状态。客户端用它建立本地占用镜像，不自行分配。
     */
    private static final EntityDataAccessor<ReloadJobKey> SLOT_OWNER_ID =
            SynchedEntityData.defineId(AmmoLoaderSubsystem.class, MMEntityDataSerializers.getRELOAD_JOB_KEY());

    /**
     * 已受理作业的占用表：消费者 → 该作业的身份。<br>
     * 只在受理点（{@link #claimFor}）写入，由消费者按身份释放：旧作业的回调因此摘不掉新作业的占用。
     * 只记消费者引用与身份，不复制作业内容；不入存档。人工段不在这张表里——那时还没有机械动作。
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

    /**
     * 人工段的用人：座位发现、执行者认领与岗位倍率。<br>
     * 发射器在人力直取路径上需要同一份东西，因此选人口径只有 {@link ReloadOperatorRoster} 一处。
     */
    private final ReloadOperatorRoster operators;

    public AmmoLoaderSubsystem(ISubsystemHost owner, String name, AmmoLoaderSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        this.operators = new ReloadOperatorRoster(this, attr.reloadOperatorDiscoveryTargets,
                attr.reloadOperatorTimeMultipliers);
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
        operators.discover(getHumanReloadTimeTicks());
    }

    @Override
    public void onVehicleStructureChanged() {
        super.onVehicleStructureChanged();
        discoverUpstreams();
        operators.discover(getHumanReloadTimeTicks());
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

    /**
     * 没有作业时读本机的那一台上游弹药架：优先可工作且有可分配库存的，其次第一台可工作的（空架照常再生）。<br>
     * 这是给显示、退弹与规格预览用的<b>当前</b>来源；某条作业实际用哪台架由预留表决定
     * （{@link #reservationRack}），两者不混用。
     */
    @Nullable
    private RegenAmmoRackSubsystem upstream() {
        RegenAmmoRackSubsystem fallback = null;
        for (RegenAmmoRackSubsystem rack : upstreams) {
            if (!rack.isOperational()) continue;
            if (rack.getAvailableCount() > 0) return rack;
            if (fallback == null) fallback = rack;
        }
        return fallback;
    }

    /**
     * 按身份找到握着这份预留的上游架。<br>
     * 来源绑定只有这一处：谁按这个身份预留，谁就是这次交付的来源。机构不另存作业到上游的映射。
     *
     * @param key 作业身份
     * @return 握着该身份预留的架；没有预留时返回 null
     */
    @Nullable
    private RegenAmmoRackSubsystem reservationRack(ReloadJobKey key) {
        for (RegenAmmoRackSubsystem rack : upstreams) {
            if (rack.reservedFor(key) > 0) return rack;
        }
        return null;
    }

    /**
     * 按身份找到某台上游架子系统（客户端按作业上同步的来源定位时用）。
     *
     * @param ref 身份；{@link SupplierRef#NONE} 一律不匹配
     * @return 对应实例；本机发现结果里没有它时返回 null
     */
    @Nullable
    private RegenAmmoRackSubsystem rackFor(SupplierRef ref) {
        if (ref.isNone()) return null;
        for (RegenAmmoRackSubsystem rack : upstreams) {
            if (SupplierRef.of(rack).equals(ref)) return rack;
        }
        return null;
    }

    // ==================== IAmmoSupplier 实现 ====================

    @Override
    @Nullable
    public IAmmoSupplier.Grant claimFor(IAmmoConsumer consumer, ReloadJobKey key) {
        if (getLevel().isClientSide() || key.isNone()) return null;
        sweepStaleClaims();
        if (!isSupplyOperational()) return null;
        if (!canSupplyMultiple() && hasOtherClaim(consumer)) return null;
        // 人工段已经按身份取得预留：这一步只补机构槽位，不重复预留，也不把来源换成另一台架
        RegenAmmoRackSubsystem reserved = reservationRack(key);
        IAmmoSupplier.Grant grant = reserved != null ? grantOf(reserved, key) : reserveOnSomeRack(key);
        if (grant == null) return null;
        claimedJobs.put(consumer, key);
        publishSlotOwner();
        return grant;
    }

    @Override
    @Nullable
    public IAmmoSupplier.Grant reserveFor(IAmmoConsumer consumer, ReloadJobKey key) {
        if (getLevel().isClientSide() || key.isNone()) return null;
        if (!isSupplyOperational()) return null;
        RegenAmmoRackSubsystem reserved = reservationRack(key);
        return reserved != null ? grantOf(reserved, key) : reserveOnSomeRack(key);
    }

    /**
     * 在可工作的上游架里挑一台预留整次作业的份额。
     *
     * @return 授予的来源与份额；一台都接不了时返回 null
     */
    @Nullable
    private IAmmoSupplier.Grant reserveOnSomeRack(ReloadJobKey key) {
        for (RegenAmmoRackSubsystem rack : upstreams) {
            if (!rack.isOperational()) continue;
            AmmoBelt spec = rack.getDeliverySpec();
            if (spec == null) continue;
            int granted = rack.reserve(key, spec.length());
            if (granted <= 0) continue;
            return new IAmmoSupplier.Grant(SupplierRef.of(rack), spec.freshCopy(), granted);
        }
        return null;
    }

    /**
     * 把某台架已有的预留读成一次授予。金额为 0（预留已被撤销或兑现）时返回 null。
     */
    @Nullable
    private IAmmoSupplier.Grant grantOf(RegenAmmoRackSubsystem rack, ReloadJobKey key) {
        AmmoBelt spec = rack.getDeliverySpec();
        int granted = rack.reservedFor(key);
        if (spec == null || granted <= 0) return null;
        return new IAmmoSupplier.Grant(SupplierRef.of(rack), spec.freshCopy(), granted);
    }

    @Override
    public void releaseClaim(IAmmoConsumer consumer, ReloadJobKey key) {
        if (key == null || key.isNone()) return;
        if (!getLevel().isClientSide()) {
            ReloadJobKey held = claimedJobs.get(consumer);
            if (held != null && held.equals(key)) {
                claimedJobs.remove(consumer);
                publishSlotOwner();
            }
        }
        // 预留记在来源架上：按身份整笔撤销，本机不需要记住是哪一台
        for (RegenAmmoRackSubsystem rack : upstreams) rack.releaseReservation(key);
    }

    @Override
    public boolean isClaimedByOther(IAmmoConsumer consumer) {
        if (canSupplyMultiple()) return false;
        sweepStaleClaims();
        for (IAmmoConsumer holder : claimedJobs.keySet()) {
            if (holder != consumer) return true;
        }
        return false;
    }

    @Override
    public boolean holdsCommitment(IAmmoConsumer consumer) {
        ReloadJob job = consumer.getReloadJob();
        if (job == null) return false;
        boolean human = job.stage() == ReloadJob.Stage.HUMAN;
        if (getLevel().isClientSide()) {
            // 人工段还没有机械槽位：承诺只是"这份预留还在"
            if (!human && !canSupplyMultiple() && !slotOwner.equals(job.key())) return false;
            return hasLocalReservedStock(job);
        }
        sweepStaleClaims();
        if (human) return reservationRack(job.key()) != null;
        return ownsClaim(consumer, job.key()) && reservationRack(job.key()) != null;
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

    /**
     * 剔除失效占用，按不存在处理。<br>
     * 失效包括消费者已摧毁／失活、它的指认已不指向本机、它的当前作业缺失或身份对不上。合法的暂停
     * （机构受阻、供能不足）不影响占用：作业还在，身份也还在。清理只删占用，预留在同一轮按身份撤销；
     * 不改库存、不补弹。
     * <p><b>调用线程：</b>主线程（每次读取或受理前）。
     */
    private void sweepStaleClaims() {
        Iterator<Map.Entry<IAmmoConsumer, ReloadJobKey>> it = claimedJobs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<IAmmoConsumer, ReloadJobKey> entry = it.next();
            if (isStaleClaim(entry.getKey(), entry.getValue())) {
                for (RegenAmmoRackSubsystem rack : upstreams) rack.releaseReservation(entry.getValue());
                it.remove();
                publishSlotOwner();
            }
        }
    }

    /** 这条占用是否已经不能代表一条真正在推进的作业。 */
    private boolean isStaleClaim(IAmmoConsumer consumer, ReloadJobKey key) {
        if (!(consumer instanceof AbstractSubsystem sub)) return true;
        if (sub.isDestroyed() || !sub.isActive()) return true;
        if (consumer.getCurrentSupplier() != this) return true;
        ReloadJob job = consumer.getReloadJob();
        return job == null || !job.key().equals(key);
    }

    /** 把独占机构的槽位拥有者推给客户端；并发机构没有独占槽位，不下发。 */
    private void publishSlotOwner() {
        if (getLevel().isClientSide() || canSupplyMultiple()) return;
        ReloadJobKey owner = claimedJobs.values().stream().findFirst().orElse(ReloadJobKey.NONE);
        getSynchedData().set(SLOT_OWNER_ID, owner);
    }

    @Override
    public boolean hasAmmo() {
        for (RegenAmmoRackSubsystem rack : upstreams) {
            if (rack.isOperational() && rack.getAvailableCount() > 0) return true;
        }
        return false;
    }

    /**
     * 交付能力：本机启用、未摧毁。<br>
     * 上游是否存在、是否可工作属于来源可用性，在受理与推进时按作业的来源判定，不并入本方法——
     * 否则一台暂时没接弹药架的机构会被报成"机械故障"。有没有可分配的弹由 {@link #hasAmmo()} 表达。
     */
    @Override
    public boolean isSupplyOperational() {
        return isActive() && !isDestroyed();
    }

    /**
     * 储备显示：上游各架<b>可分配</b>数量之和（{@code N - R}）。<br>
     * 已按身份预留的那一份不计入——它记在某条作业名下，不是可供新作业申请的数量；
     * 总量包含自己的份额，因此这里只用可分配数量，不单独再减一次。
     */
    @Override
    public int getRemainingCount() {
        int total = 0;
        for (RegenAmmoRackSubsystem rack : upstreams) total += rack.getAvailableCount();
        return total;
    }

    @Override
    @Nullable
    public ProjectileType getSuppliedType() {
        RegenAmmoRackSubsystem rack = upstream();
        return rack == null ? null : rack.getSuppliedType();
    }

    @Override
    @Nullable
    public AmmoBelt getDeliverySpec() {
        RegenAmmoRackSubsystem rack = upstream();
        return rack == null ? null : rack.getDeliverySpec();
    }

    /**
     * 机械段的基准时长：型号的 {@code reload_time} 秒换算成 tick。<br>
     * 消费者的作业在进入机械段时读一次并定死，段内不再改读本方法。
     */
    @Override
    public int getReloadTimeTicks() {
        return Math.max(1, (int) (attr.staticAttribute.getReloadTime() * 20f)); // 秒 → tick
    }

    /**
     * 人工段的基准时长：型号的 {@code human_reload_time} 秒换算成 tick；没有配置人工段时是 0。<br>
     * 这是标准人力下的秒数，岗位倍率与个人倍率由消费者按 {@link #getOperatorTimeScale} 乘上去。
     */
    @Override
    public int getHumanReloadTimeTicks() {
        float seconds = attr.staticAttribute.getHumanReloadTime();
        if (seconds <= 0f) return 0;
        return Math.max(1, (int) (seconds * 20f));
    }

    @Override
    public boolean canSupplyMultiple() {
        return attr.staticAttribute.isCanSupplyMultiple();
    }

    /**
     * 本 tick 能为该消费者的作业推进多少。<br>
     * 人工段只看人还在不在：时长已经把岗位倍率与个人倍率折进去，本机不再按秒数算推进量。
     * 机械段认两条：本机可工作、本机受理过这条作业且它的预留还在。客户端用同一套判据的镜像——人工段
     * 恒为 1（人员状态由服务端判定），机械段看下发的槽位拥有者（独占机构）与来源架剩余库存。
     */
    @Override
    public int getAdvanceTicks(IAmmoConsumer consumer) {
        if (!isSupplyOperational()) return 0;
        ReloadJob job = consumer.getReloadJob();
        if (job == null) return 0;
        boolean human = job.stage() == ReloadJob.Stage.HUMAN;
        if (getLevel().isClientSide()) {
            if (human) return 1;
            if (!canSupplyMultiple() && !slotOwner.equals(job.key())) return 0;
            return hasLocalReservedStock(job) ? 1 : 0;
        }
        if (human) return holdsOperator(consumer) ? 1 : 0;
        sweepStaleClaims();
        if (!ownsClaim(consumer, job.key())) return 0;
        return reservationRack(job.key()) != null ? 1 : 0;
    }

    /**
     * 客户端判断这条作业的预留是否还在：来源架可工作，且架内库存仍有它的份额。<br>
     * 客户端没有预留表（服务端不下发整张表），因此用"来源架库存不小于作业份额"这条保守判据；
     * 份额被撤销时服务端会结束作业并下发新的分配，本地这几 tick 的提前停推不会造成多发。
     */
    private boolean hasLocalReservedStock(ReloadJob job) {
        if (job.reservedCount() <= 0) return false;
        RegenAmmoRackSubsystem rack = rackFor(job.upstreamOrigin());
        if (rack == null) rack = upstream();
        if (rack == null || !rack.isOperational()) return false;
        return rack.getAmmoCount() >= job.reservedCount();
    }

    /**
     * 兑现一条作业的预留，把上游架的那份弹变成一份独立实例。<br>
     * 只有持有本机占用、且该身份仍有预留的作业能兑现——交付与受理是同一条作业的两端，
     * 替没被受理的请求取弹等于替别人的承诺扣库存。
     *
     * @return 一份独立实例的 Belt；本机不可交付、没有占用或该身份没有预留时返回 null
     */
    @Override
    @Nullable
    public AmmoBelt consumeReadyBelt(IAmmoConsumer consumer) {
        if (getLevel().isClientSide() || !isSupplyOperational()) return null;
        sweepStaleClaims();
        ReloadJob job = consumer.getReloadJob();
        if (job == null || !ownsClaim(consumer, job.key())) return null;
        RegenAmmoRackSubsystem rack = reservationRack(job.key());
        return rack == null ? null : rack.deliverReserved(job.key());
    }

    @Override
    public boolean returnBelt(AmmoBelt belt) {
        RegenAmmoRackSubsystem rack = upstream();
        return rack != null && rack.returnBelt(belt);
    }

    /** 交付规格的弹链名（HUD 弹链选项卡用）。仅在客户端调用：文本取自玩家语言的翻译键。 */
    @Override
    public String getLabel() {
        RegenAmmoRackSubsystem rack = upstream();
        return rack == null ? "empty" : rack.getLabel();
    }

    @Override
    public int getCapacity() {
        RegenAmmoRackSubsystem rack = upstream();
        return rack == null ? -1 : rack.getCapacity();
    }

    // ==================== 人工段 ====================

    /**
     * 为人工段认领一名执行者，或在已有更好的人时换人。<br>
     * 候选、代价与决胜由 {@link ReloadOperatorRoster} 给出：本机只交出自己的基准时长与作业身份。
     * <p>选不到人时返回 false：消费者据此暂停（作业与预留都还在），暂停超过上限才终止。
     * <p><b>调用线程：</b>主线程（服务端，消费者的每个装填 tick）。
     */
    @Override
    public boolean claimOperator(IAmmoConsumer consumer, ReloadJobKey key) {
        return operators.claim(consumer, key, getHumanReloadTimeTicks());
    }

    /** 这条作业的人工段是否仍有可用执行者；客户端恒为 true（人员状态由服务端判定）。 */
    @Override
    public boolean holdsOperator(IAmmoConsumer consumer) {
        return operators.holds(consumer);
    }

    @Override
    public void releaseOperator(IAmmoConsumer consumer, ReloadJobKey key) {
        operators.release(consumer, key);
    }

    /**
     * 已认领执行者的人工段时间倍率（岗位倍率 × 个人倍率）。<br>
     * 消费者拿它在换人后重算剩余时长；没有认领时按标准人力 1。
     */
    @Override
    public double getOperatorTimeScale(IAmmoConsumer consumer) {
        return operators.timeScale();
    }

    // ==================== 状态显示 ====================

    /**
     * 没有进行中的作业时，本机在忙哪一段：把上游架的再生阶段映射进来，让打空后的等待在界面上
     * 仍然可见，而不是一片熄灭。
     */
    @Override
    public ReloadPhase getRefillPhase() {
        if (!isSupplyOperational()) return ReloadPhase.IDLE;
        RegenAmmoRackSubsystem rack = upstream();
        if (rack == null) return ReloadPhase.IDLE;
        return rack.getRegenPhase();
    }

    @Override
    public float getRefillProgress() {
        RegenAmmoRackSubsystem rack = upstream();
        return rack == null ? 0f : rack.getRegenProgress();
    }

    @Override
    public int getRefillRemainingTicks() {
        RegenAmmoRackSubsystem rack = upstream();
        return rack == null ? 0 : rack.getRemainingRegenTicks();
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
     * 比较投递前后的 {@link #isSupplyOperational()} 而不是只看耐久，是因为"失效"还包含上游架被摧毁的
     * 情形，零耐久后的追加命中也不该重复触发清理。
     * <p>
     * <b>调用线程：</b>主线程（伤害在主线程结算）。
     */
    @Override
    public void onHurt(io.github.sweetzonzi.machine_max.common.mech.vehicle.event.subpart.SubPartDamageEvent.Pre event) {
        boolean wasOperational = isSupplyOperational();
        super.onHurt(event);
        if (wasOperational && !isSupplyOperational()) {
            soundCursors.clear();
        }
    }

    /**
     * 按消费者作业的当前进度处理本段的进度音效。<br>
     * 阈值 0 是段开始音、1 是本段走完的音，两者都不进越过循环；一次推进跨过多个阈值时按升序逐个播放。
     * 阶段切换先把上一段剩下的标记补完，再为新段重建游标；进度回退只把游标拉回当前进度，不重播。
     * <p><b>调用线程：</b>主线程（客户端 tick，由 {@link LauncherSubsystem#onTick()} 驱动）。
     *
     * @param consumer 正在装填的消费者
     * @param progress 该消费者当前作业的进度（0~1）
     */
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
        if (attr.reloadOperatorDiscoveryTargets.isEmpty()) return attr.ammoDiscoveryTargets;
        Map<String, List<String>> merged = new LinkedHashMap<>(attr.ammoDiscoveryTargets);
        merged.putAll(attr.reloadOperatorDiscoveryTargets);
        return merged;
    }
}
