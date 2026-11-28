package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.api.SparkLevel;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.energy.EnergyGrid;
import io.github.sweetzonzi.machine_max.common.mech.energy.IMechPowerProducer;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalBus;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.IPartAssembly;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.network.payload.SubsystemSyncPayload;
import lombok.Getter;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Getter
public class SubsystemController implements ISignalBus {
    public final String name = "subsystemController";
    public final Level level;
    public final ConcurrentMap<String, SignalChannel> channels = new ConcurrentHashMap<>();//可查可改
    public final ConcurrentMap<String, Object> signalStorage = new ConcurrentHashMap<>();//部件内供Molang查询的信号
    public final ConcurrentMap<String, Object> resources = new ConcurrentHashMap<>();//可查可改
    /**
     * 成员快照：按宿主的注册调用序排列，发布后不再修改。<br>
     * 写入只发生在主线程的成员变化点，一次替换整份快照；物理线程 {@link #prePhysicsTick()} /
     * {@link #postPhysicsTick()} 与主线程遍历各自捕获当时那一份，因此不需要 CopyOnWrite 的逐次复制，
     * 也不会看到构建到一半的集合。顺序属于装配状态：成员追加不减既有位置，删除只移除目标本身。
     */
    private volatile Set<AbstractSubsystem> allSubsystems = Set.of();
    public final EnergyGrid energyGrid = new EnergyGrid();
    private static final ThreadLocal<Level> PRE_PHYSICS_LEVEL = new ThreadLocal<>();

    // 总线订阅者集合（广播时遍历此集合，运行时检查接受条件）
    private final Set<ISignalReceiver> busSubscribers = ConcurrentHashMap.newKeySet();

    /**
     * 所属装配体，用于把 {@code global} 事件付给宿主的零件扇出。
     * <p>独立构造的控制器（只有世界、没有宿主）为 {@code null}，此时事件只分发给订阅者。
     */
    @Nullable
    private final IPartAssembly assembly;

    /** 是否已经跑过首次订阅；此前加入的子系统由 {@link #initAllSubsystems()} 统一 attach。 */
    private boolean initialized;

    /** 客户端待应用的权威同步值，业务 tick 收尾统一落地。 */
    private final SyncValueBuffer pendingValues = new SyncValueBuffer();

    public SubsystemController(Level level) {
        this.level = level;
        this.assembly = null;
    }

    public SubsystemController(VehicleCore vehicle) {
        this.level = vehicle.getLevel();
        this.assembly = vehicle;
    }

    /**
     * 当前成员快照：只读、按宿主的注册调用序排列。
     * <p>遍历方拿到的是调用时刻那一份，不会看到发布到一半的集合，也不会因为随后发布的
     * 新快照而在遍历中途改变内容。
     *
     * @return 不可变成员快照
     */
    public Set<AbstractSubsystem> getAllSubsystems() {
        return allSubsystems;
    }

    // ===== 现有生命周期方法（整合总线订阅） =====

    /** 调用所有子系统的 onTick，在主线程 20tps 下执行 */
    public void tick() {
        Set<AbstractSubsystem> snapshot = allSubsystems;
        for (AbstractSubsystem subsystem : snapshot) {
            if (subsystem != null) {
                subsystem.onTick();
            }
        }
    }

    /**
     * 本轮子系统业务 tick 之后的统一落地边界。
     *
     * <p>先把客户端收到的权威值写进各子系统的同步数据，再逐个调用
     * {@link AbstractSubsystem#onPostTick()}。分成两趟而不是在同一次遍历里做，是因为一个子系统的
     * 收尾可能依赖别的子系统本 tick 的结果：客户端把同步到的分配、库存与作业一起落地时，读的
     * 必须是同一轮已推进完的状态。</p>
     *
     * <p>由 {@code VehicleCore#preTick()} 在 {@link #tick()} 之后紧接着调用。它既不是物理线程的
     * {@code postPhysicsTick}，也不在 {@code LevelTickEvent.Post}：粒度是"这台载具的子系统本轮跑完了"，
     * 因此同一个 tick 内到达的同步值只会被应用一次。</p>
     */
    public void postTick() {
        applyPendingValues();
        Set<AbstractSubsystem> snapshot = allSubsystems;
        for (AbstractSubsystem subsystem : snapshot) {
            if (subsystem != null) {
                subsystem.onPostTick();
            }
        }
    }

