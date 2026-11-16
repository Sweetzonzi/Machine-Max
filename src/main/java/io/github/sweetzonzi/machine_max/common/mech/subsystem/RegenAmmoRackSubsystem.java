package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.physics.PenetrationKey;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.github.sweetzonzi.machine_max.common.mech.energy.EnergyGrid;
import io.github.sweetzonzi.machine_max.common.mech.explosion.CookoffComposition;
import io.github.sweetzonzi.machine_max.common.mech.explosion.ExplosionManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.RegenAmmoRackSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.subpart.SubPartDamageEvent;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 再生弹药架子系统。<br>
 * 只持有库存、自身再生与<b>弹药预留</b>：数字存量、再生进度、交付规格 {@code belt_definition}、
 * 各作业的预留量。它不做装填计时，也不向任何人推送进度——装填进度记在 {@link LauncherSubsystem} 的作业上。
 * <p>
 * <b>提供者角色：</b>本架只出库存，运输由别人完成——接了一台自动装弹机的管，由机械运输者沿自己的
 * 喂弹线来取弹；武器型号自己声明了人力路的管，由本管的人力运输者（{@link LoaderRoster}）在人工段里
 * 直接从本架取弹。两种路径的库存承诺是同一份预留表，受理与撤销口径不因运输者是谁而变；本架不知道
 * 接收者是谁，"哪根管占着这一份"只由作业身份表达。
 * <p>
 * <b>预留：</b>每条作业在受理时按整次作业取一份 {@code q}（{@link #reserve}），可分配库存随之减少，
 * 但实际库存 {@code N} 在交付那一刻才扣（{@link #deliverReserved}）。已取得预留的作业不受其他作业
 * 抢走可分配库存的影响；预留被撤销（来源失效、强制损失、作业终止）时只减少承诺，不补弹。
 * 任意时刻 {@code 0 ≤ R = Σq ≤ N}，{@link #trimReservationsToStock} 维护这条不变量。
 * <p>
 * <b>两端账本：</b>预留表、本端库存与三项累计量（产出／收回／交付）在两端都维护，因此受理、交付与
 * 退弹在客户端也是真实的状态变更，只是不产生物品、不扣真实能源。客户端收到权威样本时按
 * "权威库存 + 未确认产出 + 未确认收回 − 未确认交付"重算本端库存；库存代次变化（殉爆、强制损失）
 * 或投影越界即放弃本端尾部、整体改认权威值。需要付费的产出只由服务端完成，客户端等权威结果。
 * <p>
 * 交付单位是一份 {@link AmmoBelt}：每次兑现按模板给一份独立实例，发数是该作业的预留量；
 * 取走后按 {@code regen_delay} 暂停，再按 {@code regen_per_minute} 与 {@code regen_round_by_round}
 * 决定逐发产出还是整批回满。
 * <p>
 * 计时器一律记<b>已消耗量</b>并只在 {@link #onTick()} 里自增自减，不记"开始时刻"：开始时刻是本机
 * {@link #tickCount} 的读数，跨存档没有意义；而计数天然满足两条要求——停摆期间（未激活 /
 * 已摧毁，{@link #onTick()} 提前返回）计时不走，恢复后接着原进度；存档直接存那个计数本身。
 * <p>
 * 耐久是普通子系统的一份：归零由基类判毁，摧毁边沿触发自身库存殉爆，恢复门槛沿用基类的 30%。<br>
 * 能量消耗通过 {@link EnergyGrid#consumeEnergy(float)} 瞬时抽取，不实现 {@code IEnergyConsumer} 接口。
 */
public class RegenAmmoRackSubsystem extends BasicSubsystem implements IAmmoSupplier {

    public final RegenAmmoRackSubsystemAttr attr;

    /** 当前弹药计数（网络同步） */
    private static final EntityDataAccessor<Integer> AMMO_COUNT_ID =
            SynchedEntityData.defineId(RegenAmmoRackSubsystem.class, EntityDataSerializers.INT);

    /** 累计成功产出（权威值；客户端本地另有一份，见 {@link #producedRounds}）。 */
    private static final EntityDataAccessor<Long> PRODUCED_ROUNDS_ID =
            SynchedEntityData.defineId(RegenAmmoRackSubsystem.class, EntityDataSerializers.LONG);

    /** 累计收回（旧弹链退弹、整份换装返还）。 */
    private static final EntityDataAccessor<Long> RETURNED_ROUNDS_ID =
            SynchedEntityData.defineId(RegenAmmoRackSubsystem.class, EntityDataSerializers.LONG);

    /** 累计已交付（兑现预留时扣掉的发数）。 */
    private static final EntityDataAccessor<Long> DELIVERED_ROUNDS_ID =
            SynchedEntityData.defineId(RegenAmmoRackSubsystem.class, EntityDataSerializers.LONG);

    /**
     * 库存代次：殉爆、强制损失这类<b>非预测业务</b>改变库存时由服务端递增。<br>
     * 客户端收到新代次即放弃本地投影，直接改认权威库存与权威累计量。
     */
    private static final EntityDataAccessor<Integer> STOCK_EPOCH_ID =
            SynchedEntityData.defineId(RegenAmmoRackSubsystem.class, EntityDataSerializers.INT);

    /** 免费再生进度（0~1），只在离散点与完整基线下发。 */
    private static final EntityDataAccessor<Float> REGEN_PROGRESS_ID =
            SynchedEntityData.defineId(RegenAmmoRackSubsystem.class, EntityDataSerializers.FLOAT);

    /** 整批再生的已消耗 tick 数，只在离散点与完整基线下发。 */
    private static final EntityDataAccessor<Integer> BATCH_ELAPSED_ID =
            SynchedEntityData.defineId(RegenAmmoRackSubsystem.class, EntityDataSerializers.INT);

    /** 是否正在整批再生。 */
    private static final EntityDataAccessor<Boolean> BATCH_ACTIVE_ID =
            SynchedEntityData.defineId(RegenAmmoRackSubsystem.class, EntityDataSerializers.BOOLEAN);

    /** 再生延迟剩余 tick。 */
    private static final EntityDataAccessor<Integer> REGEN_DELAY_ID =
            SynchedEntityData.defineId(RegenAmmoRackSubsystem.class, EntityDataSerializers.INT);

    /**
     * 各作业的预留量 {@code q}：作业身份 → 份数。<br>
     * <b>两端都维护</b>：受理时新增（{@link #reserve}），交付（{@link #deliverReserved}）与释放
     * （{@link #releaseReservation}）时整笔删除，没有部分兑现。两端各按本端表求和得到 {@code R}，
     * 不互相下发：客户端那一份随本地受理与交付增减，凡本端的条目都由本端按作业身份撤销（见
     * {@link #getReservedTotal()}）。不入存档——读档后未完成的装填重新申请。
     */
    private final Map<ReloadJobKey, Integer> reservations = new LinkedHashMap<>();

    /**
     * 本端运行态库存：{@link #getAmmoCount()} 的唯一来源。
     * <p>服务端恒等于同步值；客户端是"权威库存 + 未确认本地产出 + 未确认本地收回 − 未确认本地交付"
     * 的投影结果，因此在服务端确认之前就能给出本端受理与交付所需的数字。</p>
     */
    private int runtimeAmmoCount;

    /**
     * 本端累计数量。服务端这三项是权威值并写进同步字段；客户端是本端累计值，包含尚未被权威确认的部分。
     * 未确认量 = 本端累计 − 权威累计，按<b>数量</b>计，不按调用次数计。
     */
    private long producedRounds;
    private long returnedRounds;
    private long deliveredRounds;

    /** 库存代次；服务端递增并下发，客户端跟随。 */
    private int stockEpoch;


    /** 逐发再生的进度（0.0 ~ 1.0+，累积超过 1.0 时产出一发） */
    private float regenProgress = 0f;

    /**
     * 批量再生的已消耗 tick 数（{@code regen_round_by_round=false} 时使用）。<br>
     * 按<b>已消耗量</b>记：进度与总时长都由 {@link #batchTotalTicks()} 按当前再生速率现算，
     * 因此速率中途变化对进行中的这一批同样生效。停摆期间不自增，存档直接存这个数。
     */
    private int batchElapsedTicks = 0;

    /** 是否正在批量冷却中 */
    private boolean isBatchReloading = false;

    /** 再生延迟剩余 tick，上次交付后需等待此时间才恢复再生 */
    private int regenDelayRemainingTicks = 0;

    public RegenAmmoRackSubsystem(ISubsystemHost owner, String name, RegenAmmoRackSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(AMMO_COUNT_ID, 0);
        builder.define(PRODUCED_ROUNDS_ID, 0L);
        builder.define(RETURNED_ROUNDS_ID, 0L);
        builder.define(DELIVERED_ROUNDS_ID, 0L);
        builder.define(STOCK_EPOCH_ID, 0);
        builder.define(REGEN_PROGRESS_ID, 0f);
        builder.define(BATCH_ELAPSED_ID, 0);
        builder.define(BATCH_ACTIVE_ID, false);
        builder.define(REGEN_DELAY_ID, 0);
    }

    // ==================== 库存 ====================

    /**
     * 本端运行态弹药计数。
     *
     * <p>两端都读这一份；服务端它与同步值恒等，客户端它是投影结果。库存对外只有一个读口，
     * 因此"可分配多少、能不能交付、要不要再生"在两端用同一套判据。</p>
     */
    public int getAmmoCount() {
        return runtimeAmmoCount;
    }

    /**
     * 服务端：写入权威库存并同步；客户端：只写本端运行态。
     *
     * <p>客户端不能把预测值写进同步字段——那是权威镜像，写进去会让后续投影以预测为基线。</p>
     */
    private void setAmmoCount(int count) {
        int clamped = Math.clamp(count, 0, getCapacity());
        runtimeAmmoCount = clamped;
        if (!getLevel().isClientSide()) getSynchedData().set(AMMO_COUNT_ID, clamped);
    }

    /** 服务端：把三项累计量与库存代次写进同步字段。 */
    private void publishLedger() {
        if (getLevel().isClientSide()) return;
        getSynchedData().set(PRODUCED_ROUNDS_ID, producedRounds);
        getSynchedData().set(RETURNED_ROUNDS_ID, returnedRounds);
        getSynchedData().set(DELIVERED_ROUNDS_ID, deliveredRounds);
        getSynchedData().set(STOCK_EPOCH_ID, stockEpoch);
    }

    /**
     * 已预留总量 {@code R}：各作业 {@code q} 之和，已包含本作业份额。
     *
     * <p>两端都按本端预留表求和：{@code R} 只表达"本端已经承诺出去的份数"，每个条目都由本端按作业身份
     * 撤销，因此没有需要对齐的外部权威值。</p>
     */
    public int getReservedTotal() {
        return localReservedTotal();
    }

    /** 本端预留表的份额之和。 */
    private int localReservedTotal() {
        int total = 0;
        for (int granted : reservations.values()) total += granted;
        return total;
    }

    /** 可分配库存 {@code N - R}：可供尚未受理的作业申请的份数 */
    public int getAvailableCount() {
        return Math.max(0, getAmmoCount() - getReservedTotal());
    }

    /**
     * 该作业在本端的预留量。
     *
     * <p>两端都读自己的预留表：客户端受理时会真的写入这一份，因此承诺判据与服务端同一口径。</p>
     *
     * @param key 作业身份
     * @return 该作业的 {@code q}；没有预留时为 0
     */
    public int reservedFor(ReloadJobKey key) {
        return reservations.getOrDefault(key, 0);
    }

    /**
     * 为一条作业预留弹药：可分配库存立即减少，实际库存不动。<br>
     * 预留量取 {@code q = min(可分配库存, wanted)}，不足一份也能开工（交付时按 {@code q} 发）。
     * 同一身份重复申请沿用原份额，不叠加；{@link ReloadJobKey#NONE} 不产生预留。
     * <p>两端都只改本端预留表：跨端对齐靠"本端的条目由本端按作业身份撤销"，没有下发的总量。
     * <p><b>调用线程：</b>主线程（受理点，两端通用）。
     *
     * @param key    作业身份
     * @param wanted 本次最多想预留多少发（通常是交付规格的长度）
     * @return 实际预留量 {@code q}；没有可分配库存时返回 0
     */
    private int reserveCount(ReloadJobKey key, int wanted) {
        if (key.isNone() || wanted <= 0) return 0;
        int existing = reservedFor(key);
        if (existing > 0) return existing;
        int granted = Math.min(getAvailableCount(), wanted);
        if (granted <= 0) return 0;
        reservations.put(key, granted);
        return granted;
    }

    /**
     * 为一条作业取得整次作业的弹药预留：可分配库存立即减少，实际库存不动。<br>
     * 份额取 {@code q = min(可分配库存, 规格长度)}；同一身份重复申请沿用原份额。
     * <p><b>调用线程：</b>主线程（受理点，两端通用）。
     */
    @Override
    @Nullable
    public IAmmoSupplier.Grant reserve(ReloadJobKey key) {
        if (key.isNone() || !isOperational()) return null;
        AmmoBelt spec = attr.beltDefinition;
        int granted = reserveCount(key, spec.length());
        if (granted <= 0) return null;
        return new IAmmoSupplier.Grant(spec.freshCopy(), granted);
    }

    /**
     * 按身份撤销一条作业的预留：只减少承诺，不改动实际库存、不补弹。<br>
     * 重复撤销、撤销不存在的身份都是无操作。
     * <p><b>调用线程：</b>主线程（两端通用）。
     *
     * @param key 作业身份
     */
    public void releaseReservation(ReloadJobKey key) {
        if (key.isNone()) return;
        reservations.remove(key);
    }

    /**
     * 兑现一条作业的预留：按该身份扣减本端库存并生成交付实例。<br>
     * 同一身份只兑现一次——预留在此处删除，重复调用返回 {@code null}，不会扣第二次。
     * 客户端的兑现只改预测运行态与本地累计量，交付实例是独立 {@link AmmoBelt}，不产生真实物品。
     * <p><b>调用线程：</b>主线程（交付点，两端通用）。
     *
     * @param key 作业身份
     * @return 交付实例，发数等于该作业的预留量；没有预留时返回 {@code null}
     */
    @Nullable
    public AmmoBelt deliverReserved(ReloadJobKey key) {
        if (key.isNone()) return null;
        Integer granted = reservations.remove(key);
        if (granted == null || granted <= 0) return null;
        deliveredRounds += granted;
        setAmmoCount(runtimeAmmoCount - granted);
        applyDeliveryDelay();
        publishLedger();
        return attr.beltDefinition.freshCopy(granted);
    }

    /**
     * 维护 {@code R ≤ N}：实际库存因非交付原因减少后，按 {@link ReloadJobKey} 元组升序排列预留键，
     * 从最大键起整笔撤销，直到剩余预留总量不超过新的实际库存。<br>
     * 只在服务端做：库存的权威值在那边，客户端没有可裁的权威库存。客户端多出的条目随之失效——
     * 被裁掉的那条作业在服务端按"预留不在了"收尾，结束结果到达时客户端按身份释放本端那一条。
     * 保留的份额不被静默缩小。
     * <p><b>调用线程：</b>主线程（服务端）。
     */
    private void trimReservationsToStock() {
        if (getLevel().isClientSide()) return;
        int total = getReservedTotal();
        if (total <= getAmmoCount()) return;
        List<ReloadJobKey> keys = new ArrayList<>(reservations.keySet());
        keys.sort(Comparator.naturalOrder());
        for (int i = keys.size() - 1; i >= 0 && total > getAmmoCount(); i--) {
            Integer granted = reservations.remove(keys.get(i));
            if (granted != null) total -= granted;
        }
    }

    /** 存量上限，来自型号的 {@code capacity} */
    @Override
    public int getCapacity() {
        return attr.staticAttribute.getCapacity();
    }

    /**
     * 此刻是否有可供<b>新作业</b>申请的弹药：本架可工作且可分配库存（{@code N - R}）大于零。<br>
     * 已有预留的作业不看这个值——那一份已经记在它名下（见 {@link #getAdvanceTicks(IAmmoConsumer)}）。
     */
    @Override
    public boolean hasAmmo() {
        return isOperational() && getAvailableCount() > 0;
    }

    /**
     * 本架此刻能否供货：自已启用、未摧毁（含 0%—30% 的恢复锁存）。<br>
     * 这是"能不能交"的实时判断，与"有没有弹"是两件事：空架但健康照常再生。
     */
    public boolean isOperational() {
        return isActive() && !isDestroyed();
    }

    /** 下一份交付的规格：{@code belt_definition} 本身，满额、回充运行态为零。调用方不得原地推进它。 */
    @Override
    public AmmoBelt getDeliverySpec() {
        return attr.beltDefinition;
    }

    /** 交付规格的弹链名（HUD 弹链选项卡用），不读库存，因此同一份规格的返回值恒定。 */
    @Override
    public String getLabel() {
        return attr.beltDefinition.displayLabel();
    }

    /** 下一发的弹种，{@code null} 表示无交付规格 */
    @Override
    @Nullable
    public ProjectileType getSuppliedType() {
        return AmmoBelt.resolve(getLevel(), attr.beltDefinition.nextRoundId());
    }

    /**
     * 交付后的暂停：{@code regen_delay} 作用于本架备用额度的继续生产，
     * 与已装 Belt 自身的 {@code recharge.regen_delay} 是两处独立状态。
     */
    private void applyDeliveryDelay() {
        float delay = attr.staticAttribute.getRegenDelay();
        if (delay <= 0f) return;
        regenDelayRemainingTicks = (int) (delay * 20f); // 秒 → tick
        // 延迟刷新时重置再生进度；库存已归零时保留已有进度（避免空仓丢进度）
        if (getAmmoCount() > 0) regenProgress = 0f;
        publishRegenState();
    }

    /**
     * 接收一份退还的弹药。<br>
     * 收下条件三条，都由本架自己判：① 本架可储存（未摧毁、非恢复锁存）；② 内容里的弹种都在本架允许范围内
     * ——收进发不出去的弹种只会让计数与实际供给能力脱节；③ 剩余容量装得下这一份的余量。
     * 三条都满足才按剩余发数计入库存，并丢弃弹序与容器信息（架只记数量）。
     * <p>两端都收：退还属于免费业务，服务端写权威与累计量，客户端只改预测运行态。
     *
     * @param belt 退还的那一份弹药
     * @return true 表示已收下并计入库存
     */
    @Override
    public boolean returnBelt(AmmoBelt belt) {
        if (!isOperational()) return false;
        if (!allowsEveryAmmo(belt)) return false;
        int amount = belt.remaining();
        if (amount <= 0) return true;
        if (getAmmoCount() + amount > getCapacity()) return false;
        returnedRounds += amount;
        setAmmoCount(runtimeAmmoCount + amount);
        publishLedger();
        return true;
    }

    /** 该份内容的弹种是否都落在本架允许范围内（即交付模板里出现过的弹种）。 */
    private boolean allowsEveryAmmo(AmmoBelt belt) {
        List<ResourceLocation> allowed = attr.beltDefinition.ammoTypes();
        for (ResourceLocation ammo : belt.ammoTypes()) {
            if (!allowed.contains(ammo)) return false;
        }
        return true;
    }

    // ==================== 供弹（提供者） ====================

    /**
     * 交付能力：本架启用且未摧毁，与 {@link #isOperational()} 同一判据。<br>
     * 库存持有者没有独立的机构条件，"有没有可分配的弹"由 {@link #hasAmmo()} 表达。
     */
    @Override
    public boolean isSupplyOperational() {
        return isOperational();
    }

    /** 可申请的数量：{@code N - R}，已按身份预留的那一份不计入。 */
    @Override
    public int getRemainingCount() {
        return getAvailableCount();
    }

    /**
     * 该作业的预留是否还在。
     *
     * <p>两端读各自的预留表，判据相同：客户端受理时真的写下了一份，因此承诺消失能立刻发现，
     * 不必靠"实际库存还够"这类保守近似。</p>
     */
    @Override
    public boolean holdsReservation(ReloadJobKey key, int reservedCount) {
        if (key == null || key.isNone()) return false;
        return reservedFor(key) > 0;
    }

    /** 没有作业时本架在忙哪一段：再生／回充阶段直接透了给显示层。 */
    @Override
    public IAmmoSupplier.ReloadPhase getRefillPhase() {
        return isOperational() ? getRegenPhase() : IAmmoSupplier.ReloadPhase.IDLE;
    }

    @Override
    public float getRefillProgress() {
        return getRegenProgress();
    }

    @Override
    public int getRefillRemainingTicks() {
        return getRemainingRegenTicks();
    }

    // ==================== 再生显示 ====================

    /**
     * 当前再生阶段：整批回充、逐发再生或空闲。给显示与机构的状态读取用，不表达"有没有被排上"。
     */
    public IAmmoSupplier.ReloadPhase getRegenPhase() {
        if (isBatchReloading) return IAmmoSupplier.ReloadPhase.REFILLING;
        if (isRegenerating()) return IAmmoSupplier.ReloadPhase.REGENERATING;
        return IAmmoSupplier.ReloadPhase.IDLE;
    }

    /** 当前再生阶段的剩余 tick。 */
    public int getRemainingRegenTicks() {
        return switch (getRegenPhase()) {
            case REFILLING -> Math.max(0, batchTotalTicks() - batchElapsedTicks);
            case REGENERATING -> regeneratingRemainingTicks();
            default -> 0;
        };
    }

    /**
     * 逐发再生还差多少 tick：再生延迟 + 按当前速率补完这一发。<br>
     * 速率中途变化时按新速率折算剩余。
     */
    private int regeneratingRemainingTicks() {
        float perMinute = attr.staticAttribute.getRegenPerMinute();
        if (perMinute <= 0f) return 0;
        int roundTicks = Math.max(1, (int) Math.ceil(1200f / perMinute));
        float remaining = Math.clamp(1f - regenProgress, 0f, 1f);
        return regenDelayRemainingTicks + (int) Math.ceil(remaining * roundTicks);
    }

    /**
     * 再生进度（0~1）。逐发再生时报这一发的积累比例，批量回充时报这一批的已完成比例；
     * 两种都不在时按"余量 / 容量"报储备水平。
     */
    public float getRegenProgress() {
        int cap = getCapacity();
        if (cap <= 0) return 0f;
        if (isBatchReloading) return progressOf(batchElapsedTicks, batchTotalTicks());
        if (isRegenerating()) return Math.clamp(regenProgress, 0f, 1f);
        return (float) getAmmoCount() / cap;
    }

    /**
     * 是否处于逐发再生中：逐发模式、速率大于零，且库存没满。<br>
     * 不看进度是否已经涨过零：刚取走那一 tick 的零进度也是"再生中"。
     */
    private boolean isRegenerating() {
        return attr.staticAttribute.isRegenRoundByRound()
                && attr.staticAttribute.getRegenPerMinute() > 0f
                && getAmmoCount() < getCapacity();
    }

    /** 经过 tick 数与总 tick 数折算成 0~1 的进度，并钳到区间内。 */
    private static float progressOf(int elapsedTicks, int totalTicks) {
        if (totalTicks <= 0) return 0f;
        return Math.clamp((float) elapsedTicks / totalTicks, 0f, 1f);
    }

    // ==================== 殉爆 ====================

    /**
     * 摧毁边沿：先作废进行中的再生，再按<b>实际库存</b>合成一次殉爆。
     * <p>
     * 触发点是框架的子系统摧毁边沿（{@link AbstractSubsystem#onTick()}），此时同步数据有效、
     * 弹药计数尚未被本次起爆消耗。该边沿不看宿主状态：残骸上的架归零照常起爆
     * （{@link #detonateStoredAmmo()} 只判库存与合成贡献），零件整体移除后子系统不再 tick，自然不会起爆。
     * <p>
     * <b>调用线程：</b>主线程。
     */
    @Override
    public void onDestroyed() {
        super.onDestroyed();
        clearRegenState();
        if (getLevel().isClientSide()) return;
        detonateStoredAmmo();
    }

    /**
     * 库存殉爆：按交付模板的配弹比例折算实际库存、合成贡献、清空库存并提交一次普通爆炸。
     * <p>
     * 合成范围是这一个库存单元：架只记数量不记弹序，因此按 {@code belt_definition} 的模式
     * 从模式起点统计实际发数（完整循环乘算，尾段逐发取型），混装弹种全部合成一次。
     * 每发弹药计入发射药贡献与各战斗部的被诱爆贡献；零贡献弹种照样计入库存总数，但不改变 D、S、W。
     * <p>
     * {@code P、D、J} 全为零时没有可生成爆炸的贡献，不消耗库存、不起爆。
     * 一旦判定需要起爆，本次快照的全部剩余弹药都被消耗（含零贡献弹种），计数归零且不产生掉落物。
     * <p>
     * <b>调用线程：</b>主线程（子系统摧毁边沿）。
     */
    private void detonateStoredAmmo() {
        int ammoCount = getAmmoCount();
        if (ammoCount <= 0) return;

        Map<ResourceLocation, Integer> roundsByType = AmmoBelt.countRounds(
                attr.beltDefinition.pattern(), 0, ammoCount);

        CookoffComposition composition = new CookoffComposition();
        for (Map.Entry<ResourceLocation, Integer> entry : roundsByType.entrySet()) {
            ProjectileType type = ProjectileType.get(getLevel(), entry.getKey());
            composition.addStoredRound(type, entry.getValue());
        }
        if (composition.isEmpty()) return;

        // 起爆位置：最近一次有效受击指认的 HitBox 局部形心；无来源回退宿主质心
        Vector3f origin = getLastDamageOrigin();
        // 爆点落在自身命中箱内部，该命中箱必须豁免，否则近距离满值穿深会吃掉整发爆炸
        Set<PenetrationKey> ignoredKeys = getCookoffIgnoredKeys();
        // 归属：打坏本架的那一击的 causing entity，殉爆继续算在同一个射手头上
        Entity attacker = getLastDamageAttacker();
        // 先消耗库存再交给普通爆炸服务：本次起爆后该单元不再有弹药可供
        setAmmoCount(0);
        // 殉爆属于非预测业务：代次前进，让客户端放弃本地投影，直接改认权威库存
        stockEpoch++;
        // 库存归零之后，架内已预留的份额也没有弹可兑现：按身份整笔撤销，不留 R > N
        trimReservationsToStock();
        publishLedger();
        ExplosionManager.get(getLevel()).detonate(
                origin, composition.toExplosionParams(), getLevel().random.nextLong(), ignoredKeys, attacker);
    }

    // ==================== 核心逻辑 ====================

    @Override
    public void onTick() {
        super.onTick();
        if (!isActive() || isDestroyed()) return;

        // ① 推进再生延迟计时器
        if (regenDelayRemainingTicks > 0) regenDelayRemainingTicks--;

        // ② 弹药再生（延迟计时未结束时暂停再生）
        if (regenDelayRemainingTicks <= 0) {
            if (attr.staticAttribute.isRegenRoundByRound()) {
                tickRegenRoundByRound();
            } else {
                tickBatchReload();
            }
        }
    }

    /**
     * 作废进行中的那一批产出：清零逐发进度、批量冷却与再生延迟。<br>
     * 三个字段都表示"架内正在做的一批弹药"，架归零后它们不再有意义——留着会让修复后的
     * 第一个 tick 直接完成受损前那一批。批量模式已预扣的能量不退款，修复后按新批次重新计费。
     * <p>
     * <b>调用线程：</b>主线程。
     */
    private void clearRegenState() {
        regenProgress = 0f;
        batchElapsedTicks = 0;
        isBatchReloading = false;
        regenDelayRemainingTicks = 0;
        publishRegenState();
    }

    /**
     * 随打随产：每 tick 累积再生进度，达到 1.0 时产出一发。
     *
     * <p>免费再生两端都跑：没有能源支出的产出属于可预测业务，客户端各自推进同一套进度。
     * 需要付费的产出只由服务端完成——客户端没有能源支出预测，等权威库存与累计量到达。</p>
     */
    private void tickRegenRoundByRound() {
        float perMinute = attr.staticAttribute.getRegenPerMinute();
        if (perMinute <= 0f) return;
        if (getAmmoCount() >= getCapacity()) return;

        // 20 tick/s × 60 s = 1200 tick/min
        regenProgress += perMinute / 1200f;

        while (regenProgress >= 1.0f && getAmmoCount() < getCapacity()) {
            if (!canProduceLocally()) break;
            produceRounds(1);
            regenProgress -= 1.0f;
        }
    }

    /**
     * 本次产出一发是否可以在本端完成：免费产出两端都行；需要付费的产出只有服务端能做。
     *
     * <p>服务端付费时从能源网瞬时抽取，抽不到就暂停这一批；客户端不扣真实能源，直接等权威产出。</p>
     */
    private boolean canProduceLocally() {
        float energyCost = attr.staticAttribute.getEnergyCostPerRound();
        if (energyCost <= 0f) return true;
        if (getLevel().isClientSide()) return false;
        return tryConsumeEnergy(energyCost);
    }

    /**
     * 记一次成功产出：推进本端累计量与本端库存；服务端同时写同步字段。
     *
     * @param rounds 本次产出数量
     */
    private void produceRounds(int rounds) {
        if (rounds <= 0) return;
        producedRounds += rounds;
        setAmmoCount(runtimeAmmoCount + rounds);
        publishLedger();
    }

    /**
     * 批量再生的总 tick 数：按<b>当前</b>再生速率与容量现算。<br>
     * 现算而不是在触发时记下，是为了让速率变化对进行中的这一批同样生效；
     * 公式为 容量 ÷ 每分钟再生次数 × 1200 tick（1200 = 20 tick/s × 60 s）。
     */
    private int batchTotalTicks() {
        float perMinute = attr.staticAttribute.getRegenPerMinute();
        if (perMinute <= 0f) return 1;
        return Math.max(1, (int) (getCapacity() / perMinute * 1200));
    }

    /**
     * 批量产出：打空后触发冷却，冷却结束后一次性回满。
     *
     * <p>整批产出同样按是否付费分流：免费批次两端都跑；付费批次只在服务端成立，客户端等权威
     * 库存回来，不预支能源额度。</p>
     */
    private void tickBatchReload() {
        if (getAmmoCount() == 0 && !isBatchReloading) {
            float perMinute = attr.staticAttribute.getRegenPerMinute();
            if (perMinute <= 0f) return;

            // 扣除整批能量（客户端不扣，也不自建这一批）
            float totalEnergy = getCapacity() * attr.staticAttribute.getEnergyCostPerRound();
            if (totalEnergy > 0f) {
                if (getLevel().isClientSide()) return;
                if (!tryConsumeEnergy(totalEnergy)) return; // 能量不够整批，等下一 tick 再试
            }

            // 冷却时长由 batchTotalTicks() 现算，这里只把已消耗量归零；
            // 本次 tick 随即计入一格，因此"触发那一 tick 本身算作冷却的第一 tick"
            batchElapsedTicks = 0;
            isBatchReloading = true;
            publishRegenState();
        }

        if (isBatchReloading) {
            batchElapsedTicks++;
            if (batchElapsedTicks >= batchTotalTicks()) {
                isBatchReloading = false;
                batchElapsedTicks = 0;
                produceRounds(getCapacity() - runtimeAmmoCount);
                publishRegenState();
            }
        }
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

    // ==================== 发现 ====================

    /**
     * 本架接受的供弹频道。<br>
     * 上游查询有两种到达方式：机构按名字直接指向本架（第一跳直连，不经频道筛选），或经 Part 总线
     * 广播到本架。后者按这张表筛选，因此型号里写下的 {@code ammo_discovery_inputs} 必须在这里生效。
     */
    @Override
    public List<String> getAcceptedChannels() {
        return attr.ammoDiscoveryInputs;
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        return Map.of();
    }

    // ==================== 双端账本投影 ====================

    /** 服务端：把再生进度、整批状态与延迟写进同步字段（只在离散点调用）。 */
    private void publishRegenState() {
        if (getLevel().isClientSide()) return;
        getSynchedData().set(REGEN_PROGRESS_ID, regenProgress);
        getSynchedData().set(BATCH_ELAPSED_ID, batchElapsedTicks);
        getSynchedData().set(BATCH_ACTIVE_ID, isBatchReloading);
        getSynchedData().set(REGEN_DELAY_ID, regenDelayRemainingTicks);
    }

    /**
     * 全量补推之前把离散写过的值刷成当前值。
     *
     * <p>库存、累计量与再生状态平时只在变化点写；新加入的客户端必须拿到"现在"，
     * 否则它会从一个空基线开始预测。预留表不在其中：它不跨端下发，两端的 {@code R} 各由本端表求和。
     */
    @Override
    public void refreshSyncBaseline() {
        if (getLevel().isClientSide()) return;
        getSynchedData().set(AMMO_COUNT_ID, runtimeAmmoCount);
        publishLedger();
        publishRegenState();
    }

    /**
     * 一批权威值落地之后重算本端库存投影。
     *
     * <p>公式：{@code 预测 N = 权威 N + 未确认产出 + 未确认收回 − 未确认交付}，每一项都是
     * "本端累计 − 权威累计"，按数量计。库存代次变化、投影越界（含 {@code R > N}）都表示本端
     * 追不上权威，直接改认权威值与本端累计量。</p>
     */
    private void reconcileFromAuthority() {
        int authorityCount = getSynchedData().get(AMMO_COUNT_ID);
        long authorityProduced = getSynchedData().get(PRODUCED_ROUNDS_ID);
        long authorityReturned = getSynchedData().get(RETURNED_ROUNDS_ID);
        long authorityDelivered = getSynchedData().get(DELIVERED_ROUNDS_ID);
        int authorityEpoch = getSynchedData().get(STOCK_EPOCH_ID);

        boolean epochChanged = authorityEpoch != stockEpoch;
        stockEpoch = authorityEpoch;
        long predicted = (long) authorityCount
                + Math.max(0L, producedRounds - authorityProduced)
                + Math.max(0L, returnedRounds - authorityReturned)
                - Math.max(0L, deliveredRounds - authorityDelivered);
        if (epochChanged
                || predicted < 0L
                || predicted > getCapacity()
                || predicted < localReservedTotal()) {
            adoptAuthority(authorityCount, authorityProduced, authorityReturned, authorityDelivered);
            return;
        }
        runtimeAmmoCount = (int) predicted;
    }

    /** 放弃本端尾部：库存与三项累计量整体改认权威值。 */
    private void adoptAuthority(int count, long produced, long returned, long delivered) {
        producedRounds = produced;
        returnedRounds = returned;
        deliveredRounds = delivered;
        runtimeAmmoCount = Math.clamp(count, 0, getCapacity());
    }

    @Override
    public void onSyncedDataUpdated(List<SynchedEntityData.DataValue<?>> values) {
        if (!getLevel().isClientSide()) return;
        reconcileFromAuthority();
    }

    /**
     * 再生状态按字段逐个跟随权威值。
     *
     * <p>这些字段只在离散点下发，因此收到就等于"服务端在这一刻的采样"，直接落在本端同样的字段上。</p>
     */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (!getLevel().isClientSide()) return;
        if (accessor == REGEN_PROGRESS_ID) {
            regenProgress = getSynchedData().get(REGEN_PROGRESS_ID);
        } else if (accessor == BATCH_ELAPSED_ID) {
            batchElapsedTicks = getSynchedData().get(BATCH_ELAPSED_ID);
        } else if (accessor == BATCH_ACTIVE_ID) {
            isBatchReloading = getSynchedData().get(BATCH_ACTIVE_ID);
        } else if (accessor == REGEN_DELAY_ID) {
            regenDelayRemainingTicks = getSynchedData().get(REGEN_DELAY_ID);
        }
    }

    // ==================== 持久化 ====================

    @Override
    public void loadData(net.minecraft.nbt.CompoundTag data) {
        super.loadData(data);
        if (data.contains("ammo_count")) setAmmoCount(data.getInt("ammo_count"));
        if (data.contains("regen_progress")) regenProgress = data.getFloat("regen_progress");
        if (data.contains("batch_elapsed")) batchElapsedTicks = data.getInt("batch_elapsed");
        if (data.contains("is_batch_reloading")) isBatchReloading = data.getBoolean("is_batch_reloading");
        if (data.contains("regen_delay_remaining")) regenDelayRemainingTicks = data.getInt("regen_delay_remaining");
        // 预留与三项累计量不入存档：读档后未完成的装填重新申请，预测窗口从零开始，R 从 0 起
        reservations.clear();
        producedRounds = 0L;
        returnedRounds = 0L;
        deliveredRounds = 0L;
        stockEpoch = 0;
        publishLedger();
        publishRegenState();
        // 恢复锁存下不把上一段生命周期的那批产出带进来：存档可能写在归零的同一 tick
        // （那时摧毁边沿还没走，onDestroyed 的清理来不及发生），读档也不会补发摧毁回调
        if (isDestroyed()) clearRegenState();
    }

    @Override
    public net.minecraft.nbt.CompoundTag saveData(net.minecraft.nbt.CompoundTag data) {
        super.saveData(data);
        data.putInt("ammo_count", getAmmoCount());
        data.putFloat("regen_progress", regenProgress);
        data.putInt("batch_elapsed", isBatchReloading ? batchElapsedTicks : 0);
        data.putBoolean("is_batch_reloading", isBatchReloading);
        data.putInt("regen_delay_remaining", regenDelayRemainingTicks);
        return data;
    }

    // ==================== 声源位置 ====================

    @Override
    public Vec3 getPosition(java.util.UUID uuid) {
        return SparkMathKt.toVec3(getSubPart().getPosition());
    }

    @Override
    public Vec3 getSpeed(java.util.UUID uuid) {
        return SparkMathKt.toVec3(getSubPart().getLinearVelocity());
    }
}
