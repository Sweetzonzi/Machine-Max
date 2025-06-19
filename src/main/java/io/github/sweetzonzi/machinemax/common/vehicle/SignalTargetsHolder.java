package io.github.sweetzonzi.machinemax.common.vehicle;

import io.github.sweetzonzi.machinemax.common.vehicle.signal.EmptySignal;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.MoveInputSignal;
import io.github.sweetzonzi.machinemax.common.vehicle.signal.RegularInputSignal;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machinemax.util.data.KeyInputMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SignalTargetsHolder {
    public Map<String, List<String>> moveSignalTargets = new HashMap<>();
    public Map<String, List<String>> viewSignalTargets = new HashMap<>();
    public Map<String, List<String>> regularSignalTargets = new HashMap<>();
    private final AbstractSubsystem subsystem;

    public SignalTargetsHolder(AbstractSubsystem master) {
        subsystem = master;
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
        if (!moveSignalTargets.isEmpty()) {
            for (String signalKey : moveSignalTargets.keySet()) {
                subsystem.sendSignalToAllTargets(signalKey, new MoveInputSignal(inputs, conflicts));
            }
            for (int i = 0; i < 6; i++) {
                if (inputs[i] != 0 && subsystem.getPart() != null && subsystem.getPart().vehicle != null) {
                    break;
                }
            }
            subsystem.getPart().vehicle.activate();
        }
    }

    public void setRegularInputSignal(KeyInputMapping inputType, int tickCount) {
        if (!regularSignalTargets.isEmpty()) {
            for (String signalKey : regularSignalTargets.keySet()) {
                subsystem.sendSignalToAllTargets(signalKey, new RegularInputSignal(inputType, tickCount));
            }
            subsystem.getPart().vehicle.activate();
        }
    }

    public void setViewInputSignal() {
        if (!viewSignalTargets.isEmpty()) {
            for (String signalKey : viewSignalTargets.keySet()) {
                subsystem.sendSignalToAllTargets(signalKey, new EmptySignal());
            }
            subsystem.getPart().vehicle.activate();
        }
    }
}
