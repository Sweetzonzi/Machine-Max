package io.github.tt432.machinemax.common.vehicle;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.signal.ISignalReceiver;
import io.github.tt432.machinemax.common.vehicle.signal.MoveInputSignal;
import io.github.tt432.machinemax.common.vehicle.signal.Signals;
import io.github.tt432.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Getter
public class SubsystemController implements ISignalReceiver {
    public final VehicleCore CORE;
    public final ConcurrentMap<String, Signals> signals = new ConcurrentHashMap<>();//可查可改
    public final ConcurrentMap<String, Object> resources = new ConcurrentHashMap<>();//可查可改
    public final Set<AbstractSubsystem> allSubsystems = new HashSet<>();

    public SubsystemController(VehicleCore core) {
        CORE = core;
    }

    public void tick() {
        allSubsystems.forEach(AbstractSubsystem::onTick);
        if (signals.get("move_input") != null && signals.get("move_input").getFirst() instanceof MoveInputSignal moveInputSignal)
            MachineMax.LOGGER.info("from vehicle:" + Arrays.toString(moveInputSignal.getMoveInput()));
    }
    public void physicsTick() {
        allSubsystems.forEach(AbstractSubsystem::onPhysicsTick);
    }

    public void addSubsystems(Collection<AbstractSubsystem> subSystems){
        subSystems.forEach(this::addSubsystem);
        allSubsystems.addAll(subSystems);
    }

    public void addSubsystem(AbstractSubsystem subSystem) {
        subSystem.onAttach();
        allSubsystems.add(subSystem);
    }

    public void removeSubsystems(Collection<AbstractSubsystem> subSystems) {
        subSystems.forEach(this::removeSubsystem);
        allSubsystems.removeAll(subSystems);
    }

    public void removeSubsystem(AbstractSubsystem subSystem) {
        subSystem.onDetach();
        allSubsystems.remove(subSystem);
    }

    @Override
    public ConcurrentMap<String, Signals> getSignalInputs() {
        return signals;
    }

    public void destroy(){
        allSubsystems.forEach(AbstractSubsystem::onDetach);
        allSubsystems.clear();
        signals.clear();
        resources.clear();
    }
}
