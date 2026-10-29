package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.api.SpreadingSoundHelper;
import cn.solarmoon.spark_core.sound.ISoundSpreader;
import cn.solarmoon.spark_core.sound.ISpreadingSoundPlayer;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.SparkMathKt;
import cn.solarmoon.spark_core.physics.PenetrationKey;
import cn.solarmoon.spark_core.util.TaskSubmitOffice;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.mech.energy.EnergyGrid;
import io.github.sweetzonzi.machine_max.common.mech.explosion.CookoffComposition;
import io.github.sweetzonzi.machine_max.common.mech.explosion.ExplosionManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.RegenLoaderSubsystemAttr;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 再生装弹机子系统。<br>
 * 统一能量武器（激光、爆能束）和街机风格再生武器（导弹冷却再生）的弹药供给模型。<br>
 * 实现 {@link IAmmoSupplier} 接口，不实现 {@link IAmmoConsumer}（不消耗外部弹药）。<br>
 * <p>
 * 两种再生模式：
 * <ul>
 *   <li>{@code regenRoundByRound=true} — 随打随产：每消耗一发立即开始再生下一发</li>
 *   <li>{@code regenRoundByRound=false} — 批量产出：弹仓全空后触发再生，一次性回满</li>
 * </ul>
 * <p>
 * 弹链机制：维护一个有序的循环弹种序列，consumeReadyRound 时逐发推进指针。
 * 单一弹种退化为长度为 1 的弹链。
 * <p>
 * 多消费者：{@code can_supply_multiple=false} 时整台装弹机同一时刻只推进一个消费者的输送计时器，
 * 没排上的消费者在 {@link #requestRound(IAmmoConsumer)} 中只得到"已受理"，不起计时器，
 * 下个 tick 重发请求再试，直到前一个消费者取走弹药腾出输弹线——一条输弹链一次只能移动一发。
 * {@code can_supply_multiple=true} 时所有消费者的计时器并行推进，各自拿到弹链上的下一发。<br>
 * 因此不存在等待队列：{@link #deliveryLoadedTicks} 里有没有本消费者，就是"轮到没轮到"。
 * <p>
 * 计时器一律记<b>已消耗/剩余量</b>并只在 {@link #onTick()} 里自增自减，不记"开始时刻"：开始时刻是
 * 本机 {@link #tickCount} 的读数，跨存档没有意义；而计数天然满足两条要求——停摆期间（未激活 /
 * 已摧毁，{@link #onTick()} 提前返回）计时不走，恢复后接着原进度；存档直接存那个计数本身。
 * 装填耗时在中途变化（加速、减速）时，进度按"已消耗 ÷ 当前耗时"现算，
 * 与 {@link #batchTotalTicks()} 的现算口径一致。
 * <p>
 * 计时器与就绪位都用插入有序的普通集合：本子系统的读写全在主线程（自身 tick、武器控制器的路由、
 * HUD 刷新都在主线程），不需要并发容器。
 * <p>
 * 能量消耗通过 {@link EnergyGrid#consumeEnergy(float)} 瞬时抽取，不实现 {@code IEnergyConsumer} 接口。
 */
public class RegenLoaderSubsystem extends BasicSubsystem implements IAmmoSupplier, ISoundSpreader {

    public final RegenLoaderSubsystemAttr attr;

    /**
     * 当前弹药计数（网络同步）
     */
    private static final EntityDataAccessor<Integer> AMMO_COUNT_ID =
            SynchedEntityData.defineId(RegenLoaderSubsystem.class, EntityDataSerializers.INT);

    /**
     * BELT_INDEX 的当前取值是否合法由 {@link #normalizedBeltIndex()} 保证。
     * <p>
     * 弹链指针的唯一载体是 {@link #BELT_INDEX} 这个 {@code SynchedEntityData} 访问器，
     * 服务端与客户端读同一个来源：服务端每发写入后按增量推送自动送达客户端，
     * 客户端因此不需要自行从存档恢复起点。
     */
    private static final EntityDataAccessor<Integer> BELT_INDEX =
            SynchedEntityData.defineId(RegenLoaderSubsystem.class, EntityDataSerializers.INT);

    /**
     * 再生进度（0.0 ~ 1.0+，累积超过 1.0 时产出一发）
     */
    private float regenProgress = 0f;

    /**
     * 批量再生的已消耗 tick 数（regenRoundByRound=false 时使用）。<br>
     * 按<b>已消耗量</b>记，与 {@link #deliveryLoadedTicks} 同一套记账方式；
     * 进度与总时长都由 {@link #batchTotalTicks()} 按当前再生速率现算，因此速率中途变化对进行中的
     * 这一批同样生效。停摆期间不自增，存档直接存这个数。
     */
    private int batchElapsedTicks = 0;

    /**
     * 是否正在批量冷却中
     */
    private boolean isBatchReloading = false;

    /**
     * 再生延迟剩余 tick，上次输送弹药后需等待此时间才恢复再生
     */
    private int regenDelayRemainingTicks = 0;

    // ——— 装填进度音效状态（仅客户端有效） ———

    /**
     * 每个消费者上次已播放音效的进度阈值（0.0~1.0），避免同段重复触发。<br>
     * 由装填音效在 tick 中维护，{@link #consumeReadyRound(IAmmoConsumer)} 里清理；
     * 两者都在主线程（取弹由发射器在 tick 中发起），插入有序即可。
     */
    private final Map<IAmmoConsumer, Float> lastPlayedProgressKeys = new LinkedHashMap<>();

    /**
     * 批量冷却模式下上次已播放音效的进度阈值
     */
    @Nullable
    private Float lastPlayedBatchProgressKey;

    // ——— 多消费者支持 ———

    /**
     * 每个消费者这次输送的<b>已装填 tick 数</b>，插入有序。<br>
     * 有键即"已轮到本消费者"；每个 {@link #onTick()} 自增 1，达到当前所需装填耗时即转入
     * {@link #readyConsumers}。计数只增不改，停摆期间（未激活 / 已摧毁）不增长。
     * <p>
     * 判定对象是<b>当前</b> {@link #getReloadTimeTicks()}，不额外记录受理时的耗时：
     * 外部属性中途改变装填耗时，对在途那一发同样生效（变快就提前完成，变慢就多等几 tick）。
     */
    private final Map<IAmmoConsumer, Integer> deliveryLoadedTicks = new LinkedHashMap<>();

    /** 每个消费者的弹药是否已就绪（输送已完成、等消费者取走），插入有序 */
    private final Set<IAmmoConsumer> readyConsumers = new LinkedHashSet<>();

    public RegenLoaderSubsystem(ISubsystemHost owner, String name, RegenLoaderSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(AMMO_COUNT_ID, 0);
        builder.define(BELT_INDEX, 0);
    }

    /**
     * 获取当前弹药计数
     */
    public int getAmmoCount() {
        return getSynchedData().get(AMMO_COUNT_ID);
    }

    /**
     * 设置当前弹药计数
     */
    private void setAmmoCount(int count) {
        getSynchedData().set(AMMO_COUNT_ID, count);
    }

    // ==================== IAmmoSupplier 实现 ====================

    @Override
    public boolean hasAmmo() {
        return getAmmoCount() > 0;
    }

    @Override
    public int getRemainingCount() {
        return getAmmoCount();
    }

    @Override
    @Nullable
    public ProjectileType getSuppliedType() {
        if (attr.projectileTypes.isEmpty()) return null;
        return ProjectileType.get(getLevel(), attr.projectileTypes.get(normalizedBeltIndex()));
    }

    @Override
    public boolean isRoundByRound() {
        return attr.staticAttribute.getReloadTime() > 0;
    }

    @Override
    public int getReloadTimeTicks() {
        return (int) (attr.staticAttribute.getReloadTime() * 20f); // 秒 → tick
    }

    @Override
    public boolean canSupplyMultiple() {
        return attr.staticAttribute.isCanSupplyMultiple();
    }

    @Override
    public String getLabel() {
        return getBeltLabel();
    }

    /**
     * 弹链名（HUD 弹链选项卡用）。<br>
     * 层级：① {@code belt_label} 非空 → 按字面返回；② 取弹链首发弹种的展示名；
     * ③ 首发尚未解码为投射物类型 → 取该资源的路径；④ 弹链为空 → {@code "---"}。<br>
     * 不读指针，因此同一弹链的返回值恒定。
     * <p>
     * <b>调用线程：</b>主线程（HUD 刷新）。<b>仅在客户端调用</b>（弹种展示名取玩家语言的文本）。
     */
    public String getBeltLabel() {
        if (!attr.getBeltLabel().isEmpty()) return attr.getBeltLabel();
        if (attr.projectileTypes.isEmpty()) return "---";
        return roundLabel(attr.projectileTypes.getFirst());
    }

    /**
     * 弹链指针当前指向的弹种展示名（HUD 弹种槽用）。<br>
     * 指针来源是 {@link #BELT_INDEX}（客户端可读，见 {@link #normalizedBeltIndex()}）；
     * 该发尚未解码为投射物类型时取该资源的路径；弹链为空时返回空串（弹种槽留空）。
     * <p>
     * <b>调用线程：</b>主线程（HUD 刷新）。<b>仅在客户端调用</b>。
     */
    public String getCurrentRoundLabel() {
        if (attr.projectileTypes.isEmpty()) return "";
        return roundLabel(attr.projectileTypes.get(normalizedBeltIndex()));
    }

    /**
     * 单个弹种的展示名：解码得到投射物类型时用其语言键文本，否则退回资源的路径。
     */
    private String roundLabel(ResourceLocation projectileTypeId) {
        ProjectileType type = ProjectileType.get(getLevel(), projectileTypeId);
        return type != null ? type.getDisplayLabel() : projectileTypeId.getPath();
    }

    /**
     * 请求一发弹药，启动非阻塞输送计时器。<br>
     * 幂等性：同一 consumer 多次调用不会创建重复计时器。
     * <p>
     * {@code can_supply_multiple=false} 且已有别人在输送时，本次请求返回 true 但不启动计时器
     * ——它没有被排上，下个 tick 重发请求即可再试。整个流程只需 {@link #deliveryLoadedTicks}
     * 一张表表达"谁的输送在进行中"，因此不记录等待队列。
     *
     * @return true 表示请求已受理（已轮到它，或本次未被排上）；false 表示弹仓无弹
     */
    @Override
    public boolean requestRound(IAmmoConsumer consumer) {
        // 已在输送中（计时中或待取）→ 幂等返回
        if (isRoundScheduled(consumer)) return true;

        // 无弹药：不受理，等再生到位后由消费者再请求
        if (getAmmoCount() <= 0) return false;

        // 独占装弹机：已有别人占着这条输弹线，本次不排上
        if (!canSupplyMultiple() && hasScheduledRound()) return true;

        startDelivery(consumer);
        return true;
    }

    /**
     * 启动该消费者的输送计时器；{@code reload_time=0} 时直接转入待取。
     */
    private void startDelivery(IAmmoConsumer consumer) {
        if (getReloadTimeTicks() > 0) {
            deliveryLoadedTicks.put(consumer, 0);
        } else {
            readyConsumers.add(consumer); // 瞬时交付
        }
    }

    @Override
    public boolean isRoundScheduled(IAmmoConsumer consumer) {
        return deliveryLoadedTicks.containsKey(consumer) || readyConsumers.contains(consumer);
    }

    /**
     * 这次输送已装填多少 tick，供外部查询（{@code -1} = 未在输送）。<br>
     * 与 {@link #onTick()} 的完成判定、{@link #getReloadProgress(IAmmoConsumer)} 的分子同源。
     */
    @Override
    public int getDeliveryElapsedTicks(IAmmoConsumer consumer) {
        Integer loaded = deliveryLoadedTicks.get(consumer);
        return loaded == null ? -1 : loaded;
    }

    /**
     * 是否已有消费者被开始交付。<br>
     * 交付计时或已就绪待取都算"占着"：待取的那发还在弹仓里没被取走，同样不能被别人认领。
     */
    @Override
    public boolean hasScheduledRound() {
        return !deliveryLoadedTicks.isEmpty() || !readyConsumers.isEmpty();
    }

    /**
     * 清掉已经不能再来取弹（被摧毁或失活）的消费者的交付状态。<br>
     * 消费者退场时不会通知供给者，而它的计时器/就绪位会一直占着输弹线，
     * 使这台装弹机对后来者永久不可用；因此在 tick 里自行清理。
     */
    private void pruneGoneConsumers() {
        deliveryLoadedTicks.keySet().removeIf(RegenLoaderSubsystem::isConsumerGone);
        readyConsumers.removeIf(RegenLoaderSubsystem::isConsumerGone);
        lastPlayedProgressKeys.keySet().removeIf(RegenLoaderSubsystem::isConsumerGone);
    }

    /**
     * 该消费者是否已经不能再来取弹（被摧毁或失活）。
     */
    private static boolean isConsumerGone(IAmmoConsumer consumer) {
        return consumer instanceof AbstractSubsystem sub && (sub.isDestroyed() || !sub.isActive());
    }

    /**
     * 取消该消费者尚未取走的交付。<br>
     * 就绪位里的那发<b>仍记在弹仓计数里</b>（{@link #takeRoundFromMagazine()} 才扣减），
     * 因此清掉计时器与就绪位即可，不需要 {@code returnRound} 补弹——补了会让同一发被算两次。
     * 取消后输弹线立刻空出，等待中的消费者下次请求即可被排上。
     */
    @Override
    public void cancelRound(IAmmoConsumer consumer) {
        deliveryLoadedTicks.remove(consumer);
        readyConsumers.remove(consumer);
        lastPlayedProgressKeys.remove(consumer);
    }

    @Override
    public boolean isRoundReady(IAmmoConsumer consumer) {
        if (readyConsumers.contains(consumer)) {
            return true;
        } else if (deliveryLoadedTicks.containsKey(consumer)) {
            return false; // 输送尚未完成
        } else {
            // 未被排上（或从未请求）：顺手再请求一次，由供给者决定这次能否排上
            requestRound(consumer);
            return false;
        }
    }

    /**
     * 取走已就绪的弹药。<br>
     * 弹链指针在这里推进：一发对应弹链上的一格，因此只有真正被取走的弹药才会让指针前进。
     * 取走后输弹线立刻空出，未被排上的消费者下次请求即可被排上。
     *
     * @return 弹药类型；消费者未就绪、弹仓已空或弹链未定义时返回 null
     */
    @Override
    @Nullable
    public ProjectileType consumeReadyRound(IAmmoConsumer consumer) {
        if (!readyConsumers.contains(consumer)) return null;
        readyConsumers.remove(consumer);
        lastPlayedProgressKeys.remove(consumer); // 弹药已消费，清理该消费者的音效追踪

        return takeRoundFromMagazine();
    }

    /**
     * 从弹仓取出一发并推进弹链指针。
     *
     * @return 弹药类型；弹仓已空或弹链未定义时返回 null
     */
    @Nullable
    private ProjectileType takeRoundFromMagazine() {
        if (getAmmoCount() <= 0) return null;
        if (!getLevel().isClientSide()) // 仅服务端更新弹药计数
            setAmmoCount(getAmmoCount() - 1);

        // 弹链为空时无弹可发（内容包未定义弹链，但存档仍带弹药计数）
        if (attr.projectileTypes.isEmpty()) return null;

        // 取当前弹链位置的弹药，然后推进指针
        int beltIndex = normalizedBeltIndex();
        ProjectileType type = ProjectileType.get(getLevel(), attr.projectileTypes.get(beltIndex));
        // 推进结果写入 BELT_INDEX：两端都以该访问器为指针载体，服务端写入后自动同步到客户端
        getSynchedData().set(BELT_INDEX, (beltIndex + 1) % attr.projectileTypes.size());

        // 弹药已输送，启动再生延迟计时器（若 regenDelay > 0）
        float delay = attr.staticAttribute.getRegenDelay();
        if (delay > 0f) {
            regenDelayRemainingTicks = (int) (delay * 20f); // 秒 → tick
            // 延迟刷新时重置再生进度；但弹药已为0时保留已有进度（避免空仓丢进度）
            if (getAmmoCount() > 0) {
                regenProgress = 0f;
            }
        }

        return type;
    }

    // ==================== 殉爆 ====================

    /**
     * 摧毁边沿：按<b>实际库存</b>合成一次殉爆。
     * <p>
     * 触发点是框架的子系统摧毁边沿（{@link AbstractSubsystem#onTick()}），此时同步数据有效、
     * 弹药计数尚未被本次起爆消耗。宿主销毁、卸载与脱离不补发本回调：{@link AbstractSubsystem#onTick()}
     * 已按宿主状态拦下摧毁边沿，这里再检查一次以免被独立调用误触发。
     * <p>
     * 客户端也会收到 {@code DATA_DESTROYED_ID} 的同步并回调本方法，但起爆只在服务端发生：
     * 客户端首行即返回，只保留基类的音效等表现。
     * <p>
     * <b>调用线程：</b>主线程。
     */
    @Override
    public void onDestroyed() {
        super.onDestroyed();
        if (getLevel().isClientSide()) return;
        detonateStoredAmmo();
    }

    /**
     * 库存殉爆：读取当前弹链构成、合成贡献、清空库存并提交一次普通爆炸。
     * <p>
     * 合成范围是这一个库存单元：从当前 {@code BELT_INDEX} 起按完整循环加尾段统计实际发数，
     * 混装弹种全部合成一次。每发弹药计入发射药贡献（弹药根对象的 {@code cookoff}）与各战斗部的
     * 被诱爆贡献；零贡献弹种照样计入库存总数，但不改变 D、S、W。
     * <p>
     * {@code P、D、J} 全为零时没有可生成爆炸的贡献，不消耗库存、不起爆。
     * 一旦判定需要起爆，本次快照的全部剩余弹药都被消耗（含零贡献弹种），
     * 弹药计数归零且不产生掉落物。
     * <p>
     * <b>调用线程：</b>主线程（子系统摧毁边沿）。
     */
    private void detonateStoredAmmo() {
        int ammoCount = getAmmoCount();
        if (ammoCount <= 0 || attr.projectileTypes.isEmpty()) return;

        // 弹仓是计数而不是物品容器：实际构成按当前弹链指针起算，统计完整循环与不足一轮的尾段
        int beltSize = attr.projectileTypes.size();
        int beltIndex = normalizedBeltIndex();
        Map<ResourceLocation, Integer> roundsByType = new LinkedHashMap<>();
        for (int i = 0; i < ammoCount; i++) {
            ResourceLocation typeId = attr.projectileTypes.get((beltIndex + i) % beltSize);
            roundsByType.merge(typeId, 1, Integer::sum);
        }

        CookoffComposition composition = new CookoffComposition();
        for (Map.Entry<ResourceLocation, Integer> entry : roundsByType.entrySet()) {
            ProjectileType type = ProjectileType.get(getLevel(), entry.getKey());
            composition.addStoredRound(type, entry.getValue());
        }
        if (composition.isEmpty()) return;

        // 起爆位置：最近有效受击指认的 HitBox 局部形心；无有效来源回退宿主质心
        Vector3f origin = getLastDamageOrigin();
        // 爆点落在自身命中箱内部，该命中箱必须豁免，否则近距离满值穿深会吃掉整发爆炸
        Set<PenetrationKey> ignoredKeys = getCookoffIgnoredKeys();
        // 先消耗库存再交给普通爆炸服务：本次起爆后该单元不再有弹药可供给
        consumeAllStoredAmmo();
        ExplosionManager.get(getLevel()).detonate(
                origin, composition.toExplosionParams(), getLevel().random.nextLong(), ignoredKeys);
    }

    /**
     * 起爆消耗：实际弹药计数归零，并清空所有在途与待取的输送承诺，
     * 防止起爆后仍有消费者取走已被烧掉的弹药。
     */
    private void consumeAllStoredAmmo() {
        setAmmoCount(0);
        deliveryLoadedTicks.clear();
        readyConsumers.clear();
        lastPlayedProgressKeys.clear();
    }

    @Override
    public int getCapacity() {
        return attr.staticAttribute.getMagazineCapacity();
    }

    /**
     * 再生装弹机当前工作状态（per-consumer）。<br>
     * 注意这里不表达"有没有被排上"——那由 {@link #isRoundScheduled(IAmmoConsumer)} 回答。
     * <p>
     * 弹仓空了但正在逐发再生时报 {@code RELOADING}：这一档是给显示用的——按 {@code regen_per_minute}
     * 产弹的型号（{@code reload_time=0}）没有输送计时器，若照"没弹就是 EMPTY"报，打空后的整段
     * 再生时间在界面上是一片熄灭，看不出它正在回弹。
     */
    @Override
    public SupplierStatus getStatus(IAmmoConsumer consumer) {
        if (readyConsumers.contains(consumer)) return SupplierStatus.READY;
        if (deliveryLoadedTicks.containsKey(consumer)) return SupplierStatus.RELOADING;
        // 批量冷却：弹仓归零后整批再生，此时每个消费者都在等同一批弹药
        if (isBatchReloading) return SupplierStatus.RELOADING;
        if (getAmmoCount() <= 0) {
            return isRegenerating() ? SupplierStatus.RELOADING : SupplierStatus.EMPTY;
        }
        return SupplierStatus.READY;
    }

    /**
     * 是否正在逐发再生中：逐发再生模式、且已经累积了一点再生进度。
     * <p>
     * 只看进度不看速率：速率大于 0 而进度仍为 0 时（刚被取走那一 tick）报 EMPTY 与报 RELOADING
     * 都成立，但"零进度"在界面上本来就是全暗的一格，不需要额外区分。
     */
    private boolean isRegenerating() {
        return attr.staticAttribute.isRegenRoundByRound() && regenProgress > 0f;
    }

    /**
     * 装填进度（0~1）：已消耗 tick ÷ <b>当前</b>装填耗时。<br>
     * 分母现算而不是用请求受理时的耗时，因此加速/减速 buff 对在途那一发同样生效，进度也不会越界。
     * 未在为本消费者输送时按弹仓状态报：逐发再生模式下报<b>再生进度</b>（下一发还有多久，
     * {@code reload_time=0} 的型号没有别的进度可报），其余报"余量 / 容量"这一储备水平。
     */
    @Override
    public float getReloadProgress(IAmmoConsumer consumer) {
        int cap = attr.staticAttribute.getMagazineCapacity();
        if (cap <= 0) return 0f;
        if (isBatchReloading) {
            // 批量冷却：已消耗量在当前总量里的占比
            return progressOf(batchElapsedTicks, batchTotalTicks());
        }
        if (deliveryLoadedTicks.containsKey(consumer) && getReloadTimeTicks() > 0) {
            return progressOf(deliveryLoadedTicks.get(consumer), getReloadTimeTicks());
        }
        // 未在为本消费者输送：逐发再生时报再生进度，其余报储备水平
        if (attr.staticAttribute.isRegenRoundByRound() && getAmmoCount() < cap) {
            return Math.clamp(regenProgress, 0f, 1f);
        }
        return (float) getAmmoCount() / cap;
    }

    /**
     * 经过 tick 数与总 tick 数折算成 0~1 的进度，并钳到区间内。<br>
     * 钳位是必要的：所需耗时在中途变化时，已装填量可能已经超过新的所需量（那一发随即转入待取）。
     */
    private static float progressOf(int elapsedTicks, int totalTicks) {
        if (totalTicks <= 0) return 0f;
        return Math.clamp((float) elapsedTicks / totalTicks, 0f, 1f);
    }

    /**
     * 接收一发退还的弹药。<br>
     * 收下条件有两条，都由本机自己判：① 弹种在本机弹链上——弹链之外的弹种收进来也发不出去，
     * 只会让弹仓计数与实际供给能力脱节；② 弹仓还有空位。两条都满足才计数 +1。
     * <p>
     * 计数只在服务端递增（客户端跟随服务端推送）。
     *
     * @param type 退还的弹药类型
     * @return true 表示已收下并计入弹仓
     */
    @Override
    public boolean returnRound(ProjectileType type) {
        if (!isOnBelt(type)) return false;
        if (getAmmoCount() >= attr.staticAttribute.getMagazineCapacity()) return false;
        if (!getLevel().isClientSide()) setAmmoCount(getAmmoCount() + 1);
        return true;
    }

    /**
     * 该弹种是否在本机弹链上。
     */
    private boolean isOnBelt(ProjectileType type) {
        ResourceLocation key = type.getRegistryKey();
        return key != null && attr.projectileTypes.contains(key);
    }

    // ==================== 弹链查询 ====================

    /**
     * 返回展平的弹链序列（不可变），供 WeaponController 构建弹药池。
     */
    public List<ResourceLocation> getProjectileTypes() {
        return attr.projectileTypes;
    }

    /**
     * 读取弹链指针前先把它收进当前弹链的有效范围。
     * <p>
     * 弹链由内容包定义、指针由存档持久化，两者生命周期不同步：改动内容包（增删弹种或改数量）
     * 后，旧存档里的 {@code belt_index} 可能已越界（如弹链由 4 项缩为 2 项而指针仍为 3）。
     * {@link #attr}{@code .projectileTypes} 是不可变列表，越界取值会抛异常，
     * 因此所有以指针索引弹链的位置都必须经由此方法。
     * <p>
     * 指针存在 {@link #BELT_INDEX} 访问器里（{@code SynchedEntityData}），服务端与客户端
     * 读同一个来源；越界时把折回后的修正值写回该访问器，服务端下次保存即为合法值。
     * 客户端不读存档、只跟随服务端的推送，因此两端的指针不会各自走偏。
     *
     * @return 有效范围内的弹链指针；弹链为空时返回 0（调用方在此之前已判空）
     */
    private int normalizedBeltIndex() {
        int size = attr.projectileTypes.size();
        if (size == 0) return 0;
        int index = getSynchedData().get(BELT_INDEX);
        int normalized = Math.floorMod(index, size);
        if (normalized != index) getSynchedData().set(BELT_INDEX, normalized);
        return normalized;
    }

    // ==================== 核心逻辑 ====================

    @Override
    public void onTick() {
        super.onTick();
        if (!isActive() || isDestroyed()) return;

        // 清掉已退场的消费者，别让它们的交付占着输弹线
        pruneGoneConsumers();

        // ① 推进输送：本次 tick 已装填量 +1，达到当前所需装填耗时的消费者转入待取。
        // 比较对象是当前耗时（getReloadTimeTicks()），因此外部属性中途改变装填耗时对在途那一发同样生效。
        // 计时器只在这里自增，因此未激活/已摧毁期间（本方法提前返回）自然停摆，恢复后接着原进度走
        int deliveryTicks = getReloadTimeTicks();
        Iterator<Map.Entry<IAmmoConsumer, Integer>> iter = deliveryLoadedTicks.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<IAmmoConsumer, Integer> entry = iter.next();
            int loaded = entry.getValue() + 1;
            if (loaded < deliveryTicks) {
                entry.setValue(loaded);
                continue;
            }
            iter.remove();
            readyConsumers.add(entry.getKey());
            lastPlayedProgressKeys.remove(entry.getKey()); // 输送完成，清理该消费者的音效追踪
        }

        // ② 推进再生延迟计时器
        if (regenDelayRemainingTicks > 0) {
            regenDelayRemainingTicks--;
        }

        // ③ 弹药再生（延迟计时未结束时暂停再生）
        if (regenDelayRemainingTicks <= 0) {
            if (attr.staticAttribute.isRegenRoundByRound()) {
                // 随打随产模式
                tickRegenRoundByRound();
            } else {
                // 批量产出模式
                tickBatchReload();
            }
        }

        // ④ 装填进度分段音效（仅客户端）
        if (getLevel().isClientSide()) {
            tickProgressSounds();
        }
    }

    /**
     * 随打随产模式：每 tick 累积再生进度，达到 1.0 时产出一发。
     */
    private void tickRegenRoundByRound() {
        float regenPerMinute = attr.staticAttribute.getRegenPerMinute();
        if (regenPerMinute <= 0f) return;

        if (getAmmoCount() < attr.staticAttribute.getMagazineCapacity()) {
            // 20 tick/s × 60 s = 1200 tick/min
            regenProgress += regenPerMinute / 1200f;

            while (regenProgress >= 1.0f && getAmmoCount() < attr.staticAttribute.getMagazineCapacity()) {
                // 尝试消耗能量
                float energyCost = attr.staticAttribute.getEnergyCostPerRound();
                if (energyCost > 0f) {
                    if (!tryConsumeEnergy(energyCost)) {
                        break; // 能量不足，暂停再生
                    }
                }
                if (!getLevel().isClientSide()) // 仅服务端更新弹药计数
                    setAmmoCount(getAmmoCount() + 1);
                regenProgress -= 1.0f;
            }
        }
    }

    /**
     * 批量再生的总 tick 数：按<b>当前</b>再生速率与弹仓容量现算。<br>
     * 现算而不是在触发时记下，是为了让速率变化对进行中的这一批同样生效；
     * 公式为 弹仓容量 ÷ 每分钟再生次数 × 1200 tick（1200 = 20 tick/s × 60 s）。
     */
    private int batchTotalTicks() {
        float regenPerMinute = attr.staticAttribute.getRegenPerMinute();
        if (regenPerMinute <= 0f) return 1;
        return Math.max(1, (int) (attr.staticAttribute.getMagazineCapacity() / regenPerMinute * 1200));
    }

    /**
     * 批量产出模式：弹仓全空后触发冷却，冷却结束后一次性回满。
     */
    private void tickBatchReload() {
        if (getAmmoCount() == 0 && !isBatchReloading) {
            // 触发批量再生
            float regenPerMinute = attr.staticAttribute.getRegenPerMinute();
            if (regenPerMinute <= 0f) return;

            // 扣除整批能量
            float totalEnergy = attr.staticAttribute.getMagazineCapacity() * attr.staticAttribute.getEnergyCostPerRound();
            if (totalEnergy > 0f) {
                if (!tryConsumeEnergy(totalEnergy)) {
                    return; // 能量不够整批，等下一 tick 再试
                }
            }

            // 冷却时长由 batchTotalTicks() 按当前再生速率现算，这里只把已消耗量归零；
            // 本次 tick 随即计入一格（见下），因此"触发那一 tick 本身算作冷却的第一 tick"
            batchElapsedTicks = 0;
            isBatchReloading = true;
            lastPlayedBatchProgressKey = null; // 新一批冷却开始，重置音效进度追踪
        }

        if (isBatchReloading) {
            // 本次 tick 计入冷却消耗；时长逐 tick 现算，速率中途变化对进行中的这一批同样生效
            batchElapsedTicks++;

            // 批量冷却进度音效（在 isBatchReloading 翻转为 false 之前检查，确保播放到"1.0"）
            if (getLevel().isClientSide()) {
                checkBatchProgressSound();
            }

            if (batchElapsedTicks >= batchTotalTicks()) {
                isBatchReloading = false;
                batchElapsedTicks = 0;
                setAmmoCount(attr.staticAttribute.getMagazineCapacity());
                lastPlayedBatchProgressKey = null;
            }
        }
    }

    // ==================== 装填进度音效 ====================

    /**
     * 处理每消费者的装填进度分段音效。<br>
     * 遍历 {@link #deliveryLoadedTicks} 中的每个消费者，按当前输送进度查找匹配的
     * 进度阈值key，越过新阈值时播放对应音效。
     * <p>
     * <b>调用线程：</b>主线程（{@link #onTick()}）。
     * </p>
     */
    private void tickProgressSounds() {
        Map<String, SoundEvent> sounds = attr.staticAttribute.getProgressSounds();
        if (sounds.isEmpty()) return;

        int totalTicks = getReloadTimeTicks();
        if (totalTicks <= 0) return; // 瞬时交付无需进度音效

        for (Map.Entry<IAmmoConsumer, Integer> entry : deliveryLoadedTicks.entrySet()) {
            IAmmoConsumer consumer = entry.getKey();
            float progress = progressOf(entry.getValue(), totalTicks);
            Float lastKey = lastPlayedProgressKeys.get(consumer);
            Float matchedKey = findBestProgressKey(sounds, progress);
            // 只认"越过更高的阈值"：耗时变长时进度会回退，此时不该把已经播过的音效再播一遍
            if (matchedKey != null && (lastKey == null || matchedKey > lastKey)) {
                lastPlayedProgressKeys.put(consumer, matchedKey);
                playProgressSound(sounds.get(String.valueOf(matchedKey)));
            }
        }

        // 清理已不在输送中的消费者追踪记录
        lastPlayedProgressKeys.keySet().removeIf(c -> !deliveryLoadedTicks.containsKey(c));
    }

    /**
     * 检查批量冷却模式下的进度音效。<br>
     * 由 {@link #tickBatchReload()} 在判定冷却是否结束之前调用，
     * 确保跨越"1.0"阈值时能播放完成音效。
     * <p>
     * <b>调用线程：</b>主线程（{@link #onTick()} → {@link #tickBatchReload()}）。
     * </p>
     */
    private void checkBatchProgressSound() {
        Map<String, SoundEvent> sounds = attr.staticAttribute.getProgressSounds();
        if (sounds.isEmpty()) return;

        float progress = progressOf(batchElapsedTicks, batchTotalTicks());
        Float matchedKey = findBestProgressKey(sounds, progress);
        if (matchedKey != null && (lastPlayedBatchProgressKey == null || matchedKey > lastPlayedBatchProgressKey)) {
            lastPlayedBatchProgressKey = matchedKey;
            playProgressSound(sounds.get(String.valueOf(matchedKey)));
        }
    }

    /**
     * 在进度音效映射中查找 ≤ 当前进度的最大key。
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

    /**
     * 播放单次进度音效。<br>
     * 使用 {@link SpreadingSoundHelper#playSpreadingSound} 在子系统位置播放，
     * 带多普勒速度传播。
     *
     * @param sound 要播放的音效，null 时静默跳过
     */
    private void playProgressSound(@Nullable SoundEvent sound) {
        if (sound == null || !getLevel().isClientSide()) return;
        playSpreadingSound(getLevel(), sound, SoundSource.NEUTRAL);
    }

    /**
     * 尝试从 EnergyGrid 消耗能量。
     *
     * @param amount 请求消耗的能量 (J)
     * @return true 表示实际消耗到了指定能量
     */
    private boolean tryConsumeEnergy(float amount) {
        EnergyGrid grid = getOwner().getEnergyGrid();
        if (grid == null) return false;
        float consumed = grid.consumeEnergy(amount);
        return consumed >= amount - 0.001f;
    }

    // ==================== 握手机制 ====================

    @Override
    public void onAttach() {
        super.onAttach();
        handShake();
    }

    @Override
    public void onVehicleStructureChanged() {
        super.onVehicleStructureChanged();
        handShake();
    }

    /**
     * 弹药链握手：向发现频道发送空信号，通过回调发现同一载具内的 IAmmoConsumer。<br>
     * 下游消费者（Launcher 等）收到回调后通过 addSupplier() 注册此供给者。
     * 回调携带频道名，供 Launcher 按 ammo_inputs 频道分组。
     */
    protected void handShake() {
        for (String signalChannel : attr.discoveryOutputs.keySet()) {
            sendSignalToAllTargetsWithCallback(signalChannel, EmptySignal.INSTANCE, false);
        }
    }

    // ==================== 持久化 ====================

    @Override
    public void loadData(net.minecraft.nbt.CompoundTag data) {
        super.loadData(data);
        if (data.contains("ammo_count")) setAmmoCount(data.getInt("ammo_count"));
        if (data.contains("regen_progress")) regenProgress = data.getFloat("regen_progress");
        // 批量冷却存的是"已消耗 tick 数"：本机计数量，跨存档直接可用。
        // 旧存档写的是"剩余 tick 数"（batch_cooldown），按当前总时长换算成已消耗量读入
        if (data.contains("batch_elapsed")) {
            batchElapsedTicks = data.getInt("batch_elapsed");
        } else if (data.contains("batch_cooldown")) {
            batchElapsedTicks = Math.max(0, batchTotalTicks() - data.getInt("batch_cooldown"));
        }
        if (data.contains("is_batch_reloading")) isBatchReloading = data.getBoolean("is_batch_reloading");
        if (data.contains("regen_delay_remaining")) regenDelayRemainingTicks = data.getInt("regen_delay_remaining");
        if (data.contains("belt_index")) getSynchedData().set(BELT_INDEX, data.getInt("belt_index"));
    }

    @Override
    public net.minecraft.nbt.CompoundTag saveData(net.minecraft.nbt.CompoundTag data) {
        super.saveData(data);
        data.putInt("ammo_count", getAmmoCount());
        data.putFloat("regen_progress", regenProgress);
        data.putInt("batch_elapsed", isBatchReloading ? batchElapsedTicks : 0);
        data.putBoolean("is_batch_reloading", isBatchReloading);
        data.putInt("regen_delay_remaining", regenDelayRemainingTicks);
        data.putInt("belt_index", getSynchedData().get(BELT_INDEX));
        return data;
    }

    // ==================== 信号 ====================

    @Override
    public boolean acceptAllRoutingInput() {
        return true;
    }

    @Override
    public boolean acceptAllBroadcastInput() {
        return acceptAllRoutingInput();
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(attr.discoveryOutputs);
        return result;
    }

    @Override
    public SignalResult onSignalUpdated(String channelName, ISignalSender sender) {
        // 弹药发现频道回调：供给者发现下游消费者并注册自身，从回调值读取频道名
        if (channelName.equals("callback") && sender instanceof IAmmoConsumer consumer) {
            if (consumer instanceof AbstractSubsystem sub) {
                if (sub.getOwner().getSubPart().getPart().assembly
                        != this.getOwner().getSubPart().getPart().assembly) {
                    return SignalResult.PASS;
                }
            }
            // 从信号频道读取回调携带的频道名，若无法获取则使用 "unknown"
            Object callbackValue = getSignalChannel("callback").get(sender);
            String discoveryChannel = callbackValue instanceof String s ? s : "unknown";
            consumer.addSupplier(this, discoveryChannel);
            return SignalResult.CONSUME;
        }
        return super.onSignalUpdated(channelName, sender);
    }

    @Override
    public @NotNull Vec3 getPosition(UUID uuid, SoundEvent event) {
        return SparkMathKt.toVec3(getSubPart().getPosition());
    }

    @Override
    public @NotNull Vec3 getSpeed(UUID uuid, SoundEvent event) {
        return SparkMathKt.toVec3(getSubPart().getLinearVelocity());
    }
}
