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
 * 只提供机械装填的<b>可工作条件</b>、<b>占用与库存承诺</b>与<b>交付本身</b>：为每条作业回答本 tick 能
 * 推进多少（{@link #getAdvanceTicks(IAmmoConsumer)}），并在作业走完后把上游弹药架的那份预留兑现成交付。
 * 装填进度归消费者的 {@link ReloadJob}，本机不持有一份并行计时；库存、容量、预留与再生归
 * {@link RegenAmmoRackSubsystem}；本机不缓存 AmmoBelt 实例，扣减与写入在同一个主线程提交点完成。
 * <p>
 * 上游来源由本机自己按 {@code ammo_discovery_targets} 查询，只接受库存持有者（弹药架）；
 * 机构之间不互认上游，机械链因此不会成环。下游按 {@code ammo_discovery_inputs} 在供弹频道上
 * 发现本机，与直接查询弹药架的人力直取路径区分开。
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
 * 进度音效属于本机（机械段的声音定位在装弹机上），但<b>触发源是消费者的作业进度</b>：
 * 消费者的每个客户端 tick 把当前进度交给 {@link #tickStageSounds(IAmmoConsumer, float)}，
 * 已播阈值记在这里，因此作业重开或进度回退都不会把同一段音效再播一遍。
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
     * 只记消费者引用与身份，不复制作业内容；不入存档。
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
     * 每个消费者上次已播放音效的进度阈值（0.0~1.0），避免同一段重复触发；仅客户端有效。<br>
     * 游标是各客户端自己的量：进度回退（作业重开、上行校准把进度往回拉）只把游标拉回去，
     * 不重播已经越过的阈值。
     */
    private final Map<IAmmoConsumer, Float> lastPlayedProgressKeys = new LinkedHashMap<>();

    /**
     * 本机查询得到的上游弹药架，按到达顺序保存；结构变化时整体重建。<br>
     * 只含库存持有者，且不含本机自身。
     */
    private final List<RegenAmmoRackSubsystem> upstreams = new ArrayList<>();

    public AmmoLoaderSubsystem(ISubsystemHost owner, String name, AmmoLoaderSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
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

    // ==================== 上游发现 ====================

    @Override
    public void onAttach() {
        super.onAttach();
        discoverUpstreams();
    }

    @Override
    public void onVehicleStructureChanged() {
        super.onVehicleStructureChanged();
        discoverUpstreams();
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
        for (RegenAmmoRackSubsystem rack : upstreams) {
            if (!rack.isOperational()) continue;
            AmmoBelt spec = rack.getDeliverySpec();
            if (spec == null) continue;
            int granted = rack.reserve(key, spec.length());
            if (granted <= 0) continue;
            claimedJobs.put(consumer, key);
            publishSlotOwner();
            return new IAmmoSupplier.Grant(SupplierRef.of(rack), spec.freshCopy(), granted);
        }
        return null;
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
        if (getLevel().isClientSide()) {
            if (!canSupplyMultiple() && !slotOwner.equals(job.key())) return false;
            return hasLocalReservedStock(job);
        }
        sweepStaleClaims();
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
     * 消费者的作业在开工时读一次并定死，段内不再改读本方法。
     */
    @Override
    public int getReloadTimeTicks() {
        return Math.max(1, (int) (attr.staticAttribute.getReloadTime() * 20f)); // 秒 → tick
    }

    @Override
    public boolean canSupplyMultiple() {
        return attr.staticAttribute.isCanSupplyMultiple();
    }

    /**
     * 本 tick 能为该消费者的作业推进多少。<br>
     * 服务端认两条：本机可工作、本机受理过这条作业且它的预留还在。客户端用同一套判据的镜像——
     * 下发的槽位拥有者（独占机构）与来源架的剩余库存（预留是否还在），因此两端算出的推进量一致，
     * 不需要逐 tick 下发。
     */
    @Override
    public int getAdvanceTicks(IAmmoConsumer consumer) {
        if (!isSupplyOperational()) return 0;
        ReloadJob job = consumer.getReloadJob();
        if (job == null) return 0;
        if (getLevel().isClientSide()) {
            if (!canSupplyMultiple() && !slotOwner.equals(job.key())) return 0;
            return hasLocalReservedStock(job) ? 1 : 0;
        }
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
            lastPlayedProgressKeys.clear();
        }
    }

    /**
     * 按消费者作业的当前进度处理机械段的分段音效。<br>
     * 一次推进跨过多个阈值时按阈值升序逐个播放，不能只播最大的那个。进度回退（作业重开或上行校准
     * 把进度往回拉）只把游标拉回当前进度，不重播已越过的阈值。
     * <p><b>调用线程：</b>主线程（客户端 tick，由 {@link LauncherSubsystem#onTick()} 驱动）。
     *
     * @param consumer 正在装填的消费者
     * @param progress 该消费者当前作业的进度（0~1）
     */
    public void tickStageSounds(IAmmoConsumer consumer, float progress) {
        if (!getLevel().isClientSide()) return;
        Map<String, SoundEvent> sounds = attr.staticAttribute.getProgressSounds();
        if (sounds.isEmpty()) return;

        float clamped = Math.clamp(progress, 0f, 1f);
        Float lastKey = lastPlayedProgressKeys.get(consumer);
        if (lastKey != null && clamped < lastKey) {
            // 作业重开或校准回退：游标回到当前进度，已经播过的那一段不重播
            lastPlayedProgressKeys.put(consumer, clamped);
            return;
        }
        float from = lastKey == null ? 0f : lastKey;
        // 记下"阈值 → 音效"再看阈值排序，避免把键重新格式化成字符串去回查
        List<Map.Entry<Float, SoundEvent>> crossed = new ArrayList<>();
        for (Map.Entry<String, SoundEvent> entry : sounds.entrySet()) {
            try {
                float threshold = Float.parseFloat(entry.getKey());
                if (!Float.isFinite(threshold)) continue;
                if (threshold > from && threshold <= clamped) {
                    crossed.add(Map.entry(threshold, entry.getValue()));
                }
            } catch (NumberFormatException ignored) {
                // 非数值键不是进度标记，跳过
            }
        }
        crossed.sort(Map.Entry.comparingByKey());
        for (Map.Entry<Float, SoundEvent> entry : crossed) {
            playProgressSound(entry.getValue());
        }
        lastPlayedProgressKeys.put(consumer, clamped);
        // 已退场的消费者不再需要游标
        lastPlayedProgressKeys.keySet().removeIf(AmmoLoaderSubsystem::isConsumerGone);
    }

    /** 播放单次进度音效；使用 SpreadingSound 在子系统位置播放，带多普勒速度传播。 */
    private void playProgressSound(@Nullable SoundEvent sound) {
        if (sound == null || !getLevel().isClientSide()) return;
        playSpreadingSound(sound, SoundSource.NEUTRAL);
    }

    // ==================== 信号 ====================

    /** 本机接受的供弹服务频道：下游 Launcher 的发现查询按它到达本机。 */
    @Override
    public List<String> getAcceptedChannels() {
        return attr.ammoDiscoveryInputs;
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        return attr.ammoDiscoveryTargets;
    }
}
