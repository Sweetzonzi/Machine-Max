package io.github.sweetzonzi.machine_max.common.vehicle;

import io.github.sweetzonzi.machine_max.common.vehicle.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import lombok.Getter;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Getter
public class SubsystemController implements ISignalReceiver {
    public final String name = "vehicle";
    public final VehicleCore CORE;
    public final ConcurrentMap<String, SignalChannel> channels = new ConcurrentHashMap<>();//可查可改
    public final ConcurrentMap<String, Object> signalStorage = new ConcurrentHashMap<>();//部件内供Molang查询的信号
    public final ConcurrentMap<String, Object> resources = new ConcurrentHashMap<>();//可查可改
    public final Set<AbstractSubsystem> allSubsystems = new CopyOnWriteArraySet<>();

    public SubsystemController(VehicleCore core) {
        CORE = core;
    }

    public void tick() {
        for (AbstractSubsystem subsystem : allSubsystems) {
            if (subsystem != null) {
                subsystem.onTick();
            }
        }
    }

    public void prePhysicsTick() {
        for (AbstractSubsystem subsystem : allSubsystems) {
            if (subsystem != null) {
                subsystem.onPrePhysicsTick();
            }
        }
    }

    public void postPhysicsTick() {
        for (AbstractSubsystem subsystem : allSubsystems) {
            if (subsystem != null) {
                subsystem.onPostPhysicsTick();
            }
        }
    }

    /**
     * 初始化子系统，调用{@link AbstractSubsystem#onAttach}方法
     */
    public void initAllSubsystems() {
        allSubsystems.forEach(AbstractSubsystem::onAttach);
    }

    /**
     * 初始化子系统，调用{@link AbstractSubsystem#onAttach}方法
     * @param subsystems 子系统集合
     */
    public void initSubsystems(Collection<AbstractSubsystem> subsystems) {
        subsystems.forEach(AbstractSubsystem::onAttach);
    }

    /**
     * 初始化子系统，调用{@link AbstractSubsystem#onAttach}方法
     * @param subsystem 子系统
     */
    public void initSubsystem(AbstractSubsystem subsystem){
        subsystem.onAttach();
    }

    /**
     * 将子系统加入控制器，等待初始化
     * @param subSystems 子系统集合
     */
    public void addSubsystems(Collection<AbstractSubsystem> subSystems) {
        allSubsystems.addAll(subSystems);
    }

    /**
     * 将子系统加入控制器，等待初始化
     * @param subSystem 子系统
     */
    public void addSubsystem(AbstractSubsystem subSystem) {
        allSubsystems.add(subSystem);
    }

    public void removeSubsystems(Collection<AbstractSubsystem> subSystems, boolean transferToAnotherVehicle) {
        for (AbstractSubsystem subSystem : subSystems) this.removeSubsystem(subSystem, transferToAnotherVehicle);
        allSubsystems.removeAll(subSystems);
    }

    public void removeSubsystem(AbstractSubsystem subSystem, boolean transferToAnotherVehicle) {
        if (!transferToAnotherVehicle) subSystem.onDetach();
        allSubsystems.remove(subSystem);
    }

    public void onVehicleStructureChanged() {
        allSubsystems.forEach(AbstractSubsystem::onVehicleStructureChanged);
    }

    @Override
    public ConcurrentMap<String, SignalChannel> getSignalInputChannels() {
        return channels;
    }

    public void destroy() {
        allSubsystems.forEach(AbstractSubsystem::onDetach);
        allSubsystems.clear();
        channels.clear();
        resources.clear();
    }
}