    /**
     * 客户端：把待应用的权威值按宿主聚合后写入各子系统的同步数据。
     *
     * <p>一个子系统在本边界只 <b>assignValues 一次</b>：合并表里同一字段只留最后的值，因此每轮
     * 收尾看到的是"本边界之后的当前值"，而不是一串中间态。写入即触发各子系统的变化回调，业务收尾
     * 随后读到的就是本轮已落地的权威值。</p>
     *
     * <p>成员缺失的条目整组跳过并记一次日志：序号对不上说明两端结构定义已经不一致，回退不到别的
     * 子系统。</p>
     */
    private void applyPendingValues() {
        if (!level.isClientSide() || pendingValues.isEmpty()) return;
        Map<Integer, Map<Integer, List<SynchedEntityData.DataValue<?>>>> grouped = new LinkedHashMap<>();
        pendingValues.forEach((key, value) -> grouped
                .computeIfAbsent(key.subPartId(), id -> new LinkedHashMap<>())
                .computeIfAbsent(key.subsystemIndex(), index -> new ArrayList<>())
                .add(value));
        pendingValues.clear();
        for (Map.Entry<Integer, Map<Integer, List<SynchedEntityData.DataValue<?>>>> byPart : grouped.entrySet()) {
            DestroyableObject holder = ObjectManager.getDestroyableObject(level, byPart.getKey());
            if (!(holder instanceof SubPart subPart)) {
                MachineMax.LOGGER.warn("丢弃子系统同步条目：维度{}没有子零件 id={}",
                        level.dimension().location(), byPart.getKey());
                continue;
            }
            for (Map.Entry<Integer, List<SynchedEntityData.DataValue<?>>> byIndex : byPart.getValue().entrySet()) {
                AbstractSubsystem subsystem = subPart.subsystemAt(byIndex.getKey());
                if (subsystem == null) {
                    MachineMax.LOGGER.warn("丢弃子系统同步条目：SubPart(id={}) 上没有序号为 {} 的子系统",
                            byPart.getKey(), byIndex.getKey());
                    continue;
                }
                subsystem.getSynchedData().assignValues(byIndex.getValue());
            }
        }
    }

    /**
     * 客户端：收下一轮权威批次，按字段并入待应用值表。
     *
     * <p>同步值是值表，不是事件流：同一字段在一轮里收到多次只有最后那次有意义，所以并入即可，
     * 不需要保存批次顺序。服务端"采样与发送成对发生"、连接内有序，后到的批次一定采样更晚，因此
     * 覆盖总是朝新值方向进行。合并后的条目数上界是本装配体已定义的同步字段总数。</p>
     *
     * <p><b>调用线程：</b>主线程（网络批次处理器）。</p>
     *
     * @param batch 收到的批次
     */
    public void mergeSyncBatch(SubsystemSyncPayload batch) {
        pendingValues.merge(batch.entries());
    }

    /**
     * 服务端：把本轮脏字段打成一份批次发给维度内的玩家。
     *
     * <p>必须在所有子系统的 {@link AbstractSubsystem#onPostTick()} 都跑完之后调用：收尾里产生的
     * 变化也要进入本轮的采样。打包与发送都只在这里发生，不在逐个执行 onPostTick 的同时进行，
     * 因为后面的收尾可能改变同一份资源。</p>
     *
     * <p><b>调用线程：</b>主线程（服务端）。</p>
     *
     * @param assemblyId 本装配体身份，随批次下发供客户端核对归属
     */
    public void flushSync(UUID assemblyId) {
        if (level.isClientSide()) return;
        List<SubsystemSyncPayload.Entry> entries = new ArrayList<>();
        Set<AbstractSubsystem> snapshot = allSubsystems;
        for (AbstractSubsystem subsystem : snapshot) {
            if (subsystem == null) continue;
            List<SynchedEntityData.DataValue<?>> dirty = subsystem.packDirtyValues();
            if (dirty.isEmpty()) continue;
            SubPart host = subsystem.getSubPart();
            entries.add(new SubsystemSyncPayload.Entry(host.getId(), host.subsystemIndex(subsystem), dirty));
        }
        if (entries.isEmpty()) return;
        PacketDistributor.sendToPlayersInDimension((ServerLevel) level,
                new SubsystemSyncPayload(assemblyId, entries));
    }

