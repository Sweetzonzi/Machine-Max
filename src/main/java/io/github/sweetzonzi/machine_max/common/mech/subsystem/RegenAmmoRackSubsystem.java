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
 * 各作业的预留量。它不做装填计时，也不向任何人推送进度——机构的机械段决定什么时候来取，
 * Launcher 的作业进度记在管上。
 * <p>
 * <b>预留：</b>每条作业在受理时按整次作业取一份 {@code q}（{@link #reserve}），可分配库存随之减少，
 * 但实际库存 {@code N} 在交付那一刻才扣（{@link #deliverReserved}）。已取得预留的作业不受其他作业
 * 抢走可分配库存的影响；预留被撤销（来源失效、强制损失、作业终止）时只减少承诺，不补弹。
 * 任意时刻 {@code 0 ≤ R = Σq ≤ N}，{@link #trimReservationsToStock} 维护这条不变量。
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
public class RegenAmmoRackSubsystem extends BasicSubsystem {

    public final RegenAmmoRackSubsystemAttr attr;

    /** 当前弹药计数（网络同步） */
    private static final EntityDataAccessor<Integer> AMMO_COUNT_ID =
            SynchedEntityData.defineId(RegenAmmoRackSubsystem.class, EntityDataSerializers.INT);

    /**
     * 已预留总量 {@code R}（网络同步）。<br>
     * 已经包含本作业自己的份额：显示可分配数量时只算 {@code N - R}，本作业的 {@code q} 就在 {@code R} 里面。
     * 逐作业的预留表 {@link #reservations} 只在服务端有内容，客户端跟随这个总量。
     */
    private static final EntityDataAccessor<Integer> RESERVED_ID =
            SynchedEntityData.defineId(RegenAmmoRackSubsystem.class, EntityDataSerializers.INT);

    /**
     * 各作业的预留量 {@code q}：作业身份 → 份数。<br>
     * 只在服务端写：受理时新增（{@link #reserve}），交付（{@link #deliverReserved}）与释放
     * （{@link #releaseReservation}）时整笔删除，没有部分兑现。不入存档——读档后未完成的装填重新申请。
     */
    private final Map<ReloadJobKey, Integer> reservations = new LinkedHashMap<>();

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
        builder.define(RESERVED_ID, 0);
    }

    // ==================== 库存 ====================

    /** 当前弹药计数 */
    public int getAmmoCount() {
        return getSynchedData().get(AMMO_COUNT_ID);
    }

    private void setAmmoCount(int count) {
        getSynchedData().set(AMMO_COUNT_ID, count);
    }

    /** 已预留总量 {@code R}：各作业 {@code q} 之和，已包含本作业份额 */
    public int getReservedTotal() {
        return getSynchedData().get(RESERVED_ID);
    }

    /** 可分配库存 {@code N - R}：可供尚未受理的作业申请的份数 */
    public int getAvailableCount() {
        return Math.max(0, getAmmoCount() - getReservedTotal());
    }

    /**
     * 该作业在本架的预留量。<br>
     * 预留表只在服务端有内容，客户端读到 0；客户端按作业上同步的 {@code reservedCount} 与这里的总量
     * 判断这条作业的承诺是否还在。
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
     * 同一身份重复申请沿用原份额，不叠加；{@link ReloadJobKey#NONE} 与客户端调用都不产生预留。
     * <p><b>调用线程：</b>主线程（服务端受理点）。
     *
     * @param key    作业身份
     * @param wanted 本次最多想预留多少发（通常是交付规格的长度）
     * @return 实际预留量 {@code q}；没有可分配库存时返回 0
     */
    public int reserve(ReloadJobKey key, int wanted) {
        if (getLevel().isClientSide() || key.isNone() || wanted <= 0) return 0;
        int existing = reservedFor(key);
        if (existing > 0) return existing;
        int granted = Math.min(getAvailableCount(), wanted);
        if (granted <= 0) return 0;
        reservations.put(key, granted);
        publishReserved();
        return granted;
    }

    /**
     * 按身份撤销一条作业的预留：只减少承诺，不改动实际库存、不补弹。<br>
     * 重复撤销、撤销不存在的身份都是无操作。
     * <p><b>调用线程：</b>主线程（服务端）。
     *
     * @param key 作业身份
     */
    public void releaseReservation(ReloadJobKey key) {
        if (getLevel().isClientSide() || key.isNone()) return;
        if (reservations.remove(key) != null) publishReserved();
    }

    /**
     * 兑现一条作业的预留：按该身份扣减实际库存并生成交付实例。<br>
     * 同一身份只兑现一次——预留在此处删除，重复调用返回 {@code null}，不会扣第二次。
     * <p><b>调用线程：</b>主线程（服务端交付点）。
     *
     * @param key 作业身份
     * @return 交付实例，发数等于该作业的预留量；没有预留时返回 {@code null}
     */
    @Nullable
    public AmmoBelt deliverReserved(ReloadJobKey key) {
        if (getLevel().isClientSide() || key.isNone()) return null;
        Integer granted = reservations.remove(key);
        if (granted == null || granted <= 0) return null;
        publishReserved();
        setAmmoCount(getAmmoCount() - granted);
        applyDeliveryDelay();
        return attr.beltDefinition.freshCopy(granted);
    }

    /** 把预留表的总量写进同步值 {@code R}。 */
    private void publishReserved() {
        int total = 0;
        for (int granted : reservations.values()) total += granted;
        getSynchedData().set(RESERVED_ID, total);
    }

    /**
     * 维护 {@code R ≤ N}：实际库存因非交付原因减少后，按 {@link ReloadJobKey} 元组升序排列预留键，
     * 从最大键起整笔撤销，直到剩余预留总量不超过新的实际库存。<br>
     * 撤销顺序只由服务端这一处决定，客户端跟随 {@code R} 的新值；保留的份额不被静默缩小。
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
        publishReserved();
    }

    /** 存量上限，来自型号的 {@code capacity} */
    public int getCapacity() {
        return attr.staticAttribute.getCapacity();
    }

    /** 实际库存非零即成立，与够不够一整份无关；已被预留的部分也算在内，可分配数量见 {@link #getAvailableCount()} */
    public boolean hasAmmo() {
        return getAmmoCount() > 0;
    }

    /**
     * 本架此刻能否供货：自已启用、未摧毁（含 0%—30% 的恢复锁存）。<br>
     * 这是"能不能交"的实时判断，与"有没有弹"是两件事：空架但健康照常再生。
     */
    public boolean isOperational() {
        return isActive() && !isDestroyed();
    }

    /** 下一份交付的规格：{@code belt_definition} 本身，满额、回充运行态为零。调用方不得原地推进它。 */
    public AmmoBelt getDeliverySpec() {
        return attr.beltDefinition;
    }

    /** 交付规格的弹链名（HUD 弹链选项卡用），不读库存，因此同一份规格的返回值恒定。 */
    public String getLabel() {
        return attr.beltDefinition.displayLabel();
    }

    /** 下一发的弹种，{@code null} 表示无交付规格 */
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
    }

    /**
     * 接收一份退还的弹药。<br>
     * 收下条件三条，都由本架自己判：① 本架可储存（未摧毁、非恢复锁存）；② 内容里的弹种都在本架允许范围内
     * ——收进发不出去的弹种只会让计数与实际供给能力脱节；③ 剩余容量装得下这一份的余量。
     * 三条都满足才按剩余发数计入库存，并丢弃弹序与容器信息（架只记数量）。
     * <p>
     * 计数只在服务端递增（客户端跟随服务端推送）。
     *
     * @param belt 退还的那一份弹药
     * @return true 表示已收下并计入库存
     */
    public boolean returnBelt(AmmoBelt belt) {
        if (!isOperational()) return false;
        if (!allowsEveryAmmo(belt)) return false;
        int amount = belt.remaining();
        if (amount <= 0) return true;
        if (getAmmoCount() + amount > getCapacity()) return false;
        if (!getLevel().isClientSide()) setAmmoCount(getAmmoCount() + amount);
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
        // 库存归零之后，架内已预留的份额也没有弹可兑现：按身份整笔撤销，不留 R > N
        trimReservationsToStock();
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
    }

    /** 随打随产：每 tick 累积再生进度，达到 1.0 时产出一发。 */
    private void tickRegenRoundByRound() {
        float perMinute = attr.staticAttribute.getRegenPerMinute();
        if (perMinute <= 0f) return;
        if (getAmmoCount() >= getCapacity()) return;

        // 20 tick/s × 60 s = 1200 tick/min
        regenProgress += perMinute / 1200f;

        while (regenProgress >= 1.0f && getAmmoCount() < getCapacity()) {
            float energyCost = attr.staticAttribute.getEnergyCostPerRound();
            if (energyCost > 0f && !tryConsumeEnergy(energyCost)) break; // 能量不足，暂停再生
            if (!getLevel().isClientSide()) setAmmoCount(getAmmoCount() + 1);
            regenProgress -= 1.0f;
        }
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

    /** 批量产出：打空后触发冷却，冷却结束后一次性回满。 */
    private void tickBatchReload() {
        if (getAmmoCount() == 0 && !isBatchReloading) {
            float perMinute = attr.staticAttribute.getRegenPerMinute();
            if (perMinute <= 0f) return;

            // 扣除整批能量
            float totalEnergy = getCapacity() * attr.staticAttribute.getEnergyCostPerRound();
            if (totalEnergy > 0f && !tryConsumeEnergy(totalEnergy)) return; // 能量不够整批，等下一 tick 再试

            // 冷却时长由 batchTotalTicks() 现算，这里只把已消耗量归零；
            // 本次 tick 随即计入一格，因此"触发那一 tick 本身算作冷却的第一 tick"
            batchElapsedTicks = 0;
            isBatchReloading = true;
        }

        if (isBatchReloading) {
            batchElapsedTicks++;
            if (batchElapsedTicks >= batchTotalTicks()) {
                isBatchReloading = false;
                batchElapsedTicks = 0;
                // 库存计数只有服务端写：客户端等下一次同步，避免把权威值改在本地
                if (!getLevel().isClientSide()) setAmmoCount(getCapacity());
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

    // ==================== 持久化 ====================

    @Override
    public void loadData(net.minecraft.nbt.CompoundTag data) {
        super.loadData(data);
        if (data.contains("ammo_count")) setAmmoCount(data.getInt("ammo_count"));
        if (data.contains("regen_progress")) regenProgress = data.getFloat("regen_progress");
        if (data.contains("batch_elapsed")) batchElapsedTicks = data.getInt("batch_elapsed");
        if (data.contains("is_batch_reloading")) isBatchReloading = data.getBoolean("is_batch_reloading");
        if (data.contains("regen_delay_remaining")) regenDelayRemainingTicks = data.getInt("regen_delay_remaining");
        // 预留不入存档：读档后未完成的装填重新申请，R 从 0 起
        reservations.clear();
        publishReserved();
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
