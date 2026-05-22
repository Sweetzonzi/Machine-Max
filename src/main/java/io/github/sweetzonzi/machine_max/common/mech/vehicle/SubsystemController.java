package io.github.sweetzonzi.machine_max.common.mech.vehicle;

import cn.solarmoon.spark_core.api.SparkLevel;
import io.github.sweetzonzi.machine_max.common.mech.energy.EnergyGrid;
import io.github.sweetzonzi.machine_max.common.mech.energy.IMechPowerProducer;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalBus;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Getter
public class SubsystemController implements ISignalBus {
    public final String name = "vehicle";
    public final VehicleCore CORE;
    public final ConcurrentMap<String, SignalChannel> channels = new ConcurrentHashMap<>();//可查可改
    public final ConcurrentMap<String, Object> signalStorage = new ConcurrentHashMap<>();//部件内供Molang查询的信号
    public final ConcurrentMap<String, Object> resources = new ConcurrentHashMap<>();//可查可改
    public final Set<AbstractSubsystem> allSubsystems = new CopyOnWriteArraySet<>();
    public final EnergyGrid energyGrid = new EnergyGrid();

    // 总线订阅者集合（广播时遍历此集合，运行时检查接受条件）
    private final Set<ISignalReceiver> busSubscribers = ConcurrentHashMap.newKeySet();

    public SubsystemController(VehicleCore core) {
        CORE = core;
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
        allSubsystems.forEach(sub -> {
            sub.onAttach();
            subscribe(sub);
        });
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
        allSubsystems.forEach(AbstractSubsystem::onVehicleStructureChanged);
        rebuildAllEnergyPaths();
        energyGrid.rebuildFrom(allSubsystems);
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

    /**
     * 当总线自身作为路由目标收到信号时（"vehicle" 目标名），
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
        allSubsystems.clear();
        busSubscribers.clear();
        channels.clear();
        resources.clear();
        signalStorage.clear();
    }

    private float getPhysicsTps() {
        return SparkLevel.getPhysicsLevel(CORE.level).getTps();
    }
}
