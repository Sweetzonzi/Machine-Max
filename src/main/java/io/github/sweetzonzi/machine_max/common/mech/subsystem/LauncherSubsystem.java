package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.particle.common.IParticleAnchor;
import cn.solarmoon.spark_core.physics.PenetrationKey;
import cn.solarmoon.spark_core.sound.ISoundSpreader;
import cn.solarmoon.spark_core.api.SpreadingSoundHelper;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.BallisticProjectile;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.projectile.type.BallisticProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier.SupplierRef;
import io.github.sweetzonzi.machine_max.common.registry.MMEntityDataSerializers;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.LauncherSubsystemAttr;
import jme3utilities.math.MyQuaternion;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractModuleAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module.BarrelModuleAttr;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 发射器子系统。<br>
 * 代表炮闩、导弹挂架、火箭发射管等单个发射口。<br>
 * 从locator位置沿其朝向发射数据驱动的投射物，初速和精度受子系统属性与投射物类型共同影响。<br>
 * 实现 {@link IAmmoConsumer} 接口以支持弹药消耗与供给，膛内弹药状态由 chamberedType 管理。<br>
 * 投射物类型由当前供给者提供，而非静态属性直接指定。<br>
 * 弹药管理已由 {@link WeaponControllerSubsystem} 接管——Controller 负责弹种选择和路由决策，
 * Launcher 仅执行 currentSupplier 上的装填和发射。
 * <p>
 * <b>供给者指认的双端口径：</b>"本管由哪台机器供弹"是服务端的决定，经 {@link #SUPPLIER_ID}
 * 同步给客户端；客户端据此解析出本机的供给者实例，挑选只发生在服务端。指认变化时旧供给者那边
 * 尚未取走的交付由 {@link #assignSupplier} 取消，两端都不会留下无人认领的输弹线。
 */
public class LauncherSubsystem extends ModularSubsystem implements IAmmoConsumer, ISoundSpreader, IParticleAnchor {

    public final LauncherSubsystemAttr attr;

    // ——— 计时器累积发射（物理线程重构） ———

    /**
     * 发射计时器累积（秒）。
     * <p>
     * 每 tick 累积 0.05s，除以单发间隔得到应发射数。
     * 停止开火时清零，防止"蓄力"。
     * <p>
     * <b>调用线程：</b>仅主线程（{@link #onTick()} 内部读写）。
     */
    private double fireAccumulator = 0.0;

    /**
     * 上一 tick 是否正在开火。
     * <p>
     * 用于检测"刚按下开火键"的首帧——首帧将 accumulator 预填充为
     * {@code intervalSec} 而非从 0 累积 0.05s，确保 RPM < 1200 的武器立即发射第一发。
     * <p>
     * <b>调用线程：</b>仅主线程（{@link #onTick()} 内部读写）。
     */
    private boolean wasFiring = false;

    /**
     * 上次成功开火的 tick 数（{@link #tickCount} 的值）。
     * <p>
     * 防止连按超射：首帧预填充仅在距离上次开火已过至少 {@code intervalSec} 时允许。
     * 连按（间隔不足）时拒绝预填充，退回正常累积。
     * 初始 -1 表示从未开火，允许首帧无条件预填充。
     * <p>
     * <b>调用线程：</b>仅主线程（{@link #onTick()} 内部读写）。
     */
    private int lastFireTick = -1;

    /**
     * 待物理线程发射的弹药类型队列。
     * <p>
     * <b>生产者：</b>主线程 onTick（弹药消费循环）。<br>
     * <b>消费者：</b>物理线程 {@link #onPrePhysicsTick()}（{@link #fireSingle(BallisticProjectileType)}）。<br>
     * 使用 {@link ConcurrentLinkedQueue} 保证无锁安全。
     */
    private final ConcurrentLinkedQueue<BallisticProjectileType> pendingFires = new ConcurrentLinkedQueue<>();

    // ——— 制导弹的目标推送（物理线程内闭环） ———

    /**
     * 本发射器打出的制导弹 objId 列表。
     * <p>
     * <b>仅物理线程访问</b>：{@link #fireSingle} 登记，{@link #updateGuidedTargets()} 剔除消亡条目；
     * 二者与武器控制器的目标推送同处物理线程 tick，故用普通 ArrayList 即可，无需加锁。
     */
    private final List<Integer> guidedObjIds = new ArrayList<>();

    /**
     * 当前制导目标点，由受控的武器控制器每物理步写入。
     * <p>
     * 目标是一个<b>世界坐标点</b>而非实体，可能每物理步移动；{@code null} = 无目标，
     * 此时在飞制导弹交回纯弹道。volatile 保证跨线程可见性。
     */
    @Nullable
    private volatile Vec3 guidanceTarget = null;

    // ——— 音效状态（仅客户端有效，由 onTick 管理） ———

    /** 当前活跃的连射循环音效UUID */
    @Nullable
    private UUID currentAutoFireUuid;

    /** 当前活跃的弹壳循环音效UUID */
    @Nullable
    private UUID currentShellSoundUuid;

    /** 当前连射音效档位key（用于检测RPM档位变化） */
    @Nullable
    private String currentFireRateTierKey;

    /** 当前弹壳音效档位key */
    @Nullable
    private String currentShellRateTierKey;

    /** 本轮连射已打的弹数（用于检测首发射击） */
    private int roundsFiredThisBurst = 0;

    /**
     * 开火动画是否已被判定为不可用（零件动画集中不存在该名称）。
     * <p>首次解析失败时置 true 并告警一次，之后静默跳过——
     * 高射速武器若每发都告警会刷屏。</p>
     * <p><b>调用线程：</b>仅客户端主线程（{@link #onTick()}）。</p>
     */
    private boolean fireAnimUnavailable = false;

    // ——— 弹药状态 ———

    /** 膛内当前弹药类型。null = 空膛 */
    @Nullable
    @Getter
    private BallisticProjectileType chamberedType;

    /**
     * 本管当前供给者的<b>身份</b>（网络同步）。<br>
     * 身份由 {@link SupplierRef} 表达：宿主 Part uuid + SubPart 名 + 子系统名，三者都随存档持久化，
     * 因此同一个值在两端、在重启前后都指同一台机器，可以直接进存档也可以直接跨端传。
     * <p>
     * <b>写者：</b>服务端路由（{@link #setCurrentSupplier}）与载入时的存档播种（{@link #loadData}）。
     * 客户端在 {@link #onSyncedDataUpdated(EntityDataAccessor)} 里把它解析成本机的供给者实例
     * （{@link #resolveSyncedSupplier()}），因此两端的指认由服务端单方面决定：
     * 客户端的路由不参与决策，只跟随。
     * <p>
     * 写入存档还有一个用途：后进玩家在收到载具数据时就能拿到当前指认，不必等服务端下一次改动。
     */
    private static final EntityDataAccessor<SupplierRef> SUPPLIER_ID =
            SynchedEntityData.defineId(LauncherSubsystem.class, MMEntityDataSerializers.getSUPPLIER_REF());

    /**
     * 当前选中的供给者引用。<br>
     * 服务端由 WeaponController 的路由决策设置；客户端由 {@link #SUPPLIER_ID} 同步值解析得到。
     * null 表示无供给者（不装填，可发射膛内已有弹药）。
     */
    @Nullable
    private IAmmoSupplier currentSupplier = null;

    /**
     * 按频道分组的供给者映射。<br>
     * key = 发现频道名（对应 LauncherStaticAttr.ammo_inputs 声明顺序）。<br>
     * LinkedHashMap 保证迭代顺序与 ammo_inputs 一致。
     */
    private final Map<String, List<IAmmoSupplier>> supplierChannels = new LinkedHashMap<>();

    // ——— 直接调用控制（替代信号轮询） ———

    /**
     * 发射控制指令：多控制器通过此 record 原子写入方向、修正角和开火指令。<br>
     * 由 {@link #setFireCommand} 写入，{@link #getActiveFireCommand} 按优先级读取。
     */
    public record FireCommand(
        @Nullable Vector3f direction,   // null = 使用 locator 朝向
        float correctionAngleDeg,       // 火控修正角（度），0 = 不修正
        boolean firing                  // 是否开火
    ) {}

    /**
     * 多控制器指令映射。<br>
     * key = controlInputs 频道名，value = 发射指令。<br>
     * 物理线程写（WC.onPrePhysicsTick），主线程读（onTick.isFiring）和物理线程读（fireSingle）。
     */
    private final ConcurrentHashMap<String, FireCommand> fireCommands = new ConcurrentHashMap<>();

    public LauncherSubsystem(ISubsystemHost owner, String name, LauncherSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        if (attr.locator == null || attr.locator.isEmpty()) {
            MachineMax.LOGGER.error("发射器子系统 {} 未配置发射点locator", name);
        }
    }

    // ==================== 模块化子系统 ====================

    @Override
    protected Map<String, ? extends AbstractModuleAttr> defineModules() {
        // 从显式字段构建模块映射，与 LauncherSubsystemStaticAttr 的结构保持一致
        Map<String, AbstractModuleAttr> modules = new LinkedHashMap<>();
        modules.put("barrel", attr.staticAttribute.getBarrel());
        modules.put("breech", attr.staticAttribute.getBreech());
        if (attr.staticAttribute.getCapacitor() != null) {
            modules.put("capacitor", attr.staticAttribute.getCapacitor());
        }
        return modules;
    }

    // ==================== 同步数据 ====================

    /**
     * 定义本子系统的同步数据项。<br>
     * 这些项与 {@link AbstractSubsystem} 的耐久/激活/摧毁位合并成同一张表，
     * 由基类每 tick 打包推送。
     */
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SUPPLIER_ID, SupplierRef.NONE);
    }

    /**
     * 同步数据落地后的处理。<br>
     * 收到子系统同步包时，基类会逐个访问器回调本方法（服务端自己写值时同样回调一次）。
     * 这里只处理客户端：服务端推来的指认要落到本机的供给者实例上。
     *
     * @param dataAccessor 发生变化的访问器
     */
    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> dataAccessor) {
        super.onSyncedDataUpdated(dataAccessor);
        if (getLevel().isClientSide() && dataAccessor.equals(SUPPLIER_ID)) {
            applySyncedSupplier();
        }
    }

    // ——— IAmmoConsumer 实现 ———

    @Override
    public boolean canAcceptAmmo() {
        return chamberedType == null || isActive();
    }

    @Override
    public int getFreeCapacity() {
        return chamberedType == null ? 1 : 0;
    }

    @Override
    public boolean receiveAmmo(ProjectileType type) {
        // 发射器只装填飞行弹丸；其它运动模型由各自的发射器处理
        if (!(type instanceof BallisticProjectileType kinetic)) return false;
        if (chamberedType == null && isActive()) {
            chamberedType = kinetic;
            return true;
        }
        return false;
    }

    @Override
    @Nullable
    public IAmmoSupplier getCurrentSupplier() {
        return currentSupplier;
    }

    /**
     * 设置当前供给者，由 WeaponController 的服务端路由调用。<br>
     * 引用发生变化时顺带取消旧供给者那边尚未取走的交付
     * （{@link IAmmoSupplier#cancelRound(IAmmoConsumer)}）：那条交付是"点名给本管的"，
     * 本管一走它就无人认领，旧装弹机会一直把输弹线占着，弹仓里那发既不会被取走也不会给别人。
     * 清理放在这里而不是交给调用方，是为了不存在"换人忘了归还"的路径。
     * <p>
     * 指认随之写入 {@link #SUPPLIER_ID} 推给客户端。客户端不调用本方法：它的指认由同步值经
     * {@link #applySyncedSupplier()} 落到同一字段上，两端因此不会各自决策出不同结果。
     * <p>
     * <b>调用线程：</b>主线程（{@link WeaponControllerSubsystem#onTick()} 内的弹药路由）。
     *
     * @param supplier 新供给者，null 表示无供给者（不装填，仍可发射膛内已有弹药）
     */
    @Override
    public void setCurrentSupplier(@Nullable IAmmoSupplier supplier) {
        assignSupplier(supplier);
        if (!getLevel().isClientSide()) {
            getSynchedData().set(SUPPLIER_ID, SupplierRef.of(supplier));
        }
    }

    /**
     * 把指认落到本机字段上，并取消旧供给者的待交付。<br>
     * 不含任何同步动作，也不动膛内那发：换指认只影响<b>后续</b>装填，膛内已经压好的那发照打
     * （它属于哪条弹链由玩家的显式退膛操作决定，不在这里替玩家丢）。服务端由
     * {@link #setCurrentSupplier} 追加写入同步值，客户端由 {@link #applySyncedSupplier()}
     * （跟随推送）与 {@link #resolveSyncedSupplier()}（载入播种）调用。
     */
    private void assignSupplier(@Nullable IAmmoSupplier supplier) {
        IAmmoSupplier previous = this.currentSupplier;
        if (previous == supplier) return;
        this.currentSupplier = supplier;
        if (previous != null) previous.cancelRound(this);
    }

    /**
     * 应用服务端推来的指认（客户端）。<br>
     * 值非空表示"这台机器派给了本管"，解析成本机实例；值为空表示服务端当前没给本管派供给者。
     * 两种情形都只改指认，不动膛内那发——与服务端的口径一致（换指认只影响后续装填）。
     */
    private void applySyncedSupplier() {
        SupplierRef ref = getSynchedData().get(SUPPLIER_ID);
        if (ref.isNone()) {
            assignSupplier(null);
            return;
        }
        IAmmoSupplier target = findSupplierByRef(ref);
        // 握手里还没出现这台机器：保持现状，等 onTick 的重试
        if (target == null || target == currentSupplier) return;
        assignSupplier(target);
    }

    /**
     * 按 {@link #SUPPLIER_ID} 里的身份在本机已发现的供给者中找出实例并指认。<br>
     * 身份是"宿主 Part uuid + SubPart 名 + 子系统名"，同一台机器在两端、在重启前后都是这个值，
     * 因此不需要跨端传对象引用，也不需要额外的查表。客户端在刚载入时（还没收到任何推送）用它把
     * 存档带回来的指认落地，服务端同样用它把存档里的指认在握手完成后落地。
     * <p>
     * 本方法只认领、不改变膛内状态：载入时的膛内弹药是存档里的既成事实，
     * 退不退由玩家显式操作决定。
     */
    private void resolveSyncedSupplier() {
        SupplierRef ref = getSynchedData().get(SUPPLIER_ID);
        if (ref.isNone()) return;
        IAmmoSupplier target = findSupplierByRef(ref);
        if (target != null && target != currentSupplier) assignSupplier(target);
    }

    /**
     * 在本机已发现的供给者里按身份找实例。
     *
     * @return 对应实例；身份为空或握手里还没有这台机器时返回 null
     */
    @Nullable
    private IAmmoSupplier findSupplierByRef(SupplierRef ref) {
        if (ref.isNone()) return null;
        for (IAmmoSupplier supplier : getSuppliers()) {
            if (SupplierRef.of(supplier).equals(ref)) return supplier;
        }
        return null;
    }

    @Override
    public List<IAmmoSupplier> getSuppliers() {
        // 返回所有频道的扁平列表
        List<IAmmoSupplier> all = new ArrayList<>();
        for (List<IAmmoSupplier> channelList : supplierChannels.values()) {
            all.addAll(channelList);
        }
        return all;
    }

    @Override
    public Map<String, List<IAmmoSupplier>> getSupplierChannels() {
        return supplierChannels;
    }

    @Override
    public void addSupplier(IAmmoSupplier supplier) {
        addSupplier(supplier, "unknown");
    }

    /**
     * 带频道名的供给者注册。<br>
     * 由 Loader 在 handshake 回调中调用。
     * 频道名对应 LauncherStaticAttr.ammo_inputs 中声明的发现频道。
     * <p>
     * 同一台重复握手（结构变化后重新发现）不重复登记。这里不写指认：谁给本管供弹由服务端的
     * 路由决定，登记只负责让本管知道"有哪些供给者存在"。
     */
    @Override
    public void addSupplier(IAmmoSupplier supplier, String channelName) {
        List<IAmmoSupplier> channelSuppliers = supplierChannels.computeIfAbsent(channelName, k -> new ArrayList<>());
        if (channelSuppliers.contains(supplier)) return;
        channelSuppliers.add(supplier);
    }

    @Override
    public boolean canAccept(ProjectileType type) {
        return attr.staticAttribute.getBreech().isAmmoCompatible(type);
    }

    /**
     * 直接装填一发弹药到膛内。<br>
     * 由外部调用（如 AmmoLoader 直接压弹），绕过 requestRound 流程。
     *
     * @return true 表示装填成功
     */
    public boolean loadRound(ProjectileType type) {
        if (!(type instanceof BallisticProjectileType kinetic)) return false;
        if (chamberedType != null || !isActive()) return false;
        if (!canAccept(type)) return false;
        chamberedType = kinetic;
        return true;
    }

    /**
     * 退膛：返回膛内弹药并清空。<br>
     * 退出的弹尝试还给当前供给者；收不收由供给者自己判（弹种是否在它的弹链上、弹仓有没有位置），
     * 被拒时弹药从膛内消失且未入库——掉落物品的落地位置与物品映射尚未实现。
     *
     * @return 退出的弹药类型，空膛返回 null
     */
    @Nullable
    public ProjectileType ejectRound() {
        ProjectileType round = chamberedType;
        if (round == null) return null;
        chamberedType = null;

        // 尝试归还给当前供给者；是否接收由供给者判定
        IAmmoSupplier supplier = getCurrentSupplier();
        if (supplier != null) supplier.returnRound(round);
        // TODO: returnRound 返回 false（不放行/放不下）时，生成 ItemEntity 掉落于发射器 locator 位置
        return round;
    }

    /**
     * 主线程 tick：计时器累积 + 弹药消费 + 入队 pendingFires + 客户端音效管理。
     * <p>
     * <b>调用线程：</b>主线程（20tps）。
     * <p>
     * 逻辑：
     * <ol>
     *   <li>若停止开火 → 处理停火音效，{@code fireAccumulator = 0}，防止蓄力</li>
     *   <li>刚按下开火键的首帧 → 预填充 accumulator 为 {@code intervalSec}，保证立即发射</li>
     *   <li>后续帧 → 正常 {@code fireAccumulator += 0.05s}，计算应发射数 {@code N}</li>
     *   <li>弹药消费循环（最多 N 发）：取弹 → 兼容校验 → 膛内 → 入队 {@link #pendingFires} → 装填下一发</li>
     *   <li>客户端音效处理：遍历 pendingFires，首发射击用单发音效，后续连射启动/维持循环音效</li>
     *   <li>扣除已发射数对应的时间：{@code fireAccumulator -= N × intervalSec}</li>
     * </ol>
     */
    @Override
    public void onTick() {
        super.onTick();
        // 指认的种子：本机字段还空着时（刚载入、握手尚未完成）按同步值里的身份认一次。
        // 少了它，载入后头几个 tick 本管处于"无供给者"，膛内那发会在路由里被当作无主而退掉。
        if (currentSupplier == null) resolveSyncedSupplier();
        // 若膛内无弹，尝试从供给者取弹
        if (chamberedType == null) tryLoadChamber();

        if (!isActive() || isDestroyed()) {
            // ★ 仅在"存在进行中的连射/未收尾的循环音效"时执行一次停火处理。
            //   子系统被摧毁、宿主零件被摧毁或装配度跌破阈值时该分支会每 tick 命中，
            //   若无条件调用 handleCeaseFire()，停火尾音会被反复播放。
            if (wasFiring || currentAutoFireUuid != null || currentShellSoundUuid != null) {
                handleCeaseFire();
            }
            resetFireState();
            return;
        }

        if (getActiveFireCommand() == null || chamberedType == null) {
            // 停止开火 → 处理停火音效 + 清零累积，防止下次开火"蓄力"
            if (wasFiring) {
                handleCeaseFire();
            }
            resetFireState();
            return;
        }

        // ★ 记录是否为本burst首帧（在 wasFiring 被置 true 之前）
        boolean burstJustStarted = !wasFiring;
        float intervalSec = 60f / attr.staticAttribute.getBreech().fireRate();

        // ① 计时器累积：首帧预填充 intervalSec 以立即发射，后续帧正常 +0.05s
        if (!wasFiring) {
            // 刚按下开火键 → 检查距离上次开火是否足够远，防止连按超射
            int elapsed = tickCount - lastFireTick;
            float elapsedSec = elapsed * 0.05f;
            if (lastFireTick < 0 || elapsedSec >= intervalSec) {
                // 从未开火或距离上次开火已超过间隔 → 允许预填充
                fireAccumulator = intervalSec;
            } else {
                // 连按（间隔不足 intervalSec）→ 正常累积，不预填充
                fireAccumulator = 0.05;
            }
        } else {
            fireAccumulator += 0.05; // 1 tick = 0.05s
        }
        wasFiring = true;
        int rounds = (int) (fireAccumulator / intervalSec);

        if (rounds < 1) return;

        // ② 弹药消费循环
        int firedCount = 0;
        // 本地快照：缓存本 tick 发射的弹药类型，供客户端特效处理使用。
        // 不直接遍历 pendingFires（物理线程可能同时 poll 消费），避免并发遍历问题。
        List<ProjectileType> firedTypes = new ArrayList<>(rounds);
        for (int i = 0; i < rounds; i++) {
            // 若膛内无弹，尝试从供给者取弹
            if (chamberedType == null) {
                if (!tryLoadChamber()) {
                    break; // 弹药未就绪，等待下一 tick
                }
            }

            // ③ 入队 pendingFires（物理线程将消费）
            pendingFires.add(chamberedType);
            firedTypes.add(chamberedType);
            firedCount++;
            chamberedType = null;

            // ④ 预请求下一发弹药：射击后立即向供给者发起请求，
            //    使输送计时器与射击并发运行，避免单膛室阻塞射速。
            if (!tryRequestNextRound()) {
                break; // 供给者无法接受请求（无弹药且无再生能力）
            }
        }

        // ⑤ 客户端音效特效处理（主线程，使用本地快照避免与物理线程并发访问 pendingFires）
        if (getLevel().isClientSide() && firedCount > 0) {
            handleFiringSounds(firedTypes, burstJustStarted);
            for (ProjectileType type : firedTypes) {
                type.playFireEffect(getLevel(), this, attr.locator);
            }
            playFireAnimation();
        }

        // ⑥ 扣除已发射数对应的时间
        fireAccumulator -= firedCount * intervalSec;
        if (fireAccumulator < 0) fireAccumulator = 0;
        // 记录上次开火 tick（用于防连按超射）
        if (firedCount > 0) lastFireTick = tickCount;
    }

    /**
     * 重置发射相关状态。
     */
    private void resetFireState() {
        fireAccumulator = 0.0;
        wasFiring = false;
    }

    /**
     * 尝试从供给者装填一发弹药到膛室。<br>
     * 弹种兼容性由 Controller 的路由负责：本方法收到不兼容弹药时只做防御性归还，
     * 等待下个 tick 的重路由。
     * <p>
     * 弹药未就绪时每次都向供给者重发请求：{@link IAmmoSupplier#requestRound} 幂等——
     * 已轮到本管时直接返回，本次没被排上也只返回"已受理"，下个 tick 重发即可再试。
     * 发射器不缓存"已请求"状态，因为"轮到没轮到"是供给者那边的交付状态，缓存在本地会在换供给者时脱节。
     *
     * @return true 表示膛室已有弹药（可立即发射）
     */
    private boolean tryLoadChamber() {
        IAmmoSupplier supplier = getCurrentSupplier();
        if (supplier == null) return false;

        if (supplier.isRoundReady(this)) {
            ProjectileType offered = supplier.consumeReadyRound(this);
            if (offered instanceof BallisticProjectileType kinetic && canAccept(offered)) {
                chamberedType = kinetic;
                return true;
            } else {
                // 防御性处理：Controller 不应选不兼容的 Loader。
                // 归还与否交给供给者判（它才知道自己收不收这个弹种）
                if (offered != null) {
                    MachineMax.LOGGER.warn("Launcher {} 收到不兼容弹药 {}，归还并等待 Controller 重路由", name, offered);
                    supplier.returnRound(offered);
                }
                return false;
            }
        }

        // 弹药未就绪 → 请求（或重复请求）交付，本次未到手就等下一 tick
        supplier.requestRound(this);
        return false;
    }

    /**
     * 预请求下一发弹药。<br>
     * 在每发射击后立即调用，启动供给者的输送计时器，使弹药输送与射击并发进行。<br>
     * 对于 {@code reloadTimeTicks > 0} 的供给者，这能显著减少有效射击间隔。
     *
     * @return true 表示请求已被受理（已轮到本管，或本次没排上、下个 tick 可再试）；
     *         false 表示供给者当前无弹可给
     */
    private boolean tryRequestNextRound() {
        IAmmoSupplier supplier = getCurrentSupplier();
        if (supplier == null) return false;

        // 即使请求被拒绝（供给者无弹药），也不阻塞：下个 tick 会重试
        // 若供给者为 RegenLoader，弹药再生产后 requestRound 会重新成功
        return supplier.requestRound(this);
    }

    /**
     * 物理线程 pre-tick：从 {@link #pendingFires} 逐发出队并执行发射。
     * <p>
     * <b>调用线程：</b>物理线程（Bullet 物理步进前）。
     * <p>
     * 每发调用 {@link #fireSingle(ProjectileType)}，在该方法内完成：
     * <ul>
     *   <li>读取刚体实时枪口位姿（物理线程，与物理体同步无延迟）</li>
     *   <li>散布 + 初速计算</li>
     *   <li>投射物 SoA 写入 + 刚体注册</li>
     *   <li>后坐力 applyImpulse（直接操作物理体）</li>
     * </ul>
     */
    @Override
    public void onPrePhysicsTick() {
        super.onPrePhysicsTick();
        if (getOwner() == null) return;

        BallisticProjectileType type;
        while ((type = pendingFires.poll()) != null) {
            fireSingle(type);
        }
    }

    /**
     * 物理线程单发发射。
     * <p>
     * <b>调用线程：</b>物理线程（由 {@link #onPrePhysicsTick()} 调用）。
     * <p>
     * 逻辑：
     * <ol>
     *   <li>读取刚体实时 {@link #getMuzzleWorldTransform()} 获取枪口位姿（与物理体同步）</li>
     *   <li>散布 + 最终初速计算</li>
     *   <li>继承发射平台速度</li>
     *   <li>调用 {@link ProjectileType#create} 创建投射物（内部进入 SoA 写入）</li>
     *   <li>后坐力 {@code applyImpulse} 直接作用到发射平台刚体</li>
     * </ol>
     */
    private void fireSingle(BallisticProjectileType type) {
        // ① 枪口位姿
        Transform muzzleTransform = getMuzzleWorldTransform();
        Vector3f jmePos = muzzleTransform.getTranslation();
        Quaternion jmeRot = muzzleTransform.getRotation();

        // ② 计算发射方向：按优先级使用火控指令中的期望方向和修正角
        Vector3f locatorForward = MyQuaternion.rotate(jmeRot, new Vector3f(0, 0, -1), null).normalize();
        Vector3f direction;
        FireCommand cmd = getActiveFireCommand();
        if (cmd != null && cmd.direction() != null && cmd.correctionAngleDeg() > 0.001f) {
            direction = clampToCone(locatorForward, cmd.direction(), cmd.correctionAngleDeg());
        } else {
            direction = locatorForward;
        }

        // ③ 客户端：不创建投射物，音效已在 onTick() 中处理
        if (getLevel().isClientSide()) {
            return;
        }

        // ④ 服务端：开火——ProjectileType 内部处理 bullet_num、散布、速度
        // 计算炮管模块对散布和初速的衰减乘数
        float spreadMult = 1.0f;
        float velMult = attr.staticAttribute.getBarrel().velocityMultiplier();
        SubModule barrelModule = getModule("barrel");
        if (barrelModule != null) {
            BarrelModuleAttr barrelAttr = (BarrelModuleAttr) barrelModule.getAttr();
            spreadMult = barrelAttr.getSpreadMultiplier(barrelModule.getDurabilityRatio());
            velMult *= barrelAttr.getMuzzleVelocityMultiplier(barrelModule.getDurabilityRatio());
        }
        List<BallisticProjectile> projectiles = type.fire(
            getLevel(), jmePos, direction,
            velMult,
            attr.staticAttribute.getBarrel().velocityBonus(),
            attr.staticAttribute.getBarrel().horizontalAccuracyMultiplier() * spreadMult,
            attr.staticAttribute.getBarrel().verticalAccuracyMultiplier() * spreadMult,
            getSubPart().getLinearVelocity()
        );

        // ⑤ 制导弹登记：记住 objId 以便后续每物理步推送目标，并装订初始目标
        if (type.hasGuidance()) {
            ProjectileManager pm = ObjectManager.getOrCreateProjectileManager(getLevel());
            Vec3 target = this.guidanceTarget;
            boolean hasTarget = target != null;
            float tx = hasTarget ? (float) target.x : Float.NaN;
            float ty = hasTarget ? (float) target.y : Float.NaN;
            float tz = hasTarget ? (float) target.z : Float.NaN;
            for (BallisticProjectile p : projectiles) {
                int id = ((DestroyableObject) p).getId();
                guidedObjIds.add(id);
                pm.setGuidanceTarget(id, tx, ty, tz);
            }
        }

        // ⑥ 自身排除：把本发射器零件的命中框预先登记为"已穿透"，使新弹从出膛起忽略本零件
        registerSelfExclusion(projectiles);

        // ⑦ 后坐力——总弹丸动量 × 后坐力倍率（默认 1.5≈弹丸+火药燃气贡献）
        float finalSpeed = type.getBaseVelocity() * attr.staticAttribute.getBarrel().velocityMultiplier()
                         + attr.staticAttribute.getBarrel().velocityBonus();
        float totalMass = type.getMass() * projectiles.size();
        float recoilImpulse = totalMass * finalSpeed * attr.staticAttribute.getBarrel().recoilMultiplier();
        if (recoilImpulse > 1e-6f) {
            Vector3f impulseWorld = direction.mult(-recoilImpulse);
            var body = getSubPart().getBody();
            Vector3f bodyWorldPos = body.getPhysicsLocation(new Vector3f());
            body.applyImpulse(impulseWorld, jmePos.subtract(bodyWorldPos));
        }
    }

    /**
     * 把本发射器所在零件的全部命中框登记为"已穿透"，令新弹从出膛起忽略该零件。
     * <p>
     * 复用投射物自身的穿透去重表：质点弹的射线检测与刚体弹的接触判定都会先查该表，
     * 因此登记一次即可同时覆盖两种运动模型；刚体弹的预碰撞回调还会据此取消接触响应，
     * 使弹体不被自身挂架弹开。
     * <p>
     * <b>调用线程：</b>物理线程（{@link #fireSingle} 内）。
     *
     * @param projectiles 本次出膛的投射物
     */
    private void registerSelfExclusion(List<BallisticProjectile> projectiles) {
        SubPart self = getSubPart();
        ProjectileManager pm = ObjectManager.getOrCreateProjectileManager(getLevel());
        for (BallisticProjectile p : projectiles) {
            int id = ((DestroyableObject) p).getId();
            for (HitBox hitBox : self.hitBoxes.values()) {
                pm.markPenetrated(id, new PenetrationKey(self, hitBox.getAttr().getId()));
            }
        }
    }

    // ——— 制导目标推送 API（由 WeaponController 每物理步调用） ———

    /**
     * 设置本发射器的制导目标点。
     * <p>
     * <b>调用线程：</b>物理线程（{@code WeaponControllerSubsystem.onPrePhysicsTick}，
     * 与 {@link #onPrePhysicsTick()} 同处物理线程 tick，因此目标推送无需队列）。
     *
     * @param target 目标世界坐标点，{@code null} 表示无目标
     */
    public void setGuidanceTarget(@Nullable Vec3 target) {
        this.guidanceTarget = target;
    }

    /**
     * 把当前目标推送给本发射器打出的所有存活制导弹，并剔除已消亡的条目。
     * <p>
     * 无可推送目标时写入 {@code NaN}——制导弹本步交回纯弹道。
     * <p>
     * <b>调用线程：</b>物理线程（由 {@code WeaponControllerSubsystem.onPrePhysicsTick} 调用）。
     */
    public void updateGuidedTargets() {
        if (guidedObjIds.isEmpty()) return;
        ProjectileManager pm = ObjectManager.getOrCreateProjectileManager(getLevel());
        Vec3 target = this.guidanceTarget;
        boolean hasTarget = target != null;
        float tx = hasTarget ? (float) target.x : Float.NaN;
        float ty = hasTarget ? (float) target.y : Float.NaN;
        float tz = hasTarget ? (float) target.z : Float.NaN;

        for (Iterator<Integer> it = guidedObjIds.iterator(); it.hasNext(); ) {
            int id = it.next();
            if (!pm.containsProjectile(id) || !pm.setGuidanceTarget(id, tx, ty, tz)) {
                it.remove();
            }
        }
    }

    // ——— 直接调用控制 API（由 WeaponController 调用） ———

    /**
     * 由 WeaponController 直接调用，原子设置发射指令。<br>
     * 替代旧的 setFiring + setFireDirection 双调，消除竞态。<br>
     * <b>调用线程：</b>物理线程。
     *
     * @param channel 控制频道名（对应 controlInputs 中的条目）
     * @param cmd     发射指令，null 或 firing=false = 释放控制权
     */
    public void setFireCommand(String channel, @Nullable FireCommand cmd) {
        if (cmd == null || !cmd.firing()) fireCommands.remove(channel);
        else fireCommands.put(channel, cmd);
    }

    /**
     * 按 controlInputs 频道优先级获取最高优先级的有效发射指令。
     *
     * @return 最高优先级的有效 FireCommand，null = 无控制器持有开火权
     */
    @Nullable
    private FireCommand getActiveFireCommand() {
        for (String channel : attr.staticAttribute.getControlInputs()) {
            FireCommand cmd = fireCommands.get(channel);
            if (cmd != null && cmd.firing()) return cmd;
        }
        return null;
    }

    /**
     * 将期望方向限制在以 locator 朝向为轴的锥角内。<br>
     * 若期望方向与 locator 朝向的夹角 ≤ maxAngleDeg，则直接使用期望方向；<br>
     * 否则将 locator 方向向期望方向旋转 maxAngleDeg 度。<br>
     * <b>调用线程：</b>物理线程（fireSingle 内部调用）。
     *
     * @param forward      locator 前方单位向量（锥轴方向）
     * @param preferred    期望方向（单位向量）
     * @param maxAngleDeg  最大允许偏离角度（度）
     * @return 修正后的发射方向（单位向量）
     */
    private static Vector3f clampToCone(Vector3f forward, Vector3f preferred, float maxAngleDeg) {
        // 手算夹角：Vector3f 无 angleBetween 方法
        float dot = forward.dot(preferred);
        float angle = (float) Math.acos(Math.clamp(dot, -1.0f, 1.0f));
        if (angle <= (float) Math.toRadians(maxAngleDeg)) return preferred;
        // 将 forward 向 preferred 方向旋转 maxAngleDeg
        // 使用 cross(..., null) 创建新实例，避免修改 forward
        // normalize() 返回新实例（Vector3f 无 normalizeLocal 实例方法）
        Vector3f axis = forward.cross(preferred, null).normalize();
        // fromAngleNormalAxis: Quaternion 的正确方法名
        Quaternion rot = new Quaternion().fromAngleNormalAxis((float) Math.toRadians(maxAngleDeg), axis);
        return MyQuaternion.rotate(rot, forward, null);
    }

    // ——— 开火动画（仅客户端 onTick 中调用） ———

    /**
     * 客户端：为本 tick 的射击触发开火动画。
     * <p>动画名取自 {@code attr.fireAnimation}（缺省 {@code "fire"}），
     * 由 {@link Part#playAnim(String)} 播放于 ACTION 覆盖层。</p>
     * <p><b>触发粒度：</b>每个 tick 至多触发一次。同一 tick 内的多发在视觉上无法区分，
     * 且 {@code enter()} 是异步提交到物理线程，重复调用会创建多个实例。</p>
     * <p><b>缺失处理：</b>动画集中不存在该名称时告警一次并永久禁用，避免高射速下刷屏。</p>
     * <p><b>调用线程：</b>主线程（{@link #onTick()} 客户端分支）。</p>
     */
    private void playFireAnimation() {
        if (fireAnimUnavailable) return;
        String animName = attr.fireAnimation;
        if (animName == null || animName.isEmpty()) return;
        Part part = getSubPart().part;
        if (part == null) return;
        if (!part.hasAnim(animName)) {
            fireAnimUnavailable = true;
            MachineMax.LOGGER.warn("发射器 {} 的开火动画 {} 不存在于零件 {} 的动画集中，已禁用开火动画",
                    name, animName, part.name);
            return;
        }
        part.playAnim(animName);
    }

    // ——— 音效管理（仅在客户端 onTick 中调用） ———

    /**
     * 处理停火：淡出循环音效，播放停火尾音，重置计数。
     * <p>
     * <b>调用线程：</b>主线程（{@link #onTick()}）。
     * </p>
     */
    private void handleCeaseFire() {
        if (!getLevel().isClientSide()) return;

        // 淡出连射循环音效
        if (currentAutoFireUuid != null) {
            SpreadingSoundHelper.fadeSound(getLevel(), currentAutoFireUuid);
            currentAutoFireUuid = null;
            currentFireRateTierKey = null;
        }

        // 淡出弹壳循环音效
        if (currentShellSoundUuid != null) {
            SpreadingSoundHelper.fadeSound(getLevel(), currentShellSoundUuid);
            currentShellSoundUuid = null;
            currentShellRateTierKey = null;
        }

        // 播放停火尾音
        ProjectileType ammo = getCurrentAmmoType();
        if (ammo != null) {
            SoundEvent ceaseSound = ammo.getCeaseFireSound();
            if (ceaseSound != null && ceaseSound != ProjectileType.ProjectileSoundAttr.NO_SOUND) {
                playSpreadingSound(getLevel(), ceaseSound, SoundSource.NEUTRAL);
            }

            // 停火时播放最后一枚弹壳音效（单发）
            SoundEvent shellSound = ammo.getShellSounds().get("0.0");
            if (shellSound != null) {
                playSpreadingSound(getLevel(), shellSound, SoundSource.NEUTRAL);
            }
        }

        roundsFiredThisBurst = 0;
    }

    /**
     * 处理开火音效：遍历本 tick 发射的弹药类型，按首发/连发分别播放音效。
     * <p>
     * 使用调用方传入的本地快照列表 {@code firedTypes}，而非直接遍历
     * {@link #pendingFires}，避免与物理线程的并发 poll 冲突。
     * </p>
     * <p>
     * <b>调用线程：</b>主线程（{@link #onTick()}）。
     * </p>
     *
     * @param firedTypes      本 tick 发射的弹药类型快照
     * @param burstJustStarted 是否为本 burst 首帧
     */
    private void handleFiringSounds(List<ProjectileType> firedTypes, boolean burstJustStarted) {
        for (ProjectileType type : firedTypes) {
            if (type == null) continue;

            roundsFiredThisBurst++;
            boolean isFirstRound = burstJustStarted && roundsFiredThisBurst == 1;

            tickFireSound(type, isFirstRound);
            tickShellSound(type, isFirstRound);
        }
    }

    /**
     * 处理单发/连发开火音效。
     * <p>
     * 首发射击（或未配置循环音效）→ 逐发播放 {@code "0.0"} 键的单发音效（不循环）。<br>
     * 后续连射 → 启动/维持循环音效，通过 {@link ISoundSpreader} 追踪枪口，RPM 跨档位时交叉淡入。
     * </p>
     *
     * @param type         弹药类型
     * @param isFirstRound 是否为首发
     */
    private void tickFireSound(ProjectileType type, boolean isFirstRound) {
        Map<String, SoundEvent> fireSounds = type.getFireSounds();
        if (fireSounds.isEmpty()) return;

        // 检查是否有非0.0的循环音效key
        boolean hasAutoKeys = fireSounds.keySet().stream().anyMatch(k -> !"0.0".equals(k));

        if (isFirstRound || !hasAutoKeys) {
            // ★ 首发射击 或 无循环音效配置 → 逐发播放单发音效
            SoundEvent singleSound = fireSounds.get("0.0");
            if (singleSound != null) {
                this.playSpreadingSound(getLevel(), singleSound, SoundSource.NEUTRAL);
            }
        } else {
            // ★ 连射 → 循环音效（通过 ISoundSpreader 追踪枪口位置/速度）
            String tierKey = findBestFireSoundKey(fireSounds);
            SoundEvent autoSound = fireSounds.get(tierKey);
            if (autoSound == null) return;

            if (currentAutoFireUuid == null) {
                // 首次连射：创建循环实例
                currentAutoFireUuid = playSpreadingSound(getLevel(), autoSound, SoundSource.NEUTRAL, 0, 0, true);
                currentFireRateTierKey = tierKey;
            } else if (!tierKey.equals(currentFireRateTierKey)) {
                // RPM 跨档位：交叉淡入新音效
                currentAutoFireUuid = transitionSound(getLevel(), currentAutoFireUuid, autoSound,
                    SoundSource.NEUTRAL, 0, 0, true);
                currentFireRateTierKey = tierKey;
            }
        }
    }

    /**
     * 处理弹壳音效，逻辑与开火音效相同。
     */
    private void tickShellSound(ProjectileType type, boolean isFirstRound) {
        Map<String, SoundEvent> shellSounds = type.getShellSounds();
        if (shellSounds.isEmpty()) return;

        boolean hasAutoKeys = shellSounds.keySet().stream().anyMatch(k -> !"0.0".equals(k));

        if (isFirstRound || !hasAutoKeys) {
            SoundEvent singleSound = shellSounds.get("0.0");
            if (singleSound != null) {
                playSpreadingSound(getLevel(), singleSound, SoundSource.NEUTRAL);
            }
        } else {
            String tierKey = findBestFireSoundKey(shellSounds);
            SoundEvent autoSound = shellSounds.get(tierKey);
            if (autoSound == null) return;

            if (currentShellSoundUuid == null) {
                currentShellSoundUuid = playSpreadingSound(getLevel(), autoSound, SoundSource.NEUTRAL, 0, 5, true);
                currentShellRateTierKey = tierKey;
            } else if (!tierKey.equals(currentShellRateTierKey)) {
                currentShellSoundUuid = transitionSound(getLevel(), currentShellSoundUuid, autoSound,
                    SoundSource.NEUTRAL, 0, 5, true);
                currentShellRateTierKey = tierKey;
            }
        }
    }

    /**
     * 根据当前发射器射速，查找最匹配的音效档位key。
     * <p>
     * 排除 {@code "0.0"} 键，在剩余key中找数值 ≤ 当前RPM 的最大者。
     * 若无匹配则回退到 {@code "0.0"}。
     * </p>
     *
     * @param sounds 音效映射（key=RPM字符串）
     * @return 最佳匹配的档位key
     */
    private String findBestFireSoundKey(Map<String, SoundEvent> sounds) {
        float rpm = attr.staticAttribute.getBreech().fireRate();
        return sounds.keySet().stream()
            .filter(k -> !"0.0".equals(k))
            .map(k -> new AbstractMap.SimpleEntry<>(k, parseRpm(k)))
            .filter(e -> !Float.isNaN(e.getValue()) && e.getValue() <= rpm)
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("0.0");
    }

    /** 安全解析RPM字符串 */
    private static float parseRpm(String key) {
        try {
            return Float.parseFloat(key);
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    // ——— ISoundSpreader 实现 ———

    @Override
    @NotNull
    public Vec3 getPosition(UUID uuid, SoundEvent event) {
        return getMuzzleWorldPosition();
    }

    @Override
    @NotNull
    public Vec3 getSpeed(UUID uuid, SoundEvent event) {
        return SparkMathKt.toVec3(getSubPart().getLinearVelocity());
    }

    @Override
    public float getPitch(UUID uuid, SoundEvent event) {
        // 根据当前RPM与档位设计RPM的比值调制音高
        String tierKey = null;
        if (uuid.equals(currentAutoFireUuid)) {
            tierKey = currentFireRateTierKey;
        } else if (uuid.equals(currentShellSoundUuid)) {
            tierKey = currentShellRateTierKey;
        }
        if (tierKey != null) {
            float designRPM = parseRpm(tierKey);
            if (!Float.isNaN(designRPM) && designRPM > 0) {
                float actualRPM = attr.staticAttribute.getBreech().fireRate();
                return Math.clamp(actualRPM / designRPM, 0.5f, 2.0f);
            }
        }
        return 1.0f;
    }

    @Override
    public boolean shouldApplyInteriorEffect(@Nullable Entity listener, SoundEvent event, boolean isFirstPerson) {
        if (listener == null || !isFirstPerson) return false;
        return ((IEntityMixin) listener).machine_Max$getControllingSubsystem() != null;
    }

    /**
     * 获取当前弹药类型（按优先级）。<br>
     * 优先返回膛内已装填的弹药类型；若空膛则尝试从当前选中的供给者获取正在装填的弹药类型。
     *
     * @return 当前弹药类型，完全无弹药信息时返回 null
     */
    @Nullable
    public ProjectileType getCurrentAmmoType() {
        if (chamberedType != null) return chamberedType;
        IAmmoSupplier supplier = getCurrentSupplier();
        return supplier != null ? supplier.getSuppliedType() : null;
    }

    /**
     * 获取发射点在世界空间中的位姿。
     */
    public Transform getMuzzleWorldTransform() {
        return getOwner().getSubPart().getLocatorWorldTransform(attr.locator);
    }

    // ——— IParticleAnchor 实现 ———

    @Override
    @Nullable
    public Transform getLocatorTransform(@NotNull UUID instanceId, @NotNull String locatorName) {
        // 仅响应自己的 locator
        if (!locatorName.equals(attr.locator)) return null;
        return getMuzzleWorldTransform();
    }

    @Override
    @Nullable
    public Vec3 getAnchorVelocity(@NotNull UUID instanceId, @NotNull String locatorName) {
        return SparkMathKt.toVec3(getSubPart().getLinearVelocity());
    }

    /**
     * 获取发射点在世界空间中的位置。
     */
    public Vec3 getMuzzleWorldPosition() {
        Vector3f pos = getMuzzleWorldTransform().getTranslation();
        return new Vec3(pos.x, pos.y, pos.z);
    }

    /**
     * 获取发射方向在世界空间中的单位向量（JME前方为-Z方向）。
     */
    public Vec3 getMuzzleDirection() {
        Transform tf = getMuzzleWorldTransform();
        Quaternion rot = tf.getRotation();
        Vector3f forward = MyQuaternion.rotate(rot, new Vector3f(0, 0, -1), null);
        return new Vec3(forward.x, forward.y, forward.z).normalize();
    }

    /**
     * 获取当前有效的发射方向（包含火控修正后的实际弹道方向）。<br>
     * 若有活跃 FireCommand 指定了期望方向和修正角，则返回 clampToCone 修正后的方向；<br>
     * 否则返回原始 locator 朝向。<br>
     * <b>用途：</b>HUD 准星绘制，体现火控修正量对实际弹道的影响。<br>
     * <b>调用线程：</b>渲染线程。
     *
     * @return 世界空间单位方向向量
     */
    public Vec3 getEffectiveFireDirection() {
        Transform tf = getMuzzleWorldTransform();
        Quaternion rot = tf.getRotation();
        Vector3f locatorForward = MyQuaternion.rotate(rot, new Vector3f(0, 0, -1), null).normalize();

        FireCommand cmd = getActiveFireCommand();
        if (cmd != null && cmd.direction() != null && cmd.correctionAngleDeg() > 0.001f) {
            Vector3f corrected = clampToCone(locatorForward, cmd.direction(), cmd.correctionAngleDeg());
            return new Vec3(corrected.x, corrected.y, corrected.z);
        }
        return new Vec3(locatorForward.x, locatorForward.y, locatorForward.z);
    }

    /**
     * 判断发射器当前是否已对准目标（使用修正后的有效发射方向）。<br>
     * 内部调用 {@link #getEffectiveFireDirection()}，自动体现火控修正量。
     *
     * @param target       目标世界坐标
     * @param toleranceDeg 容差角度（度）
     * @return true 表示发射器已对准目标
     */
    public boolean isAimedAt(Vec3 target, float toleranceDeg) {
        Vec3 muzzlePos = getMuzzleWorldPosition();
        Vec3 toTarget = target.subtract(muzzlePos).normalize();
        Vec3 fireDir = getEffectiveFireDirection();
        double dot = toTarget.dot(fireDir);
        double angleRad = Math.acos(Math.clamp(dot, -1.0, 1.0));
        return Math.toDegrees(angleRad) <= toleranceDeg;
    }

    // ==================== 持久化 ====================

    @Override
    public void loadData(CompoundTag data) {
        super.loadData(data);
        if (data.contains("chambered_type")) {
            ResourceLocation key = ResourceLocation.parse(data.getString("chambered_type"));
            ProjectileType stored = ProjectileType.get(getLevel(), key);
            chamberedType = stored instanceof BallisticProjectileType kinetic ? kinetic : null;
        } else {
            chamberedType = null;
        }
        // 供给者指认随载具数据一起送达（同步值只在变化时推送，所以这里补一次当前值）。
        // 身份是 Part uuid + SubPart 名 + 子系统名，三者都在数据里持久化，重启后仍指同一台机器。
        // 客户端解析要等本机握手完成，落空时由 onTick 重试。
        if (data.hasUUID("supplier_part")) {
            getSynchedData().set(SUPPLIER_ID, new SupplierRef(
                    data.getUUID("supplier_part"),
                    data.getString("supplier_sub_part"),
                    data.getString("supplier_subsystem")));
        }
    }

    @Override
    public CompoundTag saveData(CompoundTag data) {
        super.saveData(data);
        if (chamberedType != null) {
            data.putString("chambered_type", chamberedType.getRegistryKey().toString());
        }
        SupplierRef supplierRef = getSynchedData().get(SUPPLIER_ID);
        if (!supplierRef.isNone()) {
            data.putUUID("supplier_part", supplierRef.partUuid());
            data.putString("supplier_sub_part", supplierRef.subPartName());
            data.putString("supplier_subsystem", supplierRef.subsystemName());
        }
        return data;
    }

    /**
     * 在水平/垂直方向分别应用椭圆锥散布。
     * <p>
     * <b>调用线程：</b>物理线程（{@link #fireSingle} 调用）。
     * 使用 {@link java.util.concurrent.ThreadLocalRandom} 避免访问 Minecraft 主线程
     * {@link net.minecraft.world.level.Level#random} 导致的线程检测异常。
     */
    @Override
    public List<String> getAcceptedChannels() {
        List<String> channels = new ArrayList<>(attr.staticAttribute.getControlInputs());
        channels.addAll(attr.staticAttribute.getAmmoInputs());
        return channels;
    }

    @Override
    public boolean acceptAllBroadcastInput() {
        return acceptAllRoutingInput();
    }

    @Override
    public boolean acceptAllRoutingInput() {
        return attr.staticAttribute.getControlInputs().isEmpty() || attr.staticAttribute.getAmmoInputs().isEmpty();
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(2);
        result.putAll(attr.ammoCountOutputs);
        return result;
    }
}
