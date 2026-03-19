package io.github.sweetzonzi.machine_max.common.vehicle.subsystem;

import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr.AbstractSubsystemAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr.BasicSubsystemDynamicAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.EmptySignal;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.MoveInputSignal;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.RegularInputSignal;
import io.github.sweetzonzi.machine_max.util.data.KeyInputMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

abstract public class AbstractControllableSubsystem extends BasicSubsystem {

    public Map<String, List<String>> moveSignalTargets = new HashMap<>();
    public Map<String, List<String>> viewSignalTargets = new HashMap<>();
    public Map<String, List<String>> regularSignalTargets = new HashMap<>();

    protected AbstractControllableSubsystem(ISubsystemHost owner, String name, BasicSubsystemDynamicAttr attr) {
        super(owner, name, attr);
    }

    public void setUp(Map<String, List<String>> moveSignalTargets, Map<String, List<String>> viewSignalTargets, Map<String, List<String>> regularSignalTargets) {
        this.moveSignalTargets = moveSignalTargets;
        this.viewSignalTargets = viewSignalTargets;
        this.regularSignalTargets = regularSignalTargets;
    }

    public Map<String, List<String>> setUpTargets(Map<String, List<String>> map) {
        map.putAll(moveSignalTargets);
        map.putAll(regularSignalTargets);
        map.putAll(viewSignalTargets);
        return map;
    }

    public void resetMoveSignalTarget(String typeName) {
        moveSignalTargets.remove(typeName);
    }

    public void addMoveSignalTarget(String typeName, String connectorName) {
        if (!moveSignalTargets.containsKey(typeName)) moveSignalTargets.put(typeName, new ArrayList<>());
        moveSignalTargets.get(typeName).add(connectorName);
    }

    public void deleteMoveSignalTarget(String typeName, String connectorName) {
        if (moveSignalTargets.containsKey(typeName)) {
            moveSignalTargets.get(typeName).remove(connectorName);
            if (moveSignalTargets.get(typeName).isEmpty()) moveSignalTargets.remove(typeName);
        }
    }

    public void setMoveInputSignal(byte[] inputs, byte[] conflicts) {
        if (!moveSignalTargets.isEmpty() && this.isActive()) {
            for (String signalKey : moveSignalTargets.keySet()) {
                this.sendSignalToAllTargets(signalKey, new MoveInputSignal(inputs, conflicts));
            }
            for (int i = 0; i < 6; i++) {
                if (inputs[i] != 0 && this.getSubPart() != null && this.getOwner().getSubPart().part.vehicle != null) {
                    break;
                }
            }
            this.getOwner().getSubPart().part.vehicle.activate();
        } else {
            for (String signalKey : moveSignalTargets.keySet()) {
                this.sendSignalToAllTargets(signalKey, EmptySignal.INSTANCE);
            }
        }
    }

    public void setRegularInputSignal(KeyInputMapping inputType, int tickCount) {
        if (!regularSignalTargets.isEmpty() && this.isActive()) {
            for (String signalKey : regularSignalTargets.keySet()) {
                this.sendSignalToAllTargets(signalKey, new RegularInputSignal(inputType, tickCount));
            }
            this.getOwner().getSubPart().part.vehicle.activate();
        } else {
            for (String signalKey : regularSignalTargets.keySet()) {
                this.sendSignalToAllTargets(signalKey, EmptySignal.INSTANCE);
            }
        }
    }

    public void setViewInputSignal() {
        if (!viewSignalTargets.isEmpty() && this.isActive()) {
            for (String signalKey : viewSignalTargets.keySet()) {
                this.sendSignalToAllTargets(signalKey, EmptySignal.INSTANCE);
            }
            this.getOwner().getSubPart().part.vehicle.activate();
        } else {
            for (String signalKey : viewSignalTargets.keySet()) {
                this.sendSignalToAllTargets(signalKey, EmptySignal.INSTANCE);
            }
        }
    }
}
