package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.api.SparkLevel;
import io.github.sweetzonzi.machine_max.common.mech.energy.EnergyGrid;
import io.github.sweetzonzi.machine_max.common.mech.energy.IMechPowerProducer;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalBus;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.network.payload.SubsystemSyncPayload;
import lombok.Getter;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Getter
public class SubsystemController implements ISignalBus {
    public final String name = "subsystemController";
    public final Level level;
    public final ConcurrentMap<String, SignalChannel> channels = new ConcurrentHashMap<>();//可查可改
    public final ConcurrentMap<String, Object> signalStorage = new ConcurrentHashMap<>();//部件内供Molang查询的信号
    public final ConcurrentMap<String, Object> resources = new ConcurrentHashMap<>();//可查可改
    public final Set<AbstractSubsystem> allSubsystems = new CopyOnWriteArraySet<>();
    public final EnergyGrid energyGrid = new EnergyGrid();

    // 总线订阅者集合（广播时遍历此集合，运行时检查接受条件）
    private final Set<ISignalReceiver> busSubscribers = ConcurrentHashMap.newKeySet();

    public SubsystemController(Level level) {
        this.level = level;
    }

    public SubsystemController(VehicleCore vehicle) {
        this.level = vehicle.getLevel();
    }

    // ===== 现有生命周期方法（整合总线订阅） =====

    /** 调用所有子系统的 onTick，在主线程 20tps 下执行 */
    public void tick() {
        for (AbstractSubsystem subsystem : allSubsystems) {
            if (subsystem != null) {
                subsystem.onTick();
            }
        }
    }

    /** 物理 tick 前调用所有子系统的 onPrePhysicsTick（物理引擎步进前更新控制量） */
    public void prePhysicsTick() {
        for (AbstractSubsystem subsystem : allSubsystems) {
            if (subsystem != null) {
                subsystem.onPrePhysicsTick();
            }
        }
        energyGrid.prePhysicsTick(getPhysicsTps());
    }

    /** 物理 tick 后调用所有子系统的 onPostPhysicsTick（物理引擎步进后读取反馈） */
    public void postPhysicsTick() {
        for (AbstractSubsystem subsystem : allSubsystems) {
            if (subsystem != null) {
                subsystem.onPostPhysicsTick();
            }
        }
    }

    /**
     * 初始化子系统，连接机械功传递链路，调用{@link AbstractSubsystem#onAttach}方法并注册总线订阅
     */
    public void initAllSubsystems() {
        rebuildAllEnergyPaths();
        allSubsystems.forEach(this::subscribe);
        allSubsystems.forEach(AbstractSubsystem::onAttach);
    }

    /**
     * 将子系统加入控制器，等待初始化
     */
    public void addSubsystems(Collection<AbstractSubsystem> subSystems) {
        allSubsystems.addAll(subSystems);
    }

    public void removeSubsystems(Collection<AbstractSubsystem> subSystems, boolean transferToAnotherVehicle) {
        for (AbstractSubsystem subSystem : subSystems) this.removeSubsystem(subSystem, transferToAnotherVehicle);
        allSubsystems.removeAll(subSystems);
    }

    public void removeSubsystem(AbstractSubsystem subSystem, boolean transferToAnotherVehicle) {
        if (!transferToAnotherVehicle) {
            subSystem.onDetach();
            unsubscribe(subSystem);
        }
        allSubsystems.remove(subSystem);
    }

    public void onVehicleStructureChanged() {
        // 重建总线订阅
        rebuildSubscriptions();
        // 两阶段：先让所有子系统清空由拓扑推导的缓存，再统一重新握手。
        // 合成一趟时遍历顺序决定成败——后清空的子系统会把先握手得到的结果抹掉。
        allSubsystems.forEach(AbstractSubsystem::onVehicleStructurePreChanged);
        allSubsystems.forEach(AbstractSubsystem::onVehicleStructureChanged);
        rebuildAllEnergyPaths();
        energyGrid.rebuildFrom(allSubsystems);
    }

    /**
     * 给单个玩家补发本装配体全部子系统的同步值全量快照。<br>
     * 用在载具刚在客户端建好之后：那些"载入后不再变化"的值走不到变化广播
     * （见 {@link AbstractSubsystem#forEachSyncedValue(Consumer)}），只能由这里补一次。
     * <p>
     * 每个子系统一个包，载荷格式与变化广播完全相同（{@link SubsystemSyncPayload}）。<b>不改动任何
     * 同步状态</b>：既不清脏标记，也不强制标脏，因此其他在线玩家不会因此多收包。
     * <p>
     * <b>调用线程：</b>主线程（服务端）。
     *
     * @param player 接收方
     */
    public void pushAllTo(ServerPlayer player) {
        for (AbstractSubsystem subsystem : allSubsystems) {
            List<SynchedEntityData.DataValue<?>> values = new ArrayList<>();
            subsystem.forEachSyncedValue(values::add);
            PacketDistributor.sendToPlayer(player,
                    new SubsystemSyncPayload(subsystem.getSubPart().getId(), subsystem.getName(), values));
        }
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

    // ===== 直送模式回调：respondCallbackToSender =====

    /**
     * 转发回调请求给所有接受此频道且可作为发送者的订阅者。
     * <p>
     * 当 SubsystemController 作为路由目标收到带回调的信号时（"global" 目标名），
     * 在 onSignalUpdated / broadcast 之前遍历所有订阅者，让每个订阅者
     * 自主决定是否向原始发送者回传 callback。
     * <p>
     * 这样 Engine 等终端子系统直接回调到 CarController，sender 是 Engine 而非 Controller。
     */
    @Override
    public void respondCallbackToSender(
            String channelName, ISignalSender sender, Object value,
            boolean requiresImmediateCallback, boolean callbackReturnsSignalValue) {
        if (!requiresImmediateCallback) return;
        for (ISignalReceiver sub : getAllSubscribers()) {
            if (sub.acceptAllBroadcastInput() || sub.getAcceptedChannels().contains(channelName)) {
                sub.respondCallbackToSender(channelName, sender, value,
                        requiresImmediateCallback, callbackReturnsSignalValue);
            }
        }
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
        allSubsystems.clear();
        busSubscribers.clear();
        channels.clear();
        resources.clear();
        signalStorage.clear();
    }

    private float getPhysicsTps() {
        return SparkLevel.getPhysicsLevel(getLevel()).getTps();
    }
}
