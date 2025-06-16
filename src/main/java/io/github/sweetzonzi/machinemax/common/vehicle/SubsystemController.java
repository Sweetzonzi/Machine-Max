package io.github.sweetzonzi.machinemax.common.vehicle;

import io.github.sweetzonzi.machinemax.common.vehicle.signal.ISignalReceiver;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.SignalChannel;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;
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
    public final ConcurrentMap<String, Object> resources = new ConcurrentHashMap<>();//可查可改
    public final Set<AbstractSubsystem> allSubsystems = new CopyOnWriteArraySet<>();

    public SubsystemController(VehicleCore core) {
        CORE = core;
    }

    public void tick() {
//        Hook.run(this);
        for (AbstractSubsystem subsystem : allSubsystems) {
            if (subsystem != null) {
                subsystem.onTick();
            }
        }
    }

    public void prePhysicsTick() {
//        Hook.run(this);
        for (AbstractSubsystem subsystem : allSubsystems) {
            if (subsystem != null) {
                subsystem.onPrePhysicsTick();
            }
        }
    }

    public void postPhysicsTick() {
//        Hook.run(this);
        for (AbstractSubsystem subsystem : allSubsystems) {
            if (subsystem != null) {
                subsystem.onPostPhysicsTick();
            }
        }
    }

    public void addSubsystems(Collection<AbstractSubsystem> subSystems, boolean newlyAdded) {
        for (AbstractSubsystem subSystem : subSystems) this.addSubsystem(subSystem, newlyAdded);
        allSubsystems.addAll(subSystems);
    }

    public void addSubsystem(AbstractSubsystem subSystem, boolean newlyAdded) {
        if (newlyAdded) subSystem.onAttach();
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
