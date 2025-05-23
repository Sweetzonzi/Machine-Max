package io.github.sweetzonzi.machinemax.common.vehicle.signal;

import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;

import java.util.*;

public interface ISignalSender {
    /**
     * 在此填入各个信号名对应的接收者名列表，用于自动组织信号传输关系。<p>
     * Return a map of signal names to a list of receiver names here, to automatically organize signal transfer.
     *
     * @return 信号频道->接收者名称列表 Map of signal channels to a list of receiver names.
     */
    Map<String, List<String>> getTargetNames();

    Map<String, Map<String, ISignalReceiver>> getTargets();//信号频道名称->接收者名称->接收者 Signal channel name -> receiver name -> receiver

    default Map<String, Set<ISignalReceiver>> getCallbackTargets() {
        return Map.of();
    }

    default void addCallbackTarget(String signalChannel, ISignalReceiver target) {
        getCallbackTargets().computeIfAbsent(signalChannel, k -> new HashSet<>()).add(target);
    }

    default void removeCallbackTarget(String signalChannel, ISignalReceiver target) {
        getCallbackTargets().computeIfAbsent(signalChannel, k -> new HashSet<>()).remove(target);
    }

    default void clearCallbackTargets() {
        getCallbackTargets().clear();
    }

    Part getPart();

    /**
     * 将发送的信号输出类型全部重置为空信号
     */
    default void resetSignalOutputs() {
        if (this instanceof SignalPort) return;//信号端口只应转发信号，不应对其输出信号进行操作
        for (Map.Entry<String, Map<String, ISignalReceiver>> entry : getTargets().entrySet()) {
            entry.getValue().forEach((receiverName, signalReceiver) -> {
                var emptySignal = new EmptySignal();
                signalReceiver.getSignalInputChannels().computeIfAbsent(entry.getKey(), k -> new SignalChannel()).put(this, emptySignal);
                signalReceiver.onSignalUpdated(entry.getKey(), this);
            });
        }
        for (Map.Entry<String, Set<ISignalReceiver>> entry : getCallbackTargets().entrySet()) {
            entry.getValue().forEach((signalReceiver) -> {
                var emptySignal = new EmptySignal();
                signalReceiver.getSignalInputChannels().computeIfAbsent(entry.getKey(), k -> new SignalChannel()).put(this, emptySignal);
                signalReceiver.onSignalUpdated(entry.getKey(), this);
            });
        }
    }

    /**
     * 将信号发送到所有接收此信号的目标，所有目标收到同名同数值的信号
     *
     * @param signalChannel   信号频道名称
     * @param signalValue 信号值
     */
    default void sendSignalToAllTargets(String signalChannel, Object signalValue) {
        sendSignalToAllTargets(signalChannel, signalValue, false);
    }

    /**
     * 将信号发送到所有接收此信号的目标，所有目标在同一频道收到同数值的信号
     *
     * @param signalChannel             信号频道名
     * @param signalValue               信号值
     * @param requiresImmediateCallback 是否需要即时回调
     */
    default void sendSignalToAllTargets(String signalChannel, Object signalValue, boolean requiresImmediateCallback) {
        if (getTargets().containsKey(signalChannel))
            getTargets().get(signalChannel).forEach((receiverName, signalReceiver) -> {
                signalReceiver.getSignalInputChannels().computeIfAbsent(signalChannel, k -> new SignalChannel()).put(this, signalValue);
                signalReceiver.onSignalUpdated(signalChannel, this);
                if (requiresImmediateCallback && this instanceof ISignalReceiver && signalReceiver instanceof ISignalSender callbackSender) {
                    callbackSender.sendImmediateCallback(signalChannel, (ISignalReceiver) this);
                }
            });
    }

    /**
     * 将信号发送到指定接收者，可用于发送同名不同值信号给不同目标
     *
     * @param targetName    接收者名称
     * @param signalChannel 信号名称
     * @param signalValue   信号值
     */
    default void sendSignalToTarget(String targetName, String signalChannel, Object signalValue) {
        sendSignalToTarget(targetName, signalChannel, signalValue, false);
    }

    /**
     * 将信号发送到指定接收者，可用于发送同名不同值信号给不同目标
     *
     * @param targetName                接收者名称
     * @param signalChannel                 信号名称
     * @param signalValue               信号值
     * @param requiresImmediateCallback 是否需要即时回调
     */
    default void sendSignalToTarget(String targetName, String signalChannel, Object signalValue, boolean requiresImmediateCallback) {
        if (getTargets().containsKey(signalChannel)) {
            ISignalReceiver signalReceiver = getTargets().get(signalChannel).get(targetName);
            if (signalReceiver != null) {
                signalReceiver.getSignalInputChannels().computeIfAbsent(signalChannel, k -> new SignalChannel()).put(this, signalValue);
                signalReceiver.onSignalUpdated(signalChannel, this);
                if (requiresImmediateCallback && this instanceof ISignalReceiver && signalReceiver instanceof ISignalSender callbackSender) {
                    callbackSender.sendImmediateCallback(signalChannel, (ISignalReceiver) this);
                }
            }
        }
    }

    /**
     * 发送回调信号给指定监听器，通常用于握手或检查连接状态
     *
     * @param callbackListener 回调监听器
     */
    default void sendImmediateCallback(String signalChannel, ISignalReceiver callbackListener) {
        sendCallbackToListener("callback", callbackListener, signalChannel);//发回收到的信号类型，尝试握手
    }

    default void sendCallbackToAllListeners(String signalChannel, Object signalValue) {
        if (this instanceof ISignalReceiver) {
            var targets = getCallbackTargets().computeIfAbsent(signalChannel, k -> new HashSet<>());
            for (ISignalReceiver target : targets) {
                target.getSignalInputChannels().computeIfAbsent(signalChannel, k -> new SignalChannel()).put(this, signalValue);
                target.onSignalUpdated(signalChannel, this);
            }
        }
    }

    default void sendCallbackToListener(String signalChannel, ISignalReceiver receiver, Object signalValue) {
        if (this instanceof ISignalReceiver) {
            receiver.getSignalInputChannels().computeIfAbsent(signalChannel, k -> new SignalChannel()).put(this, signalValue);
            receiver.onSignalUpdated(signalChannel, this);
        }
    }

    /**
     * 设置信号传输目标
     */
    default void setTargetFromNames() {
        if (getPart() != null) {
            Map<String, AbstractSubsystem> subSystems = getPart().subsystems;
            Map<String, SignalPort> ports = new HashMap<>();
            getPart().allConnectors.forEach((name, connector) -> ports.put(name, connector.signalPort));
            for (Map.Entry<String, List<String>> entry : getTargetNames().entrySet()) {
                Map<String, ISignalReceiver> signalReceivers = new HashMap<>(2);
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
            Map<String, SignalPort> ports) {
        List<ISignalReceiver> targets = new ArrayList<>();
        for (String targetName : targetNames) {
            if (targetName.equals("vehicle")) {
                if (ownerPart.vehicle != null)
                    targets.add(ownerPart.vehicle.subSystemController);
            } else if (targetName.equals("part")) {
                targets.add(ownerPart);
            } else if (subSystems.containsKey(targetName)) {
                AbstractSubsystem subSystem = subSystems.get(targetName);
                if (subSystem != null) {
                    targets.add(subSystem);
                }
            } else if (ports.containsKey(targetName)) {
                SignalPort signalPort = ports.get(targetName);
                targets.add(signalPort);
            } else MachineMax.LOGGER.error("未在部件内找到目标端口或子系统: {}", targetName);
        }
        return targets;
    }
}