    /** 物理 tick 前调用所有子系统的 onPrePhysicsTick（物理引擎步进前更新控制量） */    public void prePhysicsTick() {
        Level previous = PRE_PHYSICS_LEVEL.get();
        PRE_PHYSICS_LEVEL.set(level);
        try {
            Set<AbstractSubsystem> snapshot = allSubsystems;
            for (AbstractSubsystem subsystem : snapshot) {
                if (subsystem != null) {
                    subsystem.onPrePhysicsTick();
                }
            }
            energyGrid.prePhysicsTick(getPhysicsTps());
        } finally {
            if (previous == null) PRE_PHYSICS_LEVEL.remove();
            else PRE_PHYSICS_LEVEL.set(previous);
        }
    }

    /** 同步信号回调用此判据确认当前线程正在为该世界执行子系统的求解前更新。 */
    static boolean isPrePhysicsTick(Level level) {
        return PRE_PHYSICS_LEVEL.get() == level;
    }

    /** 物理 tick 后调用所有子系统的 onPostPhysicsTick（物理引擎步进后读取反馈） */
    public void postPhysicsTick() {
        Set<AbstractSubsystem> snapshot = allSubsystems;
        for (AbstractSubsystem subsystem : snapshot) {
            if (subsystem != null) {
                subsystem.onPostPhysicsTick();
            }
        }
    }

    /** 所有部件动画混合完成后调用各子系统，在本物理步求解前应用动画驱动目标。 */
    public void postAnimationPhysicsTick() {
        Set<AbstractSubsystem> snapshot = allSubsystems;
        for (AbstractSubsystem subsystem : snapshot) {
            if (subsystem != null) subsystem.onPostAnimationPhysicsTick();
        }
    }

    /**
     * 初始化子系统，连接机械功传递链路，调用{@link AbstractSubsystem#onAttach}方法并注册总线订阅
     */
    public void initAllSubsystems() {
        rebuildAllEnergyPaths();
        allSubsystems.forEach(this::subscribe);
        allSubsystems.forEach(this::attachSubsystem);
        initialized = true;
    }

    /**
     * 将子系统加入控制器。初始化之后加入的零件在这里立即订阅并 attach；初始化之前的加入由
     * {@link #initAllSubsystems()} 统一处理。
     * <p>
     * 参数顺序即执行序：调用方按零件安装序、零件内按子零件声明序、子系统安装序展开后传入。
     * 已经在集合里的成员保位不动，重复注册不会把既有成员挪到队尾。
     * <p><b>调用线程：</b>主线程。
     */
    public void addSubsystems(Collection<AbstractSubsystem> subSystems) {
        List<AbstractSubsystem> added = new ArrayList<>(subSystems.size());
        Set<AbstractSubsystem> current = allSubsystems;
        LinkedHashSet<AbstractSubsystem> ordered = LinkedHashSet.newLinkedHashSet(current.size() + subSystems.size());
        ordered.addAll(current);
        for (AbstractSubsystem subSystem : subSystems) {
            if (subSystem != null && ordered.add(subSystem)) added.add(subSystem);
        }
        if (added.isEmpty()) return;
        publishMembers(ordered);
        if (!initialized) return;
        for (AbstractSubsystem subSystem : added) {
            subscribe(subSystem);
            attachSubsystem(subSystem);
        }
    }

    /** 主线程：以新成员序一次替换整份快照；发布后的集合只读，遍历方各自持有自己捕获的那一份。 */
    private void publishMembers(Collection<AbstractSubsystem> members) {
        LinkedHashSet<AbstractSubsystem> ordered = LinkedHashSet.newLinkedHashSet(members.size());
        for (AbstractSubsystem member : members) {
            if (member != null) ordered.add(member);
        }
        allSubsystems = Collections.unmodifiableSet(ordered);
    }

