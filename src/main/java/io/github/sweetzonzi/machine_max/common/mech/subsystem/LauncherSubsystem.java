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
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.registry.MMEntityDataSerializers;
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
 * 投射物类型由当前路的提供者给出，而非静态属性直接指定。<br>
 * 弹药管理已由 {@link WeaponControllerSubsystem} 接管——Controller 负责弹种选择和路由决策，
 * Launcher 仅执行当前路由上的装填和发射。
 * <p>
 * <b>膛内那发是独立的一份库存单元：</b>炮闩（唯一的关键模块）归零时，膛内那发按自己的发射药与
 * 战斗部 {@code cookoff} 贡献合成一次普通爆炸并被消耗，见 {@link #onDestroyed()}。它已经从提供者
 * 的弹仓里扣掉，因此与装弹机的库存殉爆互不重复计数。
 * <p>
 * <b>路由指认的双端口径：</b>"本管走哪条路"是服务端的决定，经 {@link #ROUTE_TRANSPORTER_ID} 与
 * {@link #ROUTE_PROVIDER_ID} 一对引用同步给客户端；客户端按整对解析出本机的两个实例，挑选只发生在
 * 服务端。指认变化时旧路上尚未取走的交付由 {@link #setCurrentRoute} 取消，两端都不会留下无人认领的
 * 输弹线。
 */
public class LauncherSubsystem extends ModularSubsystem implements IAmmoConsumer, ISoundSpreader, IParticleAnchor {

    public final LauncherSubsystemAttr attr;

    // ——— 射速相位（整数子 tick 单位） ———

    /**
     * 本管的射速相位与防连点冷却。
     * <p>
     * 调度只依赖实际执行的业务 tick，不含绝对时刻；周期来自炮闩射速，随模块受损或升级按新周期
     * 结算当轮。相位、冷却与连射标记各自独立，见 {@link FirePhase}。
     * <p>
     * <b>调用线程：</b>仅主线程（{@link #onTick()} 内部读写）。
     */
    private final FirePhase firePhase = new FirePhase();

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
     * 本管当前路由的<b>运输者</b>身份（网络同步）。{@link AmmoRef#NONE} = 人力运输者（本管自己的装填手）。<br>
     * <p>
     * <b>写者：</b>服务端路由（{@link #setCurrentRoute}）。客户端在
     * {@link #onSyncedDataUpdated(EntityDataAccessor)} 里把这一对引用解析成本机实例
     * （{@link #applySyncedRoute()}），因此两端的指认由服务端单方面决定：客户端的路由不参与决策，只跟随。
     * <p>
     * <b>两个字段是一对量：</b>{@link #ROUTE_PROVIDER_ID} 为 {@code NONE} 表示没有路由；单独一个先到不构成
     * 一条路。客户端按整对解析与替换（§4.4），任一解析失败就保留上一帧的整对。
     * <p>
     * <b>初值来源是补推，不是变化推送：</b>指认是"服务端每 tick 现算、载入后往往不再变化"的值，
     * 载具在客户端建好那一刻由 {@code SubsystemController.pushAllTo(ServerPlayer)} 补一份全量快照。
     */
    private static final EntityDataAccessor<AmmoRef> ROUTE_TRANSPORTER_ID =
            SynchedEntityData.defineId(LauncherSubsystem.class, MMEntityDataSerializers.getAMMO_REF());

    /** 本管当前路由的<b>提供者</b>身份（网络同步）；{@link AmmoRef#NONE} = 没有路由。 */
    private static final EntityDataAccessor<AmmoRef> ROUTE_PROVIDER_ID =
            SynchedEntityData.defineId(LauncherSubsystem.class, MMEntityDataSerializers.getAMMO_REF());

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
     * 当前装填作业的整包快照（网络同步），空值表示这一 tick 没有作业。<br>
     * 轮次、运输者、提供者、冻结规格与预留份额一起下发，见
     * {@link ReloadJobSpec}：这五项在作业存续期间不变，受理时一起定下。进度、本阶段时长与阶段另由
     * {@link #JOB_PROGRESS_ID} 承载，因为它们在换段与换人重定时都要变，而这份规格不必跟着重发。
     * <p>
     * 作业身份是 {@link ReloadJobKey}：宿主 SubPart ID、本子系统序号与这份分配里的运输者、提供者四个值拼成，
     * 运输者的机械槽与提供者的预留都用它匹配。客户端据轮次区分"同一轮又收到一次更新"与"换了一轮"
     * ——前者保留本地已推进的进度，后者按身份收尾本地那一条后从基线重新开始。
     */
    private static final EntityDataAccessor<Optional<ReloadJobSpec>> JOB_SPEC_ID =
            SynchedEntityData.defineId(LauncherSubsystem.class, MMEntityDataSerializers.getOPTIONAL_RELOAD_JOB_SPEC());

    /**
     * 当前作业的动态样本（网络同步），见 {@link ReloadJobProgress}：轮次、进度基线、本阶段时长与阶段。
     * <p>
     * 与 {@link #JOB_SPEC_ID} 的轮次始终一致——两者由同一次受理一起写下，之后只有本项会变。
     * 因此客户端拿轮次把两条样本配成一条作业；轮次不一致说明静态部分还没跟上，本项不被应用。
     */
    private static final EntityDataAccessor<Optional<ReloadJobProgress>> JOB_PROGRESS_ID =
            SynchedEntityData.defineId(LauncherSubsystem.class, MMEntityDataSerializers.getOPTIONAL_RELOAD_JOB_PROGRESS());

    /**
     * 最近若干条作业结束结果（网络同步），见 {@link ReloadJobResults}：按发生顺序排列的
     * 〔轮次，已交付？〕。<br>
     * 一轮服务端 tick 里可以结束不止一条作业——零时长作业受理即交付、紧接着受理下一条并同样交付，
     * 是连发机构的常规路径。用一对标量字段只能留下最后一次，前一条的终态在客户端看不到，本端的预测
     * 作业就会一直等一个不会再来的结果，因此这里是有序列表。取消、换人、摧毁、来源失效、暂停超时与
     * 成功交付共用这一份列表，客户端不需要为"超时"或"只清资源"另设一条通路。
     */
    private static final EntityDataAccessor<ReloadJobResults> JOB_RESULTS_ID =
            SynchedEntityData.defineId(LauncherSubsystem.class, MMEntityDataSerializers.getRELOAD_JOB_RESULTS());

    /** 结果列表的保留条数：这是同步值而不是日志，客户端用轮次水位忽略已确认过的旧条目。 */
    private static final int MAX_RECENT_RESULTS = 8;

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
    private boolean pendingRoute;
    private boolean pendingJob;
    private boolean pendingBelt;
    private boolean pendingChambered;

    /**
     * 本管最近一次本地交付的轮次；{@code 0} 表示还没有过。<br>
     * 本地交完后作业就清空了，权威结果随后到达时靠它认出"这条结果就是我刚替它交过的那一份"，
     * 从而不在终态上再动一次弹药内容，并在权威判它未交付时把那一轮还回去。只保留最近一次，不存作业历史。
     */
    private int lastLocalDeliveryRound;

    /** 服务端最近若干条终态，按发生顺序排列；只在服务端追加，经 {@link #JOB_RESULTS_ID} 下发。 */
    private final Deque<ReloadJobResults.Entry> recentResults = new ArrayDeque<>();

    /** 客户端已经应用过的结束结果轮次，用来忽略同一结果的重复到达。 */
    private int lastAppliedResultRound;

    /**
     * 本 tick 写过进度样本（服务端）：那一笔写的是"写的那一刻"的基线，要在本 tick 收尾定稿。<br>
     * 客户端拿到样本后的下一个业务 tick 才开始按它推进，所以样本必须等于服务端本 tick 结束时的进度。
     * 受理与换段都发生在 tick 中途，写下的基线还差本 tick 那一次推进；客户端照它起算会永远比服务端
     * 晚一拍，见 {@link #settleProgressBaseline()}。
     */
    private boolean progressDraft;

    /**
     * 暂停超时：作业连续这么多 tick 推不动就按未交付终止并释放资源。<br>
     * 10 秒是"卡住"与"等下一发"的分界——机构受阻、供能不足与执行者缺席都在这个窗口内恢复或
     * 由路由改派；超过它仍推不动的作业既占着机构槽位也拽着架上的预留，比放弃它更伤。
     */
    private static final int PAUSE_TIMEOUT_TICKS = 200;

    /**
     * 分辨"本端在途的射击"与"权威只做过的减少"所用的单向延迟上限（业务 tick）。<br>
     * 权威样本与本端副本的余量差，只有超出本端这段时间里可能打掉的发数，才算权威独有的减少，
     * 见 {@link #inFlightShotBound()}。取 6 tick（300 ms 单程、600 ms 往返）：这是单人载具场景下
     * 该上界的保守值，延迟再高时两种来源本来就分不开，宁可多保留本端一会——判错的代价是多打几发，
     * 而照抄的代价是本端那几发没有声音与炮口特效。
     */
    private static final int SHIFT_CEILING_TICKS = 6;

    /**
     * 本管已经受理过的轮次数，下一次受理取它加一。<br>
     * 这是轮次的唯一来源：本端每成功受理一份作业就推进一次，改认权威作业时改成权威那一份的轮次。它只作样本
     * 与收据的先后标记，不参与作业身份，因此不会像取号那样让两端的账本键错开——机械槽与预留的键是结构三元组
     * {@link ReloadJobKey}，与本值无关。<br>
     * <b>按受理推进而不是按交付推进：</b>中止的作业也是一个轮次，它那条未交付的终态收据必须能与紧接着受理的
     * 新作业区分开，否则新作业会被上一轮的收据收掉。<br>
     * 不存档——作业本身不跨存档延续，读档后从 0 重新计。
     */
    private int roundsAccepted;

    /**
     * 本管当前指认的那条路：运输者 + 提供者。<br>
     * 服务端由 WeaponController 的路由决策设置；客户端由 {@link #ROUTE_TRANSPORTER_ID}／
     * {@link #ROUTE_PROVIDER_ID} 两个同步值整对解析得到。null 表示没有路由（不装填，可发射膛内已有弹药）。
     * <p>
     * 内容是<b>当前拓扑的发现结果</b>的一个组合，不累积历史：每次结构变化先清空两张发现表
     * （{@link #onVehicleStructurePreChanged()}），再由本管的发现阶段与装弹机的喂弹线重新合成。
     */
    @Nullable
    private AmmoPath currentRoute = null;

    /**
     * 发现到的运输者（装弹机），按到达顺序保存。<br>
     * 频道只是登记键：路由不按频道分组，也不把频道写进池条目。
     */
    private final List<IAmmoTransporter> transporters = new ArrayList<>();

    /**
     * 发现到的提供者（弹药架），按到达顺序保存。<br>
     * 只有本型号声明了完整人力流程时才登记：它证明"这条管自己有一条人力路"，机械段为零的架不能在没有
     * 人工段的型号上充当提供者。
     */
    private final List<IAmmoSupplier> providers = new ArrayList<>();

    /**
     * 本机的人工段用人，同时是人力运输者（人力直取路径）。<br>
     * `human_reload_time` 为 0 时它不进入路由（{@link #discoverRoutes()} 不合成人力路）。
     */
    private final LoaderRoster loaders;

    /**
     * 客户端：本机人工段已播到的进度比例；负数表示本段还没有游标。<br>
     * 游标是各客户端自己的量：进度回退（作业重开、上行校准把进度往回拉）只把游标拉回去，不重播已经越过的
     * 阈值。换作业时由 {@link #humanSoundRound} 识别并重建。
     */
    private float humanSoundProgress = -1f;

    /** 客户端：{@link #humanSoundProgress} 对应的作业编号；换作业时游标从头建立 */
    private int humanSoundRound;

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
        this.loaders = new LoaderRoster(this, attr.loaderDiscoveryTargets,
                attr.loaderTimeMultipliers);
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
        builder.define(ROUTE_TRANSPORTER_ID, AmmoRef.NONE);
        builder.define(ROUTE_PROVIDER_ID, AmmoRef.NONE);
        builder.define(BELT_ID, Optional.empty());
        builder.define(CHAMBERED_ID, Optional.empty());
        builder.define(JOB_SPEC_ID, Optional.empty());
        builder.define(JOB_PROGRESS_ID, Optional.empty());
        builder.define(JOB_RESULTS_ID, ReloadJobResults.EMPTY);
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
        if (dataAccessor.equals(ROUTE_TRANSPORTER_ID) || dataAccessor.equals(ROUTE_PROVIDER_ID)) {
            pendingRoute = true;
        } else if (dataAccessor.equals(BELT_ID)) {
            pendingBelt = true;
        } else if (dataAccessor.equals(CHAMBERED_ID)) {
            pendingChambered = true;
        } else if (dataAccessor.equals(JOB_SPEC_ID)
                || dataAccessor.equals(JOB_PROGRESS_ID)
                || dataAccessor.equals(JOB_RESULTS_ID)) {
            pendingJob = true;
        }
    }

    /**
     * 两端都在本轮模拟结束后收尾：客户端统一落地待处理同步值，服务端把本 tick 写过的进度样本定稿。<br>
     * 客户端的顺序是先指认、再作业、最后膛内弹与已装 Belt：指认变化会先按身份结束旧作业，作业字段随后
     * 组成新状态，弹药内容最后覆盖本地副本，因此一次收到的多项变化不会出现"作业已换而膛内还是旧的那发"
     * 中间态。
     * <p><b>调用线程：</b>主线程（tick 收尾，由 {@code SubsystemController#postTick()} 统一调用）。
     */
    @Override
    public void onPostTick() {
        super.onPostTick();
        if (!getLevel().isClientSide()) {
            // 本 tick 写过的进度样本在这里定稿成 tick 末尾的值：客户端下一个业务 tick 才按它推进
            if (progressDraft) {
                progressDraft = false;
                settleProgressBaseline();
            }
            return;
        }
        if (pendingRoute) {
            pendingRoute = false;
            applySyncedRoute();
        }
        if (pendingJob) {
            pendingJob = false;
            applySyncedJob();
        }
        if (pendingBelt) {
            pendingBelt = false;
            applySyncedBelt(false);
        }
        if (pendingChambered) {
            pendingChambered = false;
            applySyncedChamber(false);
        }
    }

    /**
     * 按同步值重建已装 Belt 的本地副本。<br>
     * 必须是副本：客户端会就地消耗它（取一发进膛、射击扣额度），不能改到同步基线本身。
     *
     * @param force true 表示无条件按同步值收口（本端替这一轮交过一份、而权威判它未交付时用）
     */
    private void applySyncedBelt(boolean force) {
        AmmoBelt synced = getSynchedData().get(BELT_ID).orElse(null);
        int inFlightBound = inFlightShotBound();
        if (!force && !AmmoBelt.sampleOverrides(loadedBelt, synced, inFlightBound)) {
            traceBelt(loadedBelt, synced, inFlightBound, true);
            return;
        }
        loadedBelt = synced == null ? null : synced.copy();
        traceBelt(loadedBelt, synced, inFlightBound, false);
    }

    /**
     * 打印一次已装 Belt 的落地：本端副本与权威样本两份读数的余量、头部弹种与回充运行态。<br>
     * 三样一起打是因为头部弹种由余量按模式取模推出（{@link AmmoBelt#nextRoundId()}），两份余量的奇偶差
     * 直接表现成"下一发是哪一型"不同；而回充是余量在射击之外唯一会动的地方，本端那份回充由本端自己推进，
     * 因此进度与延迟各记一份。{@code keep} 说明这一次是留本端还是按权威落地。<br>
     * 余量相同则头部弹种必然相同；反过来两端的余量差正好是模式总发数的整数倍时弹种也相同，因此两个字段都要看。
     *
     * @param local     落地后的本端副本，可为 null
     * @param synced    权威样本，可为 null
     * @param bound     这一次比对用的在途上界
     * @param keepLocal true 表示保留了本端副本
     */
    private void traceBelt(@Nullable AmmoBelt local, @Nullable AmmoBelt synced, int bound, boolean keepLocal) {
        if (!WeaponTrace.isEnabled()) return;
        WeaponTrace.log(getLevel(), "sync-belt launcher={} local={} synced={} head={} rech={} bound={} keep={}",
                traceId(), beltCount(local), beltCount(synced), beltHead(local) + "/" + beltHead(synced),
                beltRecharge(local) + "/" + beltRecharge(synced), bound, keepLocal ? "local" : "authority");
    }

    /** 插桩用的余量写法：{@code 余量/长度}；没有副本时 {@code -}。 */
    private static String beltCount(@Nullable AmmoBelt belt) {
        return belt == null ? "-" : belt.remaining() + "/" + belt.length();
    }

    /** 插桩用的头部弹种：没有副本、或余量为零取不到弹种时 {@code -}。 */
    private static String beltHead(@Nullable AmmoBelt belt) {
        if (belt == null) return "-";
        ResourceLocation head = belt.nextRoundId();
        return head == null ? "-" : head.toString();
    }

    /** 插桩用的回充运行态：{@code 零碎进度:延迟tick}；没有回充配置时 {@code -}。 */
    private static String beltRecharge(@Nullable AmmoBelt belt) {
        if (belt == null || !belt.hasRecharge()) return "-";
        return (Math.round(belt.rechargeProgress() * 100f) / 100f) + ":" + belt.rechargeDelayTicks();
    }

    /**
     * 本端在途射击最多可能有几发：权威写下样本之后、本端落地之前，这段时间里本端还能打掉几发。<br>
     * 本端时间线比权威晚一个单程，因此两者的余量差只有在这一档以内才解释得成"本端还没打的那几发"。
     * 由射速把 {@link #SHIFT_CEILING_TICKS} 折成发数，再加跨在窗口边界上的那一发；射速不可用时按一发
     * 算——折算不出来的武器不靠这条判据细分，差额超过一发即按权威收口。
     */
    private int inFlightShotBound() {
        long intervalU = FirePhase.intervalUFor(attr.staticAttribute.getBreech().fireRate());
        if (intervalU <= 0L) return 1;
        long intervalTicks = (intervalU + FirePhase.TICK_UNITS - 1) / FirePhase.TICK_UNITS;
        return (int) (SHIFT_CEILING_TICKS / intervalTicks + 1);
    }

    /**
     * 按同步值重建膛内那一发。<br>
     * 权威的膛内样本取的是它那一 tick 末尾的值，而它在交付那一 tick 就开火清膛了；本端这一发还要等
     * 下一次相位到期才打出去。这段窗口里样本是空的，照抄就等于把本端手里那一发抹掉——服务端打出去了，
     * 本端这一发没有声音与炮口特效。<br>
     * 因此空样本不清本端膛内的弹：本端这一发是自己时间线上的那一发，权威的空样本说的是"它已经把它打掉了"，
     * 而不是"本端不该有这一发"。非空样本一律覆盖，权威手里有弹时本端就按它那一发（换装、换弹种这类
     * 本端产不出来的变化由此落地）。撤回是另一回事：那里必须按权威值收口，走 {@code force = true}。
     *
     * @param force true 表示无条件按同步值收口（本端替这一轮交过一发、而权威判它未交付时用）
     */
    private void applySyncedChamber(boolean force) {
        ProjectileType stored = AmmoBelt.resolve(getLevel(), getSynchedData().get(CHAMBERED_ID).orElse(null));
        BallisticProjectileType synced = stored instanceof BallisticProjectileType kinetic ? kinetic : null;
        if (!force && synced == null && chamberedType != null) {
            WeaponTrace.log(getLevel(), "sync-chamber launcher={} ammo={} keep=local", traceId(),
                    chamberedType.getRegistryKey());
            return;
        }
        chamberedType = synced;
        WeaponTrace.log(getLevel(), "sync-chamber launcher={} ammo={}", traceId(),
                chamberedType == null ? "-" : chamberedType.getRegistryKey());
    }

    /**
     * 全量补推前把进度样本里的基线刷成当前进度。<br>
     * {@link ReloadJobProgress#elapsed()} 是客户端起算的起点，不是每 tick 的当前进度，因此样本停在
     * 写下它的那一刻：直接推给新加入的客户端会让它从"受理那一拍"开始模拟一份已经进行到一半的作业。
     * 这一刻刷新一次，客户端拿到的基线就是"现在"（{@link #settleProgressBaseline()}）。
     * 冻结规格不需要刷新：它在作业存续期间不变，访问器里那份就是当前值。
     * <p><b>调用线程：</b>主线程（服务端，{@code SubsystemController#pushAllTo}）。
     */
    @Override
    public void refreshSyncBaseline() {
        if (getLevel().isClientSide()) return;
        settleProgressBaseline();
    }

    /**
     * 把进度样本里的基线刷成当前进度；没有作业或样本已经是当前值时不写。<br>
     * 基线是客户端起算的起点，而客户端要等下一个业务 tick 才开始按它推进，因此它必须是服务端<b>本 tick
     * 结束时的进度</b>。两个调用点都满足这一条：tick 收尾（{@link #onPostTick()}，本 tick 写过样本时）
     * 与全量补推（{@link #refreshSyncBaseline()}，此刻的进度就是最近一 tick 的末尾值）。受理与换段写在
     * tick 中途，那一刻的进度还差本 tick 那一次推进——基线早一拍，镜像作业就永远比服务端晚一拍：交付落在
     * 服务端开火之后，而同一 tick 的终态收据已经把这条本地作业按"已交付"收尾，本端于是连"晚一拍"的
     * 那一发都没有，只剩投射物飞出去、没有声音与炮口特效。
     * <p><b>调用线程：</b>主线程（服务端）。
     */
    private void settleProgressBaseline() {
        ReloadJob job = reloadJob;
        if (job == null) return;
        Optional<ReloadJobProgress> synced = getSynchedData().get(JOB_PROGRESS_ID);
        if (synced.isEmpty()) return;
        ReloadJobProgress progress = synced.get();
        if (progress.elapsed() == job.elapsedTicks()) return;
        getSynchedData().set(JOB_PROGRESS_ID, Optional.of(progress.withElapsed(job.elapsedTicks())));
    }

    /**
     * 客户端按同步样本组成本地作业。<br>
     * 先应用结束结果，再落地作业：结果只影响轮次匹配的那一条本地作业，两条样本才是"现在有哪份作业"。
     * 本地作业只经 {@link #finishReloadJob(boolean)} 消失——换作业与清空都先按身份收尾，释放它在
     * 本端占着的执行资源与预留。这一条纪律对本端自己受理的预测尤其必要：那份作业的占用只有本端知道，
     * 服务端从未受理过它，也就不会有任何权威消息来释放它。<br>
     * 两条样本必须轮次一致才算一份作业，配不上时本轮不应用（也不清本地作业：清掉会让一条仍在权威里
     * 存在的作业从本端消失）。<br>
     * 身份（哪根管 + 哪条路）相同的两条样本按轮次分三种：轮次相同是同一次受理的离散更新，沿用本地进度、
     * 只向前对齐基线；权威轮次更大说明两端的受理计数差了一拍，就地改成权威的轮次再对齐（不动机械槽与预留）；
     * 权威轮次更小说明本端已经走过那一轮或者权威手里还有更早的在途作业——上一轮已有结束结果时不动，
     * 否则收尾本端这条后整条改认。<br>
     * <b>权威样本为空时不收尾本地作业：</b>两端的受理差一拍是常态——本端刚为这一发建好预测，权威那条
     * 要后一个业务 tick 才受理，中间这段快照就是空的，而同一轮往往还带着上一条作业的结束结果。在这里收尾
     * 会杀掉本端正在推进的预测，让本端在服务端那一发已经在飞的 tick 上膛内为空
     * （{@code fire-end … reason=no-round}）：服务端打出去了，本端这一发却没有声音与炮口特效。
     * 预测的终态只由它自己的交付、或权威关于同一轮次的结束结果给出；服务端最终没有受理的那一条最多
     * 白预测一次（§7.4）。
     */
    private void applySyncedJob() {
        applyJobResult();
        Optional<ReloadJobSpec> specSynced = getSynchedData().get(JOB_SPEC_ID);
        if (specSynced.isEmpty()) return;
        ReloadJobSpec spec = specSynced.get();
        Optional<ReloadJobProgress> progressSynced = getSynchedData().get(JOB_PROGRESS_ID);
        ReloadJobProgress progress = progressSynced.orElse(null);
        if (progress == null || progress.round() != spec.round()) {
            WeaponTrace.log(getLevel(), "reload-progress-mismatch launcher={} specRound={} progressRound={}",
                    traceId(), spec.round(), progress == null ? -1 : progress.round());
            return;
        }
        ReloadJobKey key = new ReloadJobKey(getSubPart().getId(),
                getSubPart().subsystemIndex(this), spec.transporter(), spec.provider());
        ReloadJob current = reloadJob;
        if (current != null && current.key().equals(key)) {
            if (current.round() == spec.round()) {
                if (!current.allocationMatches(spec)) {
                    // 同一轮、不同的冻结分配：本地那一条不是权威的这一条（弹种或份额不同），
                    // 按分叉处理——沿用本地进度会让本端按另一份规格继续推进，交付内容也与权威不同。
                    WeaponTrace.log(getLevel(), "reload-divergence launcher={} round={} reason=allocation "
                                    + "localReserved={} authorityReserved={}", traceId(), spec.round(),
                            current.reservedCount(), spec.reserved());
                    // 先按本端身份释放这一条的预测占用与预留，再改认权威这一条：直接替换会把这几个预测
                    // 资源留在本端表里，权威那条作业根本不知道它们，之后也没有别的路径会回收
                    finishReloadJob(false);
                    adoptSyncedJob(spec, progress, key);
                    return;
                }
                // 同一次交付的离散更新：阶段变了就按新阶段从基线起算（人工段走完换成机械段），
                // 阶段没变只把时长与基线对齐（换人重定时会同时给出缩放过的进度与新的剩余量）
                boolean restaged = current.stage() != progress.stage() || current.totalTicks() != progress.total();
                current.alignRemote(progress.stage(), progress.total(), progress.elapsed());
                if (restaged) {
                    WeaponTrace.log(getLevel(), "reload-mirror-stage launcher={} round={} stage={} stageTicks={} baseline={}",
                            traceId(), progress.round(), progress.stage(), progress.total(), progress.elapsed());
                }
                return;
            }
            if (spec.round() > current.round()) {
                // 两端的受理计数差了一拍，权威把同一条作业记成更后面的轮次：就地改成它的轮次再对齐基线。
                // 轮次不是身份，机械槽与预留一个都不用动
                if (current.allocationMatches(spec)) {
                    WeaponTrace.log(getLevel(), "reload-rekey launcher={} localRound={} authorityRound={} baseline={}",
                            traceId(), current.round(), spec.round(), progress.elapsed());
                    current.adoptRound(spec.round());
                    roundsAccepted = Math.max(roundsAccepted, spec.round());
                    current.alignRemote(progress.stage(), progress.total(), progress.elapsed());
                    return;
                }
                // 同一轮号却是另一份分配：本端这条不是权威那一条，按分叉重建
                finishReloadJob(false);
                adoptSyncedJob(spec, progress, key);
                return;
            }
            // 权威说的轮次在本端之后：要么是本端已经结束过的那一轮（收据重复到达），要么是权威手里另一条
            // 更早的在途作业。前者不动；后者以权威为准，收尾本端这条后整条改认
            if (spec.round() <= lastAppliedResultRound) {
                WeaponTrace.log(getLevel(), "reload-mirror-stale launcher={} round={} applied={}", traceId(),
                        spec.round(), lastAppliedResultRound);
                return;
            }
            finishReloadJob(false);
            adoptSyncedJob(spec, progress, key);
            return;
        }
        // 路换了，或本端根本没有作业：以权威这一条为准
        if (spec.round() <= lastAppliedResultRound) {
            WeaponTrace.log(getLevel(), "reload-mirror-stale launcher={} round={} applied={}", traceId(),
                    spec.round(), lastAppliedResultRound);
            return;
        }
        if (current != null) finishReloadJob(false);
        adoptSyncedJob(spec, progress, key);
    }

    /**
     * 采纳权威的那条作业：在本端按同一套受理顺序取得执行资源与弹药预留，再按权威的轮次与进度基线建作业。<br>
     * 冻结分配取权威那一份（作业上那一对是权威），本端只是把它的承诺也落在自己的表里。不这么做，采纳来的
     * 作业下一 tick 就会在「承诺分两半」的检查里被判失效（{@code reload-end … delivered=false progress=0/9}），
     * 本端从此只剩"权威受理、本端不预测"的降级态。<br>
     * 取得不了（路还没解析出来、机器已被别的作业占着、架上没有可分配库存）就不建作业：不预测比建一条拿不到
     * 弹的作业好，权威的终态仍会按结束结果落地。<br>
     * 采纳成功时本管的受理计数改成权威这一条的轮次：轮次是"本管第几次受理"，权威把它记成第几轮，本端就认
     * 第几轮，下一次受理从它之后接着走。
     * <p><b>调用线程：</b>主线程（客户端 tick 收尾）。
     *
     * @return 采纳成功时 true；false 表示本端这一轮不持有作业
     */
    private boolean adoptSyncedJob(ReloadJobSpec spec, ReloadJobProgress progress, ReloadJobKey key) {
        IAmmoTransporter transporter = resolveTransporter(spec.transporter());
        IAmmoSupplier provider = resolveProvider(spec.provider());
        boolean adopted = false;
        if (transporter != null && provider != null && transporter.claim(this, key)) {
            if (provider.reserve(key) != null) {
                beginReloadJob(spec.toLocalJob(key, progress));
                // 权威把这一条记成第几轮，本端就认第几轮：下一次受理从它之后接着走
                roundsAccepted = spec.round();
                adopted = true;
            } else {
                transporter.releaseClaim(this, key);
            }
        }
        if (adopted) {
            WeaponTrace.log(getLevel(), "reload-mirror launcher={} round={} stage={} stageTicks={} baseline={} transporter={} provider={}",
                    traceId(), spec.round(), progress.stage(), progress.total(), progress.elapsed(),
                    WeaponTrace.ref(spec.transporter()), WeaponTrace.ref(spec.provider()));
        } else {
            WeaponTrace.log(getLevel(), "reload-mirror-skip launcher={} round={}", traceId(), spec.round());
        }
        return adopted;
    }

    /**
     * 应用结束结果（客户端）。<br>
     * 轮次匹配才算本机的事，两种匹配分开处理：轮次等于<b>当前这条本地作业</b>时，这条终态就是它自己的
     * 结局；轮次等于<b>刚才本地交付过的那一条</b>时，本端已经自己走到了终点，终态只是收据。两者可以同时
     * 成立——本端替上一轮交过一发，下一轮已经在手——因此不能压成一个"是否匹配本机"的判断：收据只许动
     * 弹药内容，不许结束正在推进的那一条作业。<br>
     * 不匹配的是更早作业的结果：只当作已经应用过，既不结束当前作业，也不改它的进度与弹药内容
     * （库存与预留本就按各自的同步值校准）。
     * <p><b>已交付的终态要在本端兑现内容：</b>两端的受理可以差一拍（切换弹种、指认晚到），于是权威的
     * 终态可能先到、本端这条预测还差一两 tick 没走完。这时不能只把作业按已交付收尾——那样本端什么也没交，
     * 而同一批里的膛内样本已经是打空的那一发（服务端在交付那一 tick 就开火并清膛），本端于是既没有弹也
     * 没有作业：服务端打出去了，本端这一发没有声音与炮口特效。按本端受理时取得的那份预留把这一份兑现掉
     * （{@link #deliverAuthorized(ReloadJob)}），本端就不少这一发，下一 tick 按相位补射。
     * <p>列表按发生顺序逐条应用，因此同一轮里连续结束的两条作业都能被看到——第一条结束、第二条才
     * 建得起来。轮次水位只用于忽略重复到达：一轮里出现的新条目的最大轮次会推进水位，已经应用过的
     * 旧条目不再重复处理。
     * <p><b>白预测的撤回：</b>本端替某一轮交过一发、而权威判它未交付时，那一发是白预测。弹药内容按权威值
     * 收口（主动重取一次同步值），本地交付收据随即清掉，别让它再匹配下一轮。轮次不退回：它按受理计，
     * 而那次受理确实发生过。
     */
    private void applyJobResult() {
        ReloadJobResults results = getSynchedData().get(JOB_RESULTS_ID);
        for (ReloadJobResults.Entry result : results.entries()) {
            if (result.round() <= lastAppliedResultRound) continue;
            ReloadJob current = reloadJob;
            boolean onLiveJob = current != null && current.round() == result.round();
            boolean onReceipt = !onLiveJob && result.round() == lastLocalDeliveryRound;
            WeaponTrace.log(getLevel(), "reload-result launcher={} round={} delivered={} mine={}", traceId(),
                    result.round(), result.delivered(), onLiveJob || onReceipt);
            if (onLiveJob) {
                if (result.delivered()) {
                    WeaponTrace.log(getLevel(), "reload-terminal-deliver launcher={} round={} progress={}/{}",
                            traceId(), current.round(), current.elapsedTicks(), current.totalTicks());
                    deliverAuthorized(current);
                } else {
                    finishReloadJob(false);
                }
            } else if (onReceipt && !result.delivered()) {
                WeaponTrace.log(getLevel(), "reload-retract launcher={} round={}", traceId(), result.round());
                // 这一轮的交付作废：内容按权威值收口，两项都强制落地，本端不该再留着它
                applySyncedBelt(true);
                applySyncedChamber(true);
            }
            if (result.round() == lastLocalDeliveryRound) lastLocalDeliveryRound = 0;
            lastAppliedResultRound = Math.max(lastAppliedResultRound, result.round());
        }
    }

    /**
     * 权威判这一轮已交付、而本端这条预测还没走完时，在本端把它交付掉。<br>
     * 内容来自本端受理时取得的那份预留（{@link #deliver} 走的就是这条路），因此交付的弹种与份额与权威
     * 那一份相同，不另建一条"按同步值补发"的旁路。路解析不出来（结构变化把运输者或提供者移出了发现结果）
     * 就只按已交付收尾：内容交不出来，至少有正确的终态，不把作业挂在这里等它。
     * <p><b>调用线程：</b>主线程（客户端 tick 收尾）。
     *
     * @param job 轮次与权威终态一致的本地作业
     */
    private void deliverAuthorized(ReloadJob job) {
        AmmoPath route = reloadRoute();
        if (route == null) {
            finishReloadJob(true);
            return;
        }
        deliver(route, job);
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

    /**
     * 写入膛内那一发并同步；客户端不写。<br>
     * 换内容只走这一条路：射击、退膛、从已装 Belt 补膛、交付与读档都经过它。
     */
    private void setChambered(@Nullable BallisticProjectileType round) {
        if (chamberedType == round) return;
        chamberedType = round;
        if (!getLevel().isClientSide()) {
            getSynchedData().set(CHAMBERED_ID, Optional.ofNullable(round == null ? null : round.getRegistryKey()));
        }
    }

    /**
     * 写入已装 Belt 并同步；客户端不写。<br>
     * 旧内容的去处是"当前路的提供者，其次本装配体里其它提供者"，不记账：退还时按 {@link #getRoutes()}
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
    public AmmoPath getCurrentRoute() {
        return currentRoute;
    }

    /**
     * 设置当前指认的路：两端都调用它，服务端由 {@code WeaponControllerSubsystem#routeAmmoToLaunchers} 选路，
     * 客户端由同一段选路代码选出、再由同步来的两个引用收口（{@link #applySyncedRoute()}）。<br>
     * 指认变化时先按旧身份收尾本管进行中的作业：那份作业是"点名给本管的"，本管一走它就无人认领，旧运输者
     * 会一直把槽位或乘员占着，新的一份既不会开始也不会给别人。旧作业的库存从未扣减（扣减只发生在交付那
     * 一刻），丢弃它不退还任何东西。清理放在这里而不是交给调用方，是为了不存在"换路忘了归还"的路径。
     * <p>
     * 只有服务端把它写进 {@link #ROUTE_TRANSPORTER_ID}／{@link #ROUTE_PROVIDER_ID}：客户端那一对来自服务端，
     * 它自己选出来的是预测，不参与下发。
     * <p><b>调用线程：</b>主线程（{@link WeaponControllerSubsystem#onTick()} 内的弹药路由，两端）。
     *
     * @param route 新路，null 表示没有路由（不装填，仍可发射膛内已有弹药）
     */
    @Override
    public void setCurrentRoute(@Nullable AmmoPath route) {
        if (Objects.equals(currentRoute, route)) return;
        // 换路等于放弃这一份作业：先按旧身份收尾（此时字段还是旧值），再改指认
        finishReloadJob();
        currentRoute = route;
        AmmoRef transporterRef = route == null ? AmmoRef.NONE : AmmoRef.ofTransporter(route.transporter());
        AmmoRef providerRef = route == null ? AmmoRef.NONE : AmmoRef.ofProvider(route.provider());
        if (!getLevel().isClientSide()) {
            getSynchedData().set(ROUTE_TRANSPORTER_ID, transporterRef);
            getSynchedData().set(ROUTE_PROVIDER_ID, providerRef);
        }
        WeaponTrace.log(getLevel(), "route launcher={} transporter={} provider={}", traceId(),
                WeaponTrace.ref(transporterRef), WeaponTrace.ref(providerRef));
    }

    /**
     * 本管此刻可用的全部路：机械运输者 × 它的喂弹线，加人力路（本管的 roster × 自己发现到的架）。<br>
     * 现算，同一对对象只出一条；结构变化后立即生效，没有缓存也没有跨 tick 状态。
     * <p><b>调用线程：</b>主线程（服务端路由、客户端解析与 HUD）。
     */
    @Override
    public List<AmmoPath> getRoutes() {
        Set<AmmoPath> routes = new LinkedHashSet<>();
        for (IAmmoTransporter transporter : transporters) {
            for (IAmmoSupplier provider : transporter.getFeedSources()) {
                if (provider != null) routes.add(new AmmoPath(transporter, provider));
            }
        }
        // 人力路只在本人力流程存在时合成：基准时长为 0 的型号没有"自己用人"这条路的时长可算
        if (loaders.getHumanReloadTimeTicks() > 0) {
            for (IAmmoSupplier provider : providers) {
                routes.add(new AmmoPath(loaders, provider));
            }
        }
        return new ArrayList<>(routes);
    }

    @Override
    @Nullable
    public IAmmoSupplier resolveProvider(AmmoRef ref) {
        if (ref == null || ref.isNone()) return null;
        for (AmmoPath route : getRoutes()) {
            IAmmoSupplier provider = route.provider();
            if (AmmoRef.ofProvider(provider).equals(ref)) return provider;
        }
        return null;
    }

    /**
     * 客户端按同步来的两个引用解析整条路，作为本端选路的收口。<br>
     * 提供者为 {@link AmmoRef#NONE} 表示服务端此刻没有路由；任一侧解析不到（发现结果还没到）就保留上一帧的
     * 整对，不替换、也不因此收尾作业——两个字段不会拼成一条服务端从未指认的路。<br>
     * 两端各自选路（{@code WeaponControllerSubsystem#routeAmmoToLaunchers}），判据与输入相同，所以这一对
     * 通常就是本端刚选出来的那一条，落下来是一次空操作（{@link #setCurrentRoute} 比对相等即返回）。真不一
     * 致时以权威为准：预测的弹种错了比丢一拍进度更糟，这一次换路会按身份收尾本端的在途作业，随后由作业快照
     * 重新建在权威那一对上。
     */
    private void applySyncedRoute() {
        AmmoRef providerRef = getSynchedData().get(ROUTE_PROVIDER_ID);
        if (providerRef.isNone()) {
            setCurrentRoute(null);
            return;
        }
        IAmmoTransporter transporter = resolveTransporter(getSynchedData().get(ROUTE_TRANSPORTER_ID));
        IAmmoSupplier provider = resolveProvider(providerRef);
        if (transporter == null || provider == null) return;
        setCurrentRoute(new AmmoPath(transporter, provider));
    }

    /**
     * 按身份解析运输者；{@link AmmoRef#NONE} 表示人力运输者——本管自己的 roster，且本型号必须有完整人力
     * 流程，否则那条同步值没有对应的对象。
     */
    @Nullable
    private IAmmoTransporter resolveTransporter(AmmoRef ref) {
        if (ref == null || ref.isNone()) {
            return loaders.getHumanReloadTimeTicks() > 0 ? loaders : null;
        }
        for (IAmmoTransporter transporter : transporters) {
            if (AmmoRef.ofTransporter(transporter).equals(ref)) return transporter;
        }
        return null;
    }

    /**
     * 客户端载入播种：本机还空着时（刚载入、发现尚未完成）按同步值里的身份认一次。<br>
     * 少了它，载入后头几个 tick 本管处于"无路由"，膛内那发会在路由里被当作无主而退掉。
     */
    private void resolveSyncedRoute() {
        if (currentRoute != null) return;
        AmmoRef providerRef = getSynchedData().get(ROUTE_PROVIDER_ID);
        if (providerRef.isNone()) return;
        IAmmoTransporter transporter = resolveTransporter(getSynchedData().get(ROUTE_TRANSPORTER_ID));
        IAmmoSupplier provider = resolveProvider(providerRef);
        if (transporter != null && provider != null) setCurrentRoute(new AmmoPath(transporter, provider));
    }

    /**
     * 结构变化的第一阶段：清空两张发现表。<br>
     * 登记表是"当前拓扑能发现到的东西"的缓存，<b>不是</b>历史累积：机器只在自己的
     * {@link AbstractSubsystem#onAttach()} 与结构变化时被查询，已经不再连到本管的不会再来声明，
     * 不清空就会一直留着——那些机器可能已经跟着拆下来的部件换到了别的载具上。那会让两端的集合不一致
     * （服务端逐件组装会留下中间态的残留，客户端只看到终局拓扑），并让已失效的机器继续出现在路由与 HUD 里。
     * <p>
     * 清空写在第一阶段而不是 {@link #onVehicleStructureChanged()}：后者是重新发现的阶段，两者若挤在同一趟
     * 遍历里，本管排在产生查询结果的机器之后就等于把本轮发现结果清掉。
     * <p>
     * <b>调用线程：</b>主线程。
     */
    @Override
    public void onVehicleStructurePreChanged() {
        transporters.clear();
        providers.clear();
    }

    /**
     * 登记一台被发现的运输者。<br>
     * 频道只是登记键：路由不按频道分组，也不把频道写进池条目。同一台重复发现不重复登记。
     * 这里不写指认：走哪条路由服务端的路由决定，登记只负责让本管知道"有哪些运输者存在"。
     */
    @Override
    public void addTransporter(IAmmoTransporter transporter, String channelName) {
        if (!transporters.contains(transporter)) transporters.add(transporter);
    }

    /**
     * 登记一台被发现的提供者。<br>
     * 只有本型号声明了完整人力流程（{@code human_reload_time > 0}）时才调用：库存持有者没有机械段，
     * 它在人力路上充当提供者，没有人工段的型号不能让"取弹不花时间"成立。
     */
    @Override
    public void addProvider(IAmmoSupplier provider, String channelName) {
        if (!providers.contains(provider)) providers.add(provider);
    }

    /**
     * 挂载后与结构变化后重新发现弹药端的两类对象。<br>
     * 需求方发起查询：对 {@code ammo_discovery_targets} 的每个频道做一次只读终端查询，命中的对象按角色登记
     * ——装弹机是运输者，弹药架是提供者。配对在 {@link #getRoutes()} 里按需合成，不在发现阶段固定成表：
     * 同一个集合里各子系统的重建顺序不确定，装弹机的喂弹线可能比本管的发现晚一拍就绪，把配对写死会把空来源
     * 留在表里。
     * <p><b>调用线程：</b>主线程（挂载与结构变化）。
     */
    @Override
    public void onAttach() {
        super.onAttach();
        discoverRoutes();
        loaders.discover(getHumanReloadTimeTicks());
    }

    @Override
    public void onVehicleStructureChanged() {
        super.onVehicleStructureChanged();
        discoverRoutes();
        loaders.discover(getHumanReloadTimeTicks());
    }

    /**
     * 按 {@code ammo_discovery_targets} 做只读发现并登记；结果已在结构变化的第一阶段清空。<br>
     * 装弹机与弹药架是两类对象，登记后由 {@link #getRoutes()} 合成路：机械运输者配它的喂弹线，人力运输者
     * 配本管自己发现到的架。
     */
    private void discoverRoutes() {
        for (String channel : attr.ammoDiscoveryTargets.keySet()) {
            for (Map.Entry<String, List<ISignalReceiver>> arrival : resolveTargets(channel).entrySet()) {
                for (ISignalReceiver terminal : arrival.getValue()) {
                    if (terminal == this) continue;
                    if (!(terminal instanceof AbstractSubsystem sub)) continue;
                    if (sub.getOwner().getSubPart().getPart().assembly
                            != getOwner().getSubPart().getPart().assembly) {
                        continue;
                    }
                    if (terminal instanceof IAmmoTransporter transporter) {
                        addTransporter(transporter, arrival.getKey());
                    } else if (terminal instanceof IAmmoSupplier provider && getHumanReloadTimeTicks() > 0) {
                        addProvider(provider, arrival.getKey());
                    }
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
     * 退出的一发包装成单发交付，先交给当前路的提供者，再按 {@link #getRoutes()} 询问本装配体的其它提供者；
     * 收不收由提供者自己判。两级都不收时丢弃并记一条警告——掉落物品的落地位置与物品映射尚未实现。
     *
     * @return 退出的弹药类型，空膛返回 null
     */
    @Nullable
    public ProjectileType ejectRound() {
        BallisticProjectileType round = chamberedType;
        if (round == null) return null;
        setChambered(null);

        // 单发交付：退膛只涉及膛内这一发，与已装 Belt 无关
        if (round.getRegistryKey() != null) {
            AmmoBelt single = new AmmoBelt(null, "",
                    List.of(new AmmoBelt.PatternEntry(round.getRegistryKey(), 1)), 1, 1, null);
            IAmmoSupplier preferred = currentRoute == null ? null : currentRoute.provider();
            if (!returnBeltToProviders(single, preferred)) {
                MachineMax.LOGGER.warn("Launcher {} 退膛的一发没有任何弹药架接收，按规则丢弃 {}", name, single);
            }
        }
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
     * 两端都先按身份收尾作业：摧毁是本管作业的终态，之后不再 tick，留着那一份会让本端的执行资源与预留
     * 永远占住（服务端的结束结果与会话无关，客户端的预测占用更是只有本端知道）。客户端的收尾只动本端表，
     * 起爆仍然只在服务端发生。
     * <p>
     * <b>调用线程：</b>主线程。
     */
    @Override
    public void onDestroyed() {
        super.onDestroyed();
        // 摧毁即收尾：按身份释放机构占用并丢弃进行中的作业，客户端随之清空进度显示（库存未扣，不退还任何东西）
        finishReloadJob();
        if (getLevel().isClientSide()) return;
        detonateStoredAmmo();
    }

    /**
     * 本管库存殉爆：按膛内那一发与已装 Belt 的实际贡献合成一次普通爆炸。
     * <p>
     * 合成范围是本管持有的全部弹药——膛内至多一发，已装 Belt 按余量逐弹种计入；两者都已经从供给者的
     * 弹仓扣掉（{@link IAmmoSupplier#deliverReserved}），所以炮闩殉爆与装弹机库存殉爆不会把同一份
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
     * 插桩用的身份：宿主零件名 ＋ 本子系统名。<br>
     * 两端同名、且不随会话分配的 int ID 变化，因此可直接用来对齐客户端与服务端的两份记录。
     */
    private String traceId() {
        SubPart host = getSubPart();
        return (host == null ? "?" : host.name) + "/" + name;
    }

    /**
     * 主线程 tick：相位推进 + 弹药消费 + 入队 pendingFires + 客户端音效管理。
     * <p>
     * <b>调用线程：</b>主线程（20tps）。
     * <p>
     * 射速调度只用实际执行的业务 tick 计数：每 tick 记 {@link FirePhase#TICK_UNITS} 个相位单位，
     * 单发周期由炮闩射速量化成整数相位。相位与冷却都是有界相对量，不含绝对时刻，因此暂停、
     * 客户端晚创建与两端 {@code tickCount} 数值不同都不需要时钟原点换算。
     */
    @Override
    public void onTick() {
        super.onTick();
        // 指认的种子：本机字段还空着时（刚载入、发现尚未完成）按同步值里的身份认一次。
        // 少了它，载入后头几个 tick 本管处于"无供给者"，膛内那发会在路由里被当作无主而退掉。
        if (currentRoute == null) resolveSyncedRoute();

        // 两端都推进本管的弹药内容：首次装填、整份换装、从已装 Belt 补膛、作业推进与射击扣弹各跑一份，
        // 客户端那一份是本地预测。库存、预留、占用、投射物与真实能源的权威结果仍只由服务端产生。
        if (isActive() && !isDestroyed()) {
            tickBeltRecharge();
            tickReload();
        }
        if (isActive() && !isDestroyed() && !isSwapping()) refillChamberFromBelt();

        // 原始扳机事实：本端武器可用、未换装锁定且确有开火指令。它与"处于一次连射中"是两件事，
        // 因此不共用一个标记——前者每轮重算，后者只在相位推进里播种与清除。
        boolean triggerHeld = isActive() && !isDestroyed() && !isSwapping() && getActiveFireCommand() != null;
        if (!triggerHeld) {
            ceaseBurst(ceaseReason());
            return;
        }
        advanceFirePhase();
    }

    /** 本轮停火的原因，只用于插桩。 */
    private String ceaseReason() {
        if (!isActive() || isDestroyed()) return "inactive";
        if (isSwapping()) return "swapping";
        return "command-free";
    }

    /**
     * 结束本次连射：相位归零、连射标记清除，防连点冷却按本 tick 递减一档。
     *
     * <p>冷却保留意味着"上一发刚过去"，因此快速连按不会把低射速武器的间隔重置成一个完整新间隔。
     * 停火音效只在确实有过连射、或还有未收尾的循环实例时处理一次：子系统被摧毁、宿主零件被摧毁
     * 或装配度跌破阈值时本方法每 tick 都会命中，无条件调用会把停火尾音反复播放。</p>
     */
    private void ceaseBurst(String reason) {
        if (firePhase.isBurstActive() || hasLiveFireSound()) {
            if (firePhase.isBurstActive()) {
                WeaponTrace.log(getLevel(), "fire-end launcher={} reason={}", traceId(), reason);
            }
            handleCeaseFire();
        }
        firePhase.stop();
    }

    /** 是否还有本次连射未收尾的射击音效；服务端的三个标记恒为空，因此只对客户端有意义。 */
    private boolean hasLiveFireSound() {
        return currentAutoFireUuid != null || currentShellSoundUuid != null || roundsFiredThisBurst > 0;
    }

    /**
     * 按相位打出本轮的发射预算。
     *
     * <p>首次按下时相位播种为 {@code intervalU - cooldownU}：刚好到期就能发射，不需要额外等待
     * 一个完整间隔；播种值经过夹紧，换型号后冷却大于新周期也不会得到负相位。已经到发射点却
     * 确实缺弹时由 {@link FirePhase} 把逾期量收敛为"当前已就绪"，不蓄积历史欠发，也不清零相位。</p>
     */
    private void advanceFirePhase() {
        long intervalU = FirePhase.intervalUFor(attr.staticAttribute.getBreech().fireRate());
        boolean burstJustStarted = !firePhase.isBurstActive();
        if (burstJustStarted && WeaponTrace.isEnabled()) {
            ProjectileType startingAmmo = getCurrentAmmoType();
            WeaponTrace.log(getLevel(), "fire-begin launcher={} rpm={} intervalU={} ammo={}", traceId(),
                    attr.staticAttribute.getBreech().fireRate(), intervalU,
                    startingAmmo == null ? "-" : startingAmmo.getRegistryKey());
        }

        // 本地快照：缓存本 tick 发射的弹药类型，供客户端特效处理使用。
        // 不直接遍历 pendingFires（物理线程可能同时 poll 消费），避免并发遍历问题。
        List<ProjectileType> firedTypes = new ArrayList<>(1);
        int firedCount = firePhase.tick(intervalU, true, () -> {
            BallisticProjectileType round = takeRoundForShot();
            if (round == null) return false; // 没有可发射的内容，等待下一 tick

            // 首段地形预约：必须先于入队。入队后物理线程在过载时可能在同一个游戏 tick 内就
            // poll 到这一发（它的物理步与我们交错），预约再登记就落在开火之后了。
            // 本行在主线程，登记经即时队列由本 tick 的 requestStep 消费。
            if (!getLevel().isClientSide()) {
                seedTrajectoryTerrain(round);
            }
            pendingFires.add(round);
            firedTypes.add(round);

            // 打出一发后立刻从已装 Belt 补膛：连射循环不必等下一 tick 才补弹。
            // 两端都补——客户端不补就会拿着同一发一直打，射击表现与剩余发数脱节
            refillChamberFromBelt();
            return true;
        });

        if (firedCount > 0 && WeaponTrace.isEnabled()) {
            // ammo 只记第一发：一个业务 tick 打出多发时两端的发数与相位已经对齐，弹种看第一发即可
            WeaponTrace.log(getLevel(), "fire-shot launcher={} n={} ammo={} intervalU={} phaseU={} cooldownU={}",
                    traceId(), firedCount, firedTypes.getFirst().getRegistryKey(),
                    intervalU, firePhase.phaseU(), firePhase.cooldownU());
        }

        // 客户端音效特效处理（主线程，使用本地快照避免与物理线程并发访问 pendingFires）
        if (getLevel().isClientSide() && firedCount > 0) {
            if (WeaponTrace.isEnabled()) {
                WeaponTrace.log(getLevel(), "fx-fire launcher={} n={} ammo={} muzzle={}", traceId(), firedCount,
                        firedTypes.get(0).getRegistryKey(), getMuzzleWorldPosition());
            }
            handleFiringSounds(firedTypes, burstJustStarted);
            for (ProjectileType type : firedTypes) {
                type.playFireEffect(getLevel(), this, attr.locator);
            }
            playFireAnimation();
            playMuzzleFlash(firedTypes.get(0));
        }

        if (firePhase.starved() && hasLiveFireSound()) {
            // 到了发射点却没有可发射内容：收掉持续音效，但不动相位与扳机事实，
            // 补上弹的下一步按当时的预算继续（不补发历史欠发）。
            WeaponTrace.log(getLevel(), "fire-end launcher={} reason=no-round", traceId());
            handleCeaseFire();
        }
    }

    /**
     * 本 tick 是否还需要向供给者拉一份。<br>
     * 单发装填看膛室，整份装填看还有没有能打的那一份：没有一份，或者那一份已经打空，都要换新的。
     * 可回充的一份打空后同样要换——换一份付的是运输者那一段（秒级），而等它自己按回充间隔把一整条
     * 补回来要按发数乘间隔，长得多。回充因此只承担两件事：停火间隙把打掉的位置补齐，以及换不到新的
     * 一份时（提供者没有可分配库存）继续供弹。
     */
    private boolean needsBelt() {
        if (singleRoundLoading()) return chamberedType == null;
        AmmoBelt belt = loadedBelt;
        return belt == null || belt.remaining() <= 0;
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
     * 最近一条结束结果的轮次（同步值）；{@code 0} 表示本端还没有结束过作业。<br>
     * 与 {@link #lastEndWasDelivered()} 成对，是同一条结果的身份与结论：取消、来源失效、暂停超时与
     * 成功交付都写进同一份结果列表，客户端据此结束轮次匹配的本地作业，核对与显示据此区分"装填完成"
     * 与"中止"。按现场核对需要，读的是列表最后一条。
     */
    public int lastEndedRound() {
        return getSynchedData().get(JOB_RESULTS_ID).lastRound();
    }

    /** 最近一条结束是否已交付，描述的就是 {@link #lastEndedRound()} 那一条。 */
    public boolean lastEndWasDelivered() {
        return getSynchedData().get(JOB_RESULTS_ID).lastDelivered();
    }

    /**
     * 本管下发的动态样本：客户端按里面的进度基线建镜像作业，再各自往上面累计（同步值，空 = 没有作业）。<br>
     * 基线是本管 tick 末尾的进度，见 {@link #settleProgressBaseline()}；镜像作业晚一拍起算就会一直晚一拍，
     * 因此它就是"两端装填节奏是否对齐"的现场可核对值。
     */
    public Optional<ReloadJobProgress> getReloadProgressSample() {
        return getSynchedData().get(JOB_PROGRESS_ID);
    }

    /**
     * 这条路上的作业还要多久交付，是控制器比较「保持当前路还是换路」的唯一输入。<br>
     * 只剩一次转发：各类加成（人工段的岗位倍率与个人倍率、机械段的基准时长）都由运输者折算，接收者与武器
     * 控制器都只读这一个值。
     *
     * @param route 候选或当前指认的路，可为 null（一律该换）
     * @return 还要多少 tick；{@link Integer#MAX_VALUE} 表示此刻给不出
     */
    public int ticksToNextRound(@Nullable AmmoPath route) {
        if (route == null) return Integer.MAX_VALUE;
        return route.transporter().estimateTicks(this, route.provider());
    }

    /**
     * 受理一条新作业。<br>
     * 先向运输者取本阶段的执行资源，再向提供者取整次作业的弹药预留：两者来自两个对象，取得顺序固定，
     * 任一步失败就撤销先取得的那一份（{@link IAmmoTransporter#releaseClaim}），因此不建作业、不开始计时。
     * 有人工段时先认领乘员、只取预留；人工段结束时才补机械槽位（{@link #finishHumanStage}）。
     *
     * @param route 当前路
     * @return 新建的作业；本 tick 接不了单时返回 null
     */
    @Nullable
    private ReloadJob acceptNewJob(AmmoPath route) {
        // 身份由结构给出：这一根管 + 这一条路。轮次只在受理成功后推进：等待期间反复尝试不消耗轮次
        IAmmoTransporter transporter = route.transporter();
        IAmmoSupplier provider = route.provider();
        ReloadJobKey key = new ReloadJobKey(getSubPart().getId(), getSubPart().subsystemIndex(this),
                AmmoRef.ofTransporter(transporter), AmmoRef.ofProvider(provider));
        int humanTicks = transporter.getHumanReloadTimeTicks();
        if (!transporter.claim(this, key)) return null; // 没有执行者或槽位：不占弹也不开工
        IAmmoSupplier.Grant grant = provider.reserve(key);
        if (grant == null) {
            transporter.releaseClaim(this, key);
            return null;
        }
        int round = roundsAccepted + 1;
        ReloadJob job;
        if (humanTicks > 0) {
            double rate = transporter.getOperatorTimeScale(this);
            job = new ReloadJob(key, round, grant.spec(), grant.reservedCount(), ReloadJob.Stage.HUMAN,
                    humanStageTicks(humanTicks, rate), rate);
        } else {
            job = new ReloadJob(key, round, grant.spec(), grant.reservedCount(), transporter.getReloadTimeTicks());
        }
        roundsAccepted = round;
        releaseSpentBelt(provider);
        beginReloadJob(job);
        WeaponTrace.log(getLevel(), "reload-start launcher={} round={} stage={} stageTicks={} rate={} reserved={} transporter={} provider={}",
                traceId(), job.round(), job.stage(), job.totalTicks(), job.humanRate(), job.reservedCount(),
                WeaponTrace.ref(job.transporter()), WeaponTrace.ref(job.provider()));
        return job;
    }

    /**
     * 把打空的那一份在开工这一刻退回去，本管随之空手等交付。<br>
     * 换新一旦受理，这一份就不再是本管的弹药：留在原位只会让自身回充继续往里补，补出来的那一发又会让
     * {@link #needsBelt()} 判成"还有能打的那一份"，作业被"不再需要这一份"收尾，下一秒再打空、再受理——两份在
     * 同一格里来回。退掉之后 {@link #needsBelt()} 在整个装填期间恒为真，作业不会被自己补出来的一发撤掉。<br>
     * 空的一份同样走退还，不走丢弃：它的去处与有弹的那一份完全一样（见 {@link #returnBeltToProviders}），
     * 提供者按余量记账，余量为 0 时收下并不改库存，但这份弹药是回到了架上而不是被本管丢掉。只有所有提供者
     * 都收不下（都已不可储存）时才记警告丢弃。<br>
     * 换不到新的一份时不会走到这里——受理失败，那一份留在原位继续按回充供弹。
     *
     * @param preferred 本次受理的提供者，先问这一台
     */
    private void releaseSpentBelt(IAmmoSupplier preferred) {
        AmmoBelt spent = loadedBelt;
        if (spent == null || spent.remaining() > 0) return;
        WeaponTrace.log(getLevel(), "belt-release launcher={} spent={}/{}", traceId(), spent.remaining(), spent.length());
        setLoadedBelt(null);
        if (!returnBeltToProviders(spent, preferred)) {
            MachineMax.LOGGER.warn("Launcher {} 打空的一份没有任何弹药架接收，按规则丢弃 {}", name, spent);
        }
    }

    /**
     * 本型号完整人力流程的基准时长：{@code human_reload_time} 秒换算成 tick；0 表示没有人力路。<br>
     * 这是标准人力下的时长，岗位倍率与个人倍率由 {@link #humanStageTicks(int, double)} 乘上去。它也决定
     * 本管的 roster 是否作为人力运输者进入路由。
     */
    public int getHumanReloadTimeTicks() {
        float seconds = attr.staticAttribute.getHumanReloadTime();
        if (seconds <= 0f) return 0;
        return Math.max(1, (int) (seconds * 20f));
    }

    /** 人工段实际时长：标准人力 tick 乘上岗位倍率与个人倍率。 */
    private static int humanStageTicks(int standardTicks, double rate) {
        double safe = rate > 0 && Double.isFinite(rate) ? rate : 1.0;
        return Math.max(1, (int) Math.round(standardTicks * safe));
    }

    /**
     * 受理后落一条新作业：写入字段并把两支样本推给客户端。<br>
     * 调用方必须先收尾旧作业——作业字段因此只有这一处被写成新值，{@link #finishReloadJob(boolean)}
     * 是唯一把它清空的入口，两者合起来就是"本地作业只经收尾消失"。
     */
    private void beginReloadJob(ReloadJob job) {
        reloadJob = job;
        pushReloadJob();
    }

    /**
     * 统一收尾：先按作业身份释放运输者的执行资源与提供者的预留，再发布结束结果并清空作业。<br>
     * 成功交付、取消、换路、摧毁、路径失效、暂停超时、换作业与清空本地镜像都走这一口，因此不存在
     * "只清进度不释放承诺"的分支；释放只按身份删除记录，不退还库存（库存扣减只发生在交付那一刻）。
     * 作业为空时是无操作，重复收尾因此安全。<br>
     * 释放按<b>作业冻结的那一对身份</b>解析，不读当前指认：客户端的指认与作业分两批到达，作业存续而指认
     * 还没落地（或已被换掉）是正常情况，按指认释放会漏掉真正持有占用的那一台，那份占用之后无人回收。
     * 解析不到时（结构变化已把那一台移出发现结果）留给运输者的失效清理，那一处会按身份代撤销。<br>
     * 结束结果带原轮次与已交付／未交付结论，客户端据它结束轮次匹配的本地作业；结论分开记是
     * 因为"作业字段被清 0"不足以说明这一份到底交成了没有。
     * <p><b>调用线程：</b>主线程（客户端也会走到这里，那一侧不发布结果——结果只由服务端写）。
     */
    private void finishReloadJob(boolean delivered) {
        ReloadJob job = reloadJob;
        if (job == null) return;
        WeaponTrace.log(getLevel(), "reload-end launcher={} round={} delivered={} stage={} progress={}/{}",
                traceId(), job.round(), delivered, job.stage(), job.elapsedTicks(), job.totalTicks());
        AmmoPath route = currentRoute;
        IAmmoTransporter transporter = route != null && AmmoRef.ofTransporter(route.transporter()).equals(job.transporter())
                ? route.transporter()
                : resolveTransporter(job.transporter());
        if (transporter != null) transporter.releaseClaim(this, job.key());
        // 撤销归接收者：只对作业指名的那台提供者按身份撤销；解析不到时同上，留给运输者的失效清理，
        // 避免预留永久占住库存
        IAmmoSupplier provider = route != null && AmmoRef.ofProvider(route.provider()).equals(job.provider())
                ? route.provider()
                : resolveProvider(job.provider());
        if (provider != null) provider.releaseReservation(job.key());
        reloadJob = null;
        if (!getLevel().isClientSide()) {
            appendJobResult(job.round(), delivered);
        }
        pushReloadJob();
    }

    /**
     * 把一条终态追加到结果列表并下发。<br>
     * 一轮可以结束不止一条作业，所以这里是追加而不是覆盖；列表有界（{@link #MAX_RECENT_RESULTS}），
     * 被裁掉的一定是客户端早已按轮次水位确认过的旧条目。
     */
    private void appendJobResult(int round, boolean delivered) {
        recentResults.addLast(new ReloadJobResults.Entry(round, delivered));
        while (recentResults.size() > MAX_RECENT_RESULTS) {
            recentResults.removeFirst();
        }
        getSynchedData().set(JOB_RESULTS_ID, new ReloadJobResults(List.copyOf(recentResults)));
    }

    /** 按未交付收尾：取消、换人、摧毁、来源失效、暂停超时都归这一种。 */
    private void finishReloadJob() {
        finishReloadJob(false);
    }

    /**
     * 把当前作业的冻结分配与动态样本一起推给客户端。<br>
     * 只在作业开始与收尾时调用：受理定下分配，收尾清空两条样本。之后只有进度会变，那些时刻走
     * {@link #pushReloadJobProgress()}，不必重发白字节级的交付规格。两条样本写的是"写的那一刻"的值，
     * 有作业时由 tick 收尾把进度基线定稿成末尾值（见 {@link #settleProgressBaseline()}）。<br>
     * 结束结果（{@link #JOB_RESULTS_ID}）不在这里写：它只描述
     * 已经结束的那一条作业，新作业开始时不覆盖它。
     */
    private void pushReloadJob() {
        if (getLevel().isClientSide()) return;
        ReloadJob job = reloadJob;
        getSynchedData().set(JOB_SPEC_ID, job == null ? Optional.empty() : Optional.of(ReloadJobSpec.of(job)));
        getSynchedData().set(JOB_PROGRESS_ID,
                job == null ? Optional.empty() : Optional.of(ReloadJobProgress.of(job)));
        // 有作业就等 tick 收尾把基线定稿成末尾值；清空那一次没有基线可定
        progressDraft = job != null;
    }

    /**
     * 只推动态样本：换段、换人重定时这类"分配没变、进度坐标变了"的时刻用这条。<br>
     * 写的是"新的本阶段时长 + 新的进度基线"，客户端因此从新基线继续跑，比例不倒退。作业已经收尾时
     * 是无操作——收尾由 {@link #pushReloadJob()} 清空两条样本。基线在这里是 tick 中途的值，随后由
     * {@link #settleProgressBaseline()} 定稿成末尾值。
     */
    private void pushReloadJobProgress() {
        if (getLevel().isClientSide()) return;
        ReloadJob job = reloadJob;
        if (job == null) return;
        getSynchedData().set(JOB_PROGRESS_ID, Optional.of(ReloadJobProgress.of(job)));
        progressDraft = true;
    }

    /**
     * 推进本管的装填作业，两端走同一套业务流程。<br>
     * 首次装填与整份换装走同一条作业：目标都是当前路的一份交付。开工前先向运输者取执行资源、再向提供者取
     * 预留；接不了单（运输者不可工作、没有可分配库存、没有人、独占槽位被别的作业占着）就不建作业、不开始
     * 计时，下个 tick 再试，因此等待中的管既不占槽也不占预留。
     * <p>
     * 有人工段的作业先走人工段（{@link #tickHumanStage}）：人工段完成才申请机械槽位，进入机械段后按运输者
     * 给的推进量走完、兑现预留、交给装填或换装的分支。人力运输者没有机械段，人工段走完即交付。
     * <p>
     * 作业的两个身份不会在作业存续期间漂移：指认只经 {@link #setCurrentRoute} 变更，而它在换路前就收尾了
     * 旧作业；提供者只由受理时授予，换架要重建作业。
     * <p>
     * <b>暂停与超时：</b>受理成立后本 tick 的推进量可能为 0（运输者受阻、乘员离场且无人替补、人工段结束却
     * 拿不到机械槽位），承诺仍在，因此不立刻收尾而是等它恢复；连续推不动 {@link #PAUSE_TIMEOUT_TICKS} 个
     * tick 才按未交付终止并释放全部资源（乘员、机械槽位与架预留），与显式取消共用同一条结束结果。这个上限
     * 只作用于"有作业、有承诺"的暂停；还没受理的等待没有作业，也就不计时。承诺整体消失（运输者的占用被按
     * 身份摘掉、提供者的预留被撤销）时立刻收尾，不等这个上限。
     * <p>
     * <b>端别：</b>只有"不再需要这一份"与"路已不可达"两处收尾分端——服务端立刻按身份收尾，客户端的作业是
     * 权威镜像，只按本地交付或权威结束结果结束，避免因为一时解析不到的路或一时读不到的库存把作业清掉。
     * <p><b>调用线程：</b>主线程（两端）。
     */
    private void tickReload() {
        AmmoPath route = reloadRoute();
        if (route == null) {
            // 服务端：没有可用的路就不该留着作业；客户端：作业上那一对可能还没解析出来，等它
            if (!getLevel().isClientSide()) finishReloadJob();
            tickReloadSounds();
            return;
        }
        if (!getLevel().isClientSide() && !isRouteValid(route)) {
            // 结构变化后旧路径已不可达（拆件、跨载具转移）：先按身份收尾再丢指认，
            // 不能带着已经不在本装配体内的占用继续推进
            setCurrentRoute(null);
            finishReloadJob();
            tickReloadSounds();
            return;
        }
        boolean swapping = pendingSwapTarget() != null;
        boolean want = route.transporter().isOperational()
                && route.provider().isSupplyOperational()
                && (swapping || needsBelt());
        if (!want) {
            if (!getLevel().isClientSide()) finishReloadJob();
            tickReloadSounds();
            return;
        }

        ReloadJob job = reloadJob;
        if (job == null) {
            job = acceptNewJob(route);
            if (job == null) {
                tickReloadSounds();
                return; // 未受理：不入占用、不占预留、不开始计时
            }
        }
        // 承诺整体消失：运输者的执行资源被摘掉，或提供者的那份预留被撤销
        if (!route.transporter().holdsCommitment(this)
                || !route.provider().holdsReservation(job.key(), job.reservedCount())) {
            finishReloadJob();
            tickReloadSounds();
            return;
        }
        if (job.stage() == ReloadJob.Stage.HUMAN) {
            tickHumanStage(route, job);
            tickReloadSounds();
            return;
        }
        int advance = route.transporter().getAdvanceTicks(this);
        job.advance(advance);
        if (advance <= 0 && job.pausedTicks() == 1) {
            WeaponTrace.log(getLevel(), "reload-stall launcher={} round={} stage={}", traceId(), job.round(), job.stage());
        }
        if (job.pausedTicks() >= PAUSE_TIMEOUT_TICKS) {
            finishReloadJob();
            tickReloadSounds();
            return;
        }
        tickReloadSounds();
        if (!job.isComplete()) return;
        deliver(route, job);
    }

    /**
     * 本轮要推进的路：优先当前指认；客户端在指认还没落地时用作业上冻结的那一对。
     *
     * <p>服务端不这样兜底：那边指认为空就是"没有路"，作业该收尾；客户端的作业是权威镜像，
     * 路与作业分两批到达是正常情况，不能因此把作业清掉。</p>
     */
    @Nullable
    private AmmoPath reloadRoute() {
        if (currentRoute != null) return currentRoute;
        if (!getLevel().isClientSide()) return null;
        ReloadJob job = reloadJob;
        return job == null ? null : deliveryRoute(job);
    }

    /**
     * 推进人工段：有人就往前走，人走了先换人、换不到就暂停，走完后释放人员并申请机械槽位。<br>
     * 换人保留已完成的进度，只按新倍率换算剩余量（{@link ReloadJob#retimeHuman(double)}）；倍率变了就重发
     * 一次时长与基线，客户端的进度条因此不会倒退。离场者的认领由运输者在换人时按身份释放
     * （见 {@link LoaderRoster#claim}），人工段因此不会把人锁在已经下车的座位上。
     *
     * @param route 当前路（人工段的用人方就是它的运输者）
     * @param job   处于人工段的作业
     */
    private void tickHumanStage(AmmoPath route, ReloadJob job) {
        IAmmoTransporter transporter = route.transporter();
        if (!transporter.claim(this, job.key())) {
            pauseReloadJob(job);
            return;
        }
        if (job.retimeHuman(transporter.getOperatorTimeScale(this))) {
            pushReloadJobProgress();
            WeaponTrace.log(getLevel(), "reload-retime launcher={} round={} rate={} stageTicks={}", traceId(),
                    job.round(), job.humanRate(), job.totalTicks());
        }
        if (!job.isComplete()) {
            job.advance(1);
            if (job.pausedTicks() >= PAUSE_TIMEOUT_TICKS) {
                finishReloadJob();
                return;
            }
        }
        if (job.isComplete()) finishHumanStage(route, job);
    }

    /**
     * 人工段完成：人力运输者当场交付；机械运输者先交还乘员、再取得机械槽位，成立了才进入机械段。<br>
     * 槽位一时拿不到时作业停在人工段末端等（原库存预留保持），由暂停上限兜底。
     *
     * @param route 当前路
     * @param job   刚走完人工段的作业
     */
    private void finishHumanStage(AmmoPath route, ReloadJob job) {
        IAmmoTransporter transporter = route.transporter();
        if (transporter.isHuman()) {
            // 人力运输者没有机械段：人工段走完即交付
            deliver(route, job);
            return;
        }
        if (!transporter.claimForMachineStage(this, job.key())) {
            pauseReloadJob(job);
            return;
        }
        job.enterMachineStage(transporter.getReloadTimeTicks());
        pushReloadJobProgress();
        WeaponTrace.log(getLevel(), "reload-stage launcher={} round={} stage=MACHINE stageTicks={}", traceId(),
                job.round(), job.totalTicks());
    }

    /** 本 tick 推不动的作业：记一次暂停，超过上限就按未交付收尾。 */
    private void pauseReloadJob(ReloadJob job) {
        job.advance(0);
        if (job.pausedTicks() == 1) {
            WeaponTrace.log(getLevel(), "reload-pause launcher={} round={} stage={}", traceId(), job.round(), job.stage());
        }
        if (job.pausedTicks() >= PAUSE_TIMEOUT_TICKS) finishReloadJob();
    }

    /**
     * 兑现这条作业的预留，并交给首次装填或整份换装。<br>
     * 兑现不出这一份（预留已不存在）时按未交付收尾，下个 tick 重开作业。<br>
     * 轮次不在这里推进：它按受理计，交付只是这一轮走完了。
     */
    private void deliver(AmmoPath route, ReloadJob job) {
        boolean swapping = pendingSwapTarget() != null;
        IAmmoSupplier provider = route.provider();
        AmmoBelt delivered = provider.deliverReserved(job.key());
        WeaponTrace.log(getLevel(), "reload-deliver launcher={} round={} ok={} rounds={} provider={}", traceId(),
                job.round(), delivered != null, delivered == null ? 0 : delivered.remaining(),
                WeaponTrace.ref(job.provider()));
        if (delivered != null && getLevel().isClientSide()) {
            // 客户端本地交付的收据：权威结果到达时按这个轮次认出"已经交过了"，不再建回来重交一次
            lastLocalDeliveryRound = job.round();
        }
        finishReloadJob(delivered != null);
        if (delivered == null) return;
        if (getLevel().isClientSide()) {
            WeaponTrace.log(getLevel(), "reload-local-deliver launcher={} round={} stage={} rounds={}", traceId(),
                    job.round(), job.stage(), delivered.remaining());
        }
        if (swapping) {
            commitBeltSwap(provider, delivered);
        } else {
            acceptDelivery(provider, delivered);
        }
    }

    /**
     * 这条指认的路是否仍然可达：两张发现表重建后，配对仍能由 {@link #getRoutes()} 合成。<br>
     * 判定读表本身而不是空间距离，因为"可达"由接线与频道决定，不由距离决定。可工作性由调用方另判。
     *
     * @param route 当前指认的路
     * @return true 表示它仍在本管这一轮发现结果里
     */
    private boolean isRouteValid(AmmoPath route) {
        for (AmmoPath known : getRoutes()) {
            if (known.equals(route)) return true;
        }
        return false;
    }

    /**
     * 接受一份交付：按本管的装填模式写入膛室或已装数据。<br>
     * 不通过整份校验的交付原样退回提供者，等下一次作业重来。整份装填时本管通常已经空手——打空的那一份在
     * 开工时就退掉了（见 {@link #releaseSpentBelt}）——这里仍按"挂着一份就先换下"处理：客户端的受理比权威
     * 早一拍，换新在途时权威那份旧样本可能把一份送回来，落地的这一刻不能连它的余量一起丢掉。
     */
    private void acceptDelivery(IAmmoSupplier provider, AmmoBelt pulled) {
        if (!canAcceptBelt(pulled)) {
            MachineMax.LOGGER.warn("Launcher {} 收到不兼容的交付 {}，整份退回并等待路由", name, pulled);
            provider.returnBelt(pulled);
            return;
        }
        if (singleRoundLoading()) {
            ProjectileType round = AmmoBelt.resolve(getLevel(), pulled.nextRoundId());
            if (round instanceof BallisticProjectileType kinetic) setChambered(kinetic);
            return;
        }
        AmmoBelt previous = loadedBelt;
        if (previous != null) {
            WeaponTrace.log(getLevel(), "belt-replace launcher={} old={}/{} new={}/{}", traceId(),
                    previous.remaining(), previous.length(), pulled.remaining(), pulled.length());
            if (!returnBeltToProviders(previous, provider)) {
                MachineMax.LOGGER.warn("Launcher {} 换下的一份没有任何弹药架接收，按规则丢弃 {}", name, previous);
            }
        }
        setLoadedBelt(pulled);
    }

    /**
     * 作业上指名的那条路：两个身份都解析成功才算数。<br>
     * 作业是权威——同步先后顺序让指认与作业短暂不一致时，仍然按作业上那一对推进与交付；机械路的提供者不在
     * 本管的发现表里（它由装填机自己发现），因此必须经 {@link #resolveProvider} 合成。
     */
    @Nullable
    private AmmoPath deliveryRoute(ReloadJob job) {
        IAmmoTransporter transporter = resolveTransporter(job.transporter());
        IAmmoSupplier provider = resolveProvider(job.provider());
        return transporter == null || provider == null ? null : new AmmoPath(transporter, provider);
    }

    /**
     * 客户端把当前作业进度交给音效持有者：机械段的声音定位在运输者上，人工段的声音定位在装填动作发生的
     * 地方。<br>
     * 机械运输者（含它的人工准备段）按自己的游标播；人力运输者没有对象可定位，人工段由本管按自己的音效表播。
     * 已播游标归各自的持有者，本管只提供进度。
     */
    private void tickReloadSounds() {
        ReloadJob job = reloadJob;
        if (job == null) return;
        AmmoPath route = deliveryRoute(job);
        // 作业上那一对还没解析出来（同步先后顺序）时不播：声音定位在实际运输的那台机器上
        if (route == null) return;
        if (route.transporter().isHuman()) {
            tickOwnHumanSounds(job);
            return;
        }
        route.transporter().tickStageSounds(this, job.progress());
    }

    /**
     * 播本管负责的人工段进度音效。<br>
     * 阈值 0 是段开始音、1 是本段走完的音，两者都不进越过循环；一次推进跨过多个阈值时按升序逐个播放。
     * 只有从段首观察到的作业才播开始音，完整补推建立的基线不补播历史标记；进度回退只把游标拉回去。
     *
     * @param job 处于人工段的本地作业
     */
    private void tickOwnHumanSounds(ReloadJob job) {
        Map<String, SoundEvent> sounds = attr.staticAttribute.getHumanProgressSounds();
        if (sounds.isEmpty()) return;
        if (humanSoundRound != job.round()) {
            humanSoundRound = job.round();
            humanSoundProgress = -1f;
        }
        float progress = Math.clamp(job.progress(), 0f, 1f);
        if (humanSoundProgress < 0f) {
            if (job.elapsedTicks() <= 1) playOwnHumanMarker(sounds, 0f);
            humanSoundProgress = 0f;
        }
        if (progress < humanSoundProgress) {
            // 作业重开或校准把进度往回拉：游标跟回去，已经播过的不重播
            humanSoundProgress = progress;
            return;
        }
        for (SoundEvent sound : ReloadSoundMarkers.crossed(sounds, humanSoundProgress, progress)) {
            playSpreadingSound(sound, SoundSource.NEUTRAL);
        }
        humanSoundProgress = progress;
    }

    /** 播放本管人工段在某个固定阈值上的音效（0 = 段开始）；没有这一档时什么都不做。 */
    private void playOwnHumanMarker(Map<String, SoundEvent> sounds, float threshold) {
        SoundEvent sound = ReloadSoundMarkers.at(sounds, threshold);
        if (sound != null) playSpreadingSound(sound, SoundSource.NEUTRAL);
    }

    /**
     * 从已装 Belt 取一发进膛。<br>
     * 普通 Belt 每发都经过膛室：先写 {@link #chamberedType}，射击时消费膛内那一发。
     * 可回充 Belt 不走这一步——它的额度在成功射击时直接消费，避免预取后回充多出一发。
     * 余量耗尽的普通 Belt 直接清除已装数据。可回充的一份打空后仍留在原位，等它按回充补出下一发——
     * 与此同时 {@link #needsBelt()} 已经在为它申请新的一份，受理的那一刻由 {@link #releaseSpentBelt}
     * 把它退回提供者，因此这条回充只是停火间隙的补齐，不是换新的替代。
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
     * 换装是否进行中：当前路的提供者要交付的规格与本机已装内容不是同一份。<br>
     * 换装没有自己的时长与进度——它与首次装填共用同一条 {@link ReloadJob}，按运输者那一段计时，
     * 本管只在它备好时取走那一份、把旧的一份交给当前合法且接受它的提供者。进行中时射击锁定，
     * 已装内容与膛内弹都留在原位。
     */
    public boolean isSwapping() {
        return pendingSwapTarget() != null;
    }

    /**
     * 在途换装的交付来源。<br>
     * 返回 null 表示没有在途换装：没有路由、没有已装数据（单发装填的管始终如此），
     * 或者提供者要给的规格与已装内容已经一致。
     */
    @Nullable
    private IAmmoSupplier pendingSwapTarget() {
        AmmoPath route = currentRoute;
        AmmoBelt loaded = loadedBelt;
        if (route == null || loaded == null) return null;
        IAmmoSupplier provider = route.provider();
        AmmoBelt spec = provider.getDeliverySpec();
        if (spec == null || AmmoBelt.compareSpec(loaded, spec) == 0) return null;
        return provider;
    }

    /**
     * 在主线程验证并提交整份交换。<br>
     * 新的一份不通过校验时原样退回、整笔放弃。旧的一份交给当前路的提供者；它拒收时按 {@link #getRoutes()}
     * 询问本装配体里其它提供者。都拒收时丢弃并记一条警告，不做"原路退回"的强制要求（来源关系不持久化，
     * 重新拾取到哪台由机器自己的收弹规则决定）。
     *
     * @param target   本次换装的提供者，由 {@link #pendingSwapTarget()} 给出
     * @param incoming 已从该来源取走的一份
     */
    private void commitBeltSwap(IAmmoSupplier target, AmmoBelt incoming) {
        if (!canAcceptBelt(incoming)) {
            target.returnBelt(incoming);
            abortBeltSwap("换入的一份不通过校验");
            return;
        }
        AmmoBelt previous = loadedBelt;
        if (previous != null && !returnBeltToProviders(previous, target)) {
            MachineMax.LOGGER.warn("Launcher {} 换装时旧的一份没有任何弹药架接收，按规则丢弃 {}", name, previous);
        }
        setLoadedBelt(incoming);
        refillChamberFromBelt();
    }

    /**
     * 把一份旧内容交给本装配体里接受它的提供者：先问当前路的那一台，再按 {@link #getRoutes()} 逐台询问，
     * 每台一次。<br>
     * 弹链只有提供者能收，因此去处不经运输者转发；这里没有第三级兜底。收不收由提供者自己判定
     * （弹种、可储存状态与剩余容量），本管只按顺序询问。
     *
     * @param belt      要交还的一份
     * @param preferred 优先询问的提供者，可为 null（没有当前路）
     * @return true 表示已由某台提供者收下
     */
    private boolean returnBeltToProviders(AmmoBelt belt, @Nullable IAmmoSupplier preferred) {
        if (preferred != null && preferred.returnBelt(belt)) return true;
        Set<IAmmoSupplier> asked = new LinkedHashSet<>();
        if (preferred != null) asked.add(preferred);
        for (AmmoPath route : getRoutes()) {
            IAmmoSupplier provider = route.provider();
            if (!asked.add(provider)) continue;
            if (provider.returnBelt(belt)) return true;
        }
        return false;
    }

    private void abortBeltSwap(String reason) {
        MachineMax.LOGGER.warn("Launcher {} 换装中止：{}", name, reason);
        // 作业已随交付取走而清空；下一 tick 重开一份，目标仍是当前路的提供者
    }

    /**
     * 推进已装 Belt 的自身回充，两端都跑。<br>
     * 回充要按发取电，而取电是真实扣费：服务端从能源网抽，客户端不预支能源额度，只在<b>不需要付费</b>时
     * 推进（见 {@link #payRechargeEnergy(float)}）。付费回充在客户端等权威结果——补出一发的那一刻服务端就
     * 推了新余量。成功射击已经通过 {@link AmmoBelt#onShot()} 重置过延迟，这里只按速率补额度。<br>
     * 打空的那一份在受理换新的那一刻就退掉了（见 {@link #releaseSpentBelt}），换新在途时本管的已装一律为空，
     * 因此这里不需要另加"作业在途就不补"的闸：没有一份挂在身上，回充也就无从补起。
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

    /**
     * 回充取电：服务端从能源网真实抽取；客户端只在这次回充不需要付费时才放行。
     *
     * <p>客户端没有能源支出预测，因此不允许"先记成已回充、以后再扣费"——那会让本地余量跑到权威前面，
     * 而且永远无法用权威值收敛。</p>
     */
    private boolean payRechargeEnergy(float amount) {
        if (getLevel().isClientSide()) return amount <= 0f;
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
        WeaponTrace.log(getLevel(), "projectile launcher={} ammo={} n={} speed={} muzzle={}", traceId(),
                type.getRegistryKey(), projectiles.size(), muzzle.speed(), muzzle.position());

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
     * 依次取膛内那一发、已装 Belt 的下一发、当前路提供者即将交付的弹种。
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
        IAmmoSupplier provider = currentRoute == null ? null : currentRoute.provider();
        return provider != null ? provider.getSuppliedType() : null;
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

    /**
     * 本机的查询表：弹药反馈输出、供弹来源与人工段座位。<br>
     * 三者都是只读发现的入口，因此都进首跳表；装填人员频道只用于握手，本机不往座位发业务信号。
     */
    @Override
    public Map<String, List<String>> getTargetNames() {
        Map<String, List<String>> result = new HashMap<>(4);
        result.putAll(attr.ammoCountOutputs);
        result.putAll(attr.ammoDiscoveryTargets);
        result.putAll(attr.loaderDiscoveryTargets);
        return result;
    }
}


