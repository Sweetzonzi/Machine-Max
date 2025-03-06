package io.github.tt432.machinemax.common.vehicle.signal;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.Part;
import io.github.tt432.machinemax.common.vehicle.VehicleCore;
import io.github.tt432.machinemax.common.vehicle.subsystem.AbstractSubsystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

public interface ISignalSender {
    ConcurrentMap<String, Signals> getSignalOutputs();

    Map<String, List<String>> getTargetNames();

    Map<String, List<ISignalReceiver>> getTargets();

    Part getPart();

    /**
     * 将发送的信号输出类型全部重置为空信号
     */
    default void resetSignalOutputs(){
        for (String signalKey :getTargetNames().keySet()){
            getSignalOutputs().put(signalKey, new Signals());
        }
    }

    default void send(String signalKey, Object signalValue) {
        //TODO:发送给载具时的处理
        getSignalOutputs().getOrDefault(signalKey, new Signals()).put(this, signalValue);
        getTargets().get(signalKey).forEach(signalReceiver -> signalReceiver.onSignalUpdated(signalKey, signalValue));
    }

    /**
     * 设置信号传输目标
     */
    default void setTargetFromNames(){
        if (getPart() != null) {
            Map<String, AbstractSubsystem> subSystems = getPart().subsystems;
            Map<String, Port> ports = new HashMap<>();
            getPart().allConnectors.forEach((name, connector) -> ports.put(name, connector.port));
            List<ISignalReceiver> signalReceivers;
            for (Map.Entry<String, List<String>> entry : getTargetNames().entrySet()) {
                signalReceivers = getReceiversFromNames(entry.getValue(), getPart(), subSystems, ports);
                getTargets().put(entry.getKey(), signalReceivers);
            }
            //设置连接关系
            setTargetConnections(this);
        }
    }

    /**
     * 设置信号传输关系
     */
    default void setTargetConnections(ISignalSender sender) {
        for (Map.Entry<String, List<ISignalReceiver>> entry : getTargets().entrySet()) {
            String signalKey = entry.getKey();
            List<ISignalReceiver> signalReceivers = entry.getValue();
            for (ISignalReceiver signalReceiver : signalReceivers) {
                signalReceiver.getSignalInputs().put(signalKey, sender.getSignalOutputs().get(signalKey));
            }
        }
    }

    default List<ISignalReceiver> getReceiversFromNames(
            List<String> targetNames,
            Part ownerPart,
            Map<String, AbstractSubsystem> subSystems,
            Map<String, Port> ports) {
        List<ISignalReceiver> targets = new ArrayList<>();
        for (String targetName : targetNames) {
            if (targetName.equals("vehicle")) {
                continue;//发往载具本身的信号于发送时处理
            } else if (targetName.equals("part")) {
                targets.add(ownerPart);
            } else if (subSystems.containsKey(targetName)) {
                AbstractSubsystem subSystem = subSystems.get(targetName);
                if (subSystem instanceof ISignalReceiver) {
                    targets.add((ISignalReceiver) subSystem);
                }
            } else if (ports.containsKey(targetName)) {
                Port port = ports.get(targetName);
                targets.add(port);
            } else MachineMax.LOGGER.error("未在部件内找到目标端口或子系统: {}", targetName);
        }
        return targets;
    }
}