    /** 每个子系统恰好 attach 一次；跨载具转移过来的已 attach 实例只重新订阅。 */
    private void attachSubsystem(AbstractSubsystem subSystem) {
        if (subSystem.isAttached()) return;
        subSystem.onAttach();
    }

    public void removeSubsystems(Collection<AbstractSubsystem> subSystems, boolean transferToAnotherVehicle) {
        if (subSystems.isEmpty()) return;
        for (AbstractSubsystem subSystem : subSystems) detachSubsystem(subSystem, transferToAnotherVehicle);
        Set<AbstractSubsystem> removing = new HashSet<>(subSystems);
        LinkedHashSet<AbstractSubsystem> ordered = new LinkedHashSet<>(allSubsystems);
        if (ordered.removeAll(removing)) publishMembers(ordered);
    }

    public void removeSubsystem(AbstractSubsystem subSystem, boolean transferToAnotherVehicle) {
        detachSubsystem(subSystem, transferToAnotherVehicle);
        LinkedHashSet<AbstractSubsystem> ordered = new LinkedHashSet<>(allSubsystems);
        if (ordered.remove(subSystem)) publishMembers(ordered);
    }

    /** 脱离生命周期；转移给其它装配体的实例保持订阅与 attach 状态，由接收方按新顺序注册。 */
    private void detachSubsystem(AbstractSubsystem subSystem, boolean transferToAnotherVehicle) {
        if (transferToAnotherVehicle) return;
        subSystem.onDetach();
        unsubscribe(subSystem);
    }

    public void onVehicleStructureChanged() {
        // 重建总线订阅
        rebuildSubscriptions();
        initialized = true;
        // 两阶段：先让所有子系统清空由拓扑推导的缓存，再统一重新握手。
        // 合成一趟时遍历顺序决定成败——后清空的子系统会把先握手得到的结果抹掉。
        allSubsystems.forEach(AbstractSubsystem::onVehicleStructurePreChanged);
        allSubsystems.forEach(AbstractSubsystem::onVehicleStructureChanged);
        rebuildAllEnergyPaths();
        energyGrid.rebuildFrom(allSubsystems);
    }

    /**
     * 主线程：清空总线的输入频道与 MoLang 信号存储。结构变化时由装配体在重建首跳表之前调用。
     */
    public void clearSignalStorage() {
        channels.clear();
        signalStorage.clear();
    }

    /**
     * 给单个玩家补发本装配体全部子系统的同步值全量快照。<br>
     * 用在载具刚在客户端建好之后：那些"载入后不再变化"的值走不到变化广播
     * （见 {@link AbstractSubsystem#forEachSyncedValue(Consumer)}），只能由这里补一次。
     * <p>
     * 全量样本与变化批次用同一种包（{@link SubsystemSyncPayload}）：它携带着每个字段的当前值，
     * 客户端按字段并入待应用值表，因此后到的值与先到的增量不会拼出中间态。
     * <b>不改动任何同步状态</b>：既不清脏标记，也不强制标脏，因此其他在线玩家不会因此多收包。
     * <p>
     * <b>调用线程：</b>主线程（服务端）。
     *
     * @param assemblyId 本装配体身份，随批次下发供客户端核对归属
     * @param player     接收方
     */
    public void pushAllTo(UUID assemblyId, ServerPlayer player) {
        List<SubsystemSyncPayload.Entry> entries = new ArrayList<>();
        Set<AbstractSubsystem> snapshot = allSubsystems;
        for (AbstractSubsystem subsystem : snapshot) {
            if (subsystem == null) continue;
            // 只写本地模拟意义的值（如作业进度基线）在这一刻刷新成当前值，
            // 新加入的客户端拿到的才是"现在"而不是上次写入时的旧值
            subsystem.refreshSyncBaseline();
            List<SynchedEntityData.DataValue<?>> values = new ArrayList<>();
            subsystem.forEachSyncedValue(values::add);
            SubPart host = subsystem.getSubPart();
            entries.add(new SubsystemSyncPayload.Entry(host.getId(), host.subsystemIndex(subsystem), values));
        }
        if (entries.isEmpty()) return;
        PacketDistributor.sendToPlayer(player, new SubsystemSyncPayload(assemblyId, entries));
    }

