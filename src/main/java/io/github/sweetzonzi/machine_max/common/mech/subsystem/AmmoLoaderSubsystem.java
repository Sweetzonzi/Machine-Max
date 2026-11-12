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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自动装弹机子系统。<br>
 * 只做机械装填那一段：为每个作业占一个槽位、按 {@code reload_time} 计时，计时结束的一次交付
 * 直接取自上游弹药架并交给下游，本机不落第二份库存。库存、容量与再生归
 * {@link RegenAmmoRackSubsystem}，作业进度归 Launcher。
 * <p>
 * 上游来源由本机自己按 {@code ammo_discovery_targets} 查询，只接受库存持有者（弹药架）；
 * 机构之间不互认上游，机械链因此不会成环。下游按 {@code ammo_discovery_inputs} 在供弹频道上
 * 发现本机，与直接查询弹药架的人力直取路径区分开。
 * <p>
 * 并发产能沿用布尔语义：{@code can_supply_multiple=false} 的机构一次只为一个作业分配槽位
 * （独占且满速）；{@code true} 时不限槽位、各作业分别满速。没排上的请求返回"已受理"但不起计时器，
 * 下个 tick 重发请求再试——一条输弹线一次只移动一份，因此没有等待队列。
 * <p>
 * 计时器记<b>已消耗 tick 数</b>并只在 {@link #onTick()} 里自增，取得与其他子系统一致的两条性质：
 * 停摆期间（未激活 / 已摧毁）计时不走，恢复后接着原进度；交付进度不跨存档，读档后重新发起。
 */
public class AmmoLoaderSubsystem extends BasicSubsystem implements IAmmoSupplier, ISoundSpreader {

    public final AmmoLoaderSubsystemAttr attr;

    /**
     * 每个消费者这次交付的<b>已装填 tick 数</b>，插入有序。<br>
     * 有键即"已轮到本消费者"；每个 {@link #onTick()} 自增 1，达到当前所需耗时即转入
     * {@link #readyConsumers}。判定对象是<b>当前</b> {@link #getReloadTimeTicks()}，
     * 因此型号属性中途改变装填耗时对在途那一份同样生效。
     */
    private final Map<IAmmoConsumer, Integer> deliveryLoadedTicks = new LinkedHashMap<>();

    /** 每个消费者的交付是否已就绪（计时完成、等消费者取走），插入有序 */
    private final Set<IAmmoConsumer> readyConsumers = new LinkedHashSet<>();

    /**
     * 当前绑着本机的消费者集合（认领），由服务端路由在选中本机时写入、发射器释放绑定时移除。<br>
     * 不入存档也不同步：读档后第一次路由按名字序重建。
     */
    private final Set<IAmmoConsumer> boundConsumers = new LinkedHashSet<>();

    /** 每个消费者上次已播放音效的进度阈值（0.0~1.0），避免同段重复触发；仅客户端有效 */
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
    public Set<IAmmoConsumer> getBoundConsumers() {
        return boundConsumers;
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

    @Override
    public int getReloadTimeTicks() {
        return Math.max(1, (int) (attr.staticAttribute.getReloadTime() * 20f)); // 秒 → tick
    }

    @Override
    public boolean canSupplyMultiple() {
        return attr.staticAttribute.isCanSupplyMultiple();
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

    /**
     * 请求一份交付，启动非阻塞计时器。<br>
     * 幂等性：同一消费者多次调用不会创建重复计时器。
     * <p>
     * {@code can_supply_multiple=false} 且已有别人在计时时，本次请求返回 true 但不启动计时器
     * ——它没有被排上，下个 tick 重发请求即可再试。
     *
     * @return true 表示请求已受理（已轮到它，或本次未被排上）；false 表示本机不可交付或上游无弹
     */
    @Override
    public boolean requestRound(IAmmoConsumer consumer) {
        if (!isSupplyOperational()) return false;
        if (isRoundScheduled(consumer)) return true;
        if (!hasAmmo()) return false;
        if (!canSupplyMultiple() && hasScheduledRound()) return true;
        deliveryLoadedTicks.put(consumer, 0);
        return true;
    }

    @Override
    public boolean isRoundScheduled(IAmmoConsumer consumer) {
        return deliveryLoadedTicks.containsKey(consumer) || readyConsumers.contains(consumer);
    }

    /**
     * 这次交付已装填多少 tick，供外部查询（{@code -1} = 未在交付）。
     * 与 {@link #onTick()} 的完成判定、{@link #getReloadProgress(IAmmoConsumer)} 的分子同源。
     */
    @Override
    public int getDeliveryElapsedTicks(IAmmoConsumer consumer) {
        Integer loaded = deliveryLoadedTicks.get(consumer);
        return loaded == null ? -1 : loaded;
    }

    /**
     * 是否已有消费者被开始交付。<br>
     * 计时中或已就绪待取都算"占着"：待取的那一份还在上游架里没被取走，同样不能被别人认领。
     */
    @Override
    public boolean hasScheduledRound() {
        return !deliveryLoadedTicks.isEmpty() || !readyConsumers.isEmpty();
    }

    /**
     * 取消该消费者尚未取走的交付。<br>
     * 就绪的那一份<b>仍记在上游架的库存里</b>（{@link #consumeReadyBelt} 才扣减），
     * 因此清掉计时器与就绪位即可，不需要向上游退还——补了会让同一份被算两次。
     */
    @Override
    public void cancelRound(IAmmoConsumer consumer) {
        deliveryLoadedTicks.remove(consumer);
        readyConsumers.remove(consumer);
        lastPlayedProgressKeys.remove(consumer);
    }

    @Override
    public boolean isRoundReady(IAmmoConsumer consumer) {
        if (!isSupplyOperational()) return false;
        if (readyConsumers.contains(consumer)) return true;
        if (deliveryLoadedTicks.containsKey(consumer)) return false;
        // 未被排上（或从未请求）：顺手再请求一次，由本机决定这次能否排上
        requestRound(consumer);
        return false;
    }

    /**
     * 取走已就绪的交付：从上游架扣减并生成一份独立实例，直接交给消费者。<br>
     * 本机不缓存这份实例，扣减与写入在同一个主线程提交点完成。
     *
     * @return 一份独立实例的 Belt；未就绪、本机不可交付或上游已无弹时返回 null
     */
    @Override
    @Nullable
    public AmmoBelt consumeReadyBelt(IAmmoConsumer consumer) {
        if (!isSupplyOperational()) return null;
        if (!readyConsumers.remove(consumer)) return null;
        lastPlayedProgressKeys.remove(consumer);
        RegenAmmoRackSubsystem rack = upstream();
        if (rack == null) return null;
        return rack.consumeBelt();
    }

    @Override
    public boolean returnBelt(AmmoBelt belt) {
        RegenAmmoRackSubsystem rack = upstream();
        return rack != null && rack.returnBelt(belt);
    }

    // ==================== 状态显示 ====================

    /**
     * 机构当前为这个消费者报的工作状态。<br>
     * 本机计时中或已就绪时报自己的档；否则把上游架的再生阶段映射进来，让打空后的等待在界面上
     * 仍然可见（{@code RELOADING}），而不是一片熄灭。
     */
    @Override
    public SupplierStatus getStatus(IAmmoConsumer consumer) {
        if (!isSupplyOperational()) return SupplierStatus.EMPTY;
        if (readyConsumers.contains(consumer)) return SupplierStatus.READY;
        if (deliveryLoadedTicks.containsKey(consumer)) return SupplierStatus.RELOADING;
        RegenAmmoRackSubsystem rack = upstream();
        if (rack == null) return SupplierStatus.EMPTY;
        if (rack.getRegenPhase() != ReloadPhase.IDLE) return SupplierStatus.RELOADING;
        return rack.hasAmmo() ? SupplierStatus.READY : SupplierStatus.EMPTY;
    }

    @Override
    public ReloadPhase getReloadPhase(IAmmoConsumer consumer) {
        if (!isSupplyOperational()) return ReloadPhase.IDLE;
        if (isRoundScheduled(consumer)) return ReloadPhase.LOADING;
        RegenAmmoRackSubsystem rack = upstream();
        return rack == null ? ReloadPhase.IDLE : rack.getRegenPhase();
    }

    @Override
    public int getRemainingReloadTicks(IAmmoConsumer consumer) {
        if (!isSupplyOperational()) return 0;
        if (isRoundScheduled(consumer)) {
            if (readyConsumers.contains(consumer)) return 0;
            int elapsed = Math.max(0, getDeliveryElapsedTicks(consumer));
            return Math.max(0, getReloadTimeTicks() - elapsed);
        }
        RegenAmmoRackSubsystem rack = upstream();
        return rack == null ? 0 : rack.getRemainingRegenTicks();
    }

    /**
     * 装填进度（0~1）：为自己计时时报已消耗 tick ÷ <b>当前</b>耗时；否则报上游架的再生进度。
     * 分母现算，因此加速／减速对在途那一份同样生效，进度也不会越界。
     */
    @Override
    public float getReloadProgress(IAmmoConsumer consumer) {
        if (!isSupplyOperational()) return 0f;
        Integer loaded = deliveryLoadedTicks.get(consumer);
        if (loaded != null) {
            int total = getReloadTimeTicks();
            return total <= 0 ? 0f : Math.clamp((float) loaded / total, 0f, 1f);
        }
        RegenAmmoRackSubsystem rack = upstream();
        return rack == null ? 0f : rack.getRegenProgress();
    }

    // ==================== 核心逻辑 ====================

    @Override
    public void onTick() {
        super.onTick();
        if (!isActive() || isDestroyed()) return;

        // 清掉已退场的消费者，别让它们的交付占着输弹线
        pruneGoneConsumers();

        // 推进交付：已装填量 +1，达到当前所需耗时的消费者转入待取
        int reloadTicks = getReloadTimeTicks();
        Iterator<Map.Entry<IAmmoConsumer, Integer>> iter = deliveryLoadedTicks.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<IAmmoConsumer, Integer> entry = iter.next();
            int loaded = entry.getValue() + 1;
            if (loaded < reloadTicks) {
                entry.setValue(loaded);
                continue;
            }
            iter.remove();
            readyConsumers.add(entry.getKey());
            lastPlayedProgressKeys.remove(entry.getKey()); // 计时完成，清理该消费者的音效追踪
        }

        if (getLevel().isClientSide()) tickProgressSounds();
    }

    /**
     * 清掉已经不能再来取弹（被摧毁或失活）的消费者的交付状态。<br>
     * 消费者退场时不会通知本机，而它的计时器／就绪位会一直占着输弹线，使本机对后来者永久不可用。
     */
    private void pruneGoneConsumers() {
        deliveryLoadedTicks.keySet().removeIf(AmmoLoaderSubsystem::isConsumerGone);
        readyConsumers.removeIf(AmmoLoaderSubsystem::isConsumerGone);
        lastPlayedProgressKeys.keySet().removeIf(AmmoLoaderSubsystem::isConsumerGone);
        boundConsumers.removeIf(AmmoLoaderSubsystem::isConsumerGone);
    }

    /** 该消费者是否已经不能再来取弹（被摧毁或失活）。 */
    private static boolean isConsumerGone(IAmmoConsumer consumer) {
        return consumer instanceof AbstractSubsystem sub && (sub.isDestroyed() || !sub.isActive());
    }

    /**
     * 降低伤害入口：供给能力因本击失效时立刻释放全部交付承诺。<br>
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
            deliveryLoadedTicks.clear();
            readyConsumers.clear();
            lastPlayedProgressKeys.clear();
        }
    }

    // ==================== 装填进度音效 ====================

    /**
     * 处理每消费者的进度分段音效：按当前计时进度查找 ≤ 进度的最大阈值，越过新阈值时播放。<br>
     * 只认"越过更高的阈值"：耗时变长时进度会回退，此时不该把已经播过的音效再播一遍。
     * <p><b>调用线程：</b>主线程（{@link #onTick()}，仅客户端）。
     */
    private void tickProgressSounds() {
        Map<String, SoundEvent> sounds = attr.staticAttribute.getProgressSounds();
        if (sounds.isEmpty()) return;

        int totalTicks = getReloadTimeTicks();
        if (totalTicks <= 0) return;

        for (Map.Entry<IAmmoConsumer, Integer> entry : deliveryLoadedTicks.entrySet()) {
            IAmmoConsumer consumer = entry.getKey();
            float progress = Math.clamp((float) entry.getValue() / totalTicks, 0f, 1f);
            Float lastKey = lastPlayedProgressKeys.get(consumer);
            Float matchedKey = findBestProgressKey(sounds, progress);
            if (matchedKey != null && (lastKey == null || matchedKey > lastKey)) {
                lastPlayedProgressKeys.put(consumer, matchedKey);
                playProgressSound(sounds.get(String.valueOf(matchedKey)));
            }
        }

        // 清理已不在计时中的消费者追踪记录
        lastPlayedProgressKeys.keySet().removeIf(c -> !deliveryLoadedTicks.containsKey(c));
    }

    /**
     * 在进度音效映射中查找 ≤ 当前进度的最大键。
     *
     * @param sounds          进度音效映射（key=进度浮点字符串）
     * @param currentProgress 当前进度（0.0~1.0）
     * @return 最佳匹配的进度阈值，无匹配时返回 null
     */
    @Nullable
    private static Float findBestProgressKey(Map<String, SoundEvent> sounds, float currentProgress) {
        return sounds.keySet().stream()
                .map(k -> {
                    try {
                        return Float.parseFloat(k);
                    } catch (NumberFormatException e) {
                        return Float.NaN;
                    }
                })
                .filter(k -> !Float.isNaN(k) && k <= currentProgress)
                .max(Float::compareTo)
                .orElse(null);
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
