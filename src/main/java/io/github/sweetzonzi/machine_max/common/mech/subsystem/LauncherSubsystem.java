package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.particle.common.IParticleAnchor;
import cn.solarmoon.spark_core.physics.PenetrationKey;
import cn.solarmoon.spark_core.sound.ISoundSpreader;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.energy.EnergyGrid;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.explosion.CookoffComposition;
import io.github.sweetzonzi.machine_max.common.mech.explosion.ExplosionManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.BallisticProjectile;
import io.github.sweetzonzi.machine_max.common.mech.projectile.MuzzleFlashLight;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileManager;
import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.projectile.type.BallisticProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.subpart.SubPartDamageEvent;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IAmmoSupplier.SupplierRef;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.registry.MMEntityDataSerializers;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
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
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.module.BreechModuleAttr;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * 发射器子系统。<br>
 * 代表炮闩、导弹挂架、火箭发射管等单个发射口。<br>
 * 从locator位置沿其朝向发射数据驱动的投射物，初速和精度受子系统属性与投射物类型共同影响。<br>
 * 实现 {@link IAmmoConsumer} 接口以支持弹药消耗与供给，膛内弹药状态由 chamberedType 管理。<br>
 * 投射物类型由当前供给者提供，而非静态属性直接指定。<br>
 * 弹药管理已由 {@link WeaponControllerSubsystem} 接管——Controller 负责弹种选择和路由决策，
 * Launcher 仅执行 currentSupplier 上的装填和发射。
 * <p>
 * <b>膛内那发是独立的一份库存单元：</b>炮闩（唯一的关键模块）归零时，膛内那发按自己的发射药与
 * 战斗部 {@code cookoff} 贡献合成一次普通爆炸并被消耗，见 {@link #onDestroyed()}。它已经从供给者
 * 的弹仓里扣掉，因此与装弹机的库存殉爆互不重复计数。
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
     * 已装弹药。null 表示没有已装供弹数据。<br>
     * 整份装填（{@code single_round_loading=false}）的武器把拉到的一整份 Belt 存在这里，
     * 并自行推进余量与回充；单发装填的武器不建立本字段，每次拉取都把那一发直接写入 {@link #chamberedType}。
     * 服务端持久化并同步它，客户端只读同步值。
     */
    @Nullable
    @Getter
    private AmmoBelt loadedBelt;

    /**
     * 本管当前供给者的<b>身份</b>（网络同步）。<br>
     * 身份由 {@link SupplierRef} 表达：宿主 SubPart ID + 子系统名，同一会话内两端一致。
     * <p>
     * <b>写者：</b>服务端路由（{@link #setCurrentSupplier}）。客户端在
     * {@link #onSyncedDataUpdated(EntityDataAccessor)} 里把它解析成本机的供给者实例
     * （{@link #resolveSyncedSupplier()}），因此两端的指认由服务端单方面决定：
     * 客户端的路由不参与决策，只跟随。
     * <p>
     * <b>初值来源是补推，不是变化推送：</b>指认是"服务端每 tick 现算、载入后往往不再变化"的值，
     * 载具在客户端建好那一刻由 {@code SubsystemController.pushAllTo(ServerPlayer)} 补一份全量快照
     * （见 {@link AbstractSubsystem#forEachSyncedValue(Consumer)}）；此后只有真的换人时才走变化广播。
     * 本字段因此不进存档。
     */
    private static final EntityDataAccessor<SupplierRef> SUPPLIER_ID =
            SynchedEntityData.defineId(LauncherSubsystem.class, MMEntityDataSerializers.getSUPPLIER_REF());

    /**
     * 已装弹药（网络同步）。<br>
     * 服务端在拉取、消耗、回充与换装后写入一份快照；客户端据此显示余量与回充进度。
     * 值是快照而不是服务端持有的那个可变实例——同值写入不会触发广播，因此写入方必须给副本。
     */
    private static final EntityDataAccessor<Optional<AmmoBelt>> BELT_ID =
            SynchedEntityData.defineId(LauncherSubsystem.class, MMEntityDataSerializers.getOPTIONAL_AMMO_BELT());

    /** 膛内那一发（网络同步），空膛为空值。 */
    private static final EntityDataAccessor<Optional<ResourceLocation>> CHAMBERED_ID =
            SynchedEntityData.defineId(LauncherSubsystem.class, MMEntityDataSerializers.getOPTIONAL_RESOURCE_LOCATION());

    /**
     * 当前装填作业的编号（网络同步）。{@code 0} 表示没有作业。<br>
     * 编号与宿主 SubPart ID、本子系统名组成作业身份 {@link ReloadJobKey}：机构占用、实际架预留与独占
     * 机构的槽位拥有者都用它匹配。客户端据它区分"同一条作业又收到一次更新"与"换了一条作业"——
     * 前者保留本地已推进的进度，后者从基线重新开始。
     */
    private static final EntityDataAccessor<Integer> JOB_ID_ID =
            SynchedEntityData.defineId(LauncherSubsystem.class, EntityDataSerializers.INT);

    /** 当前作业的交付目标（网络同步）：机构路径是机构，纯人工直取路径是弹药架。 */
    private static final EntityDataAccessor<SupplierRef> JOB_TARGET_ID =
            SynchedEntityData.defineId(LauncherSubsystem.class, MMEntityDataSerializers.getSUPPLIER_REF());

    /**
     * 当前作业的实际来源弹药架（网络同步）。<br>
     * 由机构在受理时挑定并写进作业；纯人工直取路径与 {@link #JOB_TARGET_ID} 相同。客户端用它定位
     * "这份交付从哪台架来"：判断本地能否推进、播放机械段音效与显示来源。
     */
    private static final EntityDataAccessor<SupplierRef> JOB_ORIGIN_ID =
            SynchedEntityData.defineId(LauncherSubsystem.class, MMEntityDataSerializers.getSUPPLIER_REF());

    /**
     * 当前作业的冻结交付规格（网络同步）。<br>
     * 受理时定下，此后供给者的规格变化不改写它；客户端据此知道这一份是什么内容、多少发，
     * 不必在完成时从变化后的供给预览重新推导。
     */
    private static final EntityDataAccessor<Optional<AmmoBelt>> JOB_SPEC_ID =
            SynchedEntityData.defineId(LauncherSubsystem.class, MMEntityDataSerializers.getOPTIONAL_AMMO_BELT());

    /** 当前作业的弹药预留量 {@code q}（网络同步）：实际架已按这个份额扣着库存。 */
    private static final EntityDataAccessor<Integer> JOB_RESERVED_ID =
            SynchedEntityData.defineId(LauncherSubsystem.class, EntityDataSerializers.INT);

    /**
     * 当前作业本阶段的实际时长（网络同步）。<br>
     * 由服务端发布，客户端不按型号或人员能力重算；段内不重算分母，进度因此不会倒退。
     */
    private static final EntityDataAccessor<Integer> JOB_TOTAL_ID =
            SynchedEntityData.defineId(LauncherSubsystem.class, EntityDataSerializers.INT);

    /**
     * 当前作业已推进 tick 数（网络同步）。<br>
     * <b>只作基线，不是每 tick 的进度镜像：</b>服务端在作业开始与离散重建（暂停恢复、换人、完整补推）
     * 时写一次，稳定装填期间不写。两端在基线之上各自按执行者给的推进量累计，因此这条通路不承担
     * 逐 tick 计时。客户端只在换作业或收到更新的基线时向前对齐，不让进度倒退。
     */
    private static final EntityDataAccessor<Integer> JOB_ELAPSED_ID =
            SynchedEntityData.defineId(LauncherSubsystem.class, EntityDataSerializers.INT);

    /**
     * 最近一次结束的作业编号（网络同步）。{@code 0} 表示本端还没有结束过作业。<br>
     * 结束结果与 {@link #JOB_RESULT_DELIVERED_ID} 成对下发，只在作业收尾时写一次：取消、换人、摧毁、
     * 来源失效、暂停超时与成功交付共用这一对字段，因此客户端不需要为"超时"或"只清资源"另设一条通路。
     * 它就是本管的作业身份（编号在本管范围内唯一），不需要把宿主 ID 与子系统名再编码一遍。
     * <p>
     * 保留最近一次而不是清空：客户端靠它认出"这条结果对应哪个 jobId"，也靠它识别重复到达的同一结果；
     * 新作业开始时也不清，历史结果只在被下一次结束覆盖时才变。
     */
    private static final EntityDataAccessor<Integer> JOB_RESULT_ID =
            SynchedEntityData.defineId(LauncherSubsystem.class, EntityDataSerializers.INT);

    /**
     * 最近一次结束是否已交付（网络同步），与 {@link #JOB_RESULT_ID} 是同一条结果的结论。<br>
     * 客户端不据它推断弹药内容——内容以权威的 {@link #BELT_ID}／{@link #CHAMBERED_ID} 为准；
     * 分开记是为了让"作业空了"与"这一份交付成功了"不混为一谈：中止的作业同样会把作业字段清 0。
     */
    private static final EntityDataAccessor<Boolean> JOB_RESULT_DELIVERED_ID =
            SynchedEntityData.defineId(LauncherSubsystem.class, EntityDataSerializers.BOOLEAN);

    /**
     * 本管的当前装填作业：身份、冻结分配与进度都记在这里，供给者不另持一份并行计时。<br>
     * 服务端每 tick 按执行者给的推进量累计；客户端在 {@link #onPostTick()} 用同步的分配字段建一份
     * 自己的作业，同样按执行者的推进量累计，只是不扣库存、不交付。两端都只有一个作业对象，
     * 不另存分配快照。不存档——未完成的装填读档后重新发起，作业不跨存档延续。
     */
    @Nullable
    private ReloadJob reloadJob;

    /**
     * 客户端待落地的同步项：回调只记"哪几项变了"，模拟结束后在 {@link #onPostTick()} 统一应用。<br>
     * 分项记是因为落地的顺序有语义（指认 → 作业 → 弹药内容），而回调的到达顺序不保证。服务端不读写。
     */
    private boolean pendingSupplier;
    private boolean pendingJob;
    private boolean pendingBelt;
    private boolean pendingChambered;

    /**
     * 客户端最近一次本地交付的作业编号；{@code 0} 表示还没有过。<br>
     * 本地交完后作业就清空了，权威结果随后到达时靠它认出"这条结果就是我刚替它交过的那一份"，
     * 从而不在终态上再动一次弹药内容。只保留最近一次，不存作业历史。
     */
    private int lastLocalDeliveryId;

    /** 客户端已经应用过的结束结果编号，用来忽略同一结果的重复到达。 */
    private int lastAppliedResultId;

    /**
     * 暂停超时：作业连续这么多 tick 推不动就按未交付终止并释放资源。<br>
     * 10 秒是"卡住"与"等下一发"的分界——机构受阻、供能不足与执行者缺席都在这个窗口内恢复或
     * 由路由改派；超过它仍推不动的作业既占着机构槽位也拽着架上的预留，比放弃它更伤。
     */
    private static final int PAUSE_TIMEOUT_TICKS = 200;

    /**
     * 下一份作业的编号，本机自增。<br>
     * 与宿主 SubPart 的 int ID、本子系统名组成 {@code (subPartId, name, jobId)}：机构占用与架预留都
     * 以这个三元组为键，因此同一台管先后两次作业不会互相认领，旧作业的回调也摘不掉新作业的占用。
     * 不存档——作业本身不跨存档延续，读档后从 1 重新分配即可。
     */
    private int nextJobId = 1;

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
     * <p>
     * 内容是<b>当前拓扑的发现结果</b>，每次结构变化由
     * {@link #onVehicleStructurePreChanged()} 清空、再由各装弹机重新发现填回，因此不会累积历史残留。
     * 频道键随首次登记产生，某个频道再也没有供给者时会在下一次结构变化后被移除。
     */
    private final Map<String, List<IAmmoSupplier>> supplierChannels = new LinkedHashMap<>();

    // ——— 信号化开火指令 ———

    /**
     * 发射控制指令：多控制器通过此 record 原子写入方向、修正角和开火指令。<br>
     * 火控以信号值写入本发射器的控制输入槽，{@link #getActiveFireCommand} 按 controlInputs 优先级读取。
     * <p>
     * {@code shooter} 是这次开火的归属者，最终成为每发弹丸的 owner（见
     * {@link #fireSingle(BallisticProjectileType)}）。它随"开火权"一起下发：谁点的火与谁允许开火
     * 是同一份记录，不另建一条并行的归属通道。发射器与火控同处一辆载具，因此这里的引用只在
     * 服务端有值——客户端没有乘客实体，也不需要归属。
     */
    public record FireCommand(
        @Nullable Vector3f direction,   // null = 使用 locator 朝向
        float correctionAngleDeg,       // 火控修正角（度），0 = 不修正
        boolean firing,                 // 是否开火
        @Nullable Entity shooter        // 本次开火的归属者（射手 / 操作者），null = 无归属
    ) {
        /** 无归属的发射指令：方向与修正角照常生效，弹丸不带 owner。 */
        public FireCommand(@Nullable Vector3f direction, float correctionAngleDeg, boolean firing) {
            this(direction, correctionAngleDeg, firing, null);
        }
    }

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
        builder.define(BELT_ID, Optional.empty());
        builder.define(CHAMBERED_ID, Optional.empty());
        builder.define(JOB_ID_ID, 0);
        builder.define(JOB_TARGET_ID, SupplierRef.NONE);
        builder.define(JOB_ORIGIN_ID, SupplierRef.NONE);
        builder.define(JOB_SPEC_ID, Optional.empty());
        builder.define(JOB_RESERVED_ID, 0);
        builder.define(JOB_ELAPSED_ID, 0);
        builder.define(JOB_TOTAL_ID, 0);
        builder.define(JOB_RESULT_ID, 0);
        builder.define(JOB_RESULT_DELIVERED_ID, false);
    }

    /**
     * 同步数据落地后的处理。<br>
     * 收到子系统同步包时，基类会逐个访问器回调本方法（服务端自己写值时同样回调一次）。
     * 客户端在这里只记"哪一项变了"：值已经写进 {@link #getSynchedData()}，真正落地到本机状态由
     * {@link #onPostTick()} 在本轮模拟结束后统一完成，避免在模拟中途替换作业与弹药内容。
     *
     * @param dataAccessor 发生变化的访问器
     */
    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> dataAccessor) {
        super.onSyncedDataUpdated(dataAccessor);
        if (!getLevel().isClientSide()) return;
        if (dataAccessor.equals(SUPPLIER_ID)) {
            pendingSupplier = true;
        } else if (dataAccessor.equals(BELT_ID)) {
            pendingBelt = true;
        } else if (dataAccessor.equals(CHAMBERED_ID)) {
            pendingChambered = true;
        } else if (dataAccessor.equals(JOB_ID_ID)
                || dataAccessor.equals(JOB_TARGET_ID)
                || dataAccessor.equals(JOB_ORIGIN_ID)
                || dataAccessor.equals(JOB_SPEC_ID)
                || dataAccessor.equals(JOB_RESERVED_ID)
                || dataAccessor.equals(JOB_ELAPSED_ID)
                || dataAccessor.equals(JOB_RESULT_ID)
                || dataAccessor.equals(JOB_RESULT_DELIVERED_ID)
                || dataAccessor.equals(JOB_TOTAL_ID)) {
            pendingJob = true;
        }
    }

    /**
     * 客户端在本轮模拟结束后统一落地待处理同步值。<br>
     * 顺序是先指认、再作业、最后膛内弹与已装 Belt：指认变化会先按身份结束旧作业，作业字段随后组成
     * 新状态，弹药内容最后覆盖本地副本，因此一次收到的多项变化不会出现"作业已换而膛内还是旧的那发"
     * 中间态。服务端没有待应用值，本方法直接返回。
     * <p><b>调用线程：</b>主线程（客户端 tick 收尾，由 {@code SubsystemController#postTick()} 统一调用）。
     */
    @Override
    public void onPostTick() {
        super.onPostTick();
        if (!getLevel().isClientSide()) return;
        if (pendingSupplier) {
            pendingSupplier = false;
            applySyncedSupplier();
        }
        if (pendingJob) {
            pendingJob = false;
            applySyncedJob();
        }
        if (pendingBelt) {
            pendingBelt = false;
            applySyncedBelt();
        }
        if (pendingChambered) {
            pendingChambered = false;
            applySyncedChamber();
        }
    }

    /**
     * 按同步值重建已装 Belt 的本地副本。<br>
     * 必须是副本：客户端会就地消耗它（取一发进膛、射击扣额度），不能改到同步基线本身。
     */
    private void applySyncedBelt() {
        AmmoBelt synced = getSynchedData().get(BELT_ID).orElse(null);
        loadedBelt = synced == null ? null : synced.copy();
    }

    /** 按同步值重建膛内那一发。 */
    private void applySyncedChamber() {
        ProjectileType stored = AmmoBelt.resolve(getLevel(), getSynchedData().get(CHAMBERED_ID).orElse(null));
        chamberedType = stored instanceof BallisticProjectileType kinetic ? kinetic : null;
    }

    /**
     * 全量补推前把进度基线刷成当前进度。<br>
     * {@link #JOB_ELAPSED_ID} 平时只在作业开始与离散重建时写，直接推给新加入的客户端会让它从 0 开始
     * 模拟一份已经进行到一半的作业；这一刻刷新一次，客户端拿到的基线就是"现在"。
     * <p><b>调用线程：</b>主线程（服务端，{@code SubsystemController#pushAllTo}）。
     */
    @Override
    public void refreshSyncBaseline() {
        if (getLevel().isClientSide()) return;
        ReloadJob job = reloadJob;
        getSynchedData().set(JOB_ELAPSED_ID, job == null ? 0 : job.elapsedTicks());
    }

    /**
     * 客户端按同步的作业字段组成本地作业。<br>
     * 先应用结束结果，再按作业字段落地当前作业：结果只影响身份匹配的那一条本地作业，作业字段才是
     * "现在有哪份作业"。编号为 {@code 0} 表示权威已收尾：本地作业结束，客户端不释放占用或预留
     * ——那些由服务端按身份释放，本端只是镜像。<br>
     * 编号与本地当前作业相同表示同一条作业的离散更新（进度基线、阶段时长）：沿用本地已推进的进度，
     * 只向前对齐基线，不让进度倒退；编号不同则是新作业，从基线重新起算。<br>
     * 规格或时长为空说明这一批字段还没到齐，保持现状等补齐，不建半份作业。
     */
    private void applySyncedJob() {
        applyJobResult();
        int jobId = getSynchedData().get(JOB_ID_ID);
        if (jobId <= 0) {
            reloadJob = null;
            return;
        }
        AmmoBelt spec = getSynchedData().get(JOB_SPEC_ID).orElse(null);
        int total = getSynchedData().get(JOB_TOTAL_ID);
        if (spec == null || total <= 0) return;

        ReloadJobKey key = new ReloadJobKey(getSubPart().getId(), getName(), jobId);
        int baseline = Math.max(0, getSynchedData().get(JOB_ELAPSED_ID));
        ReloadJob current = reloadJob;
        if (current != null && current.key().equals(key)) {
            current.advanceTo(baseline);
            return;
        }
        // 本地已经替这一条交过了：只等权威结束结果，把它建回来会再交一次
        if (jobId == lastLocalDeliveryId) return;
        ReloadJob job = new ReloadJob(key,
                getSynchedData().get(JOB_TARGET_ID),
                getSynchedData().get(JOB_ORIGIN_ID),
                spec,
                getSynchedData().get(JOB_RESERVED_ID),
                total);
        job.advanceTo(baseline);
        reloadJob = job;
    }

    /**
     * 应用一条结束结果（客户端）。<br>
     * 身份匹配才算本机的事：要么就是当前这条本地作业，要么是刚才本地替它交过的那一条（本地先完成、
     * 权威结果后到）。匹配时结束本地作业，并把膛内弹与已装 Belt 按权威值收口——本地预测的内容不得活过
     * 终态，否则一条被撤销的作业会在本机留下一份多出来的弹。不匹配的是更早作业的结果：只当作已经
     * 应用过，既不结束当前作业，也不改它的进度与弹药内容（库存与预留本就按各自的同步值校准）。
     * <p>同一编号只应用一次：结果字段只在下一次结束时变值，重复到达的旧值在这里被忽略。
     */
    private void applyJobResult() {
        int resultId = getSynchedData().get(JOB_RESULT_ID);
        if (resultId <= 0 || resultId == lastAppliedResultId) return;
        lastAppliedResultId = resultId;
        ReloadJob current = reloadJob;
        boolean mine = current != null ? current.jobId() == resultId : resultId == lastLocalDeliveryId;
        if (!mine) return;
        reloadJob = null;
        applySyncedBelt();
        applySyncedChamber();
    }

    // ——— IAmmoConsumer 实现 ———

    /**
     * 单发装填：一次拉取只把一发推进膛室，不建立持久的已装 Belt。<br>
     * 取值来自发射器定义，不由供给者类型或它的配置推导。
     */
    public boolean singleRoundLoading() {
        return attr.staticAttribute.isSingleRoundLoading();
    }

    /**
     * 这一份交付能不能被本管接收。<br>
     * 单发装填要求恰好 1 发；整份装填要求至少 1 发，不设长度上限。两种模式都要求模式里每个弹种
     * 都能解码、且炮闩接受它。校验发生在拉到交付之后——供给者不判断消费者收不收。
     */
    public boolean canAcceptBelt(AmmoBelt belt) {
        if (belt.remaining() <= 0) return false;
        if (singleRoundLoading() && (belt.length() != 1 || belt.remaining() != 1)) return false;
        for (AmmoBelt.PatternEntry entry : belt.pattern()) {
            ProjectileType type = AmmoBelt.resolve(getLevel(), entry.ammo());
            if (!(type instanceof BallisticProjectileType)) return false;
            if (!canAccept(type)) return false;
        }
        return true;
    }

    /** 写入膛内那一发并同步；客户端不写。 */
    private void setChambered(@Nullable BallisticProjectileType round) {
        if (chamberedType == round) return;
        chamberedType = round;
        if (!getLevel().isClientSide()) {
            getSynchedData().set(CHAMBERED_ID, Optional.ofNullable(round == null ? null : round.getRegistryKey()));
        }
    }

    /**
     * 写入已装 Belt 并同步；客户端不写。<br>
     * 旧内容的退回目标是"当前合法且接受它的机器"，不记账：换装提交时按当前供给者与已发现的供给者
     * 依次询问，因此不需要把产出它的那台机器存下来——那份来源关系既不持久化，也不参与路由。
     */
    private void setLoadedBelt(@Nullable AmmoBelt belt) {
        loadedBelt = belt;
        pushBelt();
    }

    /** 把已装 Belt 的当前状态作为快照推给客户端。 */
    private void pushBelt() {
        if (getLevel().isClientSide()) return;
        getSynchedData().set(BELT_ID, Optional.ofNullable(loadedBelt == null ? null : loadedBelt.syncCopy()));
    }

    @Override
    @Nullable
    public IAmmoSupplier getCurrentSupplier() {
        return currentSupplier;
    }

    /**
     * 设置当前供给者，由 WeaponController 的服务端路由调用。<br>
     * 引用发生变化时丢弃本管进行中的作业并把自己从旧供给者的认领集合里摘掉：那份作业是"点名给本管的"，
     * 本管一走它就无人认领，旧装弹机会一直把输弹线占着，新的一份既不会开始也不会给别人；
     * 独占机器还会因此永远无法被别的管选中。旧作业的库存从未扣减（扣减只发生在交付那一刻），
     * 丢弃它不退还任何东西。清理放在这里而不是交给调用方，是为了不存在"换人忘了归还"的路径。
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
     * 把指认落到本机字段上，并按身份收尾旧供给者上属于本管的那条作业。<br>
     * 不含任何同步动作，也不动膛内那发：换指认只影响<b>后续</b>装填，膛内已经压好的那发照打
     * （它属于哪条弹链由玩家的显式退膛操作决定，不在这里替玩家丢）。服务端由
     * {@link #setCurrentSupplier} 追加写入同步值，客户端由 {@link #applySyncedSupplier()}
     * （跟随推送）与 {@link #resolveSyncedSupplier()}（载入播种）调用。
     */
    private void assignSupplier(@Nullable IAmmoSupplier supplier) {
        IAmmoSupplier previous = this.currentSupplier;
        if (previous == supplier) return;
        // 换人等于放弃这一份作业：先按旧身份收尾（此时字段还是旧值），再改指认。
        // 供给者那边的库存本来就没扣，本管重新开工从 0 起算
        finishReloadJob();
        this.currentSupplier = supplier;
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
        // 发现结果里还没出现这台机器：保持现状，等 onTick 的重试
        if (target == null || target == currentSupplier) return;
        assignSupplier(target);
    }

    /**
     * 按 {@link #SUPPLIER_ID} 里的身份在本机已发现的供给者中找出实例并指认。<br>
     * 身份是"宿主 RegularPart uuid + SubPart 名 + 子系统名"，同一台机器在两端、在重启前后都是这个值，
     * 因此不需要跨端传对象引用，也不需要额外的查表。客户端在刚载入时（还没收到任何推送）用它把
     * 存档带回来的指认落地，服务端同样用它把存档里的指认在发现完成后落地。
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
     * @return 对应实例；身份为空或发现结果里还没有这台机器时返回 null
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
     * 结构变化的第一阶段：清空供给者登记表。<br>
     * 登记表是"当前拓扑能发现到的供给者"的缓存，<b>不是</b>历史累积：装弹机只在自己的
     * {@link AbstractSubsystem#onAttach()} 与结构变化时广播发现，已经不再连到本管的机器不会再来声明，
     * 不清空就会一直留着——那些机器可能已经跟着拆下来的部件换到了别的载具上。
     * 那会让两端的集合不一致（服务端逐件组装会留下中间态的残留，客户端只看到终局拓扑），
     * 并让已失效的供给者继续出现在 HUD 的兜底取数里。
     * <p>
     * 清空写在第一阶段而不是 {@link #onVehicleStructureChanged()}：后者是装弹机重新广播发现的阶段，
     * 两者若挤在同一趟遍历里，本管排在装弹机之后就等于把本轮发现结果清掉。
     * <p>
     * <b>调用线程：</b>主线程。
     */
    @Override
    public void onVehicleStructurePreChanged() {
        for (List<IAmmoSupplier> channelSuppliers : supplierChannels.values()) {
            channelSuppliers.clear();
        }
        supplierChannels.values().removeIf(List::isEmpty);
    }

    /**
     * 带频道名的供给者注册。<br>
     * 由装弹机在结构变化的发现阶段调用；频道名必须是 {@code ammo_inputs} 中声明的到达频道。
     * 声明外的登记直接拒绝，不建对应列表。
     * <p>
     * 同一台重复发现不重复登记（一次结构变化里可能有多个装弹机广播同一条发现频道）。
     * 这里不写指认：谁给本管供弹由服务端的路由决定，登记只负责让本管知道"有哪些供给者存在"。
     */
    @Override
    public void addSupplier(IAmmoSupplier supplier, String channelName) {
        List<IAmmoSupplier> channelSuppliers = supplierChannels.computeIfAbsent(channelName, k -> new ArrayList<>());
        if (channelSuppliers.contains(supplier)) return;
        channelSuppliers.add(supplier);
    }

    /**
     * 挂载后与结构变化后重新发现供弹来源。<br>
     * 需求方发起查询：对 {@code ammo_discovery_targets} 的每个频道做一次只读终端查询，命中的
     * {@link IAmmoSupplier}（自动装弹机）登记进本管的供给者表。弹药架不是供给者——直接向它取弹属于
     * 人力直取，只有本型号定义了人工段时才作为候选，因此这里不会把它当成交付来源。
     * <p><b>调用线程：</b>主线程（挂载与结构变化）。
     */
    @Override
    public void onAttach() {
        super.onAttach();
        discoverSuppliers();
    }

    @Override
    public void onVehicleStructureChanged() {
        super.onVehicleStructureChanged();
        discoverSuppliers();
    }

    /** 按 {@code ammo_discovery_targets} 做只读发现并登记供给者；结果已在结构变化的第一阶段清空。 */
    private void discoverSuppliers() {
        for (String channel : attr.ammoDiscoveryTargets.keySet()) {
            for (Map.Entry<String, List<ISignalReceiver>> arrival : resolveTargets(channel).entrySet()) {
                for (ISignalReceiver terminal : arrival.getValue()) {
                    if (terminal == this) continue;
                    if (!(terminal instanceof IAmmoSupplier supplier)) continue;
                    if (terminal instanceof AbstractSubsystem sub
                            && sub.getOwner().getSubPart().getPart().assembly
                            != getOwner().getSubPart().getPart().assembly) {
                        continue;
                    }
                    addSupplier(supplier, arrival.getKey());
                }
            }
        }
    }

    @Override
    public boolean canAccept(ProjectileType type) {
        return attr.staticAttribute.getBreech().isAmmoCompatible(type);
    }

    /**
     * 直接把一发弹药压进膛室，绕过取弹链。<br>
     * 人工装填、调试工具与测试用它表达"膛内已有一发实弹"；正常供弹走 {@link #tickReload()}。
     *
     * @return true 表示装填成功
     */
    public boolean loadRound(ProjectileType type) {
        if (!(type instanceof BallisticProjectileType kinetic)) return false;
        if (chamberedType != null || !isActive()) return false;
        if (!canAccept(type)) return false;
        setChambered(kinetic);
        return true;
    }

    /**
     * 退膛：返回膛内弹药并清空。<br>
     * 退出的一发包装成单发交付还给当前供给者；收不收由供给者自己判，被拒时弹药从膛内消失且未入库
     * ——掉落物品的落地位置与物品映射尚未实现。
     *
     * @return 退出的弹药类型，空膛返回 null
     */
    @Nullable
    public ProjectileType ejectRound() {
        BallisticProjectileType round = chamberedType;
        if (round == null) return null;
        setChambered(null);

        // 单发交付：退膛只涉及膛内这一发，与已装 Belt 无关
        IAmmoSupplier supplier = getCurrentSupplier();
        if (supplier != null && round.getRegistryKey() != null) {
            supplier.returnBelt(new AmmoBelt(null, "", List.of(new AmmoBelt.PatternEntry(round.getRegistryKey(), 1)), 1, 1, null));
        }
        // TODO: 供给者拒收时，生成 ItemEntity 掉落于发射器 locator 位置
        return round;
    }

    // ==================== 殉爆 ====================

    /**
     * 摧毁边沿：本管持有的膛内弹与已装 Belt 随炮闩一起殉爆。
     * <p>
     * 触发点是框架的子系统摧毁边沿（{@link AbstractSubsystem#onTick()}）。炮闩是发射器唯一的关键模块
     * （{@link BreechModuleAttr}），因此"摧毁"总意味着炮闩归零：两者按
     * {@link CookoffComposition#addStoredRound} 计入各自的发射药与各战斗部贡献，合成一次普通爆炸。
     * 这与装弹机库存殉爆是同一条口径——残骸上的炮闩照样起爆，宿主零件整体移除后子系统不再 tick，
     * 自然不起爆。
     * <p>
     * 客户端也会收到 {@code DATA_DESTROYED_ID} 的同步并回调本方法，但起爆只在服务端发生：客户端首行
     * 即返回，只保留基类的音效等表现。
     * <p>
     * <b>调用线程：</b>主线程。
     */
    @Override
    public void onDestroyed() {
        super.onDestroyed();
        if (getLevel().isClientSide()) return;
        // 摧毁即收尾：按身份释放机构占用并丢弃进行中的作业，客户端随之清空进度显示（库存未扣，不退还任何东西）
        finishReloadJob();
        detonateStoredAmmo();
    }

    /**
     * 本管库存殉爆：按膛内那一发与已装 Belt 的实际贡献合成一次普通爆炸。
     * <p>
     * 合成范围是本管持有的全部弹药——膛内至多一发，已装 Belt 按余量逐弹种计入；两者都已经从供给者的
     * 弹仓扣掉（{@link IAmmoSupplier#consumeReadyBelt}），所以炮闩殉爆与装弹机库存殉爆不会把同一份
     * 算两次。
     * <p>
     * {@code P、D、J} 全为零时没有可生成爆炸的贡献，不消耗这些弹药、不起爆；发射器已毁，弹药也
     * 打不出去，修好炮闩后仍在原处。
     * <p>
     * <b>调用线程：</b>主线程（子系统摧毁边沿）。
     */
    private void detonateStoredAmmo() {
        CookoffComposition composition = new CookoffComposition();
        composition.addStoredRound(chamberedType, 1);
        AmmoBelt belt = loadedBelt;
        if (belt != null) {
            for (Map.Entry<ResourceLocation, Integer> entry : belt.countRemaining().entrySet()) {
                composition.addStoredRound(AmmoBelt.resolve(getLevel(), entry.getKey()), entry.getValue());
            }
        }
        if (composition.isEmpty()) return;

        // 起爆位置：炮闩模块最近一次有效受击的 HitBox 局部形心；无来源回退宿主质心
        Vector3f origin = getLastDamageOrigin();
        // 爆点落在自身命中箱内部，该命中箱必须豁免，否则近距离满值穿深会吃掉整发爆炸
        Set<PenetrationKey> ignoredKeys = getCookoffIgnoredKeys();
        // 归属：打坏炮闩的那一击的 causing entity，殉爆继续算在同一个射手头上
        Entity attacker = getLastDamageAttacker();
        // 先消耗本管的弹药再交给普通爆炸服务：本次起爆后本管不再持有这些内容
        setChambered(null);
        setLoadedBelt(null);
        ExplosionManager.get(getLevel()).detonate(
                origin, composition.toExplosionParams(), getLevel().random.nextLong(), ignoredKeys, attacker);
    }

    /**
     * 起爆定位、豁免与归属只看炮闩模块自己的最近有效受击。
     * <p>
     * 膛内那发躺在炮闩里，而炮闩正是发射器唯一的关键模块，因此"打到本子系统"与"打到这发弹药"
     * 在本管上是同一件事；按模块读与装弹机按 {@code magazine} 模块读是同一套口径。模块记录只在
     * 炮闩自己的耐久下降时更新，未标注模块的命中按比例分摊时同样会记到炮闩上。
     * <p>
     * 定位（{@link #getLastHitBox()}）、豁免（{@link #getCookoffIgnoredKeys()}）与归属
     * （{@link #getLastDamageAttacker()}）都由基类经本方法取事件，因此殉爆记的是"打坏炮闩的那一击"。
     *
     * @return 炮闩的最近有效受击；没受过有效击、或已从摧毁中恢复时为 {@code null}，
     *         此时起爆点回退宿主质心、不豁免任何命中箱、且本次殉爆无归属
     */
    @Override
    @Nullable
    protected SubPartDamageEvent.Pre getCookoffDamageEvent() {
        SubModule breech = getModule("breech");
        return breech == null ? null : breech.getLastHitEvent();
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
     *   <li>弹药消费循环（最多 N 发）：取弹 → 兼容校验 → 膛内 → 入队 {@link #pendingFires}
     *       → 提交地形首段预约（服务端） → 装填下一发</li>
     *   <li>客户端音效处理：遍历 pendingFires，首发射击用单发音效，后续连射启动/维持循环音效</li>
     *   <li>扣除已发射数对应的时间：{@code fireAccumulator -= N × intervalSec}</li>
     * </ol>
     */
    @Override
    public void onTick() {
        super.onTick();
        // 指认的种子：本机字段还空着时（刚载入、发现尚未完成）按同步值里的身份认一次。
        // 少了它，载入后头几个 tick 本管处于"无供给者"，膛内那发会在路由里被当作无主而退掉。
        if (currentSupplier == null) resolveSyncedSupplier();

        // 两端都推进本管的弹药内容：首次装填、整份换装、从已装 Belt 补膛与射击扣弹各跑一份，客户端那一份
        // 是本地镜像。库存、预留、占用与投射物归服务端；回充也只由服务端推进——它的余量与进度都在已装
        // 同步值里，客户端跟随即可（见 tickBeltRecharge 的说明）。
        if (getLevel().isClientSide()) {
            tickClientReload();
        } else if (isActive() && !isDestroyed()) {
            tickBeltRecharge();
            tickReload();
        }
        if (isActive() && !isDestroyed() && !isSwapping()) refillChamberFromBelt();

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

        // 换装期间锁定射击：膛内弹与旧 Belt 都留在原位，等供给者备好那一份再整份替换
        if (isSwapping()) {
            if (wasFiring) handleCeaseFire();
            resetFireState();
            return;
        }

        if (getActiveFireCommand() == null || !hasRoundToFire()) {
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
            BallisticProjectileType round = takeRoundForShot();
            if (round == null) break; // 没有可发射的内容，等待下一 tick

            // ③ 首段地形预约：必须先于入队。入队后物理线程在过载时可能在同一个游戏 tick 内就
            //     poll 到这一发（它的物理步与我们交错），预约再登记就落在开火之后了。
            //     本行在主线程，登记经即时队列由本 tick 的 requestStep 消费。
            if (!getLevel().isClientSide()) {
                seedTrajectoryTerrain(round);
            }
            // ③.① 入队 pendingFires（物理线程将消费）
            pendingFires.add(round);
            firedTypes.add(round);
            firedCount++;

            // ④ 打出一发后立刻从已装 Belt 补膛：连射循环不必等下一 tick 才补弹。
            //    两端都补——客户端不补就会拿着同一发一直打，射击表现与剩余发数脱节
            refillChamberFromBelt();
        }

        // ⑤ 客户端音效特效处理（主线程，使用本地快照避免与物理线程并发访问 pendingFires）
        if (getLevel().isClientSide() && firedCount > 0) {
            handleFiringSounds(firedTypes, burstJustStarted);
            for (ProjectileType type : firedTypes) {
                type.playFireEffect(getLevel(), this, attr.locator);
            }
            playFireAnimation();
            playMuzzleFlash(firedTypes.get(0));
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
     * 本 tick 是否还需要向供给者拉一份。<br>
     * 单发装填看膛室，整份装填看是否已有已装数据；可回充 Belt 打空后仍算"有已装数据"，等它自己补额度。
     */
    private boolean needsBelt() {
        return singleRoundLoading() ? chamberedType == null : loadedBelt == null;
    }

    /**
     * 此刻是否有可发射的一发：膛内有弹，或已装可回充 Belt 里还有额度。<br>
     * 普通 Belt 的弹先入膛，因此它的可发射性由 {@link #chamberedType} 表达。
     * <p>
     * 不判断换装：换装期间 {@link #isSwapping()} 为真，射击会被锁住，即使本方法返回 true 也打不出去。
     * HUD 判断"这根管能不能打"需要同时看这两个条件。
     */
    public boolean hasRoundToFire() {
        if (chamberedType != null) return true;
        AmmoBelt belt = loadedBelt;
        return belt != null && belt.hasRecharge() && belt.remaining() > 0;
    }

    /**
     * 本管当前的装填作业；{@code null} 表示没有进行中的作业。<br>
     * 供给者（放行、交付、占用校验）与显示层都从这里读同一份作业，不另存一份分配记录。
     */
    @Override
    @Nullable
    public ReloadJob getReloadJob() {
        return reloadJob;
    }

    /**
     * 最近一次结束的作业编号（同步值）；{@code 0} 表示本端还没有结束过作业。<br>
     * 与 {@link #lastEndWasDelivered()} 成对，是同一条结果的身份与结论：取消、来源失效、暂停超时与
     * 成功交付都写这一对，客户端据此结束身份匹配的本地作业，核对与显示据此区分"装填完成"与"中止"。
     */
    public int lastEndedJobId() {
        return getSynchedData().get(JOB_RESULT_ID);
    }

    /** 最近一次结束是否已交付，描述的就是 {@link #lastEndedJobId()} 那一条。 */
    public boolean lastEndWasDelivered() {
        return getSynchedData().get(JOB_RESULT_DELIVERED_ID);
    }

    /**
     * 这份作业还要多久交付，是控制器比较「保持当前供给者还是换人」的唯一输入。<br>
     * 分档：本管正在为它推进的那一份 = 作业剩余 tick；供给者可交付且有余弹但还没开工 = 整段
     * {@link IAmmoSupplier#getReloadTimeTicks()}；无余弹或供给者不可交付 = {@link Integer#MAX_VALUE}
     * （现货优先：空匣的机器即使下一 tick 就会回充出一发也不值得留）。
     *
     * @param supplier 候选或当前供给者，可为 null（一律该换）
     * @return 还要多少 tick；{@link Integer#MAX_VALUE} 表示此刻给不出
     */
    public int ticksToNextRound(@Nullable IAmmoSupplier supplier) {
        if (supplier == null || !supplier.isSupplyOperational()) return Integer.MAX_VALUE;
        ReloadJob job = reloadJob;
        if (job != null && job.deliveryTarget().equals(SupplierRef.of(supplier))) {
            return job.remainingTicks();
        }
        if (!supplier.hasAmmo()) return Integer.MAX_VALUE;
        return Math.max(0, supplier.getReloadTimeTicks());
    }

    /**
     * 受理一份新作业：写入当前作业并把它的身份、分配与进度推给客户端（仅服务端写同步值）。<br>
     * 受理前必须已经取得供给者授予的占用与预留（{@link IAmmoSupplier#claimFor}），因此这里不重复判断。
     * 客户端按同步字段建自己的作业，走 {@link #applySyncedJob()}，不调用本方法。
     */
    private void beginReloadJob(ReloadJob job) {
        reloadJob = job;
        pushReloadJob();
    }

    /**
     * 统一收尾：先按作业身份释放供给者那边的占用与实际架预留，再发布结束结果并清空作业。<br>
     * 成功交付、取消、换人、摧毁、路径失效与暂停超时都走这一口，因此不存在"只清进度不释放承诺"的分支；
     * 释放只按身份删除记录，不退还库存（库存扣减只发生在交付那一刻）。作业为空时是无操作，
     * 重复收尾因此安全。<br>
     * 结束结果带原 {@code jobId} 与已交付／未交付结论，客户端据它结束身份匹配的本地作业；结论分开记是
     * 因为"作业字段被清 0"不足以说明这一份到底交成了没有。
     * <p><b>调用线程：</b>主线程（客户端经 {@link #assignSupplier} 也会走到这里，那一侧不发布结果——
     * 结果只由服务端写）。
     */
    private void finishReloadJob(boolean delivered) {
        ReloadJob job = reloadJob;
        if (job == null) return;
        IAmmoSupplier supplier = currentSupplier;
        if (supplier != null) supplier.releaseClaim(this, job.key());
        reloadJob = null;
        if (!getLevel().isClientSide()) {
            getSynchedData().set(JOB_RESULT_ID, job.jobId());
            getSynchedData().set(JOB_RESULT_DELIVERED_ID, delivered);
        }
        pushReloadJob();
    }

    /** 按未交付收尾：取消、换人、摧毁、来源失效、暂停超时都归这一种。 */
    private void finishReloadJob() {
        finishReloadJob(false);
    }

    /**
     * 把当前作业的分配字段推给客户端。<br>
     * 只在作业开始与收尾时调用：稳定装填期间服务端不写进度字段，两端在基线之上各自累计
     * （{@link #JOB_ELAPSED_ID} 是基线，不是每 tick 的镜像）。离散重建（暂停恢复、换人、作业重建）
     * 另行写入当时的进度作为新基线。<br>
     * 结束结果（{@link #JOB_RESULT_ID}／{@link #JOB_RESULT_DELIVERED_ID}）不在这里写：它只描述
     * 已经结束的那一条作业，新作业开始时不覆盖它。
     */
    private void pushReloadJob() {
        if (getLevel().isClientSide()) return;
        ReloadJob job = reloadJob;
        getSynchedData().set(JOB_ID_ID, job == null ? 0 : job.jobId());
        getSynchedData().set(JOB_TARGET_ID, job == null ? SupplierRef.NONE : job.deliveryTarget());
        getSynchedData().set(JOB_ORIGIN_ID, job == null ? SupplierRef.NONE : job.upstreamOrigin());
        getSynchedData().set(JOB_SPEC_ID, job == null ? Optional.empty() : Optional.of(job.spec().syncCopy()));
        getSynchedData().set(JOB_RESERVED_ID, job == null ? 0 : job.reservedCount());
        getSynchedData().set(JOB_ELAPSED_ID, job == null ? 0 : job.elapsedTicks());
        getSynchedData().set(JOB_TOTAL_ID, job == null ? 0 : job.totalTicks());
    }

    /**
     * 推进本管的装填作业。<br>
     * 首次装填与整份换装走同一条作业：目标都是当前供给者的一份交付。开工前先向供给者申请受理——
     * 它在同一次调用里取得机构槽位与该次作业的弹药预留；接不了单（不可工作、没有可分配库存、独占槽位
     * 被别的作业占着）就不建作业、不开始计时，下个 tick 再试，因此等待中的管既不占槽也不占预留。
     * 受理后把机械段基准时长与冻结的分配写进作业，此后每 tick 向供给者索取可推进量；走完后兑现预留、
     * 取走那一份，交给装填或换装的分支。
     * <p>
     * 作业的交付目标与来源不会在作业存续期间漂移：指认只经 {@link #assignSupplier} 变更，而它在换人前
     * 就收尾了旧作业；来源只由受理时授予，换架要重建作业。
     * <p>
     * <b>暂停与超时：</b>受理成立后本 tick 的推进量可能为 0（机构受阻、供能不足、将来的执行者缺席），
     * 承诺仍在，因此不立刻收尾而是等它恢复；连续推不动 {@link #PAUSE_TIMEOUT_TICKS} 个 tick 才按未交付
     * 终止并释放全部资源，与显式取消共用同一条结束结果。这个上限只作用于"有作业、有承诺"的暂停；
     * 还没受理的等待没有作业，也就不计时。
     * <p><b>调用线程：</b>主线程（仅服务端：库存与交付都在这一侧执行）。
     */
    private void tickReload() {
        IAmmoSupplier supplier = getCurrentSupplier();
        if (supplier != null && !isStillDiscovered(supplier)) {
            // 结构变化后旧路径已不可达（拆件、跨载具转移）：先按身份收尾再丢指认，
            // 不能带着已经不在本装配体内的占用继续推进
            setCurrentSupplier(null);
            return;
        }
        boolean swapping = supplier != null && pendingSwapTarget() != null;
        boolean want = supplier != null
                && supplier.isSupplyOperational()
                && (swapping || needsBelt());
        if (!want) {
            finishReloadJob();
            return;
        }

        ReloadJob job = reloadJob;
        if (job == null) {
            // 编号只在受理成功后推进：等待期间反复尝试不消耗编号
            ReloadJobKey key = new ReloadJobKey(getSubPart().getId(), getName(), nextJobId);
            IAmmoSupplier.Grant grant = supplier.claimFor(this, key);
            if (grant == null) return; // 未受理：不入占用、不占预留、不开始计时
            nextJobId++;
            job = new ReloadJob(key, SupplierRef.of(supplier), grant.origin(), grant.spec(),
                    grant.reservedCount(), supplier.getReloadTimeTicks());
            beginReloadJob(job);
        }
        if (!supplier.holdsCommitment(this)) {
            // 占用或预留已不存在（来源失效、份额被撤销）：按失效收尾，由路由重选
            finishReloadJob();
            return;
        }
        job.advance(supplier.getAdvanceTicks(this));
        if (job.pausedTicks() >= PAUSE_TIMEOUT_TICKS) {
            finishReloadJob();
            return;
        }
        if (!job.isComplete()) return;

        AmmoBelt delivered = supplier.consumeReadyBelt(this);
        finishReloadJob(delivered != null);
        if (delivered == null) return; // 兑现不出这一份：下个 tick 重开作业
        if (swapping) {
            commitBeltSwap(supplier, delivered);
        } else {
            acceptDelivery(supplier, delivered);
        }
    }

    /**
     * 指认的供给者是否仍在当前发现结果里。<br>
     * 结构变化会整体重建供给者表：拆下来的机器跟着部件去了别的装配体，它不会再来登记，占着它的作业
     * 因此必须收尾——继续推进等于让弹药跨装配体流动。判定读表本身而不是空间距离，因为"可达"由接线
     * 与频道决定，不由距离决定。
     *
     * @param supplier 当前指认的供给者
     * @return true 表示它仍在本管这一轮发现结果里
     */
    private boolean isStillDiscovered(IAmmoSupplier supplier) {
        for (List<IAmmoSupplier> channelSuppliers : supplierChannels.values()) {
            if (channelSuppliers.contains(supplier)) return true;
        }
        return false;
    }

    /**
     * 接受一份交付：按本管的装填模式写入膛室或已装数据。<br>
     * 不通过整份校验的交付原样退回供给者，等下一次作业重来。
     */
    private void acceptDelivery(IAmmoSupplier supplier, AmmoBelt pulled) {
        if (!canAcceptBelt(pulled)) {
            MachineMax.LOGGER.warn("Launcher {} 收到不兼容的交付 {}，整份退回并等待路由", name, pulled);
            supplier.returnBelt(pulled);
            return;
        }
        if (singleRoundLoading()) {
            ProjectileType round = AmmoBelt.resolve(getLevel(), pulled.nextRoundId());
            if (round instanceof BallisticProjectileType kinetic) setChambered(kinetic);
            return;
        }
        setLoadedBelt(pulled);
    }

    /**
     * 客户端推进本管的装填作业，并在走完时按本管的装填模式本地提交这一份交付。<br>
     * 计时在两端各自累计：服务端只给分配、基线与运行条件，客户端每个 tick 按执行者给出的推进量前进，
     * 不等待逐 tick 的进度包。走完后用作业上冻结的规格与份额生成同一份交付，交给首次装填或整份换装
     * ——这是"本地先交付"那一半：交出什么不由本端推导，规格与份额都是受理时定死的。<br>
     * 本端不建立真实预留、不扣实际库存（那些只在服务端），因此这份交付内容会在权威结果到达时被覆盖：
     * 交付成功时覆盖成同一份，作业被撤销时覆盖成撤销后的样子。同一 {@code jobId} 只交一次。
     * <p><b>调用线程：</b>主线程（客户端）。
     */
    private void tickClientReload() {
        ReloadJob job = reloadJob;
        if (job == null) {
            tickReloadSounds();
            return;
        }
        if (!isActive() || isDestroyed()) {
            // 本管已被摧毁：本地不做交付，等权威结果把作业清掉
            tickReloadSounds();
            return;
        }
        IAmmoSupplier supplier = deliverySupplier(job);
        if (supplier == null) {
            // 指认与作业目标还没对齐（同步先后顺序）：先不推进，也不按别的机器放行
            tickReloadSounds();
            return;
        }
        job.advance(supplier.getAdvanceTicks(this));
        tickReloadSounds();
        if (!job.isComplete()) return;

        boolean swapping = isSwapping();
        AmmoBelt delivered = job.spec().freshCopy(job.reservedCount());
        reloadJob = null;
        lastLocalDeliveryId = job.jobId();
        if (swapping) {
            commitBeltSwap(supplier, delivered);
        } else {
            acceptDelivery(supplier, delivered);
        }
    }

    /**
     * 本管当前作业的交付目标实例：只有指认与作业上的目标一致时才返回它。<br>
     * 两者在同步先后顺序上可能短暂不一致，那时不能用别的机器回答"本 tick 能推进多少"——放行条件与
     * 交付都按这条作业自己的目标算。
     */
    @Nullable
    private IAmmoSupplier deliverySupplier(ReloadJob job) {
        IAmmoSupplier supplier = getCurrentSupplier();
        if (supplier == null || !job.deliveryTarget().equals(SupplierRef.of(supplier))) return null;
        return supplier;
    }

    /**
     * 客户端把当前作业进度交给供给者，驱动它负责的那一段音效（机械段的声音定位在装弹机上）。<br>
     * 已播阈值由供给者自己记，本管只提供进度。
     */
    private void tickReloadSounds() {
        ReloadJob job = reloadJob;
        if (job == null) return;
        IAmmoSupplier supplier = deliverySupplier(job);
        // 指认与作业目标还没对齐（同步先后顺序）时不播：声音定位在实际交付的那台机器上
        if (supplier == null) return;
        supplier.tickStageSounds(this, job.progress());
    }

    /**
     * 从已装 Belt 取一发进膛。<br>
     * 普通 Belt 每发都经过膛室：先写 {@link #chamberedType}，射击时消费膛内那一发。
     * 可回充 Belt 不走这一步——它的额度在成功射击时直接消费，避免预取后回充多出一发。
     * 余量耗尽的普通 Belt 直接清除已装数据，下一 tick 由 {@link #tickReload()} 拉下一份。
     */
    private void refillChamberFromBelt() {
        AmmoBelt belt = loadedBelt;
        if (belt == null) return;
        if (belt.remaining() <= 0) {
            if (!belt.hasRecharge()) setLoadedBelt(null);
            return;
        }
        if (belt.hasRecharge() || chamberedType != null) return;

        ProjectileType next = AmmoBelt.resolve(getLevel(), belt.nextRoundId());
        if (!(next instanceof BallisticProjectileType kinetic) || !canAccept(next)) return;
        belt.consumeNext();
        pushBelt();
        setChambered(kinetic);
    }

    /**
     * 取一发用于本次射击。<br>
     * 膛内有弹就先打膛内那发；可回充 Belt 的射击在这里消费额度并刷新自身回充延迟。
     * 普通 Belt 的弹已经在膛内，走到这里说明没有可发射内容。<br>
     * 两端都就地消费自己的副本：服务端扣的是真实库存，客户端扣的是本地镜像，差额由随后的权威值
     * 覆盖。只在服务端消费会让客户端的连射循环一直拿着同一发，射击表现与真实剩余发数脱节。
     *
     * @return 本次发射的弹种；没有可发射内容时返回 null
     */
    @Nullable
    private BallisticProjectileType takeRoundForShot() {
        if (chamberedType != null) {
            BallisticProjectileType round = chamberedType;
            setChambered(null);
            return round;
        }
        AmmoBelt belt = loadedBelt;
        if (belt == null || belt.remaining() <= 0) return null;
        ProjectileType next = AmmoBelt.resolve(getLevel(), belt.nextRoundId());
        if (!(next instanceof BallisticProjectileType kinetic)) return null;
        if (!belt.hasRecharge()) return null;
        belt.consumeNext();
        belt.onShot();
        pushBelt();
        return kinetic;
    }

    // ==================== 整份换装 ====================

    /**
     * 换装是否进行中：当前供给者要交付的规格与本机已装内容不是同一份。<br>
     * 换装没有自己的时长与进度——它与首次装填共用同一条 {@link ReloadJob}，按供给者的机械段计时，
     * 本管只在它备好时取走那一份、把旧的一份交给当前合法且接受它的机器。进行中时射击锁定，
     * 已装内容与膛内弹都留在原位。
     */
    public boolean isSwapping() {
        return pendingSwapTarget() != null;
    }

    /**
     * 在途换装的交付来源。<br>
     * 返回 null 表示没有在途换装：没有供给者指认、没有已装数据（单发装填的管始终如此），
     * 或者供给者要给的规格与已装内容已经一致。
     */
    @Nullable
    private IAmmoSupplier pendingSwapTarget() {
        IAmmoSupplier supplier = getCurrentSupplier();
        AmmoBelt loaded = loadedBelt;
        if (supplier == null || loaded == null) return null;
        AmmoBelt spec = supplier.getDeliverySpec();
        if (spec == null || AmmoBelt.compareSpec(loaded, spec) == 0) return null;
        return supplier;
    }

    /**
     * 在主线程验证并提交整份交换。<br>
     * 新的一份不通过校验时原样退回、整笔放弃。旧的一份交给当前合法且接受它的机器——先问本次交付的
     * 来源，再问当前指认，最后按发现顺序尝试其余供给者；都拒收时丢弃并记一条警告，不做"原路退回"
     * 的强制要求（来源关系不持久化，重新拾取到哪台由机器自己的收弹规则决定）。
     *
     * @param target   本次换装的交付来源，由 {@link #pendingSwapTarget()} 给出
     * @param incoming 已从该来源取走的一份
     */
    private void commitBeltSwap(IAmmoSupplier target, AmmoBelt incoming) {
        if (!canAcceptBelt(incoming)) {
            target.returnBelt(incoming);
            abortBeltSwap("换入的一份不通过校验");
            return;
        }
        AmmoBelt previous = loadedBelt;
        if (previous != null && !returnBeltToAnySupplier(previous, target)) {
            MachineMax.LOGGER.warn("Launcher {} 换装时旧的一份没有任何机器接收，按规则丢弃 {}", name, previous);
        }
        setLoadedBelt(incoming);
        refillChamberFromBelt();
    }

    /**
     * 把一份旧内容交给当前合法且接受它的机器。<br>
     * 收不收由机器自己判定（弹种、可储存状态与剩余容量），本管只按顺序询问。
     *
     * @param belt      要交还的一份
     * @param preferred 优先询问的机器，可为 null
     * @return true 表示已由某台机器收下
     */
    private boolean returnBeltToAnySupplier(AmmoBelt belt, @Nullable IAmmoSupplier preferred) {
        if (preferred != null && preferred.returnBelt(belt)) return true;
        IAmmoSupplier current = getCurrentSupplier();
        if (current != null && current != preferred && current.returnBelt(belt)) return true;
        for (IAmmoSupplier supplier : getSuppliers()) {
            if (supplier == preferred || supplier == current) continue;
            if (supplier.returnBelt(belt)) return true;
        }
        return false;
    }

    private void abortBeltSwap(String reason) {
        MachineMax.LOGGER.warn("Launcher {} 换装中止：{}", name, reason);
        // 作业已随交付取走而清空；下一 tick 重开一份，目标仍是当前供给者
    }

    /**
     * 推进已装 Belt 的自身回充。<br>
     * 只有服务端推进：回充要按发取电，而取电是真实扣费。客户端不做这件事也不会落后——余量与零碎的
     * 回充进度都在已装同步值里（进度按 1/20 取整后才推送），补出一发的那一刻服务端就推了新余量。
     * 成功射击已经通过 {@link AmmoBelt#onShot()} 重置过延迟，这里只按速率补额度；
     * 取电由本机所在的能源网格提供，取不到时停在当前进度等待下一 tick。
     */
    private void tickBeltRecharge() {
        AmmoBelt belt = loadedBelt;
        if (belt == null || !belt.hasRecharge()) return;
        int before = belt.remaining();
        float beforeProgress = belt.rechargeProgress();
        belt.tickRecharge(this::payRechargeEnergy);
        if (belt.remaining() != before
                || Math.round(belt.rechargeProgress() * 20f) != Math.round(beforeProgress * 20f)) {
            pushBelt();
        }
    }

    private boolean payRechargeEnergy(float amount) {
        EnergyGrid grid = getOwner().getEnergyGrid();
        if (grid == null) return false;
        return grid.consumeEnergy(amount) >= amount - 0.001f;
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
     *   <li>经 {@link #computeMuzzleState} 读刚体实时 {@link #getMuzzleWorldTransform()}，
     *       取枪口位姿、发射方向与出膛速率（与物理体同步）</li>
     *   <li>散布 + 最终初速计算</li>
     *   <li>继承发射平台速度</li>
     *   <li>调用 {@link ProjectileType#create} 创建投射物（内部进入 SoA 写入）</li>
     *   <li>把持有开火权那一方的 {@code shooter} 记进每发弹丸，作为伤害与殉爆的归属来源</li>
     *   <li>后坐力 {@code applyImpulse} 直接作用到发射平台刚体</li>
     * </ol>
     */
    private void fireSingle(BallisticProjectileType type) {
        MuzzleState muzzle = computeMuzzleState(type);

        // ① 客户端：不创建投射物，音效已在 onTick() 中处理
        if (getLevel().isClientSide()) {
            return;
        }

        // ② 服务端：开火——ProjectileType 内部处理 bullet_num、散布、速度
        //    归属取自此刻持有开火权的指令：允许本发存在的那个来源就是它的主人，指令已释放则本发无主
        FireCommand command = getActiveFireCommand();
        Entity shooter = command == null ? null : command.shooter();
        List<BallisticProjectile> projectiles = type.fire(
            getLevel(), muzzle.position(), muzzle.direction(),
            muzzle.velocityMultiplier(),
            attr.staticAttribute.getBarrel().velocityBonus(),
            attr.staticAttribute.getBarrel().horizontalAccuracyMultiplier() * muzzle.spreadMultiplier(),
            attr.staticAttribute.getBarrel().verticalAccuracyMultiplier() * muzzle.spreadMultiplier(),
            getSubPart().getLinearVelocity(),
            shooter
        );

        // ③ 制导弹登记：记住 objId 以便后续每物理步推送目标，并装订初始目标
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

        // ④ 自身排除：把本发射器零件的命中框预先登记为"已穿透"，使新弹从出膛起忽略本零件
        registerSelfExclusion(projectiles);

        // ⑤ 后坐力——总弹丸动量 × 后坐力倍率（默认 1.5≈弹丸+火药燃气贡献）
        //    初速取 muzzle.speed()，即实际出膛速率（含炮管耐久衰减），不取弹种基础初速
        float finalSpeed = muzzle.speed();
        float totalMass = type.getMass() * projectiles.size();
        float recoilImpulse = totalMass * finalSpeed * attr.staticAttribute.getBarrel().recoilMultiplier();
        if (recoilImpulse > 1e-6f) {
            Vector3f impulseWorld = muzzle.direction().mult(-recoilImpulse);
            var body = getSubPart().getBody();
            Vector3f bodyWorldPos = body.getPhysicsLocation(new Vector3f());
            body.applyImpulse(impulseWorld, muzzle.position().subtract(bodyWorldPos));
        }
    }

    /**
     * 枪口位姿与出膛状态：位置、方向、出膛速率、炮管两项乘子。
     * <p>
     * 开火（物理线程）与首段预约（主线程）共用这一份读法，两边因此读到同一套
     * “刚体实时变换 + 火控修正 + 炮管状态”，不会各自复制一份口径。
     *
     * @param position           枪口世界坐标（JME），是刚体变换里的实时值
     * @param direction          出膛方向（JME 单位向量，未含散布）
     * @param speed              出膛速率（m/s，含炮管耐久衰减、未含散布与平台速度），
     *                           后坐力按它算而不按弹种基础初速算——炮管受损时弹丸确实更慢
     * @param velocityMultiplier 炮管对初速的乘子（含耐久衰减）
     * @param spreadMultiplier   炮管对散布的乘子（含耐久衰减）
     */
    private record MuzzleState(Vector3f position, Vector3f direction,
                               float speed, float velocityMultiplier, float spreadMultiplier) {
    }

    /**
     * 读一次枪口状态。
     * <p>
     * 方向的优先级与既有开火一致：活跃 {@link FireCommand} 的期望方向 + 修正角生效时用
     * {@link #clampToCone} 修正后的方向，否则用 locator 朝向。
     * <p>
     * <b>调用线程：</b>物理线程（{@link #fireSingle}）与主线程（{@link #onTick} 的首段预约）。
     * 读的是刚体实时变换，与 HUD 的 {@link #getMuzzleWorldPosition()} 同一读法。
     *
     * @param type 即将出膛的弹种（提供基础初速）
     * @return 枪口状态
     */
    private MuzzleState computeMuzzleState(BallisticProjectileType type) {
        Transform muzzleTransform = getMuzzleWorldTransform();
        Vector3f jmePos = muzzleTransform.getTranslation();
        Quaternion jmeRot = muzzleTransform.getRotation();

        Vector3f locatorForward = MyQuaternion.rotate(jmeRot, new Vector3f(0, 0, -1), null).normalize();
        Vector3f direction;
        FireCommand cmd = getActiveFireCommand();
        if (cmd != null && cmd.direction() != null && cmd.correctionAngleDeg() > 0.001f) {
            direction = clampToCone(locatorForward, cmd.direction(), cmd.correctionAngleDeg());
        } else {
            direction = locatorForward;
        }

        float spreadMult = 1.0f;
        float velMult = attr.staticAttribute.getBarrel().velocityMultiplier();
        SubModule barrelModule = getModule("barrel");
        if (barrelModule != null) {
            BarrelModuleAttr barrelAttr = (BarrelModuleAttr) barrelModule.getAttr();
            spreadMult = barrelAttr.getSpreadMultiplier(barrelModule.getDurabilityRatio());
            velMult *= barrelAttr.getMuzzleVelocityMultiplier(barrelModule.getDurabilityRatio());
        }

        float speed = type.getBaseVelocity() * velMult + attr.staticAttribute.getBarrel().velocityBonus();
        return new MuzzleState(jmePos, direction, speed, velMult, spreadMult);
    }

    /**
     * 开火瞬间的地形首段预约。
     * <p>
     * 深度与每 tick 的视界行走相同（{@code Δ = 1/20 s} 时 {@code W₀ = W}），差别只在时刻：
     * 这里赶在同一个游戏 tick 的即时队列消费之前登记，比投射物自己的第一次 {@code preTick}
     * 早整整一个 tick。少了这一段，开火后第一个 tick 的飞段永远进不了预约。
     * <p>
     * <b>调用线程：</b>主线程（{@link #onTick()} 的服务端分支）。
     *
     * @param type 即将出膛的弹种
     */
    private void seedTrajectoryTerrain(BallisticProjectileType type) {
        if (getOwner() == null) return;

        MuzzleState muzzle = computeMuzzleState(type);
        Vector3f muzzlePos = muzzle.position();
        Vector3f dir = muzzle.direction();
        float speed = muzzle.speed();
        // 平台速度按既有出膛口径叠加：真实初速是“方向 × 出膛速率 + 载具速度”
        Vector3f platformVelocity = getSubPart().getLinearVelocity();

        ObjectManager.getOrCreateProjectileManager(getLevel()).seedTerrainPreload(type,
                muzzlePos.x, muzzlePos.y, muzzlePos.z,
                dir.x * speed + platformVelocity.x,
                dir.y * speed + platformVelocity.y,
                dir.z * speed + platformVelocity.z);
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

    // ——— 信号化开火控制（火控写入输入槽） ———

    /**
     * 按 controlInputs 频道优先级从当前信号容器读取最高优先级的有效发射指令。<br>
     * 火控把 {@link FireCommand} 作为信号值写入本发射器的到达频道；
     * {@link io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal} 表示该来源已释放。
     * 结构变化后输入槽被清空，无需逐个来源调用释放。
     *
     * @return 最高优先级的有效 FireCommand，null = 无控制器持有开火权
     */
    @Nullable
    private FireCommand getActiveFireCommand() {
        for (String channel : attr.staticAttribute.getControlInputs()) {
            SignalChannel values = getSignalInputChannels().get(channel);
            if (values == null) continue;
            for (Object value : values.values()) {
                if (value instanceof FireCommand cmd && cmd.firing()) return cmd;
            }
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
     * 由 {@link RegularPart#playAnim(String)} 播放于 ACTION 覆盖层。</p>
     * <p><b>触发粒度：</b>每个 tick 至多触发一次。同一 tick 内的多发在视觉上无法区分，
     * 且 {@code enter()} 是异步提交到物理线程，重复调用会创建多个实例。</p>
     * <p><b>缺失处理：</b>动画集中不存在该名称时告警一次并永久禁用，避免高射速下刷屏。</p>
     * <p><b>调用线程：</b>主线程（{@link #onTick()} 客户端分支）。</p>
     */
    private void playFireAnimation() {
        if (fireAnimUnavailable) return;
        String animName = attr.fireAnimation;
        if (animName == null || animName.isEmpty()) return;
        var part = getSubPart().part;
        if (part == null) return;
        if (!part.hasAnim(animName)) {
            fireAnimUnavailable = true;
            MachineMax.LOGGER.warn("发射器 {} 的开火动画 {} 不存在于零件 {} 的动画集中，已禁用开火动画",
                    name, animName, part.name);
            return;
        }
        part.playAnim(animName);
    }

    /**
     * 客户端：为本 tick 的开火登记一次枪口火光。
     * <p>
     * <b>触发粒度：</b>每个 tick 至多一条，与 {@link #playFireAnimation()} 同粒度——同一 tick 内的
     * 多发落在枪口同一点上，视觉无法区分，逐发登记只会让实际亮度随射速漂移。
     * <p>
     * <b>参数来源：</b>半径、颜色、强度与寿命全部由弹种的 {@code visual.muzzle_flash} 与口径在
     * {@link MuzzleFlashLight#emit} 内部求值，本方法只提供枪口位姿与本 tick 的弹种；火光不在
     * 服务端结算，也不经过网络。
     * <p>
     * <b>失败处理：</b>弹种未启用火光、强度为 0 或口径缺失时，{@code emit} 自行早退，本方法无分支。
     * <p><b>调用线程：</b>主线程（{@link #onTick()} 客户端分支）。
     *
     * @param type 本 tick 发射的弹种（取首发：一 tick 内膛室只装一种弹）
     */
    private void playMuzzleFlash(ProjectileType type) {
        if (!(type instanceof BallisticProjectileType ballistic)) return;
        MuzzleFlashLight.emit(getLevel(), getMuzzleWorldPosition(), ballistic);
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
            fadeSound(currentAutoFireUuid);
            currentAutoFireUuid = null;
            currentFireRateTierKey = null;
        }

        // 淡出弹壳循环音效
        if (currentShellSoundUuid != null) {
            fadeSound(currentShellSoundUuid);
            currentShellSoundUuid = null;
            currentShellRateTierKey = null;
        }

        // 播放停火尾音
        ProjectileType ammo = getCurrentAmmoType();
        if (ammo != null) {
            SoundEvent ceaseSound = ammo.getCeaseFireSound();
            if (ceaseSound != null && ceaseSound != ProjectileType.ProjectileSoundAttr.NO_SOUND) {
                playSpreadingSound(ceaseSound, SoundSource.NEUTRAL);
            }

            // 停火时播放最后一枚弹壳音效（单发）
            SoundEvent shellSound = ammo.getShellSounds().get("0.0");
            if (shellSound != null) {
                playSpreadingSound(shellSound, SoundSource.NEUTRAL);
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
                this.playSpreadingSound(singleSound, SoundSource.NEUTRAL);
            }
        } else {
            // ★ 连射 → 循环音效（通过 ISoundSpreader 追踪枪口位置/速度）
            String tierKey = findBestFireSoundKey(fireSounds);
            SoundEvent autoSound = fireSounds.get(tierKey);
            if (autoSound == null) return;

            if (currentAutoFireUuid == null) {
                // 首次连射：创建循环实例
                currentAutoFireUuid = playSpreadingSound(autoSound, SoundSource.NEUTRAL, 0, 0, true);
                currentFireRateTierKey = tierKey;
            } else if (!tierKey.equals(currentFireRateTierKey)) {
                // RPM 跨档位：交叉淡入新音效
                currentAutoFireUuid = transitionSound(currentAutoFireUuid, autoSound,
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
                playSpreadingSound(singleSound, SoundSource.NEUTRAL);
            }
        } else {
            String tierKey = findBestFireSoundKey(shellSounds);
            SoundEvent autoSound = shellSounds.get(tierKey);
            if (autoSound == null) return;

            if (currentShellSoundUuid == null) {
                currentShellSoundUuid = playSpreadingSound(autoSound, SoundSource.NEUTRAL, 0, 5, true);
                currentShellRateTierKey = tierKey;
            } else if (!tierKey.equals(currentShellRateTierKey)) {
                currentShellSoundUuid = transitionSound(currentShellSoundUuid, autoSound,
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
    public Vec3 getPosition(UUID uuid) {
        return getMuzzleWorldPosition();
    }

    @Override
    @NotNull
    public Vec3 getSpeed(UUID uuid) {
        return SparkMathKt.toVec3(getSubPart().getLinearVelocity());
    }

    @Override
    public float getPitch(UUID uuid) {
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

    /** 使用子系统共用的第一人称车内音判定。 */
    @Override
    public boolean shouldApplyInteriorEffect(@Nullable Entity listener, boolean isFirstPerson) {
        return isInteriorListener(listener, isFirstPerson);
    }

    /**
     * 获取当前弹药类型（按优先级）。<br>
     * 依次取膛内那一发、已装 Belt 的下一发、当前供给者即将交付的弹种。
     *
     * @return 当前弹药类型，完全无弹药信息时返回 null
     */
    @Nullable
    public ProjectileType getCurrentAmmoType() {
        if (chamberedType != null) return chamberedType;
        AmmoBelt belt = loadedBelt;
        if (belt != null) {
            ProjectileType next = AmmoBelt.resolve(getLevel(), belt.nextRoundId());
            if (next != null) return next;
        }
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
        ProjectileType stored = data.contains("chambered_type")
                ? AmmoBelt.resolve(getLevel(), ResourceLocation.parse(data.getString("chambered_type")))
                : null;
        setChambered(stored instanceof BallisticProjectileType kinetic ? kinetic : null);

        loadedBelt = data.contains("loaded_belt") ? AmmoBelt.load(data.getCompound("loaded_belt")) : null;
        // 读档不免费产出：存档里没有的内容保持为空，下一次正常交付才转移库存
        pushBelt();
    }

    @Override
    public CompoundTag saveData(CompoundTag data) {
        super.saveData(data);
        if (chamberedType != null) {
            data.putString("chambered_type", chamberedType.getRegistryKey().toString());
        }
        if (loadedBelt != null) data.put("loaded_belt", loadedBelt.save());
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
        return attr.staticAttribute.getControlInputs();
    }

    @Override
    public boolean acceptAllBroadcastInput() {
        return acceptAllRoutingInput();
    }

    /**
     * 宽接收只由控制频道列表决定：{@code control_inputs} 为空表示接受任意路由与广播，
     * 非空则按所列频道筛选。供弹来源的发现表属于发送侧查询，不参与接收声明。
     */
    @Override
    public boolean acceptAllRoutingInput() {
        return attr.staticAttribute.getControlInputs().isEmpty();
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(2);
        result.putAll(attr.ammoCountOutputs);
        result.putAll(attr.ammoDiscoveryTargets);
        return result;
    }
}