    public void rebuildAllEnergyPaths() {
        for (AbstractSubsystem sub : allSubsystems) {
            if (sub instanceof IMechPowerProducer producer) {
                producer.rebuildMechPowerTargets();
            }
        }
    }

    @Override
    public ConcurrentMap<String, SignalChannel> getSignalInputChannels() {
        return channels;
    }

    /** 装配体在信号路由中的保留地址：{@code "global"} */
    @Override
    public String getSignalAddress() {
        return "global";
    }

    @Override
    public ConcurrentMap<String, Object> getSignalStorage() {
        return signalStorage;
    }

    /**
     * 当总线自身作为路由目标收到信号时（"global" 目标名），
     * 将信号转发给所有订阅了此频道的子系统，并写入 signalStorage。
     */
    @Override
    public SignalResult onSignalUpdated(String channelName, ISignalSender sender) {
        SignalChannel sigChannel = channels.get(channelName);
        if (sigChannel != null) {
            for (Map.Entry<ISignalSender, Object> entry : sigChannel.entrySet()) {
                signalStorage.put(channelName, entry.getValue());
                broadcast(channelName, entry.getValue(), entry.getKey());
            }
        }
        return SignalResult.PASS;
    }

    /**
     * 事件到达装配体总线（{@code global}）：交给所属装配体把事件广播给全部零件，各零件按自己的
     * 控制器决定是否响应。订阅者的分发由 {@link ISignalBus#broadcastEvent} 在调用本方法之后完成。
     * <p>装配体的零件扇出不经过线网，也不计入投递会话的重访判定；没有装配体的独立控制器只向
     * 订阅者分发，不产生零件扇出。</p>
     * <p><b>调用线程：</b>发送方线程；交互点击与机构通知都在主线程，动画状态机因此不会被物理步推进。</p>
     */
    @Override
    public void onSignalEvent(String channelName, ISignalSender sender, Object payload) {
        IPartAssembly assembly = this.assembly;
        if (assembly != null) assembly.broadcastInteractionEvent(channelName);
    }

    /**
     * {@code global} 与 {@code local} 取值一致：总线本身一律接受经连接器路由的输入，频道筛选交给订阅者。
     * <p>
     * 端口转发要求 {@code acceptAllRoutingInput() || getAcceptedChannels().contains(到达频道)}。总线没有排他性的
     * 接受列表，它的职责是把频道交给订阅者、由订阅者各自的声明决定收不收（{@link #broadcast} 与
     * {@link ISignalBus#broadcastEvent} 都做这层筛选）。返回 {@code false} 会让经连接点指向 {@code global} 的
     * 频道在端口处被静默丢掉，只读发现也随之表现为不可达——变的是线路能力，不是接受语义。
     */
    @Override
    public boolean acceptAllRoutingInput() {
        return true;
    }

    // ===== ISignalBus 实现 =====

    @Override
    public Set<ISignalReceiver> getAllSubscribers() {
        return busSubscribers;
    }

    @Override
    public void subscribe(ISignalReceiver subscriber) {
        busSubscribers.add(subscriber);
    }

    @Override
    public void unsubscribe(ISignalReceiver subscriber) {
        busSubscribers.remove(subscriber);
    }

    /**
     * 清除所有旧的订阅关系，为所有子系统重新注册订阅。
     * 在载具结构变化时调用。
     */
    private void rebuildSubscriptions() {
        busSubscribers.clear();
        busSubscribers.addAll(allSubsystems);
    }

    // ===== 销毁 =====

    public void destroy() {
        allSubsystems.forEach(AbstractSubsystem::onDetach);
        discardUninitialized();
    }

    /** Releases construction-local state without gameplay detach effects such as inventory drops. */
    public void discardUninitialized() {
        allSubsystems = Set.of();
        busSubscribers.clear();
        channels.clear();
        resources.clear();
        signalStorage.clear();
        initialized = false;
    }

    private float getPhysicsTps() {
        return SparkLevel.getPhysicsLevel(getLevel()).getTps();
    }
}
