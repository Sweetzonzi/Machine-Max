package io.github.tt432.machinemax.common.vehicle.signal;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.Part;
import io.github.tt432.machinemax.common.vehicle.subsystem.AbstractSubsystem;

import java.util.*;

public interface ISignalSender {
    Map<String, List<String>> getTargetNames();

    Map<String, Map<String, ISignalReceiver>> getTargets();//信号名称->接收者名称->接收者 Signal key -> receiver name -> receiver

    default Map<String, Set<ISignalReceiver>> getCallbackTargets(){return Map.of();}

    default void clearCallbackTargets() {getCallbackTargets().clear();}

    Part getPart();

    /**
     * 将发送的信号输出类型全部重置为空信号
     */
    default void resetSignalOutputs() {
        if (this instanceof Port) return;//信号端口只应转发信号，不应对其输出信号进行操作
        for (Map.Entry<String, Map<String, ISignalReceiver>> entry : getTargets().entrySet()) {
            entry.getValue().forEach((receiverName, signalReceiver) -> {
                var emptySignal = new EmptySignal();
                signalReceiver.getSignalInputs().computeIfAbsent(entry.getKey(), k -> new Signals()).put(this, emptySignal);
                signalReceiver.onSignalUpdated(entry.getKey());
            });
        }
        for (Map.Entry<String, Set<ISignalReceiver>> entry : getCallbackTargets().entrySet()){
            entry.getValue().forEach((signalReceiver) -> {
                var emptySignal = new EmptySignal();
                signalReceiver.getSignalInputs().computeIfAbsent(entry.getKey(), k -> new Signals()).put(this, emptySignal);
                signalReceiver.onSignalUpdated(entry.getKey());
            });
        }
    }

    /**
     * 将信号发送到所有接收此信号的目标，所有目标收到同名同数值的信号
     * @param signalKey 信号名称
     * @param signalValue 信号值
     */
    default void sendSignalToAllTargets(String signalKey, Object signalValue) {
        if (getTargets().containsKey(signalKey))
            getTargets().get(signalKey).forEach((receiverName, signalReceiver) -> {
                signalReceiver.getSignalInputs().computeIfAbsent(signalKey, k -> new Signals()).put(this, signalValue);
                signalReceiver.onSignalUpdated(signalKey);
            });
    }

    /**
     * 将信号发送到指定接收者，可用于发送同名不同值信号给不同目标
     * @param targetName 接收者名称
     * @param signalKey 信号名称
     * @param signalValue 信号值
     */
    default void sendSignalToTarget(String targetName, String signalKey, Object signalValue) {
        if (getTargets().containsKey(signalKey)) {
            ISignalReceiver signalReceiver =  getTargets().get(signalKey).get(targetName);
            if (signalReceiver != null) {
                signalReceiver.getSignalInputs().computeIfAbsent(signalKey, k -> new Signals()).put(this, signalValue);
                signalReceiver.onSignalUpdated(signalKey);
            }
        }
    }

    default void sendCallbackToListeners(String signalKey, Object signalValue) {
        if (this instanceof ISignalReceiver) {
            var targets = getCallbackTargets().computeIfAbsent(signalKey, k -> new HashSet<>());
            for (ISignalReceiver target : targets) {
                target.getSignalInputs().computeIfAbsent(signalKey, k -> new Signals()).put(this, signalValue);
                target.onSignalUpdated(signalKey);
            }
        }
    }

    default void sendCallbackToListener(String signalKey, ISignalReceiver receiver, Object signalValue){
        if (this instanceof ISignalReceiver) {
            receiver.getSignalInputs().computeIfAbsent(signalKey, k -> new Signals()).put(this, signalValue);
            receiver.onSignalUpdated(signalKey);
        }
    }

    /**
     * 设置信号传输目标
     */
    default void setTargetFromNames() {
        if (getPart() != null) {
            Map<String, AbstractSubsystem> subSystems = getPart().subsystems;
            Map<String, Port> ports = new HashMap<>();
            getPart().allConnectors.forEach((name, connector) -> ports.put(name, connector.port));
            Map<String, ISignalReceiver> signalReceivers = new HashMap<>(2);
            for (Map.Entry<String, List<String>> entry : getTargetNames().entrySet()) {
                if (entry.getKey().isEmpty()) continue;
                getReceiversFromNames(entry.getValue(), getPart(), subSystems, ports).forEach(receiver -> signalReceivers.put(receiver.getName(), receiver));
                getTargets().put(entry.getKey(), signalReceivers);
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
                if (ownerPart.vehicle != null)
                    targets.add(ownerPart.vehicle.subSystemController);
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
