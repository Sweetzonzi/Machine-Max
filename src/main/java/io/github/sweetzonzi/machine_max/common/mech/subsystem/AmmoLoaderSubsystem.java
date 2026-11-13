package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.sound.ISoundSpreader;
import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AmmoLoaderSubsystemAttr;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自动装弹机子系统。<br>
 * 只提供机械装填的<b>可工作条件</b>与<b>交付本身</b>：为每条作业回答本 tick 能推进多少
 * （{@link #getAdvanceTicks(IAmmoConsumer)}），并在作业走完后把上游弹药架的一份交付直接交给下游。
 * 装填进度归消费者的 {@link ReloadJob}，本机不持有一份并行计时；库存、容量与再生归
 * {@link RegenAmmoRackSubsystem}；本机不缓存 AmmoBelt 实例，扣减与写入在同一个主线程提交点完成。
 * <p>
 * 上游来源由本机自己按 {@code ammo_discovery_targets} 查询，只接受库存持有者（弹药架）；
 * 机构之间不互认上游，机械链因此不会成环。下游按 {@code ammo_discovery_inputs} 在供弹频道上
 * 发现本机，与直接查询弹药架的人力直取路径区分开。
 * <p>
 * 并发产能沿用布尔语义：{@code can_supply_multiple=false} 的机构一次只为一条作业推进
 * （独占且满速）；{@code true} 时不限槽位、各作业分别满速。能不能推进由本机受理过的占用决定
 * （{@link #claimFor}）：没被接单的请求拿到 {@code 0} 推进量，下个 tick 再问——一条输弹线一次只
 * 移动一份，因此没有等待队列。
 * <p>
 * 进度音效属于本机（机械段的声音定位在装弹机上），但<b>触发源是消费者的作业进度</b>：
 * 消费者的每个客户端 tick 把当前进度交给 {@link #tickStageSounds(IAmmoConsumer, float)}，
 * 已播阈值记在这里，因此作业重开或进度回退都不会把同一段音效再播一遍。
 */
public class AmmoLoaderSubsystem extends BasicSubsystem implements IAmmoSupplier, ISoundSpreader {

    public final AmmoLoaderSubsystemAttr attr;

    /**
     * 已受理作业的占用表：消费者 → 该作业的 {@code jobId}。<br>
     * 只在受理点（{@link #claimFor}）写入，由消费者按身份释放：旧作业的回调因此摘不掉新作业的占用。
     * 只记消费者引用与编号，不复制作业内容；不入存档也不同步，读档后第一次受理重新建立。
     */
    private final Map<IAmmoConsumer, Integer> claimedJobs = new LinkedHashMap<>();

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
     * 本次交付使用的上游弹药架：优先取可工作且有货的那一个，其次取可工作但空仓的（它会自行再生）。
     *
     * @return 上游弹药架；没有可工作的上游时返回 {@code null}
     */
    @Nullable
    private RegenAmmoRackSubsystem upstream() {
        RegenAmmoRackSubsystem fallback = null;
        for (RegenAmmoRackSubsystem rack : upstreams) {
            if (!rack.isOperational()) continue;
            if (rack.hasAmmo()) return rack;
            if (fallback == null) fallback = rack;
        }
        return fallback;
    }

    // ==================== IAmmoSupplier 实现 ====================

    @Override
    public boolean claimFor(IAmmoConsumer consumer, int jobId) {
        sweepStaleClaims();
        if (!isSupplyOperational()) return false;
        if (!hasAmmo()) return false;
        if (!canSupplyMultiple() && hasOtherClaim(consumer)) return false;
        claimedJobs.put(consumer, jobId);
        return true;
    }

    @Override
    public void releaseClaim(IAmmoConsumer consumer, int jobId) {
        Integer held = claimedJobs.get(consumer);
        if (held != null && held == jobId) {
            claimedJobs.remove(consumer);
        }
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

    /** 该消费者是否正持有本机的一条占用。 */
    private boolean ownsClaim(IAmmoConsumer consumer) {
        return claimedJobs.containsKey(consumer);
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
     * 失效包括消费者已摧毁／失活、它的指认已不指向本机、它的当前作业缺失或编号对不上。合法的暂停
     * （缺弹、机构受阻）不影响占用：作业还在，编号也还在。清理只删记录，不改库存、不补弹。
     * <p><b>调用线程：</b>主线程（每次读取或受理前）。
     */
    private void sweepStaleClaims() {
        Iterator<Map.Entry<IAmmoConsumer, Integer>> it = claimedJobs.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<IAmmoConsumer, Integer> entry = it.next();
            if (isStaleClaim(entry.getKey(), entry.getValue())) it.remove();
        }
    }

    /** 这条占用是否已经不能代表一条真正在推进的作业。 */
    private boolean isStaleClaim(IAmmoConsumer consumer, int jobId) {
        if (!(consumer instanceof AbstractSubsystem sub)) return true;
        if (sub.isDestroyed() || !sub.isActive()) return true;
        if (consumer.getCurrentSupplier() != this) return true;
        ReloadJob job = consumer.getReloadJob();
        return job == null || job.jobId() != jobId;
    }

    @Override
    public boolean hasAmmo() {
        RegenAmmoRackSubsystem rack = upstream();
        return rack != null && rack.hasAmmo();
    }

    /**
     * 交付能力：本机启用、未摧毁，且存在可工作的上游弹药架。<br>
     * 空架但健康仍算可交付——它照常再生，缺弹等待由状态与阶段表达。
     */
    @Override
    public boolean isSupplyOperational() {
        return isActive() && !isDestroyed() && upstream() != null;
    }

    @Override
    public int getRemainingCount() {
        RegenAmmoRackSubsystem rack = upstream();
        return rack == null ? 0 : rack.getAmmoCount();
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
     * 只有同时满足"本机可工作、上游有弹、本机受理过这条作业"时才推进；没被接单的请求（还在等槽位、
     * 或占用已失效）拿到 0，下个 tick 再问。
     */
    @Override
    public int getAdvanceTicks(IAmmoConsumer consumer) {
        if (!isSupplyOperational()) return 0;
        if (!hasAmmo()) return 0;
        sweepStaleClaims();
        return ownsClaim(consumer) ? 1 : 0;
    }

    /**
     * 取走一份交付：从上游架扣减并生成一份独立实例，直接交给消费者。<br>
     * 本机不缓存这份实例，扣减与写入在同一个主线程提交点完成。只有持有本机占用的作业能取走交付
     * ——交付与受理是同一条作业的两端，替没被接单的请求取弹等于替别人的承诺扣库存。
     *
     * @return 一份独立实例的 Belt；本机不可交付或上游已无弹时返回 null
     */
    @Override
    @Nullable
    public AmmoBelt consumeReadyBelt(IAmmoConsumer consumer) {
        if (!isSupplyOperational()) return null;
        sweepStaleClaims();
        if (!ownsClaim(consumer)) return null;
        RegenAmmoRackSubsystem rack = upstream();
        if (rack == null) return null;
        return rack.consumeBelt();
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
